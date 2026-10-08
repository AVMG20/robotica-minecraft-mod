package com.arno.robotica.industry;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the industry module (server config, file robotica-industry-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class IndustryConfig {
    private IndustryConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue ALLOY_SMELTER_POWER;
    private static final ModConfigSpec.IntValue CENTRIFUGE_POWER;
    private static final ModConfigSpec.IntValue ASSEMBLER_POWER;
    private static final ModConfigSpec.IntValue MACHINE_BUFFER;
    private static final ModConfigSpec.IntValue MACHINE_INPUT;
    private static final ModConfigSpec.DoubleValue[] TIER_SPEED = new ModConfigSpec.DoubleValue[4];
    private static final ModConfigSpec.IntValue FORTUNE_BONUS;
    private static final ModConfigSpec.IntValue RTG_POWER;
    private static final ModConfigSpec.IntValue RTG_PELLET_TICKS;
    private static final ModConfigSpec.IntValue RTG_BUFFER;
    private static final ModConfigSpec.IntValue RTG_OUTPUT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("machines");
        ALLOY_SMELTER_POWER = b.comment("Alloy Smelter: base FE/t while working (the Pyrosteel and Resonant Alloy recipes ask for more).")
                .defineInRange("alloySmelterPower", 40, 1, 1_000_000);
        CENTRIFUGE_POWER = b.comment("Centrifuge: base FE/t while working.")
                .defineInRange("centrifugePower", 60, 1, 1_000_000);
        ASSEMBLER_POWER = b.comment("Assembler: base FE/t while working (Age 3 and 4 parts ask for more).")
                .defineInRange("assemblerPower", 80, 1, 1_000_000);
        MACHINE_BUFFER = b.comment("FE buffer of a Mk1 industry machine (a MkN holds N times as much).")
                .defineInRange("machineBuffer", 200_000, 1_000, 100_000_000);
        MACHINE_INPUT = b.comment("FE/t a Mk1 industry machine accepts from cables and Tesla Coils (a MkN N times as much). A recipe that wants more FE/t runs at this rate and takes longer.")
                .defineInRange("machineInput", 20_000, 1, 100_000_000);
        double[] speeds = {1.0, 2.0, 3.0, 5.0};
        for (int i = 0; i < 4; i++) {
            TIER_SPEED[i] = b.comment("Mk" + (i + 1) + " machines work this many times faster than Mk1 (and draw that much more FE/t, so FE per item stays the same).")
                    .defineInRange("tierSpeedMk" + (i + 1), speeds[i], 0.1, 100.0);
        }
        FORTUNE_BONUS = b.comment("Percent chance per Fortune card that a craft gives one more of its main result (Mk2 and up; every card adds 25% FE).")
                .defineInRange("fortuneBonus", 10, 0, 100);
        b.pop();
        b.push("rtg");
        RTG_POWER = b.comment("Radioisotope Generator: FE/t while a pellet decays (before the global generation multiplier).")
                .defineInRange("rtgPower", 150, 1, 1_000_000);
        RTG_PELLET_TICKS = b.comment("Ticks one fuel pellet lasts in the RTG (24,000 = 20 minutes, 3.6M FE at 150 FE/t; the Tesla Spire and Core Reactor get more out of a pellet).")
                .defineInRange("rtgPelletTicks", 24_000, 20, 10_000_000);
        RTG_BUFFER = b.comment("Internal FE buffer of the RTG.")
                .defineInRange("rtgBuffer", 100_000, 1_000, 100_000_000);
        RTG_OUTPUT = b.comment("FE/t the RTG pushes into neighbours and lets Tesla Coils take.")
                .defineInRange("rtgOutput", 1_000, 1, 100_000_000);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int alloySmelterPower() { return get(ALLOY_SMELTER_POWER); }
    public static int centrifugePower() { return get(CENTRIFUGE_POWER); }
    public static int assemblerPower() { return get(ASSEMBLER_POWER); }
    public static int machineBuffer() { return get(MACHINE_BUFFER); }
    public static int machineInput() { return get(MACHINE_INPUT); }
    /** tier 1-4 */
    public static double tierSpeed(int tier) {
        ModConfigSpec.DoubleValue v = TIER_SPEED[Math.max(1, Math.min(4, tier)) - 1];
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }
    public static int fortuneBonus() { return get(FORTUNE_BONUS); }
    public static int rtgPower() { return get(RTG_POWER); }
    public static int rtgPelletTicks() { return get(RTG_PELLET_TICKS); }
    public static int rtgBuffer() { return get(RTG_BUFFER); }
    public static int rtgOutput() { return get(RTG_OUTPUT); }
}
