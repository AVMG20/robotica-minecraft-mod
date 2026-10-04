package com.arno.robotica.power.conduit;

import com.arno.robotica.power.PowerConfig;

/** Conduit tiers. A network made of mixed tiers is capped at the lowest tier it contains. */
public enum ConduitTier {
    COPPER("copper_conduit"),
    GOLD("gold_conduit");

    public final String blockName;

    ConduitTier(String blockName) {
        this.blockName = blockName;
    }

    /** FE/t the whole network may move. */
    public int rate() {
        return switch (this) {
            case COPPER -> PowerConfig.copperConduitRate();
            case GOLD -> PowerConfig.goldConduitRate();
        };
    }
}
