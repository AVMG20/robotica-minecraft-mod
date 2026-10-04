package com.arno.robotica.gear.tool;

import com.arno.robotica.gear.GearComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Reads and writes the gear data components on a tool stack. */
public final class ToolSettings {
    private ToolSettings() {}

    public static final int ENCHANT_NONE = 0;
    public static final int ENCHANT_FORTUNE = 1;
    public static final int ENCHANT_SILK = 2;

    public static int flags(ItemStack stack) {
        return stack.getOrDefault(GearComponents.TOGGLES.get(), ToggleKind.DEFAULT_FLAGS);
    }

    public static boolean has(ItemStack stack, ToggleKind kind) {
        return (flags(stack) & kind.bit) != 0;
    }

    public static void set(ItemStack stack, ToggleKind kind, boolean on) {
        int f = flags(stack);
        stack.set(GearComponents.TOGGLES.get(), on ? f | kind.bit : f & ~kind.bit);
    }

    public static int enchantMode(ItemStack stack) {
        return stack.getOrDefault(GearComponents.ENCHANT_MODE.get(), ENCHANT_NONE);
    }

    public static void setEnchantMode(ItemStack stack, int mode) {
        stack.set(GearComponents.ENCHANT_MODE.get(), mode);
    }

    /** Next state of the silk/fortune swap: none, fortune, silk, none... */
    public static int nextEnchantMode(int current) {
        return (current + 1) % 3;
    }

    /**
     * Makes the real enchantments on the stack match the swap state, so vanilla loot (and every other mod) just sees
     * Silk Touch or Fortune on the tool. Only the levels the swap injected itself (remembered in a data component) are
     * ever removed: Silk Touch or Fortune the player applied through an anvil or enchanting table stays, and the swap
     * only tops the level up to what it wants. Only touches the stack when something changed.
     */
    public static void syncEnchantments(ItemStack stack, GearToolItem tool, HolderLookup.Provider registries) {
        if (tool.spec.fortuneLevel <= 0) return;
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> silk = lookup.getOrThrow(Enchantments.SILK_TOUCH);
        Holder<Enchantment> fortune = lookup.getOrThrow(Enchantments.FORTUNE);
        int mode = enchantMode(stack);
        int wantSilk = mode == ENCHANT_SILK ? 1 : 0;
        int wantFortune = mode == ENCHANT_FORTUNE ? tool.spec.fortuneLevel : 0;
        int injected = stack.getOrDefault(GearComponents.INJECTED_ENCHANTS.get(), 0);
        int curSilk = stack.getEnchantmentLevel(silk);
        int curFortune = stack.getEnchantmentLevel(fortune);
        int baseSilk = Math.max(0, curSilk - (injected & 1));
        int baseFortune = Math.max(0, curFortune - (injected >> 1));
        int targetSilk = Math.max(baseSilk, wantSilk);
        int targetFortune = Math.max(baseFortune, wantFortune);
        int newInjected = (targetSilk > baseSilk ? 1 : 0) | ((targetFortune - baseFortune) << 1);
        if (targetSilk != curSilk || targetFortune != curFortune) {
            EnchantmentHelper.updateEnchantments(stack, m -> {
                if (targetSilk != curSilk) m.set(silk, targetSilk);
                if (targetFortune != curFortune) m.set(fortune, targetFortune);
            });
        }
        if (newInjected != injected) {
            if (newInjected == 0) stack.remove(GearComponents.INJECTED_ENCHANTS.get());
            else stack.set(GearComponents.INJECTED_ENCHANTS.get(), newInjected);
        }
    }
}
