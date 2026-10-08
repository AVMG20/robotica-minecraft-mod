package com.arno.robotica.energy.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyDataMaps.ReactorCore;
import com.arno.robotica.energy.EnergyDataMaps.ReactorFuel;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.menu.CoreReactorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Core Reactor controller. Fixed 5x5x5: Reactor Casing frame, walls of casing, Reactor Glass and Reactor Ports, the
 * controller in a side wall. Inside, the middle block stays empty (the core floats there) and the other 26 are air or
 * modulators (block data map robotica:core_modulator).
 * <ul>
 *   <li>A boss core (item data map robotica:reactor_core) is loaded from the core slot and burns for its {@code life}
 *       ticks; it breaks when worn out and the next one loads. Its wear travels with the item
 *       ({@link EnergyRegistry#CORE_WEAR}) when the controller is broken.</li>
 *   <li>Fuel pellets (robotica:reactor_fuel) burn one at a time. Output {@code = pellet power x core power x P} FE/t,
 *       where {@code P = 1 + sum of the modulators' power}; the pellet and the core both burn {@code B = 1 + sum of the
 *       modulators' burn} ticks per tick. P and B never drop below 0.25.</li>
 *   <li>With less buffer room than a full tick's output it burns only that share (fuel, core and FE alike); with no
 *       room, no core, no fuel or no room for waste it pauses. No heat, no meltdown, no block changes.</li>
 * </ul>
 */
public class CoreReactorBlockEntity extends StructureControllerBlockEntity {
    public static final int SIZE = 5, FUEL_SLOTS = 3, WASTE_SLOTS = 3;
    public static final double MIN_MULTIPLIER = 0.25;
    public static final String MILESTONE = "core_reactor_running";

    public static final CuboidSpec SPEC = CuboidSpec.builder(Component.translatable("multiblock.robotica.core_reactor"))
            .frame(s -> s.is(EnergyRegistry.REACTOR_CASING.get()), Component.translatable("block.robotica.reactor_casing"))
            .wall(s -> s.is(EnergyRegistry.REACTOR_CASING.get()) || s.is(EnergyRegistry.REACTOR_GLASS.get())
                    || s.is(EnergyRegistry.REACTOR_POWER_PORT.get()) || s.is(EnergyRegistry.REACTOR_ACCESS_PORT.get()),
                    Component.translatable("multiblock.robotica.reactor_wall"))
            .controller(s -> s.is(EnergyRegistry.REACTOR_CONTROLLER.get()))
            .width(() -> SIZE, () -> SIZE)
            .height(() -> SIZE, () -> SIZE)
            .build();

    /** Why the reactor is not making power (GUI word). */
    public enum State { NOT_FORMED, RUNNING, NO_CORE, NO_FUEL, WASTE_FULL, BUFFER_FULL }

    /** A modulator inside, for the renderer's beams: +1 amplifier, -1 stabilizer. */
    public record Modulator(BlockPos pos, int sign) {}

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

    /** The next core: one at a time, it loads when the active one is gone. */
    public static ItemStackHandler newCoreHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return EnergyDataMaps.reactorCore(stack) != null;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public final ItemStackHandler fuel = newFuelHandler(this::setChanged);
    public final ItemStackHandler waste = newWasteHandler(this::setChanged);
    public final ItemStackHandler cores = newCoreHandler(this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(EnergyConfig.coreReactorBuffer(), 0, 0, this::setChanged);
    private final IItemHandler access = new AccessHandler();

    // saved state
    private ItemStack activeCore = ItemStack.EMPTY;
    private double coreWear;
    private int burnPower;
    private int burnTotal;
    private double burnLeft;
    @Nullable
    private Item burnWaste;
    private ItemStack pendingWaste = ItemStack.EMPTY;
    private boolean everRan;

    // from the last scan
    private double powerMod, burnMod;
    private final List<Modulator> modulators = new ArrayList<>();
    @Nullable
    private BlockPos center;

    // live numbers of the last tick
    private int fePerTick;
    private State state = State.NOT_FORMED;
    private int litHold;
    private static final int LIT_HOLD = 40;
    /** Client: running glow for the renderer (synced on change). */
    private boolean running;

    public CoreReactorBlockEntity(BlockPos pos, BlockState blockState) {
        super(EnergyRegistry.REACTOR_BE.get(), pos, blockState);
    }

    // ---------------------------------------------------------------- structure

    @Override
    protected CuboidSpec spec() {
        return SPEC;
    }

    @Override
    protected Visitor newVisitor() {
        return new CoreVisitor();
    }

    /** Inside: the middle stays empty, the rest is air or modulators. */
    static final class CoreVisitor extends Visitor {
        double power, burn;
        final List<Modulator> modulators = new ArrayList<>();

        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            if (pos.equals(box.getCenter())) {
                return state.isAir() ? null : StructureProblem.of(pos, "multiblock.robotica.core_chamber", StructureProblem.name(state), StructureProblem.at(pos));
            }
            if (state.isAir()) return null;
            EnergyDataMaps.CoreModulator m = EnergyDataMaps.modulator(state);
            if (m == null) return StructureProblem.of(pos, "multiblock.robotica.core_inside", StructureProblem.name(state), StructureProblem.at(pos));
            power += m.power();
            burn += m.burn();
            modulators.add(new Modulator(pos.immutable(), m.power() > 0 || (m.power() == 0 && m.burn() > 0) ? 1 : -1));
            return null;
        }

        @Override
        public StructureProblem finish(BoundingBox box) {
            if (ports(PortBlock.Kind.REACTOR_POWER) == 0) return StructureProblem.of(null, "multiblock.robotica.reactor_no_power_port");
            return null;
        }
    }

    @Override
    protected void onFormed(Visitor visitor) {
        CoreVisitor v = (CoreVisitor) visitor;
        BlockPos newCenter = box() == null ? null : box().getCenter().immutable();
        boolean changed = powerMod != v.power || burnMod != v.burn || !modulators.equals(v.modulators)
                || center == null || !center.equals(newCenter);
        powerMod = v.power;
        burnMod = v.burn;
        modulators.clear();
        modulators.addAll(v.modulators);
        center = newCenter;
        if (changed) setChangedAndSync();
    }

    @Override
    protected void onUnformed() {
        powerMod = burnMod = 0;
        modulators.clear();
        center = null;
        setChangedAndSync();
    }

    // ---------------------------------------------------------------- numbers

    /** Output multiplier from the modulators (at least 0.25). */
    public double powerMultiplier() {
        return Math.max(MIN_MULTIPLIER, 1.0 + powerMod);
    }

    /** Fuel and core burn rate from the modulators (at least 0.25). */
    public double burnMultiplier() {
        return Math.max(MIN_MULTIPLIER, 1.0 + burnMod);
    }

    @Nullable
    private ReactorCore core() {
        return EnergyDataMaps.reactorCore(activeCore);
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void tickController(ServerLevel level, long now) {
        if (!pendingWaste.isEmpty()) pendingWaste = ItemHandlerHelper.insertItem(waste, pendingWaste, false);
        loadCore(level);
        if (!isFormed()) {
            fePerTick = 0;
            state = State.NOT_FORMED;
            litHold = 0;
            setRunning(false);
            return;
        }
        pushOut(level, p -> p.kind() == PortBlock.Kind.REACTOR_POWER, Long.MAX_VALUE, energy::getEnergyStored, n -> energy.consume((int) n));
        step();
        if (running && center != null) ambience(level, now);
    }

    /** One reactor tick. Public for game tests (run it many times without waiting). */
    public void simulate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (level instanceof ServerLevel sl) loadCore(sl);
            step();
        }
    }

    private void step() {
        fePerTick = 0;
        ReactorCore core = core();
        boolean burning = false;
        if (core == null) state = State.NO_CORE;
        else if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) state = State.BUFFER_FULL;
        else if (!pendingWaste.isEmpty()) state = State.WASTE_FULL;
        else {
            int ready = ensureBurning();
            if (ready == 1) {
                double full = burnPower * core.power() * powerMultiplier() * CoreConfig.generation();
                double room = energy.getMaxEnergyStored() - energy.getEnergyStored();
                double share = full <= 0 || room >= full ? 1.0 : Math.max(0.0, room / full);
                double burn = burnMultiplier() * share;
                fePerTick = (int) Math.min(Integer.MAX_VALUE, Math.round(full * share));
                if (fePerTick > 0) energy.generate(fePerTick);
                burnLeft -= burn;
                if (burnLeft <= 1e-9) finishUnit();
                coreWear += burn;
                if (coreWear >= core.life()) breakCore();
                state = State.RUNNING;
                burning = true;
                // the guide step waits until the owner is around to see it run
                if (!everRan && level instanceof ServerLevel sl) {
                    var player = com.arno.robotica.core.progress.Milestones.nearbyOwner(sl, worldPosition, owner(), 64);
                    if (player != null) {
                        com.arno.robotica.core.progress.Milestones.award(player, MILESTONE);
                        everRan = true;
                        setChanged();
                    }
                }
            } else {
                state = ready == 0 ? State.NO_FUEL : State.WASTE_FULL;
            }
        }
        if (burning) litHold = LIT_HOLD;
        else if (litHold > 0) litHold--;
        setLit(litHold > 0);
        setRunning(litHold > 0);
    }

    /** Moves the next core in when there is none. */
    private void loadCore(ServerLevel level) {
        if (!activeCore.isEmpty()) return;
        ItemStack next = cores.getStackInSlot(0);
        if (next.isEmpty() || EnergyDataMaps.reactorCore(next) == null) return;
        activeCore = cores.extractItem(0, 1, false);
        Integer wear = activeCore.get(EnergyRegistry.CORE_WEAR.get());
        coreWear = wear == null ? 0 : wear;
        activeCore.remove(EnergyRegistry.CORE_WEAR.get());
        if (center != null) {
            CoreSounds.play(level, center, CoreSounds.CORE_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        setChangedAndSync();
    }

    private void breakCore() {
        if (level instanceof ServerLevel sl && center != null) {
            CoreSounds.play(sl, center, CoreSounds.CORE_BURNOUT, SoundSource.BLOCKS, 1.4F, 0.8F);
            sl.sendParticles(ParticleTypes.EXPLOSION, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.02);
            var player = com.arno.robotica.core.progress.Milestones.nearbyOwner(sl, worldPosition, owner(), 64);
            if (player != null) player.displayClientMessage(Component.translatable("message.robotica.core_burned_out", activeCore.getHoverName()), true);
        }
        activeCore = ItemStack.EMPTY;
        coreWear = 0;
        setChangedAndSync();
    }

    /** Hum while it runs, a crackle of sparks in the chamber now and then (both cheap, server side). */
    private void ambience(ServerLevel level, long now) {
        if (CoreSounds.due(level, worldPosition, 70)) {
            CoreSounds.play(level, center, CoreSounds.CORE_REACTOR_HUM, SoundSource.BLOCKS, 0.8F, 0.9F + 0.1F * (float) Math.min(2.0, powerMultiplier() - 1.0));
        }
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
            burnPower = f.power();
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

    private void setRunning(boolean value) {
        if (running != value) {
            running = value;
            setChangedAndSync();
        }
    }

    // ---------------------------------------------------------------- accessors (GUI, renderer, tests)

    @Override
    protected boolean litSounds() {
        return true;
    }

    public ItemStack activeCore() { return activeCore; }
    public double coreWear() { return coreWear; }
    public double burnLeft() { return burnLeft; }
    public int fePerTick() { return fePerTick; }
    public State state() { return state; }
    public boolean running() { return running; }
    @Nullable
    public BlockPos center() { return center; }
    public List<Modulator> modulators() { return modulators; }

    /** Jade: core integrity. */
    @Override
    protected int infoProgress() {
        ReactorCore core = core();
        return core == null ? -1 : (int) Math.round(100.0 * Math.max(0, core.life() - coreWear) / core.life());
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

    /** Access Port view: slots 0-2 take fuel, 3-5 give waste, 6 takes the next core. */
    private final class AccessHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return FUEL_SLOTS + WASTE_SLOTS + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < FUEL_SLOTS) return fuel.getStackInSlot(slot);
            if (slot < FUEL_SLOTS + WASTE_SLOTS) return waste.getStackInSlot(slot - FUEL_SLOTS);
            return cores.getStackInSlot(0);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < FUEL_SLOTS) return fuel.insertItem(slot, stack, simulate);
            if (slot == FUEL_SLOTS + WASTE_SLOTS) return cores.insertItem(0, stack, simulate);
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= FUEL_SLOTS && slot < FUEL_SLOTS + WASTE_SLOTS) return waste.extractItem(slot - FUEL_SLOTS, amount, simulate);
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == FUEL_SLOTS + WASTE_SLOTS ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < FUEL_SLOTS) return fuel.isItemValid(slot, stack);
            return slot == FUEL_SLOTS + WASTE_SLOTS && cores.isItemValid(0, stack);
        }
    }

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CoreReactorMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        ReactorCore core = core();
        tag.putInt("fe", fePerTick);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("capacity", energy.getMaxEnergyStored());
        tag.putFloat("burn", burnTotal > 0 ? (float) (burnLeft / burnTotal) : 0F);
        tag.putInt("burnPower", burnTotal > 0 ? (int) Math.round(burnPower * CoreConfig.generation()) : 0);
        tag.putInt("powerPct", (int) Math.round(powerMultiplier() * 100));
        tag.putInt("burnPct", (int) Math.round(burnMultiplier() * 100));
        tag.putInt("amps", (int) modulators.stream().filter(m -> m.sign() > 0).count());
        tag.putInt("dampers", (int) modulators.stream().filter(m -> m.sign() < 0).count());
        if (core != null) {
            tag.putString("core", BuiltInRegistries.ITEM.getKey(activeCore.getItem()).toString());
            tag.putFloat("corePower", core.power());
            tag.putFloat("integrity", (float) Math.max(0, (core.life() - coreWear) / core.life()));
            tag.putLong("coreTicks", (long) Math.max(0, (core.life() - coreWear) / burnMultiplier()));
        }
        tag.putInt("state", state.ordinal());
    }

    /** Renderer data: the chamber, the active core, whether it runs and where the modulators sit. */
    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        if (center != null) tag.putLong("center", center.asLong());
        if (!activeCore.isEmpty()) tag.putString("core", BuiltInRegistries.ITEM.getKey(activeCore.getItem()).toString());
        tag.putBoolean("running", running);
        ListTag amps = new ListTag(), dampers = new ListTag();
        for (Modulator m : modulators) (m.sign() > 0 ? amps : dampers).add(LongTag.valueOf(m.pos().asLong()));
        tag.put("mods", amps);
        tag.put("dampers", dampers);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("waste", waste.serializeNBT(registries));
        tag.put("cores", cores.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        if (!activeCore.isEmpty()) tag.put("activeCore", activeCore.save(registries));
        tag.putDouble("coreWear", coreWear);
        tag.putInt("burnPower", burnPower);
        tag.putInt("burnTotal", burnTotal);
        tag.putDouble("burnLeft", burnLeft);
        if (burnWaste != null) tag.putString("burnWaste", BuiltInRegistries.ITEM.getKey(burnWaste).toString());
        if (!pendingWaste.isEmpty()) tag.put("pendingWaste", pendingWaste.save(registries));
        tag.putBoolean("everRan", everRan);
        saveClientData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("waste")) waste.deserializeNBT(registries, tag.getCompound("waste"));
        if (tag.contains("cores")) cores.deserializeNBT(registries, tag.getCompound("cores"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("activeCore")) activeCore = ItemStack.parseOptional(registries, tag.getCompound("activeCore"));
        else if (tag.contains("core")) {
            // client update: only the item id is sent
            activeCore = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(tag.getString("core"))).map(ItemStack::new).orElse(ItemStack.EMPTY);
        } else if (tag.contains("mods")) {
            activeCore = ItemStack.EMPTY;
        }
        coreWear = tag.getDouble("coreWear");
        burnPower = tag.getInt("burnPower");
        burnTotal = tag.getInt("burnTotal");
        burnLeft = tag.getDouble("burnLeft");
        burnWaste = tag.contains("burnWaste") ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(tag.getString("burnWaste"))).orElse(null) : null;
        pendingWaste = tag.contains("pendingWaste") ? ItemStack.parseOptional(registries, tag.getCompound("pendingWaste")) : ItemStack.EMPTY;
        everRan = tag.getBoolean("everRan");
        center = tag.contains("center") ? BlockPos.of(tag.getLong("center")) : null;
        running = tag.getBoolean("running");
        if (tag.contains("mods", Tag.TAG_LIST)) {
            modulators.clear();
            for (Tag t : tag.getList("mods", Tag.TAG_LONG)) modulators.add(new Modulator(BlockPos.of(((LongTag) t).getAsLong()), 1));
            for (Tag t : tag.getList("dampers", Tag.TAG_LONG)) modulators.add(new Modulator(BlockPos.of(((LongTag) t).getAsLong()), -1));
        }
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : new ItemStackHandler[]{fuel, waste, cores}) {
            for (int i = 0; i < handler.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
        }
        if (!pendingWaste.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pendingWaste);
        if (!activeCore.isEmpty()) {
            ItemStack core = activeCore.copy();
            long wear = Math.round(coreWear);
            if (wear > 0) core.set(EnergyRegistry.CORE_WEAR.get(), (int) Math.min(Integer.MAX_VALUE, wear));
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), core);
            activeCore = ItemStack.EMPTY;
        }
    }
}
