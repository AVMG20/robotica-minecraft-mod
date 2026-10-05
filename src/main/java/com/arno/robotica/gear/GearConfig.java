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

    /** Value of an energy entry, scaled by the global energy multiplier. */
    public static int fe(ModConfigSpec.IntValue value, int fallback) {
        return CoreConfig.scaleEnergy(SPEC.isLoaded() ? value.get() : fallback);
    }
}
