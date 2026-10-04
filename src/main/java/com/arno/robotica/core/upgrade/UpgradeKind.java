package com.arno.robotica.core.upgrade;

import java.util.Locale;

/** Upgrade card kinds. Level ranges are fixed here so every machine agrees. See docs/DESIGN.md. */
public enum UpgradeKind {
    SPEED(1, 4),
    RANGE(1, 4),
    EFFICIENCY(1, 4),
    FORTUNE(2, 4),
    SILK(1, 1),
    GROWTH(1, 4),
    VOID(1, 1);

    public final int minLevel;
    public final int maxLevel;

    UpgradeKind(int minLevel, int maxLevel) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Registry name of the card item, e.g. upgrade_speed_2. */
    public String itemName(int level) {
        return "upgrade_" + id() + "_" + level;
    }
}
