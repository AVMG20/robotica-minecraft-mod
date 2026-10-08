package com.arno.robotica.exo;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.module.ModuleKind;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the exo module (server config, file robotica-exo-server.toml). Every getter falls back to the
 * default before the config is loaded. Leveled values have one key per level: the level I key keeps the old name
 * (for example {@code magnetRadius}), levels II and III append the level ({@code magnetRadius2}).
 */
public final class ExoConfig {
    private ExoConfig() {}

    public static final ModConfigSpec SPEC;

    // ---- energy costs (FE per unit of the module)
    private static final ModConfigSpec.IntValue[] NIGHT_VISION, JET_ASSIST, JET_ASSIST_JUMP, KINETIC_SHIELD, SERVO, SPRING_HEELS,
            FALL_DAMPENER, MAGNET, SONAR, MED_INJECTOR;
    private static final ModConfigSpec.IntValue REBREATHER, ROBOT_HUD, FLIGHT, STEP_ASSIST, AUTO_FEEDER, SOLAR_WEAVE, HAZARD_SEAL,
            KINETIC_GEN_WALK, KINETIC_GEN_SPRINT, DASH, HYDRO_FINS, OVERCLOCK;
    private static final ModConfigSpec.IntValue CELL_RECHARGE_RATE;
    private static final ModConfigSpec.BooleanValue CELL_RECHARGE;

    // ---- module numbers
    private static final ModConfigSpec.IntValue[] MAGNET_RADIUS, AIR_JUMPS, THERMAL_RADIUS, SONAR_RADIUS, MED_COOLDOWN;
    private static final ModConfigSpec.DoubleValue[] SHIELD_ABSORB, SERVO_SPEED, SPRING_BOOST, MED_HEAL, CAPACITOR_BONUS;
    private static final ModConfigSpec.DoubleValue STEP_BONUS, MED_THRESHOLD, HYDRO_SWIM, HYDRO_MINING, DASH_SPEED;
    private static final ModConfigSpec.IntValue AUTO_FEEDER_HUNGER, SONAR_COOLDOWN, SONAR_DURATION, DASH_COOLDOWN;

    // ---- armor
    private static final ModConfigSpec.IntValue[] CAPACITY;
    private static final ModConfigSpec.IntValue MARK_MULTIPLIER;

    // ---- core socket set bonuses
    private static final ModConfigSpec.IntValue OVERCLOCK_DURATION, OVERCLOCK_COOLDOWN, OVERCLOCK_HASTE, OVERCLOCK_SPEED, MAGMA_BURN;
    private static final ModConfigSpec.DoubleValue ANTIGRAV_FLIGHT, ANTIGRAV_DASH;
    private static final ModConfigSpec.IntValue SET_BONUS_MARK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("exoEnergy");
        NIGHT_VISION = costs(b, "nightVisionPerSecond", "FE per second while Night Vision is on (I, II with thermal sight, III).", 10, 20, 30);
        REBREATHER = cost(b, "rebreatherPerSecond", "FE per second while breathing underwater with the Rebreather.", 60);
        ROBOT_HUD = cost(b, "robotHudPerSecond", "FE per second while the Robot HUD is on.", 5);
        AUTO_FEEDER = cost(b, "autoFeederPerFood", "FE per food item the Auto-Feeder feeds you.", 500);
        SOLAR_WEAVE = cost(b, "solarWeavePerSecond", "FE per second Solar Weave makes in full sunlight (scaled by the generation multiplier).", 200);
        SONAR = costs(b, "sonarPulsePerPing", "FE per Sonar Pulse ping (I, II, III).", 4_000, 6_000, 8_000);
        JET_ASSIST = costs(b, "jetAssistPerSecond", "FE per second while Jet Assist slows a fall (I, II, III).", 40, 30, 20);
        JET_ASSIST_JUMP = costs(b, "jetAssistPerDoubleJump", "FE per Jet Assist double jump (I, II, III).", 400, 350, 300);
        FLIGHT = cost(b, "flightPerSecond", "FE per second while flying (120 FE/t).", 2_400);
        KINETIC_SHIELD = costs(b, "kineticShieldPerDamage", "FE per point of damage the Kinetic Shield blocks (I, II, III).", 8_000, 6_000, 4_000);
        MED_INJECTOR = costs(b, "medInjectorPerUse", "FE per Med Injector shot (I, II, III).", 20_000, 30_000, 40_000);
        HAZARD_SEAL = cost(b, "hazardSealPerEffect", "FE per harmful effect the Hazard Seal clears.", 2_000);
        SERVO = new ModConfigSpec.IntValue[]{
                cost(b, "servoStride1PerSecond", "FE per second while running with Servo Stride I.", 30),
                cost(b, "servoStride2PerSecond", "FE per second while running with Servo Stride II.", 60),
                cost(b, "servoStride3PerSecond", "FE per second while running with Servo Stride III.", 100)};
        KINETIC_GEN_WALK = cost(b, "kineticGeneratorPerBlock", "FE the Kinetic Generator makes per block walked.", 5);
        KINETIC_GEN_SPRINT = cost(b, "kineticGeneratorPerSprintBlock", "FE the Kinetic Generator makes per block sprinted.", 8);
        DASH = cost(b, "dashThrustersPerDash", "FE per Dash Thrusters dash.", 3_000);
        STEP_ASSIST = cost(b, "stepAssistPerSecond", "FE per second while walking with Step Assist.", 10);
        SPRING_HEELS = costs(b, "springHeelsPerJump", "FE per boosted jump (I, II, III).", 100, 150, 200);
        FALL_DAMPENER = costs(b, "fallDampenerPerBlock", "FE per block of a fall the Fall Dampener absorbs (I, II, III).", 150, 100, 60);
        MAGNET = costs(b, "magnetPerSecond", "FE per second while the Magnet pulls items (I, II, III).", 40, 60, 80);
        HYDRO_FINS = cost(b, "hydroFinsPerSecond", "FE per second while swimming with Hydro Fins.", 20);
        OVERCLOCK = cost(b, "overclockPerUse", "FE per Overclock (Servo Core set bonus).", 50_000);
        CELL_RECHARGE = b.comment("Energy cells (and Mainsprings) in the inventory top up the worn suit once per second.")
                .define("cellsRechargeSuit", true);
        CELL_RECHARGE_RATE = b.comment("Most FE per second the inventory cells move into the suit, all cells together.")
                .defineInRange("cellRechargePerSecond", 2_000, 0, 10_000_000);
        b.pop();

        b.push("exoModules");
        THERMAL_RADIUS = ints(b, "thermalSightRadius", "Night Vision thermal sight radius in blocks (I has none, II, III).", 0, 128, 0, 24, 48);
        AUTO_FEEDER_HUNGER = b.comment("The Auto-Feeder feeds you when your food level is at or below this (20 is full).")
                .defineInRange("autoFeederHunger", 19, 0, 19);
        SONAR_RADIUS = ints(b, "sonarPulseRadius", "Sonar Pulse radius in blocks (I, II, III).", 4, 64, 16, 24, 32);
        SONAR_COOLDOWN = b.comment("Ticks between two Sonar Pulse pings.").defineInRange("sonarPulseCooldown", 100, 0, 72_000);
        SONAR_DURATION = b.comment("Ticks the Sonar Pulse outlines stay visible.").defineInRange("sonarPulseDuration", 200, 20, 1_200);
        AIR_JUMPS = ints(b, "airJumps", "Extra jumps in the air with Jet Assist before landing (I, II, III).", 0, 5, 1, 2, 3);
        SHIELD_ABSORB = doubles(b, "kineticShieldAbsorb", "Share of each hit (after armor) the Kinetic Shield can absorb instead of health (I, II, III); the rest hurts as normal.",
                0.0, 1.0, 0.75, 0.85, 0.95);
        MED_HEAL = doubles(b, "medInjectorHeal", "Health the Med Injector restores per shot (I, II, III; 2 = one heart).", 0.0, 40.0, 4.0, 6.0, 8.0);
        MED_COOLDOWN = ints(b, "medInjectorCooldown", "Ticks between two Med Injector shots (I, II, III).", 0, 72_000, 1_200, 900, 600);
        MED_THRESHOLD = b.comment("The Med Injector fires when health drops to or below this share of max health.")
                .defineInRange("medInjectorThreshold", 0.4, 0.05, 0.95);
        SERVO_SPEED = doubles(b, "servoStrideSpeed", "Movement speed bonus of Servo Stride (I, II, III; 0.2 = +20%).", 0.0, 3.0, 0.2, 0.4, 0.6);
        DASH_SPEED = b.comment("Horizontal speed of a Dash Thrusters dash in blocks per tick.").defineInRange("dashThrustersSpeed", 1.6, 0.2, 5.0);
        DASH_COOLDOWN = b.comment("Ticks between two dashes.").defineInRange("dashThrustersCooldown", 60, 0, 72_000);
        STEP_BONUS = b.comment("Step height Step Assist adds (players step 0.6; +0.4 walks up full blocks).")
                .defineInRange("stepAssistHeight", 0.4, 0.0, 2.0);
        SPRING_BOOST = doubles(b, "springHeelsBoost", "Jump strength Spring Heels add (I, II, III; normal jump 0.42).", 0.0, 2.0, 0.15, 0.25, 0.35);
        MAGNET_RADIUS = ints(b, "magnetRadius", "Magnet pull radius in blocks (I, II, III).", 1, 32, 6, 10, 16);
        HYDRO_SWIM = b.comment("Swim speed bonus of Hydro Fins (0.5 = +50%).").defineInRange("hydroFinsSwimSpeed", 0.5, 0.0, 4.0);
        HYDRO_MINING = b.comment("Underwater mining speed Hydro Fins add (players mine at 0.2 under water, +0.8 is full speed).")
                .defineInRange("hydroFinsMiningSpeed", 0.8, 0.0, 1.0);
        CAPACITOR_BONUS = doubles(b, "capacitorPlatingBonus", "Extra battery of the piece with Capacitor Plating (I, II, III; 0.5 = +50%).",
                0.0, 10.0, 0.5, 1.0, 2.0);
        b.pop();

        b.push("exoArmor");
        CAPACITY = new ModConfigSpec.IntValue[]{
                b.comment("Battery of a Mk1 Exo Helmet in FE.").defineInRange("capacityHelmet", 200_000, 1_000, 50_000_000),
                b.comment("Battery of a Mk1 Exo Chestplate in FE.").defineInRange("capacityChestplate", 1_000_000, 1_000, 50_000_000),
                b.comment("Battery of a Mk1 Exo Leggings in FE.").defineInRange("capacityLeggings", 400_000, 1_000, 50_000_000),
                b.comment("Battery of a Mk1 Exo Boots in FE.").defineInRange("capacityBoots", 200_000, 1_000, 50_000_000)};
        MARK_MULTIPLIER = b.comment("Every mark multiplies the battery by this (Mk2 x4, Mk3 x16, Mk4 x64 with 4).")
                .defineInRange("markCapacityMultiplier", 4, 1, 8);
        b.pop();

        b.push("exoCoreSocket");
        SET_BONUS_MARK = b.comment("Lowest mark all four worn pieces need for the chestplate core's set bonus.")
                .defineInRange("setBonusMinMark", 2, 1, 4);
        OVERCLOCK_DURATION = b.comment("Ticks an Overclock lasts (Servo Core).").defineInRange("overclockDuration", 200, 20, 6_000);
        OVERCLOCK_COOLDOWN = b.comment("Ticks between two Overclocks.").defineInRange("overclockCooldown", 1_200, 0, 72_000);
        OVERCLOCK_HASTE = b.comment("Haste level of an Overclock (2 = Haste II).").defineInRange("overclockHaste", 2, 0, 5);
        OVERCLOCK_SPEED = b.comment("Speed level of an Overclock (1 = Speed I).").defineInRange("overclockSpeed", 1, 0, 5);
        MAGMA_BURN = b.comment("Seconds a melee hit sets the target on fire (Magma Core).").defineInRange("magmaBurnSeconds", 4, 0, 60);
        ANTIGRAV_FLIGHT = b.comment("Flight cost factor with the Antigrav Core (0.5 = half).").defineInRange("antigravFlightFactor", 0.5, 0.0, 1.0);
        ANTIGRAV_DASH = b.comment("Dash cooldown factor with the Antigrav Core (0.5 = half).").defineInRange("antigravDashFactor", 0.5, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private static ModConfigSpec.IntValue cost(ModConfigSpec.Builder b, String key, String comment, int def) {
        return b.comment(comment).defineInRange(key, def, 0, 10_000_000);
    }

    private static String levelKey(String key, int level) {
        return level == 1 ? key : key + level;
    }

    private static ModConfigSpec.IntValue[] costs(ModConfigSpec.Builder b, String key, String comment, int... defs) {
        return ints(b, key, comment, 0, 10_000_000, defs);
    }

    private static ModConfigSpec.IntValue[] ints(ModConfigSpec.Builder b, String key, String comment, int min, int max, int... defs) {
        ModConfigSpec.IntValue[] out = new ModConfigSpec.IntValue[defs.length];
        for (int i = 0; i < defs.length; i++) {
            out[i] = b.comment(comment + " Level " + ModuleKind.roman(i + 1) + ".").defineInRange(levelKey(key, i + 1), defs[i], min, max);
        }
        return out;
    }

    private static ModConfigSpec.DoubleValue[] doubles(ModConfigSpec.Builder b, String key, String comment, double min, double max, double... defs) {
        ModConfigSpec.DoubleValue[] out = new ModConfigSpec.DoubleValue[defs.length];
        for (int i = 0; i < defs.length; i++) {
            out[i] = b.comment(comment + " Level " + ModuleKind.roman(i + 1) + ".").defineInRange(levelKey(key, i + 1), defs[i], min, max);
        }
        return out;
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

    private static <T> T lv(T[] values, int level) {
        return values[Math.max(0, Math.min(values.length, level) - 1)];
    }

    // ---------------------------------------------------------------- costs

    private static int base(ModuleKind kind, int level) {
        return switch (kind) {
            case NIGHT_VISION -> get(lv(NIGHT_VISION, level));
            case REBREATHER -> get(REBREATHER);
            case ROBOT_HUD -> get(ROBOT_HUD);
            case AUTO_FEEDER -> get(AUTO_FEEDER);
            case SOLAR_WEAVE -> get(SOLAR_WEAVE);
            case SONAR_PULSE -> get(lv(SONAR, level));
            case JET_ASSIST -> get(lv(JET_ASSIST, level));
            case FLIGHT -> get(FLIGHT);
            case KINETIC_SHIELD -> get(lv(KINETIC_SHIELD, level));
            case MED_INJECTOR -> get(lv(MED_INJECTOR, level));
            case HAZARD_SEAL -> get(HAZARD_SEAL);
            case SERVO_STRIDE -> get(lv(SERVO, level));
            case KINETIC_GENERATOR -> get(KINETIC_GEN_WALK);
            case DASH_THRUSTERS -> get(DASH);
            case STEP_ASSIST -> get(STEP_ASSIST);
            case SPRING_HEELS -> get(lv(SPRING_HEELS, level));
            case FALL_DAMPENER -> get(lv(FALL_DAMPENER, level));
            case MAGNET -> get(lv(MAGNET, level));
            case HYDRO_FINS -> get(HYDRO_FINS);
            default -> 0;
        };
    }

    /**
     * Configured cost of a module in FE per unit (second, jump, block, damage point, use...),
     * scaled by the global energy multiplier. For Solar Weave and the Kinetic Generator it is what they make, scaled
     * by the generation multiplier.
     */
    public static int cost(ModuleKind kind, int level) {
        int base = base(kind, level);
        if (kind == ModuleKind.SOLAR_WEAVE || kind == ModuleKind.KINETIC_GENERATOR) return CoreConfig.scaleGeneration(base);
        return CoreConfig.scaleEnergy(base);
    }

    /** FE per tick of a per-second module. */
    public static double perTick(ModuleKind kind, int level) {
        return cost(kind, level) / 20.0;
    }

    public static int doubleJumpCost(int level) {
        return CoreConfig.scaleEnergy(get(lv(JET_ASSIST_JUMP, level)));
    }

    public static int kineticSprintPerBlock() {
        return CoreConfig.scaleGeneration(get(KINETIC_GEN_SPRINT));
    }

    public static int overclockCost() {
        return CoreConfig.scaleEnergy(get(OVERCLOCK));
    }

    public static int cellRechargePerSecond() {
        return CoreConfig.scaleEnergy(get(CELL_RECHARGE_RATE));
    }

    public static boolean cellRecharge() {
        return get(CELL_RECHARGE);
    }

    // ---------------------------------------------------------------- module numbers

    public static int thermalRadius(int level) {
        return get(lv(THERMAL_RADIUS, level));
    }

    public static int autoFeederHunger() {
        return get(AUTO_FEEDER_HUNGER);
    }

    public static int sonarRadius(int level) {
        return get(lv(SONAR_RADIUS, level));
    }

    public static int sonarCooldown() {
        return get(SONAR_COOLDOWN);
    }

    public static int sonarDuration() {
        return get(SONAR_DURATION);
    }

    public static int airJumps(int level) {
        return get(lv(AIR_JUMPS, level));
    }

    public static double shieldAbsorb(int level) {
        return get(lv(SHIELD_ABSORB, level));
    }

    public static float medHeal(int level) {
        return (float) get(lv(MED_HEAL, level));
    }

    public static int medCooldown(int level) {
        return get(lv(MED_COOLDOWN, level));
    }

    public static double medThreshold() {
        return get(MED_THRESHOLD);
    }

    public static double servoSpeed(int level) {
        return get(lv(SERVO_SPEED, level));
    }

    public static double dashSpeed() {
        return get(DASH_SPEED);
    }

    public static int dashCooldown() {
        return get(DASH_COOLDOWN);
    }

    public static double stepBonus() {
        return get(STEP_BONUS);
    }

    public static double springBoost(int level) {
        return get(lv(SPRING_BOOST, level));
    }

    public static int magnetRadius(int level) {
        return get(lv(MAGNET_RADIUS, level));
    }

    public static double hydroSwim() {
        return get(HYDRO_SWIM);
    }

    public static double hydroMining() {
        return get(HYDRO_MINING);
    }

    public static double capacitorBonus(int level) {
        return level <= 0 ? 0 : get(lv(CAPACITOR_BONUS, level));
    }

    // ---------------------------------------------------------------- armor and set bonus

    /** Mk1 battery of a piece (0 head, 1 chest, 2 legs, 3 feet). */
    public static int baseCapacity(int piece) {
        return get(CAPACITY[piece]);
    }

    public static int markMultiplier() {
        return get(MARK_MULTIPLIER);
    }

    public static int setBonusMinMark() {
        return get(SET_BONUS_MARK);
    }

    public static int overclockDuration() {
        return get(OVERCLOCK_DURATION);
    }

    public static int overclockCooldown() {
        return get(OVERCLOCK_COOLDOWN);
    }

    public static int overclockHaste() {
        return get(OVERCLOCK_HASTE);
    }

    public static int overclockSpeed() {
        return get(OVERCLOCK_SPEED);
    }

    public static int magmaBurnSeconds() {
        return get(MAGMA_BURN);
    }

    public static double antigravFlightFactor() {
        return get(ANTIGRAV_FLIGHT);
    }

    public static double antigravDashFactor() {
        return get(ANTIGRAV_DASH);
    }

    // ---------------------------------------------------------------- tooltips

    /** Format arguments of a core's set bonus description ({@code exo.robotica.bonus.<id>.desc}). */
    public static Object[] bonusArgs(String id) {
        return switch (id) {
            case "servo" -> new Object[]{com.arno.robotica.core.item.HasDetails.key("key.robotica.exo.overclock"),
                    net.minecraft.network.chat.Component.translatable("enchantment.level." + overclockHaste()),
                    net.minecraft.network.chat.Component.translatable("enchantment.level." + overclockSpeed()),
                    Math.round(overclockDuration() / 20.0), Math.round(overclockCooldown() / 20.0)};
            case "magma" -> new Object[]{magmaBurnSeconds()};
            case "antigrav" -> new Object[]{pct(antigravFlightFactor()), pct(antigravDashFactor())};
            default -> new Object[0];
        };
    }

    private static String pct(double share) {
        return Long.toString(Math.round(share * 100));
    }

    /** Format arguments of the module's description line ({@code exo.robotica.module.<id>.desc}). */
    public static Object[] describeArgs(ModuleKind kind, int level) {
        return switch (kind) {
            case NIGHT_VISION -> new Object[]{thermalRadius(level)};
            case SONAR_PULSE -> new Object[]{sonarRadius(level)};
            case JET_ASSIST -> new Object[]{airJumps(level)};
            case KINETIC_SHIELD -> new Object[]{pct(shieldAbsorb(level))};
            case MED_INJECTOR -> new Object[]{String.format(java.util.Locale.ROOT, "%.0f", medHeal(level) / 2.0), medCooldown(level) / 20};
            case SERVO_STRIDE -> new Object[]{pct(servoSpeed(level))};
            case DASH_THRUSTERS -> new Object[]{dashCooldown() / 20.0};
            case SPRING_HEELS -> new Object[]{level};
            case MAGNET -> new Object[]{magnetRadius(level)};
            case CAPACITOR_PLATING -> new Object[]{pct(capacitorBonus(level))};
            case KINETIC_GENERATOR -> new Object[]{kineticSprintPerBlock()};
            default -> new Object[0];
        };
    }
}
