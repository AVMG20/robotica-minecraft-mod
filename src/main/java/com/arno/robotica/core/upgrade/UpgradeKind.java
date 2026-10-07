package com.arno.robotica.core.upgrade;

import java.util.Locale;

/**
 * Upgrade card kinds: cards upgrade machines. One card item per kind ({@code robotica:upgrade_<kind>}); stackable kinds
 * stack in one slot and every card adds a step, up to the machine's cap ({@link UpgradeRules}). {@link #maxStack} is
 * the most any machine takes, {@link #age} the age of the card recipe.
 */
public enum UpgradeKind {
    /** Faster work, more FE per action: x2, x3, x4, x6, x8, x11, x15, x20 ({@link Upgrades#speedMultiplier}). */
    SPEED(8, 1),
    /** Bigger area or reach; one step is up to the machine (robots +2 radius, Excavator +10 wide). */
    RANGE(4, 2),
    /** -15% FE per action per card, never below 40% ({@link Upgrades#energyMultiplier}). */
    EFFICIENCY(4, 1),
    /** Fortune I-III on mined blocks, more output in ore machines, Looting in the replicator. Not with silk. */
    FORTUNE(3, 2),
    /** Mined blocks drop themselves. Single card, not with fortune. */
    SILK(1, 2),
    /** +50% crop and sapling growth per card (farm bots). */
    GROWTH(4, 1),
    /** Deletes junk (tag robotica:voidable) or outputs that do not fit. Single card. */
    VOID(1, 1),
    /** Architect Table: one block taller buildings per card (6 cards: 12 high). */
    HEIGHT(6, 1),
    /** Storage Terminal: keeps everything inside when picked up. Single card, installed by right-click. */
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
