package com.arno.robotica.core.module;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** What a module can go into: a kind of power tool, FE weapon or Exo armor piece. */
public enum ModuleTarget {
    DRILL, CHAINSAW, SHOCK_BATON, RIVET_GUN, ARC_BLADE, NULL_LANCE, HELMET, CHESTPLATE, LEGGINGS, BOOTS;

    public static final int DRILLS = DRILL.bit();
    public static final int TOOLS = DRILL.bit() | CHAINSAW.bit();
    public static final int WEAPONS = SHOCK_BATON.bit() | RIVET_GUN.bit() | ARC_BLADE.bit() | NULL_LANCE.bit();
    public static final int ARMOR = HELMET.bit() | CHESTPLATE.bit() | LEGGINGS.bit() | BOOTS.bit();
    public static final int ALL = TOOLS | WEAPONS | ARMOR;

    public int bit() {
        return 1 << ordinal();
    }

    public boolean isTool() {
        return (TOOLS & bit()) != 0;
    }

    public boolean isWeapon() {
        return (WEAPONS & bit()) != 0;
    }

    public boolean isArmor() {
        return (ARMOR & bit()) != 0;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("module.robotica.target." + id());
    }

    /** The Exo piece for an armor slot, null for any other slot. */
    @Nullable
    public static ModuleTarget armor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HELMET;
            case CHEST -> CHESTPLATE;
            case LEGS -> LEGGINGS;
            case FEET -> BOOTS;
            default -> null;
        };
    }
}
