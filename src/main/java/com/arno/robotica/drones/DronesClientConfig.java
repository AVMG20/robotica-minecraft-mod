package com.arno.robotica.drones;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual toggles of the drones module (file robotica-drones-client.toml, registered by DronesClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class DronesClientConfig {
    private DronesClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue BOLT_TRAILS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("sentry");
        BOLT_TRAILS = b.comment("Trail of light along a Sentry Drone's bolt (the sparks where it hits always show).").define("boltTrails", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean boltTrails() {
        return !SPEC.isLoaded() || BOLT_TRAILS.get();
    }
}
