package com.arno.robotica.codex;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only toggles of the codex module (file robotica-codex-client.toml, registered by CodexClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class CodexClientConfig {
    private CodexClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue PAGE_SOUNDS;
    private static final ModConfigSpec.BooleanValue UNLOCK_EFFECTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("codex");
        PAGE_SOUNDS = b.comment("Sounds when the Codex opens and its pages turn.").define("pageSounds", true);
        UNLOCK_EFFECTS = b.comment("A chime and a few sparkles when you finish a guide step.").define("unlockEffects", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean pageSounds() {
        return !SPEC.isLoaded() || PAGE_SOUNDS.get();
    }

    public static boolean unlockEffects() {
        return !SPEC.isLoaded() || UNLOCK_EFFECTS.get();
    }
}
