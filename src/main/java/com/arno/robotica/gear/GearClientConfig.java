package com.arno.robotica.gear;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only effect toggles of the gear module (file robotica-gear-client.toml, registered by GearClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class GearClientConfig {
    private GearClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue LAMP_ANIMATED;
    private static final ModConfigSpec.BooleanValue LAMP_PARTICLES;
    private static final ModConfigSpec.BooleanValue LAMP_SOUNDS;
    private static final ModConfigSpec.IntValue LAMP_FX_RANGE;
    private static final ModConfigSpec.BooleanValue OUTLINE_ANIMATED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("sparkLamp");
        LAMP_ANIMATED = b.comment("Animated Spark Lamp wisps: floating, flickering, crackling filaments. Off: a still glow.")
                .define("sparkLampAnimated", true);
        LAMP_PARTICLES = b.comment("Arcs and sparks at Spark Lamps.").define("sparkLampParticles", true);
        LAMP_SOUNDS = b.comment("Quiet crackle and hum at Spark Lamps.").define("sparkLampSounds", true);
        LAMP_FX_RANGE = b.comment("Spark Lamp arcs, sparks and sounds only play within this many blocks of the camera.")
                .defineInRange("sparkLampFxRange", 20, 4, 32);
        b.pop();
        b.push("tools");
        OUTLINE_ANIMATED = b.comment("Tool area outline fades in and shimmers. Off: a steady glow.").define("areaOutlineAnimated", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean lampAnimated() {
        return !SPEC.isLoaded() || LAMP_ANIMATED.get();
    }

    public static boolean lampParticles() {
        return !SPEC.isLoaded() || LAMP_PARTICLES.get();
    }

    public static boolean lampSounds() {
        return !SPEC.isLoaded() || LAMP_SOUNDS.get();
    }

    public static int lampFxRange() {
        return SPEC.isLoaded() ? LAMP_FX_RANGE.get() : 20;
    }

    public static boolean outlineAnimated() {
        return !SPEC.isLoaded() || OUTLINE_ANIMATED.get();
    }
}
