package com.arno.robotica.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Global balance knobs (server config, synced to clients, stored per world in serverconfig/).
 * Read through the static helpers: they fall back to defaults before the config is loaded.
 */
public final class CoreConfig {
    private CoreConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ENERGY_USE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WORK_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GENERATION_MULTIPLIER;
    public static final ModConfigSpec.IntValue SIDE_TRANSFER_INTERVAL;
    public static final ModConfigSpec.IntValue SIDE_TRANSFER_ITEMS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Global Robotica balance. Defaults are tuned for modpack pace (faster than vanilla).").push("balance");
        ENERGY_USE_MULTIPLIER = b.comment("Multiplies all FE used by robots, machines and tools.")
                .defineInRange("energyUseMultiplier", 1.0, 0.0, 100.0);
        WORK_SPEED_MULTIPLIER = b.comment("Multiplies work speed of robots and machines. Lower it for a slower pack.")
                .defineInRange("workSpeedMultiplier", 1.0, 0.05, 20.0);
        GENERATION_MULTIPLIER = b.comment("Multiplies FE produced by Robotica generators.")
                .defineInRange("generationMultiplier", 1.0, 0.0, 100.0);
        b.pop();
        b.comment("Machine side configuration: auto-input and auto-eject.").push("sides");
        SIDE_TRANSFER_INTERVAL = b.comment("Ticks between two auto-input / auto-eject transfers of a machine.")
                .defineInRange("sideTransferInterval", 10, 1, 200);
        SIDE_TRANSFER_ITEMS = b.comment("Items a machine moves per transfer, each way.")
                .defineInRange("sideTransferItems", 16, 1, 64);
        b.pop();
        SPEC = b.build();
    }

    public static int sideTransferInterval() {
        return SPEC.isLoaded() ? SIDE_TRANSFER_INTERVAL.get() : 10;
    }

    public static int sideTransferItems() {
        return SPEC.isLoaded() ? SIDE_TRANSFER_ITEMS.get() : 16;
    }

    public static double energyUse() {
        return SPEC.isLoaded() ? ENERGY_USE_MULTIPLIER.get() : 1.0;
    }

    public static double workSpeed() {
        return SPEC.isLoaded() ? WORK_SPEED_MULTIPLIER.get() : 1.0;
    }

    public static double generation() {
        return SPEC.isLoaded() ? GENERATION_MULTIPLIER.get() : 1.0;
    }

    /** Applies the energy use multiplier to a base FE cost. Never returns less than 0. */
    public static int scaleEnergy(int baseCost) {
        return (int) Math.max(0, Math.round(baseCost * energyUse()));
    }

    /** Applies the work speed multiplier to a base interval in ticks. Never returns less than 1. */
    public static int scaleInterval(int baseTicks) {
        return (int) Math.max(1, Math.round(baseTicks / workSpeed()));
    }

    public static int scaleGeneration(int baseFePerTick) {
        return (int) Math.max(0, Math.round(baseFePerTick * generation()));
    }
}
