package com.arno.robotica.energy;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the energy module (server config, file robotica-energy-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class EnergyConfig {
    private EnergyConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue SCAN_COOLDOWN;
    private static final ModConfigSpec.IntValue RESCAN_INTERVAL;

    private static final ModConfigSpec.IntValue SPIRE_MIN_CONDUCTORS;
    private static final ModConfigSpec.IntValue SPIRE_MAX_CONDUCTORS;
    private static final ModConfigSpec.IntValue SPIRE_BUFFER;
    private static final ModConfigSpec.IntValue SPIRE_SPACING;
    private static final ModConfigSpec.DoubleValue SPIRE_ALTITUDE_MAX;
    private static final ModConfigSpec.IntValue SPIRE_STRIKE_SECONDS;
    private static final ModConfigSpec.IntValue[] SPIRE_STRIKE_EVERY = new ModConfigSpec.IntValue[3];

    private static final ModConfigSpec.IntValue CORE_REACTOR_BUFFER;

    private static final ModConfigSpec.IntValue BANK_MIN_SIZE;
    private static final ModConfigSpec.IntValue BANK_MAX_SIZE;
    private static final ModConfigSpec.LongValue[] CAPACITOR = new ModConfigSpec.LongValue[4];
    private static final ModConfigSpec.IntValue[] COIL = new ModConfigSpec.IntValue[3];

    private static final ModConfigSpec.IntValue COLLIDER_MIN_LENGTH;
    private static final ModConfigSpec.IntValue COLLIDER_MAX_LENGTH;
    private static final ModConfigSpec.IntValue COLLIDER_SEGMENT_POWER;
    private static final ModConfigSpec.IntValue COLLIDER_RESONANT_POWER;
    private static final ModConfigSpec.IntValue COLLIDER_SPINUP;
    private static final ModConfigSpec.IntValue COLLIDER_CHARGE_RATE;
    private static final ModConfigSpec.IntValue COLLIDER_BUFFER;
    private static final ModConfigSpec.IntValue COLLIDER_WARMUP;
    private static final ModConfigSpec.IntValue COLLIDER_STARVE;
    private static final ModConfigSpec.IntValue COLLIDER_MATTER_COST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("multiblock");
        SCAN_COOLDOWN = b.comment("Ticks between two structure checks after a block in or next to a structure changed.")
                .defineInRange("multiblockScanCooldown", 10, 1, 200);
        RESCAN_INTERVAL = b.comment("A controller re-checks its structure at least this often (ticks), to catch changes made without block updates.")
                .defineInRange("multiblockRescanInterval", 200, 20, 12_000);
        b.pop();

        b.push("tesla_spire");
        SPIRE_MIN_CONDUCTORS = b.comment("Fewest conductor blocks between the Spire Base and the Spire Crown.")
                .defineInRange("spireMinConductors", 8, 1, 128);
        SPIRE_MAX_CONDUCTORS = b.comment("Most conductor blocks between the Spire Base and the Spire Crown.")
                .defineInRange("spireMaxConductors", 24, 1, 128);
        SPIRE_BUFFER = b.comment("FE a Tesla Spire holds. Fuel only burns while there is room.")
                .defineInRange("spireBuffer", 4_000_000, 10_000, Integer.MAX_VALUE);
        SPIRE_SPACING = b.comment("Spires closer than this (blocks, between the bases) weaken each other: x(0.5 + 0.5 x distance / spacing) per neighbour. 0 turns it off.")
                .defineInRange("spireSpacing", 64, 0, 1024);
        SPIRE_ALTITUDE_MAX = b.comment("Most extra output for height: +0.25% per block the crown sits above Y 64, up to this share.")
                .defineInRange("spireAltitudeMaxBonus", 0.5, 0.0, 10.0);
        SPIRE_STRIKE_SECONDS = b.comment("A lightning strike on the crown adds this many seconds of the spire's full output, fuel or not.")
                .defineInRange("spireStrikeSeconds", 30, 0, 3600);
        String[] weather = {"Clear", "Rain", "Thunder"};
        int[] every = {360, 120, 30};
        for (int i = 0; i < 3; i++) {
            SPIRE_STRIKE_EVERY[i] = b.comment("Average seconds between strikes on a spire (" + weather[i].toLowerCase(java.util.Locale.ROOT) + "). 0: never.")
                    .defineInRange("spireStrikeEvery" + weather[i], every[i], 0, 1_000_000);
        }
        b.pop();

        b.push("core_reactor");
        CORE_REACTOR_BUFFER = b.comment("FE the Core Reactor holds before its Power Ports send it on. Fuel and the core only burn while there is room.")
                .defineInRange("coreReactorBuffer", 10_000_000, 10_000, Integer.MAX_VALUE);
        b.pop();

        b.push("capacitor_bank");
        BANK_MIN_SIZE = b.comment("Smallest Capacitor Bank, outside size in blocks (every axis).")
                .defineInRange("bankMinSize", 3, 3, 32);
        BANK_MAX_SIZE = b.comment("Largest Capacitor Bank, outside size in blocks (every axis).")
                .defineInRange("bankMaxSize", 9, 3, 32);
        long[] caps = {4_000_000L, 64_000_000L, 512_000_000L, 4_096_000_000L};
        int[] coils = {16_000, 512_000, 4_000_000};
        String[] capNames = {"Copper", "Redstone", "Ender", "Resonant"};
        String[] coilNames = {"Basic", "Advanced", "Elite"};
        for (int i = 0; i < CAPACITOR.length; i++) {
            CAPACITOR[i] = b.comment(capNames[i] + " Capacitor: FE stored per block.")
                    .defineInRange("capacitor" + capNames[i], caps[i], 1L, Long.MAX_VALUE / 4096);
        }
        for (int i = 0; i < 3; i++) {
            COIL[i] = b.comment(coilNames[i] + " Transfer Coil: FE/t it adds to the bank's input and output limit.")
                    .defineInRange("transferCoil" + coilNames[i], coils[i], 1, Integer.MAX_VALUE / 512);
        }
        b.pop();

        b.push("ring_collider");
        COLLIDER_MIN_LENGTH = b.comment("Shortest Ring Collider loop in blocks (the controller counts). 24 is a 7x7 square outline.")
                .defineInRange("colliderMinLength", 24, 8, 4096);
        COLLIDER_MAX_LENGTH = b.comment("Longest Ring Collider loop in blocks.")
                .defineInRange("colliderMaxLength", 256, 8, 4096);
        COLLIDER_SEGMENT_POWER = b.comment("FE/t one Accelerator Segment adds.")
                .defineInRange("colliderSegmentPower", 1_000, 0, 1_000_000);
        COLLIDER_RESONANT_POWER = b.comment("FE/t one Resonant Segment adds.")
                .defineInRange("colliderResonantPower", 2_500, 0, 1_000_000);
        COLLIDER_SPINUP = b.comment("FE per ring block the collider needs in one charge to start the beam (used up on start).")
                .defineInRange("colliderSpinupPerSegment", 500_000, 0, 100_000_000);
        COLLIDER_CHARGE_RATE = b.comment("Most FE/t the collider takes in while it charges.")
                .defineInRange("colliderChargeRate", 2_000_000, 1, Integer.MAX_VALUE);
        COLLIDER_BUFFER = b.comment("FE the Ring Collider holds.")
                .defineInRange("colliderBuffer", 50_000_000, 10_000, Integer.MAX_VALUE);
        COLLIDER_WARMUP = b.comment("Ticks the beam takes from start to full output.")
                .defineInRange("colliderWarmupTicks", 200, 1, 72_000);
        COLLIDER_STARVE = b.comment("Ticks the beam survives without fuel before it collapses and needs a new charge.")
                .defineInRange("colliderStarveTicks", 100, 1, 72_000);
        COLLIDER_MATTER_COST = b.comment("Luminosity (1 per Accelerator Segment, 2 per Resonant Segment, per tick) for one Strange Matter.")
                .defineInRange("colliderMatterCost", 768_000, 1, Integer.MAX_VALUE);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static long get(ModConfigSpec.LongValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int scanCooldown() { return get(SCAN_COOLDOWN); }
    public static int rescanInterval() { return get(RESCAN_INTERVAL); }

    public static int spireMinConductors() { return get(SPIRE_MIN_CONDUCTORS); }
    public static int spireMaxConductors() { return Math.max(spireMinConductors(), get(SPIRE_MAX_CONDUCTORS)); }
    public static int spireBuffer() { return get(SPIRE_BUFFER); }
    public static int spireSpacing() { return get(SPIRE_SPACING); }
    public static double spireAltitudeMaxBonus() { return get(SPIRE_ALTITUDE_MAX); }
    public static int spireStrikeSeconds() { return get(SPIRE_STRIKE_SECONDS); }
    /** weather 0 clear, 1 rain, 2 thunder */
    public static int spireStrikeEvery(int weather) { return get(SPIRE_STRIKE_EVERY[weather]); }

    public static int coreReactorBuffer() { return get(CORE_REACTOR_BUFFER); }

    public static int bankMinSize() { return get(BANK_MIN_SIZE); }
    public static int bankMaxSize() { return Math.max(bankMinSize(), get(BANK_MAX_SIZE)); }
    /** tier 0 copper, 1 redstone, 2 ender */
    public static long capacitor(int tier) { return get(CAPACITOR[tier]); }
    /** tier 0 basic, 1 advanced, 2 elite */
    public static int transferCoil(int tier) { return get(COIL[tier]); }

    public static int colliderMinLength() { return get(COLLIDER_MIN_LENGTH); }
    public static int colliderMaxLength() { return Math.max(colliderMinLength(), get(COLLIDER_MAX_LENGTH)); }
    public static int colliderSegmentPower() { return get(COLLIDER_SEGMENT_POWER); }
    public static int colliderResonantPower() { return get(COLLIDER_RESONANT_POWER); }
    public static int colliderSpinupPerSegment() { return get(COLLIDER_SPINUP); }
    public static int colliderChargeRate() { return get(COLLIDER_CHARGE_RATE); }
    public static int colliderBuffer() { return get(COLLIDER_BUFFER); }
    public static int colliderWarmupTicks() { return get(COLLIDER_WARMUP); }
    public static int colliderStarveTicks() { return get(COLLIDER_STARVE); }
    public static int colliderMatterCost() { return get(COLLIDER_MATTER_COST); }
}
