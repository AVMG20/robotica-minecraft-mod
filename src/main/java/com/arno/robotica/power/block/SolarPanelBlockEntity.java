package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates while it is day and the sky is visible (checked once per second). Pushes into every neighbour.
 * Two card slots: each speed card adds {@link PowerConfig#solarSpeedBonus()} % daylight output, each efficiency card
 * lets it keep {@link PowerConfig#solarNightPerCard()} % of it at night under open sky (moonlight).
 */
public class SolarPanelBlockEntity extends PowerBlockEntity implements net.minecraft.world.MenuProvider, com.arno.robotica.power.menu.EnergyInfoMenu.Source {
    public final MachineEnergyStorage energy;
    private final SolarPanelBlock.Tier tier;
    private final int pushRate;
    private boolean sunny;
    private boolean openSky;
    public final com.arno.robotica.core.upgrade.Upgrades upgrades = new com.arno.robotica.core.upgrade.Upgrades(2,
            java.util.Map.of(com.arno.robotica.core.upgrade.UpgradeKind.SPEED, 4, com.arno.robotica.core.upgrade.UpgradeKind.EFFICIENCY, 4), this::setChanged);

    @Override
    public net.neoforged.neoforge.items.IItemHandler quickUpgrades() {
        return upgrades;
    }

    @Override
    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos) {
        drop(level, pos, upgrades);
    }

    /** FE/t (before the global multiplier) for a base output, sun or night, and the installed cards. */
    public static int outputFor(int base, boolean sun, int speedCards, int efficiencyCards) {
        if (sun) return (int) Math.round(base * (1.0 + PowerConfig.solarSpeedBonus() / 100.0 * speedCards));
        return (int) Math.round(base * PowerConfig.solarNightPerCard() / 100.0 * efficiencyCards);
    }

    private int currentOutput() {
        if (!openSky) return 0;
        return CoreConfig.scaleGeneration(outputFor(tier.output(), sunny,
                upgrades.level(com.arno.robotica.core.upgrade.UpgradeKind.SPEED), upgrades.level(com.arno.robotica.core.upgrade.UpgradeKind.EFFICIENCY)));
    }

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
        return sunny ? 1 : 0;
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
        }
        int gen = currentOutput();
        if (gen > 0) energy.generate(gen);
        if (energy.getEnergyStored() > 0) EnergyUtil.pushToNeighbors(level, pos, energy, pushRate);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
    }
}
