package com.arno.robotica.automation;

import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Work area of the walking robots (Stumpy, Sprout, Rancher): the same on every Mk, range cards make it bigger. The Mk
 * only sets speed, growth and card slots, so a small Mk4 or a big Mk1 are both possible.
 */
public final class RobotArea {
    private RobotArea() {}

    /** Range cards a robot takes on every Mk. */
    public static final int RANGE_CARDS = 4;
    /** Radius each range card adds (area side +6). */
    public static final int RADIUS_PER_CARD = 3;
    /** Card caps that do not depend on the Mk. */
    public static final Map<UpgradeKind, Integer> FIXED_CAPS = Map.of(UpgradeKind.RANGE, RANGE_CARDS);

    /** Range cards in a saved card inventory (item tooltips, no registry access needed). */
    public static int rangeCards(@Nullable CompoundTag upgrades) {
        if (upgrades == null) return 0;
        int n = 0;
        for (Tag t : upgrades.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) t;
            if ("robotica:upgrade_range".equals(item.getString("id"))) n += Math.max(1, item.getInt("count"));
        }
        return Math.min(n, RANGE_CARDS);
    }

    public static int radius(int rangeCards) {
        return AutomationConfig.farmRadius() + RADIUS_PER_CARD * Math.max(0, Math.min(RANGE_CARDS, rangeCards));
    }

    /** Side of the square area. */
    public static int size(int rangeCards) {
        return 2 * radius(rangeCards) + 1;
    }
}
