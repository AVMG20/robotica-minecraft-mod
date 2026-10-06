package com.arno.robotica.gear.tool;

import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Boolean settings stored as bits of the {@code gear_toggles} component. Kept to the few that matter: the old light
 * placer, leaves and replant toggles are gone (tree tools now always replant from your saplings, the Chainsaw always
 * clears the leaves). Bits 16, 32 and 64 belonged to them and are ignored on old stacks.
 *
 * <p>Auto-pickup and the void filter are modules: they only work once their upgrade card is installed in the tool at a
 * Tinker's Bench (see {@link ToolSettings#installed}).
 */
public enum ToggleKind {
    KEEP_FLOOR("keep_floor", 1, null),
    AUTO_PICKUP("auto_pickup", 2, UpgradeKind.PICKUP),
    VOID_FILTER("void_filter", 4, UpgradeKind.VOID),
    AUTO_SMELT("auto_smelt", 8, null);

    /** Flags of a tool that never had a toggle changed: pick drops up (once the Auto-Pickup Upgrade is in). Keep floor starts off. */
    public static final int DEFAULT_FLAGS = AUTO_PICKUP.bit;

    public final String id;
    public final int bit;
    /** Card that has to be installed for this toggle to work, null for a plain setting. */
    @Nullable
    public final UpgradeKind module;

    ToggleKind(String id, int bit, @Nullable UpgradeKind module) {
        this.id = id;
        this.bit = bit;
        this.module = module;
    }

    public boolean isModule() {
        return module != null;
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

    /** The toggle a card installs, or null if the card is no tool module. */
    @Nullable
    public static ToggleKind forModule(UpgradeKind card) {
        for (ToggleKind kind : values()) {
            if (kind.module == card) return kind;
        }
        return null;
    }
}
