package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.util.SidedEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.Block;

/** FE buffer. The energy travels with the item through the core ENERGY data component. */
public class AccumulatorBlockEntity extends PowerBlockEntity implements net.minecraft.world.MenuProvider, com.arno.robotica.power.menu.EnergyInfoMenu.Source {
    private static final int KIND = com.arno.robotica.power.menu.EnergyInfoMenu.KIND_STORAGE;
    private int lastStored = -1, netRate;
    public final MachineEnergyStorage energy;
    private final IEnergyStorage inputView;
    private final IEnergyStorage outputView;
    private final int io;
    private int lastSignal = -1;

    public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.ACCUMULATOR_BE.get(), pos, state);
        AccumulatorBlock.Tier tier = ((AccumulatorBlock) state.getBlock()).tier();
        this.io = tier.io;
        this.energy = new MachineEnergyStorage(tier.capacity, tier.io, tier.io, this::setChanged);
        this.inputView = new SidedEnergy(energy, true, false);
        this.outputView = new SidedEnergy(energy, false, true);
    }

    /** Capability for a face: input everywhere but the front, output on the front. */
    public IEnergyStorage energyFor(@Nullable Direction side) {
        if (side == null) return energy;
        return side == getBlockState().getValue(PowerBlock.FACING) ? outputView : inputView;
    }

    public int comparatorSignal() {
        int stored = energy.getEnergyStored();
        if (stored <= 0) return 0;
        return 1 + (int) (14L * stored / energy.getMaxEnergyStored());
    }

    @Override
    public int stored() {
        return energy.getEnergyStored();
    }

    @Override
    public int capacity() {
        return energy.getMaxEnergyStored();
    }

    @Override
    public int rate() {
        return netRate;
    }

    @Override
    public int maxIo() {
        return io;
    }

    @Override
    public int flag() {
        return 0;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player player) {
        return new com.arno.robotica.power.menu.EnergyInfoMenu(id, inv, getBlockPos(), KIND, this);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            int now = energy.getEnergyStored();
            netRate = lastStored < 0 ? 0 : (now - lastStored) / 20;
            lastStored = now;
        }
        if (energy.getEnergyStored() > 0) {
            Direction front = state.getValue(PowerBlock.FACING);
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(front), front.getOpposite());
            if (target != null && target.canReceive()) {
                int offer = Math.min(io, energy.getEnergyStored());
                int accepted = target.receiveEnergy(offer, false);
                if (accepted > 0) energy.consume(accepted);
            }
        }
        updateGauge(level, pos);
        int signal = comparatorSignal();
        if (signal != lastSignal) {
            if (lastSignal != -1) level.updateNeighbourForOutputSignal(pos, state.getBlock());
            lastSignal = signal;
        }
    }

    /** Ticks between gauge checks: the block state only changes when the level does, and at most this often. */
    private static final int GAUGE_INTERVAL = 10;

    /** Sets the CHARGE state when the lit cell count changed (client update only, no neighbour updates). */
    private void updateGauge(ServerLevel level, BlockPos pos) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), GAUGE_INTERVAL) != 0) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(AccumulatorBlock.CHARGE)) return;
        int charge = AccumulatorBlock.chargeLevel(energy.getEnergyStored(), energy.getMaxEnergyStored());
        if (state.getValue(AccumulatorBlock.CHARGE) != charge) {
            level.setBlock(pos, state.setValue(AccumulatorBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.getEnergyStored() > 0) components.set(CoreComponents.ENERGY.get(), energy.getEnergyStored());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer stored = input.get(CoreComponents.ENERGY.get());
        if (stored != null) energy.setEnergy(stored);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
    }
}
