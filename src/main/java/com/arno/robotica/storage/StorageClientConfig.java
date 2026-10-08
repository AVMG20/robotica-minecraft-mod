package com.arno.robotica.storage;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client preferences of the storage module (file robotica-storage-client.toml, registered by StorageClient).
 * Safe to read from common code: falls back to the defaults when the config is not loaded (dedicated server).
 */
public final class StorageClientConfig {
    private StorageClientConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue JEI_SYNC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("storageTerminal");
        JEI_SYNC = b.comment("Sync the Storage Terminal search box with JEI's search. Toggled with the button next to the search box.")
                .define("jeiSearchSync", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean jeiSync() {
        return !SPEC.isLoaded() || JEI_SYNC.get();
    }

    /** Stores the toggle and writes the client config file. */
    public static void setJeiSync(boolean on) {
        if (!SPEC.isLoaded()) return;
        JEI_SYNC.set(on);
        JEI_SYNC.save();
    }
}
