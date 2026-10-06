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

    private static final ModConfigSpec.IntValue REACTOR_MIN_SIZE;
    private static final ModConfigSpec.IntValue REACTOR_MAX_SIZE;
    private static final ModConfigSpec.IntValue REACTOR_BUFFER;
    private static final ModConfigSpec.DoubleValue REACTOR_ROD_EXPONENT;
    private static final ModConfigSpec.IntValue REACTOR_PASSIVE_COOLING;
    private static final ModConfigSpec.IntValue REACTOR_COOLANT_CAPACITY;
    private static final ModConfigSpec.DoubleValue REACTOR_COOLANT_EFFICIENCY;
    private static final ModConfigSpec.IntValue REACTOR_SAFE_TEMP;
    private static final ModConfigSpec.IntValue REACTOR_SCRAM_TEMP;
    private static final ModConfigSpec.IntValue REACTOR_RESET_TEMP;
    private static final ModConfigSpec.DoubleValue REACTOR_HEAT_RATE;
    private static final ModConfigSpec.DoubleValue REACTOR_MIN_THROTTLE;

    private static final ModConfigSpec.IntValue BANK_MIN_SIZE;
    private static final ModConfigSpec.IntValue BANK_MAX_SIZE;
    private static final ModConfigSpec.LongValue[] CAPACITOR = new ModConfigSpec.LongValue[4];
    private static final ModConfigSpec.IntValue[] COIL = new ModConfigSpec.IntValue[3];

    private static final ModConfigSpec.IntValue FUSION_IGNITION;
    private static final ModConfigSpec.IntValue FUSION_BUFFER;
    private static final ModConfigSpec.IntValue FUSION_WARMUP;
    private static final ModConfigSpec.IntValue FUSION_STARVE;
    private static final ModConfigSpec.IntValue FUSION_CHARGE_RATE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("multiblock");
        SCAN_COOLDOWN = b.comment("Ticks between two structure checks after a block in or next to a structure changed.")
                .defineInRange("multiblockScanCooldown", 10, 1, 200);
        RESCAN_INTERVAL = b.comment("A controller re-checks its structure at least this often (ticks), to catch changes made without block updates.")
                .defineInRange("multiblockRescanInterval", 200, 20, 12_000);
        b.pop();

        b.push("fission_reactor");
        REACTOR_MIN_SIZE = b.comment("Smallest Fission Reactor, outside size in blocks (every axis).")
                .defineInRange("reactorMinSize", 3, 3, 32);
        REACTOR_MAX_SIZE = b.comment("Largest Fission Reactor, outside size in blocks (every axis).")
                .defineInRange("reactorMaxSize", 7, 3, 32);
        REACTOR_BUFFER = b.comment("FE the reactor holds before its Power Ports send it on. Fuel only burns while there is room.")
                .defineInRange("reactorBuffer", 5_000_000, 10_000, Integer.MAX_VALUE);
        REACTOR_ROD_EXPONENT = b.comment("Every extra fuel rod adds a bit less: the reactor burns rods^exponent units at once (1 = linear).")
                .defineInRange("reactorRodExponent", 0.8, 0.1, 1.0);
        REACTOR_PASSIVE_COOLING = b.comment("Heat per tick one (effective) rod sheds without coolant.")
                .defineInRange("reactorPassiveCooling", 100, 0, 1_000_000);
        REACTOR_COOLANT_CAPACITY = b.comment("Heat per tick one point of coolant next to a rod carries away (water 1, ice 1.5, packed ice 2, blue ice 3, Cryo Coolant 4).")
                .defineInRange("reactorCoolantCapacity", 250, 1, 1_000_000);
        REACTOR_COOLANT_EFFICIENCY = b.comment("FE per heat rises by this much per point of coolant around the average rod (4 water: x1.2, 4 Cryo Coolant: x1.8).")
                .defineInRange("reactorCoolantEfficiency", 0.05, 0.0, 10.0);
        REACTOR_SAFE_TEMP = b.comment("Temperature (C) at full cooling load. Above it the output throttles down.")
                .defineInRange("reactorSafeTemp", 1000, 100, 100_000);
        REACTOR_SCRAM_TEMP = b.comment("Temperature (C) that SCRAMs the reactor: rods drop in, it shuts down. Never explodes.")
                .defineInRange("reactorScramTemp", 1800, 200, 100_000);
        REACTOR_RESET_TEMP = b.comment("A SCRAMed reactor can be restarted from the GUI below this temperature (C).")
                .defineInRange("reactorResetTemp", 200, 20, 100_000);
        REACTOR_HEAT_RATE = b.comment("Share of the gap to its target temperature the reactor closes per tick (0.02 = about 2.5 s to settle).")
                .defineInRange("reactorHeatRate", 0.02, 0.001, 1.0);
        REACTOR_MIN_THROTTLE = b.comment("Output share left just before the SCRAM point (linear from 100% at the safe temperature).")
                .defineInRange("reactorMinThrottle", 0.25, 0.0, 1.0);
        b.pop();

        b.push("capacitor_bank");
        BANK_MIN_SIZE = b.comment("Smallest Capacitor Bank, outside size in blocks (every axis).")
                .defineInRange("bankMinSize", 3, 3, 32);
        BANK_MAX_SIZE = b.comment("Largest Capacitor Bank, outside size in blocks (every axis).")
                .defineInRange("bankMaxSize", 9, 3, 32);
        long[] caps = {8_000_000L, 64_000_000L, 512_000_000L, 4_096_000_000L};
        int[] coils = {64_000, 512_000, 4_000_000};
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

        b.push("fusion_reactor");
        FUSION_IGNITION = b.comment("FE the Fusion Reactor needs in one charge to ignite (used up on ignition).")
                .defineInRange("fusionIgnitionEnergy", 20_000_000, 0, Integer.MAX_VALUE);
        FUSION_BUFFER = b.comment("FE the Fusion Reactor holds before its Power Ports send it on.")
                .defineInRange("fusionBuffer", 20_000_000, 10_000, Integer.MAX_VALUE);
        FUSION_WARMUP = b.comment("Ticks the plasma takes from ignition to full output.")
                .defineInRange("fusionWarmupTicks", 200, 1, 72_000);
        FUSION_STARVE = b.comment("Ticks the plasma survives without fuel before it collapses and needs a new ignition charge.")
                .defineInRange("fusionStarveTicks", 100, 1, 72_000);
        FUSION_CHARGE_RATE = b.comment("Most FE/t the reactor takes in through each Power Port while charging.")
                .defineInRange("fusionChargeRate", 1_000_000, 1, Integer.MAX_VALUE);
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

    public static int scanCooldown() { return get(SCAN_COOLDOWN); }
    public static int rescanInterval() { return get(RESCAN_INTERVAL); }

    public static int reactorMinSize() { return get(REACTOR_MIN_SIZE); }
    public static int reactorMaxSize() { return Math.max(reactorMinSize(), get(REACTOR_MAX_SIZE)); }
    public static int reactorBuffer() { return get(REACTOR_BUFFER); }
    public static double reactorRodExponent() { return get(REACTOR_ROD_EXPONENT); }
    public static int reactorPassiveCooling() { return get(REACTOR_PASSIVE_COOLING); }
    public static int reactorCoolantCapacity() { return get(REACTOR_COOLANT_CAPACITY); }
    public static double reactorCoolantEfficiency() { return get(REACTOR_COOLANT_EFFICIENCY); }
    public static int reactorSafeTemp() { return get(REACTOR_SAFE_TEMP); }
    public static int reactorScramTemp() { return Math.max(reactorSafeTemp() + 1, get(REACTOR_SCRAM_TEMP)); }
    public static int reactorResetTemp() { return get(REACTOR_RESET_TEMP); }
    public static double reactorHeatRate() { return get(REACTOR_HEAT_RATE); }
    public static double reactorMinThrottle() { return get(REACTOR_MIN_THROTTLE); }

    public static int bankMinSize() { return get(BANK_MIN_SIZE); }
    public static int bankMaxSize() { return Math.max(bankMinSize(), get(BANK_MAX_SIZE)); }
    /** tier 0 copper, 1 redstone, 2 ender */
    public static long capacitor(int tier) { return get(CAPACITOR[tier]); }
    /** tier 0 basic, 1 advanced, 2 elite */
    public static int transferCoil(int tier) { return get(COIL[tier]); }

    public static int fusionIgnitionEnergy() { return get(FUSION_IGNITION); }
    public static int fusionBuffer() { return get(FUSION_BUFFER); }
    public static int fusionWarmupTicks() { return get(FUSION_WARMUP); }
    public static int fusionStarveTicks() { return get(FUSION_STARVE); }
    public static int fusionChargeRate() { return get(FUSION_CHARGE_RATE); }
}
