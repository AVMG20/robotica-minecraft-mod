package com.arno.robotica.core.upgrade;

import java.util.Locale;

/**
 * Upgrade card kinds. There is one card item per kind ({@code robotica:upgrade_<kind>}). Stackable kinds go into one
 * upgrade slot as a stack and every card in the stack adds one step; the machine decides how many it accepts
 * ({@link Upgrades#cap}). {@link #maxStack} is the most any machine can take, {@link #age} the age of the card recipe.
 */
public enum UpgradeKind {
    /** Faster work. Steps: x2, x3, x4, x6, x8, x11, x15, x20 (see {@link Upgrades#speedMultiplier}). */
    SPEED(8, 1),
    /** Bigger area. What a step means is up to the machine (robots +2 radius, Excavator 16/32/48/64). */
    RANGE(4, 2),
    /** -15% energy per action per card, never below 40%. */
    EFFICIENCY(4, 1),
    /** Fortune I-III for mined blocks, Looting I-III in the replicator. Excludes silk. */
    FORTUNE(3, 2),
    /** Mined blocks drop themselves. Single card, excludes fortune. */
    SILK(1, 2),
    /** +50% crop and sapling growth per card. */
    GROWTH(4, 1),
    /** Deletes junk (tag robotica:voidable). Single card. Also the void filter module of a tool (Tinker's Bench). */
    VOID(1, 1),
    /** Auto-pickup module of a tool (Tinker's Bench): drops go straight into the inventory. Single card, no machine takes it. */
    PICKUP(1, 1),
    /** Architect Table: one block taller buildings per card (6 cards: 12 high). */
    HEIGHT(6, 1),
    /** Utility blocks (Storage Terminal): keeps everything inside when picked up. Single card, installed by right-click. */
    CARRY(1, 1);

    /** Most cards of this kind any machine accepts in its slot. 1 = not stackable. */
    public final int maxStack;
    /** Age of the card recipe. */
    public final int age;

    UpgradeKind(int maxStack, int age) {
        this.maxStack = maxStack;
        this.age = age;
    }

    public boolean stackable() {
        return maxStack > 1;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Registry name of the card item, e.g. upgrade_speed. */
    public String itemName() {
        return "upgrade_" + id();
    }
}
