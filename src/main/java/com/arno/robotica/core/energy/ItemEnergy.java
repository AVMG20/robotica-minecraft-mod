package com.arno.robotica.core.energy;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Static helpers for items implementing {@link EnergyItem}. Safe on both sides. */
public final class ItemEnergy {
    private ItemEnergy() {}

    public static final int BAR_COLOR = 0x5CC8D8;

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof EnergyItem e ? e.getEnergyCapacity(stack) : 0;
    }

    public static int get(ItemStack stack) {
        return stack.getOrDefault(CoreComponents.ENERGY.get(), 0);
    }

    public static void set(ItemStack stack, int value) {
        stack.set(CoreComponents.ENERGY.get(), Mth.clamp(value, 0, capacity(stack)));
    }

    public static void fill(ItemStack stack) {
        set(stack, capacity(stack));
    }

    /** Adds energy ignoring the item's receive limit (for internal chargers like the Winding Crank). Returns amount added. */
    public static int addInternal(ItemStack stack, int amount) {
        int stored = get(stack);
        int added = Math.min(amount, capacity(stack) - stored);
        if (added > 0) set(stack, stored + added);
        return Math.max(0, added);
    }

    public static boolean has(ItemStack stack, int amount) {
        return get(stack) >= amount;
    }

    /** Consumes energy if enough is stored. Returns false (and consumes nothing) otherwise. Call on the server. */
    public static boolean tryUse(ItemStack stack, int amount) {
        int stored = get(stack);
        if (stored < amount) return false;
        set(stack, stored - amount);
        return true;
    }

    /** Takes up to amount, returns what was actually taken. */
    public static int drain(ItemStack stack, int amount) {
        int stored = get(stack);
        int taken = Math.min(stored, amount);
        if (taken > 0) set(stack, stored - taken);
        return taken;
    }

    public static int barWidth(ItemStack stack) {
        int cap = capacity(stack);
        return cap <= 0 ? 0 : Math.round(13.0F * get(stack) / cap);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.robotica.energy", Fmt.energy(get(stack)), Fmt.energy(capacity(stack)))
                .withStyle(ChatFormatting.AQUA));
    }
}
