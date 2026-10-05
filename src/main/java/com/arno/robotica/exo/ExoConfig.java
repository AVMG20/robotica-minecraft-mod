package com.arno.robotica.exo;

import com.arno.robotica.core.CoreConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance values of the exo module (server config, file robotica-exo-server.toml). Falls back to defaults before load. */
public final class ExoConfig {
    private ExoConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue NIGHT_VISION, REBREATHER, ROBOT_HUD, JET_ASSIST, JET_ASSIST_JUMP, FLIGHT,
            KINETIC_SHIELD, SERVO_1, SERVO_2, SERVO_3, STEP_ASSIST, SPRING_HEELS, FALL_DAMPENER, MAGNET;
    private static final ModConfigSpec.IntValue MAGNET_RADIUS, AIR_JUMPS, CELL_RECHARGE_RATE;
    private static final ModConfigSpec.DoubleValue SHIELD_ABSORB;
    private static final ModConfigSpec.BooleanValue CELL_RECHARGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("exoEnergy");
        NIGHT_VISION = cost(b, "nightVisionPerSecond", "FE per second while Night Vision is on.", 10);
        REBREATHER = cost(b, "rebreatherPerSecond", "FE per second while breathing underwater with the Rebreather.", 60);
        ROBOT_HUD = cost(b, "robotHudPerSecond", "FE per second while the Robot HUD is on.", 5);
        JET_ASSIST = cost(b, "jetAssistPerSecond", "FE per second while Jet Assist slows a fall.", 40);
        JET_ASSIST_JUMP = cost(b, "jetAssistPerDoubleJump", "FE per Jet Assist double jump.", 400);
        FLIGHT = cost(b, "flightPerSecond", "FE per second while flying (120 FE/t).", 2400);
        KINETIC_SHIELD = cost(b, "kineticShieldPerDamage", "FE per point of damage the Kinetic Shield blocks.", 2000);
        SERVO_1 = cost(b, "servoStride1PerSecond", "FE per second while running with Servo Stride I.", 30);
        SERVO_2 = cost(b, "servoStride2PerSecond", "FE per second while running with Servo Stride II.", 60);
        SERVO_3 = cost(b, "servoStride3PerSecond", "FE per second while running with Servo Stride III.", 100);
        STEP_ASSIST = cost(b, "stepAssistPerSecond", "FE per second while walking with Step Assist.", 20);
        SPRING_HEELS = cost(b, "springHeelsPerJump", "FE per boosted jump.", 100);
        FALL_DAMPENER = cost(b, "fallDampenerPerBlock", "FE per block of a fall the Fall Dampener absorbs.", 150);
        MAGNET = cost(b, "magnetPerSecond", "FE per second while the Magnet pulls items.", 40);
        CELL_RECHARGE = b.comment("Energy cells (and Mainsprings) in the inventory top up the worn suit once per second.")
                .define("cellsRechargeSuit", true);
        CELL_RECHARGE_RATE = b.comment("Most FE per second the inventory cells move into the suit, all cells together.")
                .defineInRange("cellRechargePerSecond", 2_000, 0, 10_000_000);
        b.pop();
        b.push("exoModules");
        MAGNET_RADIUS = b.comment("Magnet pull radius in blocks.").defineInRange("magnetRadius", 6, 1, 16);
        AIR_JUMPS = b.comment("Extra jumps in the air with Jet Assist before landing.").defineInRange("airJumps", 1, 0, 5);
        SHIELD_ABSORB = b.comment("Share of each hit the Kinetic Shield can absorb; the rest reaches the armor as normal.")
                .defineInRange("kineticShieldAbsorb", 0.75, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private static ModConfigSpec.IntValue cost(ModConfigSpec.Builder b, String key, String comment, int def) {
        return b.comment(comment).defineInRange(key, def, 0, 10_000_000);
    }

    private static int base(ExoModuleKind kind) {
        ModConfigSpec.IntValue v = switch (kind) {
            case NIGHT_VISION -> NIGHT_VISION;
            case REBREATHER -> REBREATHER;
            case ROBOT_HUD -> ROBOT_HUD;
            case JET_ASSIST -> JET_ASSIST;
            case FLIGHT -> FLIGHT;
            case KINETIC_SHIELD -> KINETIC_SHIELD;
            case SERVO_STRIDE_1 -> SERVO_1;
            case SERVO_STRIDE_2 -> SERVO_2;
            case SERVO_STRIDE_3 -> SERVO_3;
            case STEP_ASSIST -> STEP_ASSIST;
            case SPRING_HEELS -> SPRING_HEELS;
            case FALL_DAMPENER -> FALL_DAMPENER;
            case MAGNET -> MAGNET;
        };
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    /** Configured cost of a module in FE, per {@link ExoModuleKind#unit} (second, jump, block or damage point), scaled by the global energy multiplier. */
    public static int cost(ExoModuleKind kind) {
        return CoreConfig.scaleEnergy(base(kind));
    }

    /** FE per tick of a per-second module. */
    public static double perTick(ExoModuleKind kind) {
        return cost(kind) / 20.0;
    }

    /** FE of one double jump. */
    public static int doubleJumpCost() {
        return CoreConfig.scaleEnergy(SPEC.isLoaded() ? JET_ASSIST_JUMP.get() : JET_ASSIST_JUMP.getDefault());
    }

    public static int magnetRadius() {
        return SPEC.isLoaded() ? MAGNET_RADIUS.get() : MAGNET_RADIUS.getDefault();
    }

    public static int airJumps() {
        return SPEC.isLoaded() ? AIR_JUMPS.get() : AIR_JUMPS.getDefault();
    }

    /** Cap on the FE per second all inventory cells together move into the suit (scaled by the global energy multiplier). */
    public static int cellRechargePerSecond() {
        return CoreConfig.scaleEnergy(SPEC.isLoaded() ? CELL_RECHARGE_RATE.get() : CELL_RECHARGE_RATE.getDefault());
    }

    /** Largest share of a hit the Kinetic Shield absorbs. */
    public static double shieldAbsorb() {
        return SPEC.isLoaded() ? SHIELD_ABSORB.get() : SHIELD_ABSORB.getDefault();
    }

    public static boolean cellRecharge() {
        return SPEC.isLoaded() ? CELL_RECHARGE.get() : CELL_RECHARGE.getDefault();
    }
}
