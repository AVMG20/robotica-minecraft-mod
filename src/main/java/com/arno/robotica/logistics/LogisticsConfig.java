package com.arno.robotica.logistics;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config of the logistics module ({@code robotica-logistics-server.toml}): item pipe rates per tier (Mk1 to Mk4). */
public final class LogisticsConfig {
    private LogisticsConfig() {}

    public static final int TIERS = 4;
    private static final int[] DEFAULT_INTERVAL = {20, 10, 5, 4};
    private static final int[] DEFAULT_ITEMS = {8, 32, 64, 128};

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue[] INTERVAL = new ModConfigSpec.IntValue[TIERS];
    private static final ModConfigSpec.IntValue[] ITEMS = new ModConfigSpec.IntValue[TIERS];
    private static final ModConfigSpec.IntValue MAX_PIPES;
    private static final ModConfigSpec.IntValue MAX_TRIES;
    private static final ModConfigSpec.IntValue MAX_VISITS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Item pipes: every Extract connection pulls this many items every this many ticks.").push("itemPipes");
        for (int i = 0; i < TIERS; i++) {
            int mk = i + 1;
            INTERVAL[i] = b.comment("Item Pipe Mk" + mk + ": ticks between two pulls.").defineInRange("pipeInterval" + mk, DEFAULT_INTERVAL[i], 1, 200);
            ITEMS[i] = b.comment("Item Pipe Mk" + mk + ": items per pull.").defineInRange("pipeItems" + mk, DEFAULT_ITEMS[i], 1, 256);
        }
        MAX_PIPES = b.comment("Largest pipe network; pipes beyond it are not reached.").defineInRange("pipeNetworkMax", 4096, 16, 65536);
        MAX_TRIES = b.comment("Insert checks one Extract connection makes per pull at most.")
                .defineInRange("pipeInsertChecks", 64, 1, 4096);
        MAX_VISITS = b.comment("Insert connections one Extract connection looks at per pull at most (filters included). Round robin resumes where it stopped.")
                .defineInRange("pipeTargetVisits", 256, 1, 65536);
        b.pop();
        SPEC = b.build();
    }

    private static int index(int tier) {
        return Math.max(0, Math.min(TIERS, tier) - 1);
    }

    public static int interval(int tier) {
        return SPEC.isLoaded() ? INTERVAL[index(tier)].get() : DEFAULT_INTERVAL[index(tier)];
    }

    public static int items(int tier) {
        return SPEC.isLoaded() ? ITEMS[index(tier)].get() : DEFAULT_ITEMS[index(tier)];
    }

    public static int maxPipes() {
        return SPEC.isLoaded() ? MAX_PIPES.get() : 4096;
    }

    public static int maxInsertTries() {
        return SPEC.isLoaded() ? MAX_TRIES.get() : 64;
    }

    public static int maxTargetVisits() {
        return SPEC.isLoaded() ? MAX_VISITS.get() : 256;
    }
}
