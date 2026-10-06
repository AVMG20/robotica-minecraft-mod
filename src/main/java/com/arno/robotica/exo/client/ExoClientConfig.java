package com.arno.robotica.exo.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings of the Exo-Frame (file robotica-exo-client.toml): where the HUD sits and which effects draw. */
public final class ExoClientConfig {
    private ExoClientConfig() {}

    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HUD;
    private static final ModConfigSpec.EnumValue<Corner> CORNER;
    private static final ModConfigSpec.IntValue OFFSET_X, OFFSET_Y;
    private static final ModConfigSpec.BooleanValue OUTLINES, FLIGHT_SOUND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("exoHud");
        HUD = b.comment("Show the Exo-Frame HUD (suit energy, active modules, cooldowns, sonar results).").define("hudEnabled", true);
        CORNER = b.comment("Screen corner of the HUD.").defineEnum("hudCorner", Corner.TOP_LEFT);
        OFFSET_X = b.comment("Distance of the HUD from the left or right screen edge.").defineInRange("hudOffsetX", 6, 0, 2_000);
        OFFSET_Y = b.comment("Distance of the HUD from the top or bottom screen edge.").defineInRange("hudOffsetY", 6, 0, 2_000);
        b.pop();
        b.push("exoEffects");
        OUTLINES = b.comment("Draw the Sonar Pulse and thermal sight outlines through walls.").define("xrayOutlines", true);
        FLIGHT_SOUND = b.comment("Play the thruster loop while flying with the Flight module.").define("flightSound", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean hud() {
        return !SPEC.isLoaded() || HUD.get();
    }

    public static Corner corner() {
        return SPEC.isLoaded() ? CORNER.get() : Corner.TOP_LEFT;
    }

    public static int offsetX() {
        return SPEC.isLoaded() ? OFFSET_X.get() : 6;
    }

    public static int offsetY() {
        return SPEC.isLoaded() ? OFFSET_Y.get() : 6;
    }

    public static boolean outlines() {
        return !SPEC.isLoaded() || OUTLINES.get();
    }

    public static boolean flightSound() {
        return !SPEC.isLoaded() || FLIGHT_SOUND.get();
    }
}
