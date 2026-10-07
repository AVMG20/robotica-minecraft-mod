package com.arno.robotica.gear.tool;

import net.minecraft.network.chat.Component;

/**
 * Plain tool settings, stored as bits of the {@code gear_toggles} component (all off by default). Everything that has
 * to be installed (Auto-Pickup, Void Filter, Fortune...) is a module ({@link com.arno.robotica.core.module.ModuleKind}).
 */
public enum ToggleKind {
    KEEP_FLOOR("keep_floor", 1),
    AUTO_SMELT("auto_smelt", 2);

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
