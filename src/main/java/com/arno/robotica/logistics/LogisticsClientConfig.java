package com.arno.robotica.logistics;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual toggles of the logistics module (file robotica-logistics-client.toml, registered by
 * LogisticsClient). Safe to read from common code: falls back to the defaults when the config is not loaded.
 */
public final class LogisticsClientConfig {
    private LogisticsClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue PIPE_GLOW;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("pipes");
        PIPE_GLOW = b.comment("Light up item pipes along the route when items move.").define("pipeGlow", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean pipeGlow() {
        return !SPEC.isLoaded() || PIPE_GLOW.get();
    }
}
