package com.arno.robotica.gear.tool;

import net.minecraft.network.chat.Component;

/**
 * Boolean settings stored as bits of the {@code gear_toggles} component. Kept to the few that matter: the old light
 * placer, leaves and replant toggles are gone (tree tools now always replant from your saplings, the Chainsaw always
 * clears the leaves). Bits 16, 32 and 64 belonged to them and are ignored on old stacks.
 */
public enum ToggleKind {
    KEEP_FLOOR("keep_floor", 1),
    AUTO_PICKUP("auto_pickup", 2),
    VOID_FILTER("void_filter", 4),
    AUTO_SMELT("auto_smelt", 8);

    /** Flags of a tool that never had a toggle changed: keep the floor and pick drops up. */
    public static final int DEFAULT_FLAGS = KEEP_FLOOR.bit | AUTO_PICKUP.bit;

    public final String id;
    public final int bit;

    ToggleKind(String id, int bit) {
        this.id = id;
        this.bit = bit;
    }

    public Component displayName() {
        return Component.translatable("gear.robotica.toggle." + id);
    }

    public Component description() {
        return Component.translatable("gear.robotica.toggle." + id + ".desc");
    }

    public static ToggleKind byOrdinal(int i) {
        ToggleKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
