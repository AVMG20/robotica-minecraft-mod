package com.arno.robotica.gear.tool;

import net.minecraft.network.chat.Component;

/** Boolean toggles stored as bits of the {@code gear_toggles} component. */
public enum ToggleKind {
    KEEP_FLOOR("keep_floor", 1),
    AUTO_PICKUP("auto_pickup", 2),
    VOID_FILTER("void_filter", 4),
    AUTO_SMELT("auto_smelt", 8),
    LIGHT_PLACER("light_placer", 16),
    LEAVES("leaves", 32),
    REPLANT("replant", 64);

    /** Flags of a tool that never had a toggle changed. */
    public static final int DEFAULT_FLAGS = KEEP_FLOOR.bit;

    public final String id;
    public final int bit;

    ToggleKind(String id, int bit) {
        this.id = id;
        this.bit = bit;
    }

    public Component displayName() {
        return Component.translatable("gear.robotica.toggle." + id);
    }

    public static ToggleKind byOrdinal(int i) {
        ToggleKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
