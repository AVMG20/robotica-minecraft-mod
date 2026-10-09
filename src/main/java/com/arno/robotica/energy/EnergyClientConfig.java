package com.arno.robotica.energy;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual toggles of the energy module (file robotica-energy-client.toml, registered by EnergyClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class EnergyClientConfig {
    private EnergyClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue STRUCTURE_EFFECTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("multiblocks");
        STRUCTURE_EFFECTS = b.comment("Light running along a multiblock's frame when it forms, sparks and smoke when it breaks.")
                .define("structureEffects", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean structureEffects() {
        return !SPEC.isLoaded() || STRUCTURE_EFFECTS.get();
    }
}
