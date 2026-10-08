package com.arno.robotica.core.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Remembers per item whether a machine's recipes take it, for the slot checks hoppers, pipes and auto-input run on
 * every insert. Starts over when the level's recipe manager changes or {@link #bump} runs (tags or recipes reloaded).
 * Stacks with extra components are always checked directly. Client and server keep separate entries. The manager is
 * held weakly, so a closed world (server stop, client logout) is not kept alive.
 */
public final class RecipeAcceptCache {
    private static final AtomicInteger GENERATION = new AtomicInteger();

    /** Called on every tag or recipe reload, both sides. */
    public static void bump() {
        GENERATION.incrementAndGet();
    }

    public static int generation() {
        return GENERATION.get();
    }

    private final Side server = new Side();
    private final Side client = new Side();

    public boolean test(Level level, ItemStack stack, Predicate<ItemStack> compute) {
        if (stack.isEmpty()) return false;
        if (!stack.isComponentsPatchEmpty()) return compute.test(stack);
        return (level.isClientSide ? client : server).test(level.getRecipeManager(), stack, compute);
    }

    private static final class Side {
        private WeakReference<RecipeManager> manager = new WeakReference<>(null);
        private int generation = -1;
        private final Map<Item, Boolean> known = new IdentityHashMap<>();

        synchronized boolean test(RecipeManager rm, ItemStack stack, Predicate<ItemStack> compute) {
            int gen = GENERATION.get();
            if (rm != manager.get() || gen != generation) {
                manager = new WeakReference<>(rm);
                generation = gen;
                known.clear();
            }
            Boolean hit = known.get(stack.getItem());
            if (hit == null) {
                hit = compute.test(stack);
                known.put(stack.getItem(), hit);
            }
            return hit;
        }
    }
}
