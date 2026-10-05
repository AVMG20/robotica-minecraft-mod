package com.arno.robotica.boss;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the boss module (server config, file robotica-boss-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded (game tests, early world gen).
 */
public final class BossConfig {
    private BossConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue HEALTH_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue MINION_CAP;
    private static final ModConfigSpec.IntValue ALTAR_COOLDOWN;
    private static final ModConfigSpec.BooleanValue FOUNDRY_ENABLED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("colossus");
        HEALTH_MULTIPLIER = b.comment("Multiplier of the Scrap Colossus hit points (base 300). Applied when it is summoned.")
                .defineInRange("bossHealthMultiplier", 1.0, 0.1, 100.0);
        DAMAGE_MULTIPLIER = b.comment("Multiplier of every attack of the Scrap Colossus and its Scrap Drones.")
                .defineInRange("bossDamageMultiplier", 1.0, 0.0, 100.0);
        MINION_CAP = b.comment("Most Scrap Drones one Colossus may have alive at once (0 turns the minions off).")
                .defineInRange("colossusMinionCap", 3, 0, 16);
        b.pop();
        b.push("altar");
        ALTAR_COOLDOWN = b.comment("Seconds before a Colossus Altar can be awakened again after a summon.")
                .defineInRange("altarCooldownSeconds", 300, 0, 86_400);
        b.pop();
        b.push("worldgen");
        FOUNDRY_ENABLED = b.comment("Generate Rusted Foundry ruins (with a Colossus Altar) in plains, deserts and badlands. Only affects new chunks.")
                .define("foundryEnabled", true);
        b.pop();
        SPEC = b.build();
    }

    public static double healthMultiplier() {
        return SPEC.isLoaded() ? HEALTH_MULTIPLIER.get() : HEALTH_MULTIPLIER.getDefault();
    }

    public static float damageMultiplier() {
        double value = SPEC.isLoaded() ? DAMAGE_MULTIPLIER.get() : DAMAGE_MULTIPLIER.getDefault();
        return (float) value;
    }

    public static int minionCap() {
        return SPEC.isLoaded() ? MINION_CAP.get() : MINION_CAP.getDefault();
    }

    public static int altarCooldownTicks() {
        return 20 * (SPEC.isLoaded() ? ALTAR_COOLDOWN.get() : ALTAR_COOLDOWN.getDefault());
    }

    public static boolean foundryEnabled() {
        return SPEC.isLoaded() ? FOUNDRY_ENABLED.get() : FOUNDRY_ENABLED.getDefault();
    }
}
