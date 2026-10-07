package com.arno.robotica.core.module;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Shared module balance (server config, file robotica-modules-server.toml): module slots per Age and Mk, and the
 * Power Regulator. Module specific numbers live in the gear and exo configs. Falls back to defaults before load.
 */
public final class ModuleConfig {
    private ModuleConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue[] GEAR_SLOTS = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] ARMOR_SLOTS = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.DoubleValue[] REGULATOR = new ModConfigSpec.DoubleValue[3];

    private static final int[] GEAR_SLOT_DEFAULTS = {2, 3, 4, 5};
    private static final int[] ARMOR_SLOT_DEFAULTS = {1, 2, 3, 4};
    private static final double[] REGULATOR_DEFAULTS = {0.15, 0.25, 0.35};

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("moduleSlots");
        for (int t = 1; t <= 4; t++) {
            GEAR_SLOTS[t - 1] = b.comment("Module slots of an Age " + t + " power tool or FE weapon.")
                    .defineInRange("toolSlotsAge" + t, GEAR_SLOT_DEFAULTS[t - 1], 0, Modules.MAX_SLOTS);
        }
        for (int t = 1; t <= 4; t++) {
            ARMOR_SLOTS[t - 1] = b.comment("Module slots of an Exo armor piece Mk" + t + ".")
                    .defineInRange("armorSlotsMk" + t, ARMOR_SLOT_DEFAULTS[t - 1], 0, Modules.MAX_ARMOR_SLOTS);
        }
        b.pop();
        b.push("powerRegulator");
        for (int lv = 1; lv <= 3; lv++) {
            REGULATOR[lv - 1] = b.comment("Power Regulator " + ModuleKind.roman(lv) + ": share of the FE it saves (tool per block, weapon per hit, every module of the Exo suit).")
                    .defineInRange("powerRegulatorSaving" + lv, REGULATOR_DEFAULTS[lv - 1], 0.0, 0.9);
        }
        b.pop();
        SPEC = b.build();
    }

    private static int tierIndex(int tier) {
        return Math.max(1, Math.min(4, tier)) - 1;
    }

    /** Module slots of a power tool or FE weapon of this Age (0 below Age 1). */
    public static int gearSlots(int age) {
        if (age < 1) return 0;
        return SPEC.isLoaded() ? GEAR_SLOTS[tierIndex(age)].get() : GEAR_SLOT_DEFAULTS[tierIndex(age)];
    }

    /** Module slots of an Exo piece of this Mk (0 below Mk1). */
    public static int armorSlots(int mk) {
        if (mk < 1) return 0;
        return SPEC.isLoaded() ? ARMOR_SLOTS[tierIndex(mk)].get() : ARMOR_SLOT_DEFAULTS[tierIndex(mk)];
    }

    /** Share of the FE a Power Regulator of this level saves, 0 for level 0. */
    public static double regulatorSaving(int level) {
        if (level <= 0) return 0.0;
        int i = Math.min(3, level) - 1;
        return SPEC.isLoaded() ? REGULATOR[i].get() : REGULATOR_DEFAULTS[i];
    }
}
