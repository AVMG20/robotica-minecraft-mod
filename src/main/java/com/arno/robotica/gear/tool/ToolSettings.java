package com.arno.robotica.gear.tool;

import com.arno.robotica.gear.GearComponents;
import net.minecraft.world.item.ItemStack;

/** Reads and writes the plain tool settings ({@link ToggleKind}) of a tool stack. */
public final class ToolSettings {
    private ToolSettings() {}

    public static int flags(ItemStack stack) {
        return stack.getOrDefault(GearComponents.TOGGLES.get(), 0);
    }

    public static boolean has(ItemStack stack, ToggleKind kind) {
        return (flags(stack) & kind.bit) != 0;
    }

    public static void set(ItemStack stack, ToggleKind kind, boolean on) {
        int f = on ? flags(stack) | kind.bit : flags(stack) & ~kind.bit;
        if (f == 0) stack.remove(GearComponents.TOGGLES.get());
        else stack.set(GearComponents.TOGGLES.get(), f);
    }
}
