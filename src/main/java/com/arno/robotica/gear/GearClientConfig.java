package com.arno.robotica.gear;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only effect toggles of the gear module (file robotica-gear-client.toml, registered by GearClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class GearClientConfig {
    private GearClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue LAMP_PARTICLES;
    private static final ModConfigSpec.BooleanValue LAMP_SOUNDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("sparkLamp");
        LAMP_PARTICLES = b.comment("Small spark particles at Spark Lamps.").define("sparkLampParticles", true);
        LAMP_SOUNDS = b.comment("A rare soft buzz at Spark Lamps.").define("sparkLampSounds", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean lampParticles() {
        return !SPEC.isLoaded() || LAMP_PARTICLES.get();
    }

    public static boolean lampSounds() {
        return !SPEC.isLoaded() || LAMP_SOUNDS.get();
    }
}
