package com.arno.robotica.storage;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance of the Storage Terminal (server config robotica-storage-server.toml).
 * Static getters fall back to the defaults while the config is not loaded (early init, game tests).
 */
public final class StorageConfig {
    private StorageConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue IDLE_FE;
    public static final ModConfigSpec.IntValue PER_EXPANSION_FE;
    public static final ModConfigSpec.IntValue ENERGY_BUFFER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Storage Terminal").push("storage");
        IDLE_FE = b.comment("FE per tick the terminal uses just by being on.")
                .defineInRange("idleFePerTick", 2, 0, 1_000);
        PER_EXPANSION_FE = b.comment("Extra FE per tick for every installed Storage Expansion.")
                .defineInRange("fePerExpansion", 1, 0, 1_000);
        ENERGY_BUFFER = b.comment("Internal FE buffer (a battery in the battery slot refills it).")
                .defineInRange("storageEnergyBuffer", 40_000, 1_000, 100_000_000);
        b.pop();
        SPEC = b.build();
    }

    public static int idleFe() {
        return SPEC.isLoaded() ? IDLE_FE.get() : 2;
    }

    public static int perExpansionFe() {
        return SPEC.isLoaded() ? PER_EXPANSION_FE.get() : 1;
    }

    public static int energyBuffer() {
        return SPEC.isLoaded() ? ENERGY_BUFFER.get() : 40_000;
    }
}
