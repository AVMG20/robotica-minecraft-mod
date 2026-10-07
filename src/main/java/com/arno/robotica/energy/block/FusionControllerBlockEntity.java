package com.arno.robotica.energy.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.core.progress.Milestones;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyDataMaps.FusionFuel;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.menu.FusionMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Fusion Reactor controller. Fixed 7x3x7 shape: Fusion Casing frame, walls of Fusion Casing, Reactor Glass and Reactor
 * Ports, the controller in a side wall; inside, a ring of 16 Fusion Coils around an empty 3x3 plasma chamber.
 * <ol>
 *   <li>Charging: the Power Ports take FE in (up to {@code fusionChargeRate} per tick) until the ignition charge
 *       (20M FE by default) is full. Tesla Coils, cables and Capacitor Banks all work.</li>
 *   <li>Ignition: with a full charge, fuel in the controller and the reactor switched on, the charge is spent and the
 *       plasma lights.</li>
 *   <li>Burning: each unit of fusion fuel gives its {@code power} FE/t for its {@code ticks}; the output ramps up over
 *       the warm-up. The plasma cannot throttle: it burns fuel whether or not the energy is taken.</li>
 *   <li>Without fuel the plasma survives {@code fusionStarveTicks}, then collapses and needs a new ignition charge.
 *       Switching it off or breaking the structure collapses it too. No explosions, no block damage.</li>
 * </ol>
 */
public class FusionControllerBlockEntity extends StructureControllerBlockEntity {
    public static final int SIZE = 7, HEIGHT = 3, FUEL_SLOTS = 3;
    public static final String MILESTONE = "fusion_ignited";

    public static final CuboidSpec SPEC = CuboidSpec.builder(Component.translatable("multiblock.robotica.fusion_reactor"))
            .frame(s -> s.is(EnergyRegistry.FUSION_CASING.get()), Component.translatable("block.robotica.fusion_casing"))
            .wall(s -> s.is(EnergyRegistry.FUSION_CASING.get()) || s.is(EnergyRegistry.REACTOR_GLASS.get())
                    || s.is(EnergyRegistry.REACTOR_POWER_PORT.get()) || s.is(EnergyRegistry.REACTOR_ACCESS_PORT.get()),
                    Component.translatable("multiblock.robotica.fusion_wall"))
            .controller(s -> s.is(EnergyRegistry.FUSION_CONTROLLER.get()))
            .width(() -> SIZE, () -> SIZE)
            .height(() -> HEIGHT, () -> HEIGHT)
            .build();

    public enum State { NOT_FORMED, OFF, CHARGING, NO_FUEL, RUNNING, STARVING }

    public static ItemStackHandler newFuelHandler(Runnable onChanged) {
        return new ItemStackHandler(FUEL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return EnergyDataMaps.fusionFuel(stack) != null;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public final ItemStackHandler fuel = newFuelHandler(this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(EnergyConfig.fusionBuffer(), 0, 0, this::setChanged);

    private boolean enabled = true;
    private int charge;
    private boolean ignited;
    private double plasma;
    private int burnPower;
    private int burnTotal;
    private int burnLeft;
    private int starve;
    private int fePerTick;
    private long chargeTick = Long.MIN_VALUE;
    private int chargedThisTick;
    private State state = State.NOT_FORMED;

    public FusionControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(EnergyRegistry.FUSION_BE.get(), pos, blockState);
    }

    @Override
    protected CuboidSpec spec() {
        return SPEC;
    }

    @Override
    protected Visitor newVisitor() {
        return new FusionVisitor();
    }

    /** Inside layer: the outer ring must be Fusion Coils, the 3x3 middle must be empty. */
    static final class FusionVisitor extends Visitor {
        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            boolean ring = pos.getX() == box.minX() + 1 || pos.getX() == box.maxX() - 1
                    || pos.getZ() == box.minZ() + 1 || pos.getZ() == box.maxZ() - 1;
            if (ring) {
                return state.is(EnergyRegistry.FUSION_COIL.get()) ? null
                        : StructureProblem.of(pos, "multiblock.robotica.fusion_ring", StructureProblem.name(state), StructureProblem.at(pos));
            }
            return state.isAir() ? null
                    : StructureProblem.of(pos, "multiblock.robotica.fusion_chamber", StructureProblem.name(state), StructureProblem.at(pos));
        }

        @Override
        public StructureProblem finish(BoundingBox box) {
            if (ports(PortBlock.Kind.REACTOR_POWER) == 0) return StructureProblem.of(null, "multiblock.robotica.reactor_no_power_port");
            return null;
        }
    }

    @Override
    protected void onFormed(Visitor visitor) {
    }

    @Override
    protected void onUnformed() {
        if (ignited) collapse();
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void tickController(ServerLevel level, long now) {
        if (!isFormed()) {
            fePerTick = 0;
            state = State.NOT_FORMED;
            setLit(false);
            return;
        }
        pushOut(level, p -> p.kind() == PortBlock.Kind.REACTOR_POWER, Long.MAX_VALUE, energy::getEnergyStored, n -> energy.consume((int) n));
        step();
    }

    /** One reactor tick (game tests call it directly). */
    public void step() {
        fePerTick = 0;
        if (!enabled) {
            if (ignited) collapse();
            state = State.OFF;
        } else if (!ignited) {
            if (charge < EnergyConfig.fusionIgnitionEnergy()) {
                state = State.CHARGING;
            } else if (!hasFuel()) {
                state = State.NO_FUEL;
            } else {
                ignite();
            }
        }
        if (ignited) {
            if (burnLeft <= 0) takeFuel();
            int warmup = Math.max(1, EnergyConfig.fusionWarmupTicks());
            if (burnLeft > 0) {
                starve = 0;
                plasma = Math.min(1.0, plasma + 1.0 / warmup);
                double fe = burnPower * plasma * CoreConfig.generation();
                fePerTick = (int) Math.min(Integer.MAX_VALUE, Math.round(fe));
                energy.generate(fePerTick);
                burnLeft--;
                state = State.RUNNING;
            } else {
                starve++;
                plasma = Math.max(0.0, plasma - 1.0 / warmup);
                state = State.STARVING;
                if (starve > EnergyConfig.fusionStarveTicks()) collapse();
            }
        }
        setLit(ignited);
    }

    private boolean hasFuel() {
        for (int i = 0; i < fuel.getSlots(); i++) {
            if (EnergyDataMaps.fusionFuel(fuel.getStackInSlot(i)) != null) return true;
        }
        return false;
    }

    private void takeFuel() {
        for (int i = 0; i < fuel.getSlots(); i++) {
            FusionFuel f = EnergyDataMaps.fusionFuel(fuel.getStackInSlot(i));
            if (f == null) continue;
            fuel.extractItem(i, 1, false);
            burnPower = f.power();
            burnTotal = f.ticks();
            burnLeft = f.ticks();
            return;
        }
    }

    private void ignite() {
        charge = 0;
        ignited = true;
        plasma = 0;
        starve = 0;
        if (level instanceof ServerLevel sl) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.4F, 1.6F);
            Milestones.awardOwner(sl, worldPosition, owner(), MILESTONE);
        }
        setChanged();
    }

    private void collapse() {
        ignited = false;
        plasma = 0;
        starve = 0;
        burnLeft = 0;
        if (level != null && !level.isClientSide) level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.5F);
        setChanged();
    }

    // ---------------------------------------------------------------- GUI actions

    /** Test hook: sets the ignition charge directly. */
    public void setCharge(int value) {
        charge = Math.max(0, Math.min(EnergyConfig.fusionIgnitionEnergy(), value));
        setChanged();
    }

    public void setEnabled(boolean value) {
        enabled = value;
        setChanged();
    }

    public boolean enabled() { return enabled; }

    /** Jade: the ignition charge while the plasma is out. */
    @Override
    protected int infoProgress() {
        int need = com.arno.robotica.energy.EnergyConfig.fusionIgnitionEnergy();
        return ignited || need <= 0 || charge <= 0 ? -1 : (int) Math.min(100, (long) charge * 100 / need);
    }

    public boolean ignited() { return ignited; }
    public int charge() { return charge; }
    public double plasma() { return plasma; }
    public int fePerTick() { return fePerTick; }
    public State state() { return state; }

    // ---------------------------------------------------------------- ports

    private boolean charging() {
        return isFormed() && enabled && !ignited && charge < EnergyConfig.fusionIgnitionEnergy();
    }

    @Override
    public int portReceive(PortBlockEntity port, int amount, boolean simulate) {
        if (port.kind() != PortBlock.Kind.REACTOR_POWER || !charging()) return 0;
        long now = level == null ? 0 : level.getGameTime();
        if (now != chargeTick) {
            chargeTick = now;
            chargedThisTick = 0;
        }
        int room = EnergyConfig.fusionIgnitionEnergy() - charge;
        int accepted = Math.max(0, Math.min(amount, Math.min(room, EnergyConfig.fusionChargeRate() - chargedThisTick)));
        if (!simulate && accepted > 0) {
            charge += accepted;
            chargedThisTick += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int portExtract(PortBlockEntity port, int amount, boolean simulate) {
        if (port.kind() != PortBlock.Kind.REACTOR_POWER) return 0;
        int take = Math.min(amount, energy.getEnergyStored());
        if (!simulate && take > 0) energy.consume(take);
        return take;
    }

    @Override
    public boolean portCanReceive(PortBlockEntity port) {
        return port.kind() == PortBlock.Kind.REACTOR_POWER && charging();
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

    /** Access Port view: fuel goes in, nothing comes out (pipes must not pull the fuel back). */
    private final IItemHandler access = new IItemHandler() {
        @Override
        public int getSlots() {
            return fuel.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return fuel.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return fuel.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return fuel.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return fuel.isItemValid(slot, stack);
        }
    };

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FusionMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("ignited", ignited);
        tag.putInt("charge", charge);
        tag.putInt("ignition", EnergyConfig.fusionIgnitionEnergy());
        tag.putInt("plasma", (int) Math.round(plasma * 100));
        tag.putInt("fe", fePerTick);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("capacity", energy.getMaxEnergyStored());
        tag.putFloat("burn", burnTotal > 0 ? (float) burnLeft / burnTotal : 0F);
        tag.putInt("burnPower", burnLeft > 0 ? burnPower : 0);
        tag.putInt("state", state.ordinal());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putBoolean("enabled", enabled);
        tag.putInt("charge", charge);
        tag.putBoolean("ignited", ignited);
        tag.putDouble("plasma", plasma);
        tag.putInt("burnPower", burnPower);
        tag.putInt("burnTotal", burnTotal);
        tag.putInt("burnLeft", burnLeft);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        charge = tag.getInt("charge");
        ignited = tag.getBoolean("ignited");
        plasma = tag.getDouble("plasma");
        burnPower = tag.getInt("burnPower");
        burnTotal = tag.getInt("burnTotal");
        burnLeft = tag.getInt("burnLeft");
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < fuel.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fuel.getStackInSlot(i));
    }
}
