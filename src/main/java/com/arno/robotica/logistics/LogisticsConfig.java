package com.arno.robotica.logistics;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config of the logistics module ({@code robotica-logistics-server.toml}): item pipe rates per tier. */
public final class LogisticsConfig {
    private LogisticsConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue INTERVAL_1, ITEMS_1, INTERVAL_2, ITEMS_2, MAX_PIPES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Item pipes: every Extract connection pulls this many items every this many ticks.").push("itemPipes");
        INTERVAL_1 = b.comment("Item Pipe: ticks between two pulls.").defineInRange("pipeInterval1", 20, 1, 200);
        ITEMS_1 = b.comment("Item Pipe: items per pull.").defineInRange("pipeItems1", 8, 1, 64);
        INTERVAL_2 = b.comment("Item Pipe Mk2: ticks between two pulls.").defineInRange("pipeInterval2", 10, 1, 200);
        ITEMS_2 = b.comment("Item Pipe Mk2: items per pull.").defineInRange("pipeItems2", 32, 1, 64);
        MAX_PIPES = b.comment("Largest pipe network; pipes beyond it are not reached.").defineInRange("pipeNetworkMax", 4096, 16, 65536);
        b.pop();
        SPEC = b.build();
    }

    public static int interval(int tier) {
        if (!SPEC.isLoaded()) return tier >= 2 ? 10 : 20;
        return tier >= 2 ? INTERVAL_2.get() : INTERVAL_1.get();
    }

    public static int items(int tier) {
        if (!SPEC.isLoaded()) return tier >= 2 ? 32 : 8;
        return tier >= 2 ? ITEMS_2.get() : ITEMS_1.get();
    }

    public static int maxPipes() {
        return SPEC.isLoaded() ? MAX_PIPES.get() : 4096;
    }
}
