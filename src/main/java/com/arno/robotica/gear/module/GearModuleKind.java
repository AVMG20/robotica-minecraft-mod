package com.arno.robotica.gear.module;

import net.minecraft.network.chat.Component;

/**
 * Modules for power tools and FE weapons, installed at the Tinker's Bench like the Exo-Frame's modules: one item per
 * kind and level, a kind fits certain tools or weapons and needs a minimum Age per level ({@link #minAge}). A kind works
 * once per item (the bench refuses a second one). Auto-Pickup and Void Filter stay upgrade cards with their own two
 * card slots on power tools (see {@link GearModules}); they do not use a module slot.
 */
public enum GearModuleKind {
    /** Drills: places a torch from your inventory where you mined when it is dark. */
    TORCH_PLACER("torch_placer", GearCategory.DRILL.bit(), 1),
    /** FE weapons: part of the hit ignores armor. */
    ARMOR_PIERCE("armor_pierce", Weapons.ALL, 1, 2, 3),
    /** Arc Blade: more arcs that jump further. */
    CHAIN_LIGHTNING("chain_lightning", GearCategory.ARC_BLADE.bit(), 3, 3, 3),
    /** Rivet Gun: rivets bounce to a second / third monster. */
    RICOCHET("ricochet", GearCategory.RIVET_GUN.bit(), 2, 2),
    /** Age 4 weapons: heals a small share of the damage dealt, paid in FE, hard capped per second, then a cooldown. */
    LIFESTEAL("lifesteal", Weapons.ALL, 4);

    private static final class Weapons {
        static final int ALL = GearCategory.SHOCK_BATON.bit() | GearCategory.RIVET_GUN.bit() | GearCategory.ARC_BLADE.bit()
                | GearCategory.NULL_LANCE.bit();
    }

    public final String id;
    /** Bit set of the {@link GearCategory} values that accept this kind. */
    public final int fits;
    /** Lowest tool Age per level (index level - 1). Its length is the number of levels. */
    private final int[] minAges;

    GearModuleKind(String id, int fits, int... minAges) {
        this.id = id;
        this.fits = fits;
        this.minAges = minAges;
    }

    public int maxLevel() {
        return minAges.length;
    }

    public boolean leveled() {
        return minAges.length > 1;
    }

    public int minAge(int level) {
        return minAges[Math.max(0, Math.min(minAges.length, level) - 1)];
    }

    public boolean fits(GearCategory category) {
        return category != GearCategory.NONE && (fits & category.bit()) != 0;
    }

    public boolean forWeapons() {
        return (fits & (GearCategory.DRILL.bit() | GearCategory.CHAINSAW.bit())) == 0;
    }

    /** Registry name of the module item at a level (1 based). */
    public String itemName(int level) {
        return level <= 1 ? id + "_module" : id + "_module_" + level;
    }

    public Component displayName() {
        return Component.translatable("gear.robotica.module." + id);
    }

    /** Name with the level in Roman numerals for leveled kinds ("Armor Pierce II"). */
    public Component displayName(int level) {
        if (!leveled()) return displayName();
        return Component.translatable("gear.robotica.module.leveled", displayName(), roman(level));
    }

    /** "Fits: drills" and friends. */
    public Component fitsLine() {
        return Component.translatable("gear.robotica.module.fits", Component.translatable("gear.robotica.module." + id + ".fits"));
    }

    public static GearModuleKind byOrdinal(int i) {
        GearModuleKind[] v = values();
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
