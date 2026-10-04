package com.arno.robotica.power;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the power module (server config, file robotica-power-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class PowerConfig {
    private PowerConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue CRANK_FE_PER_CLICK;
    private static final ModConfigSpec.IntValue CRANK_CLICKS_PER_SECOND;
    private static final ModConfigSpec.IntValue CRANK_AUTO_RATE;
    private static final ModConfigSpec.IntValue GENERATOR_OUTPUT;
    private static final ModConfigSpec.IntValue GENERATOR_BUFFER;
    private static final ModConfigSpec.IntValue SOLAR_MK1;
    private static final ModConfigSpec.IntValue SOLAR_MK2;
    private static final ModConfigSpec.IntValue COPPER_CONDUIT_RATE;
    private static final ModConfigSpec.IntValue GOLD_CONDUIT_RATE;
    private static final ModConfigSpec.IntValue CHARGER_RATE;
    private static final ModConfigSpec.IntValue PRESS_POWER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("winding_crank");
        CRANK_FE_PER_CLICK = b.comment("FE wound into the Mainspring per hand click.")
                .defineInRange("crankFePerClick", 2_000, 1, 1_000_000);
        CRANK_CLICKS_PER_SECOND = b.comment("Server side cap on hand clicks per player and second.")
                .defineInRange("crankClicksPerSecond", 4, 1, 20);
        CRANK_AUTO_RATE = b.comment("FE/t the crank accepts from an FE source (water wheel, Create, ...).")
                .defineInRange("crankAutoRate", 200, 1, 1_000_000);
        b.pop();
        b.push("combustion_generator");
        GENERATOR_OUTPUT = b.comment("FE/t while burning (before the global generation multiplier).")
                .defineInRange("generatorOutput", 40, 1, 1_000_000);
        GENERATOR_BUFFER = b.comment("Internal FE buffer.")
                .defineInRange("generatorBuffer", 40_000, 1_000, 100_000_000);
        b.pop();
        b.push("solar");
        SOLAR_MK1 = b.comment("Solar Panel Mk1, FE/t in daylight with sky access.")
                .defineInRange("solarMk1", 8, 1, 1_000_000);
        SOLAR_MK2 = b.comment("Solar Panel Mk2, FE/t in daylight with sky access.")
                .defineInRange("solarMk2", 32, 1, 1_000_000);
        b.pop();
        b.push("conduits");
        COPPER_CONDUIT_RATE = b.comment("FE/t a network made of Copper Conduits can move.")
                .defineInRange("copperConduitRate", 256, 1, 100_000_000);
        GOLD_CONDUIT_RATE = b.comment("FE/t a network made of Gold Conduits can move.")
                .defineInRange("goldConduitRate", 1_024, 1, 100_000_000);
        b.pop();
        b.push("charger");
        CHARGER_RATE = b.comment("FE/t the Charger pushes into the item.")
                .defineInRange("chargerRate", 400, 1, 100_000_000);
        b.pop();
        b.push("metal_press");
        PRESS_POWER = b.comment("Base FE/t while pressing (recipes take 100 ticks by default).")
                .defineInRange("pressPower", 20, 1, 1_000_000);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int crankFePerClick() { return get(CRANK_FE_PER_CLICK); }
    public static int crankClickGapTicks() { return Math.max(1, (int) Math.ceil(20.0 / get(CRANK_CLICKS_PER_SECOND))); }
    public static int crankAutoRate() { return get(CRANK_AUTO_RATE); }
    public static int generatorOutput() { return get(GENERATOR_OUTPUT); }
    public static int generatorBuffer() { return get(GENERATOR_BUFFER); }
    public static int solarMk1() { return get(SOLAR_MK1); }
    public static int solarMk2() { return get(SOLAR_MK2); }
    public static int copperConduitRate() { return get(COPPER_CONDUIT_RATE); }
    public static int goldConduitRate() { return get(GOLD_CONDUIT_RATE); }
    public static int chargerRate() { return get(CHARGER_RATE); }
    public static int pressPower() { return get(PRESS_POWER); }
}
