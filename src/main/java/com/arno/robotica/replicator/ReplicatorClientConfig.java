package com.arno.robotica.replicator;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual toggles of the replicator module (file robotica-replicator-client.toml, registered by
 * ReplicatorClient). Safe to read from common code: falls back to the defaults when the config is not loaded.
 */
public final class ReplicatorClientConfig {
    private ReplicatorClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SPAWN_EFFECTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("replicator");
        SPAWN_EFFECTS = b.comment("Shimmer of light where the Replicator spawns a mob.").define("spawnEffects", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean spawnEffects() {
        return !SPEC.isLoaded() || SPAWN_EFFECTS.get();
    }
}
