package com.arno.robotica.gear.module;

import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.weapon.ArcBladeItem;
import com.arno.robotica.gear.weapon.EnergyWeaponItem;
import com.arno.robotica.gear.weapon.NullLanceItem;
import com.arno.robotica.gear.weapon.RivetGunItem;
import com.arno.robotica.gear.weapon.ShockBatonItem;
import net.minecraft.world.item.ItemStack;

/** What kind of FE tool or weapon a stack is, for the module fit rules. NONE takes no modules (Age 0 tools, other items). */
public enum GearCategory {
    NONE, DRILL, CHAINSAW, SHOCK_BATON, RIVET_GUN, ARC_BLADE, NULL_LANCE;

    public static GearCategory of(ItemStack stack) {
        if (stack.getItem() instanceof GearToolItem tool) {
            if (!tool.spec.isEnergy()) return NONE;
            return tool.spec.isAxe() ? CHAINSAW : DRILL;
        }
        if (stack.getItem() instanceof ShockBatonItem) return SHOCK_BATON;
        if (stack.getItem() instanceof RivetGunItem) return RIVET_GUN;
        if (stack.getItem() instanceof ArcBladeItem) return ARC_BLADE;
        if (stack.getItem() instanceof NullLanceItem) return NULL_LANCE;
        return NONE;
    }

    public boolean isTool() {
        return this == DRILL || this == CHAINSAW;
    }

    public boolean isWeapon() {
        return this == SHOCK_BATON || this == RIVET_GUN || this == ARC_BLADE || this == NULL_LANCE;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Age of a module-capable stack (1-4), 0 for anything else. */
    public static int age(ItemStack stack) {
        if (stack.getItem() instanceof GearToolItem tool) return tool.spec.isEnergy() ? tool.spec.age : 0;
        if (stack.getItem() instanceof EnergyWeaponItem weapon) return weapon.age();
        return 0;
    }
}
