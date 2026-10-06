package com.arno.robotica.energy.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.core.progress.Milestones;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyDataMaps.ReactorFuel;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.menu.ReactorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fission Reactor controller. Heat model (all numbers in the server config):
 * <ul>
 *   <li>n fuel rods count as {@code n^0.8} effective rods. One fuel unit (pellet) burns in one effective rod for its
 *       {@code ticks}; heat per tick {@code H = fuel heat * effective rods * (1 - rod insertion)}.</li>
 *   <li>Cooling {@code C = effective rods * (100 + 250 * average coolant points next to a rod)}; coolant points are the
 *       {@code robotica:reactor_coolant} values of the four horizontal neighbours of each rod.</li>
 *   <li>Efficiency {@code 1 + 0.05 * average coolant points}. FE/t = {@code min(H, C) * efficiency * throttle}.</li>
 *   <li>The temperature moves towards {@code 20 + 980 * H / C} C. Above 1000 C the output throttles down (to 25% at
 *       the SCRAM point), at 1800 C the reactor SCRAMs: rods drop in, it cools down and waits for a reset from the GUI.
 *       It never explodes and never touches blocks.</li>
 * </ul>
 * Fuel only burns while the FE buffer has room and the waste has somewhere to go; with less room than a full tick's
 * output it burns only that share (fuel and heat), so no fuel is wasted on FE the buffer cannot take.
 */
public class ReactorControllerBlockEntity extends StructureControllerBlockEntity {
    public static final int FUEL_SLOTS = 3, WASTE_SLOTS = 3;
    public static final double AMBIENT = 20.0;
    public static final String MILESTONE = "reactor_formed";

    public static final CuboidSpec SPEC = CuboidSpec.builder(Component.translatable("multiblock.robotica.fission_reactor"))
            .frame(s -> s.is(EnergyRegistry.REACTOR_CASING.get()), Component.translatable("block.robotica.reactor_casing"))
            .wall(s -> s.is(EnergyRegistry.REACTOR_CASING.get()) || s.is(EnergyRegistry.REACTOR_GLASS.get())
                    || s.is(EnergyRegistry.REACTOR_POWER_PORT.get()) || s.is(EnergyRegistry.REACTOR_ACCESS_PORT.get()),
                    Component.translatable("multiblock.robotica.reactor_wall"))
            .controller(s -> s.is(EnergyRegistry.REACTOR_CONTROLLER.get()))
            .size(EnergyConfig::reactorMinSize, EnergyConfig::reactorMaxSize)
            .build();

    /** Why the reactor is not making power (GUI word). */
    public enum State { NOT_FORMED, RUNNING, NO_FUEL, WASTE_FULL, BUFFER_FULL, RODS_IN, SCRAM }

    public static ItemStackHandler newFuelHandler(Runnable onChanged) {
        return new ItemStackHandler(FUEL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return EnergyDataMaps.reactorFuel(stack) != null;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public static ItemStackHandler newWasteHandler(Runnable onChanged) {
        return new ItemStackHandler(WASTE_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public final ItemStackHandler fuel = newFuelHandler(this::setChanged);
    public final ItemStackHandler waste = newWasteHandler(this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(EnergyConfig.reactorBuffer(), 0, 0, this::setChanged);
    private final IItemHandler access = new AccessHandler();

    // settings and saved state
    private int rodInsertion;
    private boolean scrammed;
    private double temperature = AMBIENT;
    private int burnHeat;
    private int burnTotal;
    private double burnLeft;
    @Nullable
    private Item burnWaste;
    private ItemStack pendingWaste = ItemStack.EMPTY;

    // from the last scan
    private int rods;
    private double effectiveRods;
    private double averageCoolant;

    // live numbers of the last tick
    private double heat, cooling, efficiency = 1.0, throttle = 1.0;
    private int fePerTick;
    private State state = State.NOT_FORMED;
    /** Ticks the working glow stays on after the last heat, so a buffer at the brim does not flicker the block. */
    private int litHold;
    private static final int LIT_HOLD = 40;

    public ReactorControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(EnergyRegistry.REACTOR_BE.get(), pos, blockState);
    }

    // ---------------------------------------------------------------- structure

    @Override
    protected CuboidSpec spec() {
        return SPEC;
    }

    @Override
    protected Visitor newVisitor() {
        return new ReactorVisitor();
    }

    /** Interior: fuel rods in full columns, the rest air or coolant (block data map robotica:reactor_coolant). */
    static final class ReactorVisitor extends Visitor {
        final Set<BlockPos> rods = new HashSet<>();
        final Map<BlockPos, Float> coolant = new HashMap<>();

        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            if (state.is(EnergyRegistry.REACTOR_FUEL_ROD.get())) {
                rods.add(pos);
                return null;
            }
            if (state.isAir()) return null;
            float value = EnergyDataMaps.cooling(state);
            if (EnergyDataMaps.isCoolant(state)) {
                if (value > 0) coolant.put(pos, value);
                return null;
            }
            return StructureProblem.of(pos, "multiblock.robotica.reactor_inside", StructureProblem.name(state), StructureProblem.at(pos));
        }

        @Override
        public StructureProblem finish(BoundingBox box) {
            if (rods.isEmpty()) return StructureProblem.of(null, "multiblock.robotica.reactor_no_rods");
            for (BlockPos rod : rods) {
                for (int y = box.minY() + 1; y < box.maxY(); y++) {
                    BlockPos p = new BlockPos(rod.getX(), y, rod.getZ());
                    if (!rods.contains(p)) return StructureProblem.of(p, "multiblock.robotica.reactor_rod_column", StructureProblem.at(p));
                }
            }
            if (ports(PortBlock.Kind.REACTOR_POWER) == 0) return StructureProblem.of(null, "multiblock.robotica.reactor_no_power_port");
            return null;
        }

        /** Sum over all rods of the coolant points of their four horizontal neighbours, divided by the rod count. */
        double averageCoolant() {
            double total = 0;
            for (BlockPos rod : rods) {
                for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) total += coolant.getOrDefault(rod.relative(dir), 0.0F);
            }
            return rods.isEmpty() ? 0 : total / rods.size();
        }
    }

    @Override
    protected void onFormed(Visitor visitor) {
        ReactorVisitor v = (ReactorVisitor) visitor;
        rods = v.rods.size();
        effectiveRods = Math.pow(rods, EnergyConfig.reactorRodExponent());
        averageCoolant = v.averageCoolant();
    }

    @Override
    protected void onUnformed() {
        rods = 0;
        effectiveRods = 0;
        averageCoolant = 0;
    }

    @Override
    protected String formedMilestone() {
        return MILESTONE;
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void tickController(ServerLevel level, long now) {
        if (!pendingWaste.isEmpty()) pendingWaste = ItemHandlerHelper.insertItem(waste, pendingWaste, false);
        if (!isFormed()) {
            heat = cooling = 0;
            fePerTick = 0;
            state = State.NOT_FORMED;
            coolTowards(AMBIENT);
            setLit(false);
            return;
        }
        pushOut(level, p -> p.kind() == PortBlock.Kind.REACTOR_POWER, Long.MAX_VALUE, energy::getEnergyStored, n -> energy.consume((int) n));
        simulate(1);
    }

    /** One reactor tick. Public for game tests (run it many times without waiting). */
    public void simulate(int ticks) {
        for (int i = 0; i < ticks; i++) step();
    }

    private void step() {
        double insertion = scrammed ? 1.0 : rodInsertion / 100.0;
        double activity = effectiveRods * (1.0 - insertion);
        heat = 0;
        if (scrammed) state = State.SCRAM;
        else if (activity <= 0) state = State.RODS_IN;
        else if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) state = State.BUFFER_FULL;
        else if (!pendingWaste.isEmpty()) state = State.WASTE_FULL;
        else {
            int ready = ensureBurning();
            if (ready == 1) {
                // Burn only the share of a tick whose FE fits into the buffer.
                double share = burnShare(burnHeat * activity);
                heat = burnHeat * activity * share;
                burnLeft -= activity * share;
                if (burnLeft <= 1e-9) finishUnit();
                state = State.RUNNING;
            } else {
                state = ready == 0 ? State.NO_FUEL : State.WASTE_FULL;
            }
        }
        cooling = coolingNow();
        double load = cooling > 0 ? heat / cooling : (heat > 0 ? 10.0 : 0.0);
        double safe = EnergyConfig.reactorSafeTemp(), scram = EnergyConfig.reactorScramTemp();
        coolTowards(AMBIENT + (safe - AMBIENT) * load);
        double minThrottle = EnergyConfig.reactorMinThrottle();
        throttle = temperature <= safe ? 1.0 : Math.max(minThrottle, 1.0 - (1.0 - minThrottle) * (temperature - safe) / (scram - safe));
        efficiency = 1.0 + EnergyConfig.reactorCoolantEfficiency() * averageCoolant;
        double fe = Math.min(heat, cooling) * efficiency * throttle * CoreConfig.generation();
        fePerTick = (int) Math.min(Integer.MAX_VALUE, Math.round(fe));
        if (fePerTick > 0) energy.generate(fePerTick);
        if (!scrammed && temperature >= scram) scram();
        if (heat > 0) litHold = LIT_HOLD;
        else if (litHold > 0) litHold--;
        setLit(litHold > 0);
    }

    private double coolingNow() {
        return effectiveRods * (EnergyConfig.reactorPassiveCooling() + EnergyConfig.reactorCoolantCapacity() * averageCoolant);
    }

    /** 0..1: how much of a full tick's burn the buffer has room for, from the output that heat would give right now. */
    private double burnShare(double fullHeat) {
        double safe = EnergyConfig.reactorSafeTemp(), scram = EnergyConfig.reactorScramTemp();
        double minThrottle = EnergyConfig.reactorMinThrottle();
        double throttleNow = temperature <= safe ? 1.0 : Math.max(minThrottle, 1.0 - (1.0 - minThrottle) * (temperature - safe) / (scram - safe));
        double eff = 1.0 + EnergyConfig.reactorCoolantEfficiency() * averageCoolant;
        double perHeat = eff * throttleNow * CoreConfig.generation();
        double expected = Math.min(fullHeat, coolingNow()) * perHeat;
        double room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        if (expected <= 0 || room >= expected) return 1.0;
        // Output is min(heat, cooling) x perHeat, so burning room / (fullHeat x perHeat) of a tick never overfills.
        return Math.max(0.0, Math.min(1.0, room / (fullHeat * perHeat)));
    }

    private void coolTowards(double target) {
        temperature += (target - temperature) * EnergyConfig.reactorHeatRate();
        if (Math.abs(temperature - target) < 0.01) temperature = target;
    }

    /** 1 burning, 0 no fuel, -1 the next unit's waste has no room. */
    private int ensureBurning() {
        if (burnLeft > 0 && burnTotal > 0) return 1;
        for (int slot = 0; slot < fuel.getSlots(); slot++) {
            ItemStack stack = fuel.getStackInSlot(slot);
            ReactorFuel f = EnergyDataMaps.reactorFuel(stack);
            if (f == null) continue;
            Item wasteItem = f.waste().orElse(null);
            if (wasteItem != null && !ItemHandlerHelper.insertItem(waste, new ItemStack(wasteItem), true).isEmpty()) return -1;
            fuel.extractItem(slot, 1, false);
            burnHeat = f.heat();
            burnTotal = f.ticks();
            burnLeft = f.ticks();
            burnWaste = wasteItem;
            return 1;
        }
        return 0;
    }

    private void finishUnit() {
        if (burnWaste != null) pendingWaste = ItemHandlerHelper.insertItem(waste, new ItemStack(burnWaste), false);
        burnLeft = 0;
        burnTotal = 0;
        burnWaste = null;
    }

    private void scram() {
        scrammed = true;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (level instanceof ServerLevel sl) {
                var player = Milestones.nearbyOwner(sl, worldPosition, owner(), 64);
                if (player != null) player.displayClientMessage(Component.translatable("message.robotica.reactor_scram"), true);
            }
        }
        setChanged();
    }

    // ---------------------------------------------------------------- GUI actions

    public void setRodInsertion(int percent) {
        rodInsertion = Math.max(0, Math.min(100, percent));
        setChanged();
    }

    /** Restarts after a SCRAM once it has cooled down. Returns true when it did. */
    public boolean resetScram() {
        if (!scrammed || temperature > EnergyConfig.reactorResetTemp()) return false;
        scrammed = false;
        setChanged();
        return true;
    }

    // ---------------------------------------------------------------- accessors (GUI, tests)

    public int rodInsertion() { return rodInsertion; }
    public boolean isScrammed() { return scrammed; }
    public double temperature() { return temperature; }
    /** Fuel ticks left of the unit that is burning (fractions while the buffer is nearly full). */
    public double burnLeft() { return burnLeft; }
    public double heat() { return heat; }
    public double cooling() { return cooling; }
    public double efficiency() { return efficiency; }
    public double throttle() { return throttle; }
    public int fePerTick() { return fePerTick; }
    public int rods() { return rods; }
    public double averageCoolant() { return averageCoolant; }
    public State state() { return state; }

    /** Test hook: sets the temperature directly. */
    public void setTemperature(double value) {
        temperature = value;
    }

    // ---------------------------------------------------------------- ports

    @Override
    public int portExtract(PortBlockEntity port, int amount, boolean simulate) {
        if (port.kind() != PortBlock.Kind.REACTOR_POWER) return 0;
        int take = Math.min(amount, energy.getEnergyStored());
        if (!simulate && take > 0) energy.consume(take);
        return take;
    }

    @Override
    public boolean portCanExtract(PortBlockEntity port) {
        return port.kind() == PortBlock.Kind.REACTOR_POWER;
    }

    @Override
    public long portStored() {
        return energy.getEnergyStored();
    }

    @Override
    public long portCapacity() {
        return energy.getMaxEnergyStored();
    }

    @Override
    public IItemHandler portItems() {
        return access;
    }

    /** Access Port view: slots 0-2 take fuel (insert only), slots 3-5 give waste (extract only). */
    private final class AccessHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return FUEL_SLOTS + WASTE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < FUEL_SLOTS ? fuel.getStackInSlot(slot) : waste.getStackInSlot(slot - FUEL_SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < FUEL_SLOTS ? fuel.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < FUEL_SLOTS ? ItemStack.EMPTY : waste.extractItem(slot - FUEL_SLOTS, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < FUEL_SLOTS && fuel.isItemValid(slot, stack);
        }
    }

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ReactorMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        tag.putInt("temp", (int) Math.round(temperature));
        tag.putInt("safe", EnergyConfig.reactorSafeTemp());
        tag.putInt("scramAt", EnergyConfig.reactorScramTemp());
        tag.putInt("resetAt", EnergyConfig.reactorResetTemp());
        tag.putInt("heat", (int) Math.round(heat));
        tag.putInt("cooling", (int) Math.round(cooling));
        tag.putInt("fe", fePerTick);
        tag.putInt("eff", (int) Math.round(efficiency * 100));
        tag.putInt("throttle", (int) Math.round(throttle * 100));
        tag.putInt("rodsIn", rodInsertion);
        tag.putBoolean("scram", scrammed);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("capacity", energy.getMaxEnergyStored());
        tag.putFloat("burn", burnTotal > 0 ? (float) (burnLeft / burnTotal) : 0F);
        tag.putInt("burnHeat", burnTotal > 0 ? burnHeat : 0);
        tag.putInt("rods", rods);
        tag.putInt("coolant", (int) Math.round(averageCoolant * 100));
        tag.putInt("state", state.ordinal());
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("waste", waste.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("rodInsertion", rodInsertion);
        tag.putBoolean("scrammed", scrammed);
        tag.putDouble("temperature", temperature);
        tag.putInt("burnHeat", burnHeat);
        tag.putInt("burnTotal", burnTotal);
        tag.putDouble("burnLeft", burnLeft);
        if (burnWaste != null) tag.putString("burnWaste", BuiltInRegistries.ITEM.getKey(burnWaste).toString());
        if (!pendingWaste.isEmpty()) tag.put("pendingWaste", pendingWaste.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("waste")) waste.deserializeNBT(registries, tag.getCompound("waste"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        rodInsertion = tag.getInt("rodInsertion");
        scrammed = tag.getBoolean("scrammed");
        temperature = tag.contains("temperature") ? tag.getDouble("temperature") : AMBIENT;
        burnHeat = tag.getInt("burnHeat");
        burnTotal = tag.getInt("burnTotal");
        burnLeft = tag.getDouble("burnLeft");
        burnWaste = tag.contains("burnWaste") ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(tag.getString("burnWaste"))).orElse(null) : null;
        pendingWaste = tag.contains("pendingWaste") ? ItemStack.parseOptional(registries, tag.getCompound("pendingWaste")) : ItemStack.EMPTY;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : new ItemStackHandler[]{fuel, waste}) {
            for (int i = 0; i < handler.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
        }
        if (!pendingWaste.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pendingWaste);
    }
}
