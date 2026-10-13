package com.arno.robotica.link;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of the link module (file robotica-link-server.toml). Rates and upkeep per Mk are constants in
 * {@link com.arno.robotica.link.block.LinkTier}; upkeep follows the core energy multiplier.
 */
public final class LinkConfig {
    private LinkConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_NETWORKS_PER_PLAYER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("quantum_link");
        MAX_NETWORKS_PER_PLAYER = b.comment("Networks one player may own at once.")
                .defineInRange("maxNetworksPerPlayer", 32, 1, 1_000);
        b.pop();
        SPEC = b.build();
    }

    public static int maxNetworksPerPlayer() {
        return SPEC.isLoaded() ? MAX_NETWORKS_PER_PLAYER.get() : MAX_NETWORKS_PER_PLAYER.getDefault();
    }
}
