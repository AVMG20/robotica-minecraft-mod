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
    public static final ModConfigSpec.IntValue SPEED_CAP_PER_MK;
    public static final ModConfigSpec.IntValue EFFICIENCY_CAP_PER_MK;
    public static final ModConfigSpec.IntValue RANGE_CAP_PER_MK;
    public static final ModConfigSpec.IntValue FORTUNE_CAP_MAX;
    public static final ModConfigSpec.IntValue GROWTH_CAP_PER_MK;
    /** Caps of the machines without a Mk, keyed "MACHINE/KIND". */
    private static final java.util.Map<String, ModConfigSpec.IntValue> FIXED_CAPS = new java.util.HashMap<>();

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
        b.comment("Upgrade cards in machines with a Mk (card slots are always Mk + 1).").push("upgrades");
        SPEED_CAP_PER_MK = b.comment("Speed cards that count per Mk (2: Mk1 2 ... Mk4 8).")
                .defineInRange("speedCapPerMk", 2, 0, 8);
        EFFICIENCY_CAP_PER_MK = b.comment("Efficiency cards that count per Mk.")
                .defineInRange("efficiencyCapPerMk", 1, 0, 4);
        RANGE_CAP_PER_MK = b.comment("Range cards that count per Mk.")
                .defineInRange("rangeCapPerMk", 1, 0, 4);
        FORTUNE_CAP_MAX = b.comment("Fortune cards that count: the Mk, at most this.")
                .defineInRange("fortuneCapMax", 3, 0, 3);
        GROWTH_CAP_PER_MK = b.comment("Growth cards that count per Mk.")
                .defineInRange("growthCapPerMk", 1, 0, 4);
        b.pop();
        b.comment("Upgrade cards in machines without a Mk: most cards of a kind that count.").push("fixedMachines");
        for (com.arno.robotica.core.upgrade.UpgradeRules.Fixed machine : com.arno.robotica.core.upgrade.UpgradeRules.Fixed.values()) {
            for (com.arno.robotica.core.upgrade.UpgradeKind kind : machine.kinds()) {
                String key = camel(machine.name()) + camel(kind.name()) + "Cap";
                FIXED_CAPS.put(machine.name() + "/" + kind.name(),
                        b.defineInRange(key, machine.defaultCap(kind), 0, kind.maxStack));
            }
        }
        b.pop();
        SPEC = b.build();
    }

    private static String camel(String name) {
        StringBuilder out = new StringBuilder();
        for (String part : name.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            out.append(out.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return out.toString();
    }

    /** Configured cap of a card kind in a machine without a Mk, or {@code fallback} before the config is loaded. */
    public static int fixedCap(String machine, String kind, int fallback) {
        ModConfigSpec.IntValue v = FIXED_CAPS.get(machine + "/" + kind);
        return SPEC.isLoaded() && v != null ? v.get() : fallback;
    }

    public static int growthCapPerMk() {
        return SPEC.isLoaded() ? GROWTH_CAP_PER_MK.get() : 1;
    }

    public static int sideTransferInterval() {
        return SPEC.isLoaded() ? SIDE_TRANSFER_INTERVAL.get() : 10;
    }

    public static int sideTransferItems() {
        return SPEC.isLoaded() ? SIDE_TRANSFER_ITEMS.get() : 16;
    }

    public static int speedCapPerMk() {
        return SPEC.isLoaded() ? SPEED_CAP_PER_MK.get() : 2;
    }

    public static int efficiencyCapPerMk() {
        return SPEC.isLoaded() ? EFFICIENCY_CAP_PER_MK.get() : 1;
    }

    public static int rangeCapPerMk() {
        return SPEC.isLoaded() ? RANGE_CAP_PER_MK.get() : 1;
    }

    public static int fortuneCapMax() {
        return SPEC.isLoaded() ? FORTUNE_CAP_MAX.get() : 3;
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
