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
    private static final ModConfigSpec.DoubleValue TYRANT_HEALTH;
    private static final ModConfigSpec.DoubleValue TYRANT_ARMOR;
    private static final ModConfigSpec.DoubleValue TYRANT_MELEE;
    private static final ModConfigSpec.DoubleValue TYRANT_BREATH;
    private static final ModConfigSpec.DoubleValue TYRANT_MORTAR;
    private static final ModConfigSpec.DoubleValue TYRANT_ERUPTION;
    private static final ModConfigSpec.IntValue TYRANT_COOLDOWN;
    private static final ModConfigSpec.IntValue TYRANT_COOLDOWN_P2;
    private static final ModConfigSpec.IntValue TYRANT_ATTACKS_PER_VENT;
    private static final ModConfigSpec.IntValue TYRANT_VENT_TICKS;
    private static final ModConfigSpec.DoubleValue TYRANT_VENT_MULTIPLIER;
    private static final ModConfigSpec.IntValue ALTAR_COOLDOWN;
    private static final ModConfigSpec.IntValue LOOT_LOCK;
    private static final ModConfigSpec.BooleanValue FOUNDRY_ENABLED;
    private static final ModConfigSpec.BooleanValue CINDER_FORGE_ENABLED;

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
        b.push("tyrant");
        TYRANT_HEALTH = b.comment("Hit points of the Forge Tyrant. Applied when it is summoned.")
                .defineInRange("tyrantHealth", 500.0, 1.0, 100_000.0);
        TYRANT_ARMOR = b.comment("Armor of the Forge Tyrant.")
                .defineInRange("tyrantArmor", 12.0, 0.0, 30.0);
        TYRANT_MELEE = b.comment("Damage of the Forge Tyrant's hammer punch.")
                .defineInRange("tyrantMeleeDamage", 14.0, 0.0, 1000.0);
        TYRANT_BREATH = b.comment("Damage of each flame breath pulse (4 pulses, 0.5 s apart).")
                .defineInRange("tyrantBreathDamage", 5.0, 0.0, 1000.0);
        TYRANT_MORTAR = b.comment("Damage of each magma glob it lobs.")
                .defineInRange("tyrantMortarDamage", 9.0, 0.0, 1000.0);
        TYRANT_ERUPTION = b.comment("Damage of each eruption under a marked spot.")
                .defineInRange("tyrantEruptionDamage", 16.0, 0.0, 1000.0);
        TYRANT_COOLDOWN = b.comment("Ticks between two big attacks of the Forge Tyrant.")
                .defineInRange("tyrantAttackCooldown", 60, 10, 1200);
        TYRANT_COOLDOWN_P2 = b.comment("Ticks between two big attacks below half health.")
                .defineInRange("tyrantAttackCooldownPhaseTwo", 35, 10, 1200);
        TYRANT_ATTACKS_PER_VENT = b.comment("Big attacks before the Forge Tyrant has to vent.")
                .defineInRange("tyrantAttacksPerVent", 3, 1, 20);
        TYRANT_VENT_TICKS = b.comment("Ticks the Forge Tyrant stands still with its core open when it vents.")
                .defineInRange("tyrantVentTicks", 100, 20, 1200);
        TYRANT_VENT_MULTIPLIER = b.comment("Damage multiplier while the Forge Tyrant vents.")
                .defineInRange("tyrantVentDamageMultiplier", 2.0, 1.0, 10.0);
        b.pop();
        b.push("altar");
        ALTAR_COOLDOWN = b.comment("Seconds before a Colossus Altar or Forge Altar can be used again after a summon.")
                .defineInRange("altarCooldownSeconds", 300, 0, 86_400);
        LOOT_LOCK = b.comment("Seconds a boss's loot belongs to its killer alone.")
                .defineInRange("lootLockSeconds", 120, 0, 3600);
        b.pop();
        b.push("worldgen");
        FOUNDRY_ENABLED = b.comment("Generate Rusted Foundry ruins (with a Colossus Altar) in plains, deserts and badlands. Only affects new chunks.")
                .define("foundryEnabled", true);
        CINDER_FORGE_ENABLED = b.comment("Generate Cinder Forge ruins (with a Forge Altar) in the Nether. Only affects new chunks.")
                .define("cinderForgeEnabled", true);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    private static double get(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    private static boolean get(ModConfigSpec.BooleanValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static double healthMultiplier() {
        return get(HEALTH_MULTIPLIER);
    }

    public static float damageMultiplier() {
        return (float) get(DAMAGE_MULTIPLIER);
    }

    public static int minionCap() {
        return get(MINION_CAP);
    }

    public static double tyrantHealth() {
        return get(TYRANT_HEALTH);
    }

    public static double tyrantArmor() {
        return get(TYRANT_ARMOR);
    }

    public static double tyrantMelee() {
        return get(TYRANT_MELEE);
    }

    public static float tyrantBreath() {
        return (float) get(TYRANT_BREATH);
    }

    public static float tyrantMortar() {
        return (float) get(TYRANT_MORTAR);
    }

    public static float tyrantEruption() {
        return (float) get(TYRANT_ERUPTION);
    }

    public static int tyrantCooldown(boolean phaseTwo) {
        return get(phaseTwo ? TYRANT_COOLDOWN_P2 : TYRANT_COOLDOWN);
    }

    public static int tyrantAttacksPerVent() {
        return get(TYRANT_ATTACKS_PER_VENT);
    }

    public static int tyrantVentTicks() {
        return get(TYRANT_VENT_TICKS);
    }

    public static float tyrantVentMultiplier() {
        return (float) get(TYRANT_VENT_MULTIPLIER);
    }

    public static int altarCooldownTicks() {
        return 20 * get(ALTAR_COOLDOWN);
    }

    public static int lootLockTicks() {
        return 20 * get(LOOT_LOCK);
    }

    public static boolean foundryEnabled() {
        return get(FOUNDRY_ENABLED);
    }

    public static boolean cinderForgeEnabled() {
        return get(CINDER_FORGE_ENABLED);
    }
}
