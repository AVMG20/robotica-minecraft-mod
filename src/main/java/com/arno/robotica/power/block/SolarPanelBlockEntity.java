package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Generates while it is day and the sky is visible (checked once per second). Pushes into every neighbour. */
public class SolarPanelBlockEntity extends PowerBlockEntity {
    public final MachineEnergyStorage energy;
    private final SolarPanelBlock.Tier tier;
    private final int pushRate;
    private boolean sunny;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
        this.tier = ((SolarPanelBlock) state.getBlock()).tier();
        this.pushRate = Math.max(64, tier.output() * 8);
        this.energy = new MachineEnergyStorage(tier.buffer, 0, pushRate, this::setChanged);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<?> typeFor(BlockState state) {
        return ((SolarPanelBlock) state.getBlock()).tier() == SolarPanelBlock.Tier.MK1
                ? PowerRegistry.SOLAR_MK1_BE.get() : PowerRegistry.SOLAR_MK2_BE.get();
    }

    public boolean isGenerating() {
        return sunny;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            sunny = !level.dimensionType().hasFixedTime() && level.isDay() && level.canSeeSky(pos.above());
        }
        if (sunny) energy.generate(CoreConfig.scaleGeneration(tier.output()));
        if (energy.getEnergyStored() > 0) EnergyUtil.pushToNeighbors(level, pos, energy, pushRate);
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
