package com.arno.robotica.power;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual toggles of the power module (file robotica-power-client.toml, registered by PowerClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class PowerClientConfig {
    private PowerClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue TESLA_ARCS;
    private static final ModConfigSpec.BooleanValue TESLA_PARTICLES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("tesla");
        TESLA_ARCS = b.comment("Draw the glowing links between Tesla Coils and their targets.").define("teslaArcs", true);
        TESLA_PARTICLES = b.comment("Spark particles at Tesla Coils and their targets.").define("teslaParticles", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean teslaArcs() {
        return !SPEC.isLoaded() || TESLA_ARCS.get();
    }

    public static boolean teslaParticles() {
        return !SPEC.isLoaded() || TESLA_PARTICLES.get();
    }
}
