package com.arno.robotica.core.module;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static com.arno.robotica.core.module.ModuleTarget.ALL;
import static com.arno.robotica.core.module.ModuleTarget.ARMOR;
import static com.arno.robotica.core.module.ModuleTarget.DRILLS;
import static com.arno.robotica.core.module.ModuleTarget.TOOLS;
import static com.arno.robotica.core.module.ModuleTarget.WEAPONS;

/**
 * Every module kind of power tools, FE weapons and Exo armor. One item per kind and level ({@link ModuleItems}); the
 * kind decides what it fits ({@link ModuleTarget} bits), the level the lowest Age or Mk of that item ({@link #minTier}).
 *
 * <p>Rules ({@link Modules#refusal}): a kind goes in once per item. On armor a kind also works once per worn suit
 * (the highest switched-on level counts), except {@link #perPiece} kinds. Kinds of the same {@link #group} are
 * alternatives: at most one of them is switched on (Fortune or Silk Touch).
 */
public enum ModuleKind {
    // power tools
    OVERCLOCK("overclock", TOOLS, 0, 1, 2, 3),
    FORTUNE("fortune", DRILLS, Group.DROPS, 1, 2, 3),
    SILK_TOUCH("silk_touch", DRILLS, Group.DROPS, 1),
    AUTO_PICKUP("auto_pickup", TOOLS, 0, 1),
    VOID_FILTER("void_filter", TOOLS, 0, 1),
    LAMP_PLACER("lamp_placer", DRILLS, 0, 1),
    // FE weapons
    SHARPENED_EDGE("sharpened_edge", WEAPONS, 0, 1, 2, 3),
    LOOTING("looting", WEAPONS, 0, 1, 2, 3),
    THERMAL_EDGE("thermal_edge", WEAPONS, 0, 1),
    ARMOR_PIERCE("armor_pierce", WEAPONS, 0, 1, 2, 3),
    CHAIN_LIGHTNING("chain_lightning", ModuleTarget.ARC_BLADE.bit(), 0, 3, 3, 3),
    RICOCHET("ricochet", ModuleTarget.RIVET_GUN.bit(), 0, 2, 2),
    LIFESTEAL("lifesteal", WEAPONS, 0, 4),
    // Exo helmet
    NIGHT_VISION("night_vision", Pieces.HEAD, 0, 1, 2, 3),
    REBREATHER("rebreather", Pieces.HEAD, 0, 2),
    ROBOT_HUD("robot_hud", Pieces.HEAD, 0, 2),
    AUTO_FEEDER("auto_feeder", Pieces.HEAD, 0, 1),
    SOLAR_WEAVE("solar_weave", Pieces.HEAD, 0, 2),
    SONAR_PULSE("sonar_pulse", Pieces.HEAD, 0, 2, 3, 4),
    // Exo chestplate
    JET_ASSIST("jet_assist", Pieces.CHEST, 0, 2, 3, 4),
    FLIGHT("flight", Pieces.CHEST, 0, 3),
    KINETIC_SHIELD("kinetic_shield", Pieces.CHEST, 0, 3, 4, 4),
    MED_INJECTOR("med_injector", Pieces.CHEST, 0, 2, 3, 4),
    HAZARD_SEAL("hazard_seal", Pieces.CHEST, 0, 3),
    // Exo leggings
    SERVO_STRIDE("servo_stride", Pieces.LEGS, 0, 1, 2, 3),
    KINETIC_GENERATOR("kinetic_generator", Pieces.LEGS, 0, 1),
    DASH_THRUSTERS("dash_thrusters", Pieces.LEGS | Pieces.FEET, 0, 3),
    // Exo boots
    STEP_ASSIST("step_assist", Pieces.FEET, 0, 1),
    SPRING_HEELS("spring_heels", Pieces.FEET, 0, 1, 2, 3),
    FALL_DAMPENER("fall_dampener", Pieces.FEET, 0, 1, 2, 3),
    MAGNET("magnet", Pieces.FEET, 0, 1, 2, 3),
    HYDRO_FINS("hydro_fins", Pieces.FEET, 0, 2),
    // any Exo piece (acts on its own piece, one per piece)
    CAPACITOR_PLATING("capacitor_plating", ARMOR, 0, 1, 2, 3),
    // tools, weapons and armor
    POWER_REGULATOR("power_regulator", ALL, 0, 2, 3, 4);

    /** Groups of alternative kinds: at most one of a group is switched on. */
    public static final class Group {
        private Group() {}

        public static final int DROPS = 1;
    }

    private static final class Pieces {
        static final int HEAD = ModuleTarget.HELMET.bit(), CHEST = ModuleTarget.CHESTPLATE.bit(), LEGS = ModuleTarget.LEGGINGS.bit(),
                FEET = ModuleTarget.BOOTS.bit();
    }

    public final String id;
    /** Bit set of the {@link ModuleTarget}s that accept this kind. */
    public final int fits;
    /** 0 = none, else a {@link Group} id. */
    public final int group;
    /** Lowest Age / Mk per level (index level - 1). Its length is the number of levels. */
    private final int[] minTiers;

    ModuleKind(String id, int fits, int group, int... minTiers) {
        this.id = id;
        this.fits = fits;
        this.group = group;
        this.minTiers = minTiers;
    }

    public int maxLevel() {
        return minTiers.length;
    }

    public boolean leveled() {
        return minTiers.length > 1;
    }

    /** Lowest Age or Mk (1-4) of the item that may hold this kind at {@code level}. */
    public int minTier(int level) {
        return minTiers[Math.max(0, Math.min(minTiers.length, level) - 1)];
    }

    public boolean fits(ModuleTarget target) {
        return target != null && (fits & target.bit()) != 0;
    }

    /** One module of this kind may sit in every Exo piece; it acts on its own piece only. */
    public boolean perPiece() {
        return this == CAPACITOR_PLATING;
    }

    /** True for kinds that fit Exo armor only. */
    public boolean armorOnly() {
        return (fits & ~ARMOR) == 0;
    }

    /** Registry name of the module item at a level (1 based). */
    public String itemName(int level) {
        if (this == SERVO_STRIDE) return "servo_stride_module_" + level;
        return level <= 1 ? id + "_module" : id + "_module_" + level;
    }

    public Component displayName() {
        return Component.translatable("module.robotica." + id);
    }

    /** Name with the level in Roman numerals for leveled kinds ("Armor Pierce II"). */
    public Component displayName(int level) {
        if (!leveled()) return displayName();
        return Component.translatable("module.robotica.leveled", displayName(), roman(level));
    }

    /** "Fits: power tools, FE weapons, any Exo piece" and friends. */
    public MutableComponent fitsLine() {
        MutableComponent list = null;
        for (int[] group : new int[][]{{TOOLS, 0}, {WEAPONS, 1}, {ARMOR, 2}}) {
            int mask = group[0];
            if ((fits & mask) == 0) continue;
            if ((fits & mask) == mask) {
                list = join(list, Component.translatable("module.robotica.targets." + group[1]));
                continue;
            }
            for (ModuleTarget t : ModuleTarget.values()) {
                if ((mask & fits & t.bit()) != 0) list = join(list, t.displayName());
            }
        }
        return Component.translatable("module.robotica.fits", list == null ? Component.empty() : list);
    }

    private static MutableComponent join(MutableComponent list, Component part) {
        return list == null ? part.copy() : list.append(", ").append(part);
    }

    /** "Needs Age 2", "Needs Mk2" or both, by what the kind fits. */
    public MutableComponent needsLine(int level) {
        int tier = minTier(level);
        boolean armor = (fits & ARMOR) != 0, gear = (fits & ~ARMOR) != 0;
        String key = armor && gear ? "module.robotica.needs.both" : armor ? "module.robotica.needs.mark" : "module.robotica.needs.age";
        return Component.translatable(key, tier);
    }

    public static ModuleKind byOrdinal(int i) {
        ModuleKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    public static String roman(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "I";
        };
    }
}
