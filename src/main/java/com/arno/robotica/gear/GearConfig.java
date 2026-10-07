package com.arno.robotica.gear;

import com.arno.robotica.core.CoreConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance values of the gear module (server config, file robotica-gear-server.toml). Falls back to defaults before load. */
public final class GearConfig {
    private GearConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue HARDNESS_RATIO;
    public static final ModConfigSpec.BooleanValue AREA_BREAKS_BLOCK_ENTITIES;
    public static final ModConfigSpec.IntValue BORE_DRILL_COST;
    public static final ModConfigSpec.IntValue SERVO_DRILL_COST;
    public static final ModConfigSpec.IntValue MAGMA_DRILL_COST;
    public static final ModConfigSpec.IntValue NULL_DRILL_COST;
    public static final ModConfigSpec.IntValue CHAINSAW_COST;
    public static final ModConfigSpec.IntValue SHOCK_BATON_COST;
    public static final ModConfigSpec.IntValue RIVET_GUN_COST;
    public static final ModConfigSpec.IntValue ARC_BLADE_COST;
    public static final ModConfigSpec.IntValue NULL_LANCE_COST;
    public static final ModConfigSpec.DoubleValue RIVET_DAMAGE;
    public static final ModConfigSpec.IntValue RIVET_STICK_TICKS;
    public static final ModConfigSpec.DoubleValue RIVET_SPEED;
    public static final ModConfigSpec.IntValue RIVET_FLIGHT_TICKS;
    // modules
    public static final ModConfigSpec.DoubleValue[] OVERCLOCK_SPEED = new ModConfigSpec.DoubleValue[3];
    public static final ModConfigSpec.DoubleValue[] OVERCLOCK_COST = new ModConfigSpec.DoubleValue[3];
    public static final ModConfigSpec.DoubleValue[] EDGE_DAMAGE = new ModConfigSpec.DoubleValue[3];
    public static final ModConfigSpec.IntValue[] EDGE_COST = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.IntValue THERMAL_SECONDS;
    public static final ModConfigSpec.IntValue THERMAL_COST;
    public static final ModConfigSpec.IntValue LAMP_LIGHT;
    public static final ModConfigSpec.IntValue LAMP_COST;
    public static final ModConfigSpec.IntValue LAMP_COOLDOWN;
    public static final ModConfigSpec.IntValue ROD_COST;
    public static final ModConfigSpec.IntValue ROD_CAPACITY;
    public static final ModConfigSpec.IntValue ROD_COOLDOWN;
    public static final ModConfigSpec.DoubleValue[] PIERCE_SHARE = new ModConfigSpec.DoubleValue[3];
    public static final ModConfigSpec.IntValue[] PIERCE_COST = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.IntValue[] CHAIN_EXTRA_ARCS = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.DoubleValue CHAIN_RANGE_PER_LEVEL;
    public static final ModConfigSpec.IntValue CHAIN_COST_PER_ARC;
    public static final ModConfigSpec.IntValue[] RICOCHET_BOUNCES = new ModConfigSpec.IntValue[2];
    public static final ModConfigSpec.IntValue[] RICOCHET_COST = new ModConfigSpec.IntValue[2];
    public static final ModConfigSpec.DoubleValue RICOCHET_RANGE;
    public static final ModConfigSpec.DoubleValue RICOCHET_DAMAGE;
    public static final ModConfigSpec.DoubleValue LIFESTEAL_SHARE;
    public static final ModConfigSpec.DoubleValue LIFESTEAL_MAX_PER_SECOND;
    public static final ModConfigSpec.IntValue LIFESTEAL_COOLDOWN;
    public static final ModConfigSpec.IntValue LIFESTEAL_COST;

    private static final double[] OVERCLOCK_SPEED_DEFAULTS = {0.5, 1.0, 2.0};
    private static final double[] OVERCLOCK_COST_DEFAULTS = {0.2, 0.4, 0.6};
    private static final double[] EDGE_DAMAGE_DEFAULTS = {0.15, 0.3, 0.45};
    private static final int[] EDGE_COST_DEFAULTS = {50, 100, 150};
    private static final double[] PIERCE_SHARE_DEFAULTS = {0.2, 0.35, 0.5};
    private static final int[] PIERCE_COST_DEFAULTS = {50, 100, 150};
    private static final int[] CHAIN_ARC_DEFAULTS = {2, 4, 6};
    private static final int[] RICOCHET_BOUNCE_DEFAULTS = {1, 2};
    private static final int[] RICOCHET_COST_DEFAULTS = {100, 200};

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("area");
        MAX_BLOCKS_PER_TICK = b.comment("Blocks per tick and player an area break may remove once it runs through the server queue (areas over 27 blocks).")
                .defineInRange("maxBlocksPerTick", 64, 1, 1024);
        HARDNESS_RATIO = b.comment("Area breaks skip blocks harder than the first block times this factor (keeps obsidian safe when you hit dirt).")
                .defineInRange("hardnessRatio", 8.0, 1.0, 1000.0);
        AREA_BREAKS_BLOCK_ENTITIES = b.comment("Allow area, vein and tree breaks to remove blocks with block entities (chests, machines).")
                .define("areaBreaksBlockEntities", false);
        b.pop();
        b.push("energy");
        BORE_DRILL_COST = b.comment("FE per block, Bore Drill.").defineInRange("boreDrillPerBlock", 40, 0, 1_000_000);
        SERVO_DRILL_COST = b.comment("FE per block, Servo Drill.").defineInRange("servoDrillPerBlock", 50, 0, 1_000_000);
        MAGMA_DRILL_COST = b.comment("FE per block, Magma Drill.").defineInRange("magmaDrillPerBlock", 60, 0, 1_000_000);
        NULL_DRILL_COST = b.comment("FE per block, Null Drill.").defineInRange("nullDrillPerBlock", 80, 0, 1_000_000);
        CHAINSAW_COST = b.comment("FE per log, Chainsaw.").defineInRange("chainsawPerLog", 30, 0, 1_000_000);
        SHOCK_BATON_COST = b.comment("FE per hit, Shock Baton.").defineInRange("shockBatonPerHit", 250, 0, 1_000_000);
        RIVET_GUN_COST = b.comment("FE per shot, Rivet Gun.").defineInRange("rivetGunPerShot", 400, 0, 1_000_000);
        ARC_BLADE_COST = b.comment("FE per hit, Arc Blade.").defineInRange("arcBladePerHit", 800, 0, 1_000_000);
        NULL_LANCE_COST = b.comment("FE per shot, Null Lance.").defineInRange("nullLancePerShot", 8_000, 0, 100_000_000);
        b.pop();
        b.push("lampRod");
        ROD_COST = b.comment("Lamp Rod: FE per Spark Lamp placed.").defineInRange("lampRodPerLamp", 20, 0, 1_000_000);
        ROD_CAPACITY = b.comment("Lamp Rod: battery size in FE.").defineInRange("lampRodCapacity", 20_000, 1, 100_000_000);
        ROD_COOLDOWN = b.comment("Lamp Rod: ticks between two uses.").defineInRange("lampRodCooldown", 4, 0, 1200);
        b.pop();
        b.push("rivet");
        RIVET_DAMAGE = b.comment("Damage of one Rivet Gun rivet.").defineInRange("rivetDamage", 8.0, 0.0, 1000.0);
        RIVET_STICK_TICKS = b.comment("Ticks a rivet stays stuck in a block before it shatters.").defineInRange("rivetStickTicks", 20, 1, 1200);
        RIVET_SPEED = b.comment("Rivet speed in blocks per tick (an arrow from a full bow is 3).").defineInRange("rivetSpeed", 4.5, 0.5, 10.0);
        RIVET_FLIGHT_TICKS = b.comment("Ticks a rivet flies before it drops out of the air.").defineInRange("rivetFlightTicks", 100, 10, 1200);
        b.pop();
        b.push("modules");
        for (int lv = 1; lv <= 3; lv++) {
            OVERCLOCK_SPEED[lv - 1] = b.comment("Overclock " + lv + ": extra mining speed (1.0 = twice as fast).")
                    .defineInRange("overclockSpeed" + lv, OVERCLOCK_SPEED_DEFAULTS[lv - 1], 0.0, 20.0);
            OVERCLOCK_COST[lv - 1] = b.comment("Overclock " + lv + ": extra FE per block as a share of the tool's cost (0.2 = +20%).")
                    .defineInRange("overclockCost" + lv, OVERCLOCK_COST_DEFAULTS[lv - 1], 0.0, 10.0);
        }
        for (int lv = 1; lv <= 3; lv++) {
            EDGE_DAMAGE[lv - 1] = b.comment("Sharpened Edge " + lv + ": extra damage of a paid hit or shot (0.15 = +15%).")
                    .defineInRange("sharpenedEdgeDamage" + lv, EDGE_DAMAGE_DEFAULTS[lv - 1], 0.0, 10.0);
            EDGE_COST[lv - 1] = b.comment("Sharpened Edge " + lv + ": extra FE per hit or shot.")
                    .defineInRange("sharpenedEdgeCost" + lv, EDGE_COST_DEFAULTS[lv - 1], 0, 1_000_000);
        }
        THERMAL_SECONDS = b.comment("Thermal Edge: seconds a paid hit or shot sets the target on fire.").defineInRange("thermalEdgeSeconds", 4, 1, 60);
        THERMAL_COST = b.comment("Thermal Edge: extra FE per hit or shot.").defineInRange("thermalEdgeCost", 100, 0, 1_000_000);
        LAMP_LIGHT = b.comment("Lamp Placer: places a Spark Lamp where you mined when the light there is this or lower (sky or block light).")
                .defineInRange("lampPlacerLight", 7, 0, 14);
        LAMP_COST = b.comment("Lamp Placer: FE per Spark Lamp placed.").defineInRange("lampPlacerCost", 10, 0, 1_000_000);
        LAMP_COOLDOWN = b.comment("Lamp Placer: ticks between two lamps.").defineInRange("lampPlacerCooldown", 10, 0, 1200);
        for (int lv = 1; lv <= 3; lv++) {
            PIERCE_SHARE[lv - 1] = b.comment("Armor Pierce " + lv + ": share of the armor reduction that is ignored.")
                    .defineInRange("armorPierceShare" + lv, PIERCE_SHARE_DEFAULTS[lv - 1], 0.0, 1.0);
            PIERCE_COST[lv - 1] = b.comment("Armor Pierce " + lv + ": extra FE per hit or shot.")
                    .defineInRange("armorPierceCost" + lv, PIERCE_COST_DEFAULTS[lv - 1], 0, 1_000_000);
        }
        for (int lv = 1; lv <= 3; lv++) {
            CHAIN_EXTRA_ARCS[lv - 1] = b.comment("Chain Lightning " + lv + ": extra arcs on top of the Arc Blade's 3.")
                    .defineInRange("chainLightningArcs" + lv, CHAIN_ARC_DEFAULTS[lv - 1], 0, 32);
        }
        CHAIN_RANGE_PER_LEVEL = b.comment("Chain Lightning: blocks of extra jump range per level (the Arc Blade jumps 6).")
                .defineInRange("chainLightningRangePerLevel", 2.0, 0.0, 16.0);
        CHAIN_COST_PER_ARC = b.comment("Chain Lightning: FE per extra arc that hits.").defineInRange("chainLightningCostPerArc", 150, 0, 1_000_000);
        for (int lv = 1; lv <= 2; lv++) {
            RICOCHET_BOUNCES[lv - 1] = b.comment("Ricochet Rivets " + lv + ": bounces after the first hit.")
                    .defineInRange("ricochetBounces" + lv, RICOCHET_BOUNCE_DEFAULTS[lv - 1], 0, 8);
            RICOCHET_COST[lv - 1] = b.comment("Ricochet Rivets " + lv + ": extra FE per shot.")
                    .defineInRange("ricochetCost" + lv, RICOCHET_COST_DEFAULTS[lv - 1], 0, 1_000_000);
        }
        RICOCHET_RANGE = b.comment("Ricochet Rivets: how far a rivet looks for the next monster.").defineInRange("ricochetRange", 10.0, 1.0, 32.0);
        RICOCHET_DAMAGE = b.comment("Ricochet Rivets: damage of each bounce as a share of the hit before it.")
                .defineInRange("ricochetDamage", 0.75, 0.0, 1.0);
        LIFESTEAL_SHARE = b.comment("Lifesteal: share of the damage dealt that heals you.").defineInRange("lifestealShare", 0.10, 0.0, 1.0);
        LIFESTEAL_MAX_PER_SECOND = b.comment("Lifesteal: healing budget in health points, also the most healed in any one second.")
                .defineInRange("lifestealMaxPerSecond", 3.0, 0.0, 100.0);
        LIFESTEAL_COOLDOWN = b.comment("Lifesteal: ticks for an empty budget to refill (at least 20), without healing once it ran empty. Long-run healing is at most budget / this.").defineInRange("lifestealCooldown", 100, 0, 12_000);
        LIFESTEAL_COST = b.comment("Lifesteal: FE per health point healed (half a heart).").defineInRange("lifestealCostPerHealth", 2_000, 0, 10_000_000);
        b.pop();
        SPEC = b.build();
    }

    public static int maxBlocksPerTick() {
        return SPEC.isLoaded() ? MAX_BLOCKS_PER_TICK.get() : 64;
    }

    public static double hardnessRatio() {
        return SPEC.isLoaded() ? HARDNESS_RATIO.get() : 8.0;
    }

    public static boolean areaBreaksBlockEntities() {
        return SPEC.isLoaded() && AREA_BREAKS_BLOCK_ENTITIES.get();
    }

    /** Overclock: extra mining speed share (0 without). */
    public static double overclockSpeed(int level) {
        if (level <= 0) return 0.0;
        return SPEC.isLoaded() ? OVERCLOCK_SPEED[lv(level, 3)].get() : OVERCLOCK_SPEED_DEFAULTS[lv(level, 3)];
    }

    /** Overclock: extra FE per block as a share of the tool's cost (0 without). */
    public static double overclockCost(int level) {
        if (level <= 0) return 0.0;
        return SPEC.isLoaded() ? OVERCLOCK_COST[lv(level, 3)].get() : OVERCLOCK_COST_DEFAULTS[lv(level, 3)];
    }

    /** Sharpened Edge: extra damage share (0 without). */
    public static double edgeDamage(int level) {
        if (level <= 0) return 0.0;
        return SPEC.isLoaded() ? EDGE_DAMAGE[lv(level, 3)].get() : EDGE_DAMAGE_DEFAULTS[lv(level, 3)];
    }

    public static int edgeCost(int level) {
        if (level <= 0) return 0;
        return fe(EDGE_COST[lv(level, 3)], EDGE_COST_DEFAULTS[lv(level, 3)]);
    }

    public static int thermalSeconds() {
        return SPEC.isLoaded() ? THERMAL_SECONDS.get() : 4;
    }

    public static int thermalCost(int level) {
        return level <= 0 ? 0 : fe(THERMAL_COST, 100);
    }

    public static float rivetDamage() {
        return SPEC.isLoaded() ? RIVET_DAMAGE.get().floatValue() : 8.0F;
    }

    public static int rivetStickTicks() {
        return SPEC.isLoaded() ? RIVET_STICK_TICKS.get() : 20;
    }

    public static float rivetSpeed() {
        return SPEC.isLoaded() ? RIVET_SPEED.get().floatValue() : 4.5F;
    }

    public static int rivetFlightTicks() {
        return SPEC.isLoaded() ? RIVET_FLIGHT_TICKS.get() : 100;
    }

    public static int rodCost() {
        return fe(ROD_COST, 20);
    }

    public static int rodCapacity() {
        return SPEC.isLoaded() ? ROD_CAPACITY.get() : 20_000;
    }

    public static int rodCooldown() {
        return SPEC.isLoaded() ? ROD_COOLDOWN.get() : 4;
    }

    public static int lampLight() {
        return SPEC.isLoaded() ? LAMP_LIGHT.get() : 7;
    }

    public static int lampCost() {
        return fe(LAMP_COST, 10);
    }

    public static int lampCooldown() {
        return SPEC.isLoaded() ? LAMP_COOLDOWN.get() : 10;
    }

    private static int lv(int level, int max) {
        return Math.max(1, Math.min(max, level)) - 1;
    }

    public static double pierceShare(int level) {
        if (level <= 0) return 0.0;
        return SPEC.isLoaded() ? PIERCE_SHARE[lv(level, 3)].get() : PIERCE_SHARE_DEFAULTS[lv(level, 3)];
    }

    public static int pierceCost(int level) {
        if (level <= 0) return 0;
        return fe(PIERCE_COST[lv(level, 3)], PIERCE_COST_DEFAULTS[lv(level, 3)]);
    }

    public static int chainExtraArcs(int level) {
        if (level <= 0) return 0;
        return SPEC.isLoaded() ? CHAIN_EXTRA_ARCS[lv(level, 3)].get() : CHAIN_ARC_DEFAULTS[lv(level, 3)];
    }

    public static double chainRangeBonus(int level) {
        return Math.max(0, level) * (SPEC.isLoaded() ? CHAIN_RANGE_PER_LEVEL.get() : 2.0);
    }

    public static int chainCostPerArc() {
        return fe(CHAIN_COST_PER_ARC, 150);
    }

    public static int ricochetBounces(int level) {
        if (level <= 0) return 0;
        return SPEC.isLoaded() ? RICOCHET_BOUNCES[lv(level, 2)].get() : RICOCHET_BOUNCE_DEFAULTS[lv(level, 2)];
    }

    public static int ricochetCost(int level) {
        if (level <= 0) return 0;
        return fe(RICOCHET_COST[lv(level, 2)], RICOCHET_COST_DEFAULTS[lv(level, 2)]);
    }

    public static double ricochetRange() {
        return SPEC.isLoaded() ? RICOCHET_RANGE.get() : 10.0;
    }

    public static float ricochetDamage() {
        return SPEC.isLoaded() ? RICOCHET_DAMAGE.get().floatValue() : 0.75F;
    }

    public static float lifestealShare() {
        return SPEC.isLoaded() ? LIFESTEAL_SHARE.get().floatValue() : 0.10F;
    }

    public static float lifestealMaxPerSecond() {
        return SPEC.isLoaded() ? LIFESTEAL_MAX_PER_SECOND.get().floatValue() : 3.0F;
    }

    public static int lifestealCooldown() {
        return SPEC.isLoaded() ? LIFESTEAL_COOLDOWN.get() : 100;
    }

    public static int lifestealCost() {
        return fe(LIFESTEAL_COST, 2_000);
    }

    /** Value of an energy entry, scaled by the global energy multiplier. */
    public static int fe(ModConfigSpec.IntValue value, int fallback) {
        return CoreConfig.scaleEnergy(SPEC.isLoaded() ? value.get() : fallback);
    }
}
