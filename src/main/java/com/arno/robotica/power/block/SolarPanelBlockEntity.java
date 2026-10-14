package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates while it is day and the sky is visible (checked once per second), at {@link #RAIN_PERCENT}% while it rains or
 * thunders in a biome with rain or snow (Mekanism's rule). Pushes into its neighbours (one shared rate for all faces). Takes
 * no cards.
 */
public class SolarPanelBlockEntity extends PowerBlockEntity implements net.minecraft.world.MenuProvider, com.arno.robotica.power.menu.EnergyInfoMenu.Source, com.arno.robotica.compat.InfoSource {
    public final MachineEnergyStorage energy;
    private final SolarPanelBlock.Tier tier;
    private final int pushRate;
    /** Output in rain or thunder, percent of the daylight output. */
    public static final int RAIN_PERCENT = 20;
    private boolean sunny;
    private boolean raining;
    private boolean openSky;
    private final com.arno.robotica.core.energy.EnergyNeighbors neighbors = new com.arno.robotica.core.energy.EnergyNeighbors();

    private int currentOutput() {
        return sunny ? weatherOutput(CoreConfig.scaleGeneration(tier.output()), raining) : 0;
    }

    /** The daylight output {@code out}, cut to {@link #RAIN_PERCENT}% in rain (at least 1 FE/t). */
    public static int weatherOutput(int out, boolean rain) {
        return rain ? Math.max(1, out * RAIN_PERCENT / 100) : out;
    }

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
        this.tier = ((SolarPanelBlock) state.getBlock()).tier();
        this.pushRate = Math.max(64, tier.output() * 8);
        this.energy = new MachineEnergyStorage(tier.buffer, 0, pushRate, this::setChanged);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<?> typeFor(BlockState state) {
        return SolarPanelBlock.typeFor(((SolarPanelBlock) state.getBlock()).tier());
    }

    public boolean isGenerating() {
        return sunny;
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
        return currentOutput();
    }

    @Override
    public int maxIo() {
        return pushRate;
    }

    @Override
    public int flag() {
        return !sunny ? 0 : raining ? 2 : 1;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player player) {
        return new com.arno.robotica.power.menu.SolarPanelMenu(id, inv, this);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            openSky = !level.dimensionType().hasFixedTime() && level.canSeeSky(pos.above());
            sunny = openSky && level.isDay();
            raining = sunny && level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos) != net.minecraft.world.level.biome.Biome.Precipitation.NONE;
        }
        int gen = currentOutput();
        if (gen > 0) energy.generate(gen);
        if (energy.getEnergyStored() > 0) EnergyUtil.pushToNeighbors(level, pos, energy, pushRate, neighbors);
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

    /** Jade: generating, generating into a full buffer, or idle (night, no sky). */
    @Override
    public void collectInfo(ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        if (!sunny) info.status = "idle";
        else info.status = energy.getEnergyStored() >= energy.getMaxEnergyStored() ? "output_full" : "working";
    }
}
