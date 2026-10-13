package com.arno.robotica.link.block;

import com.arno.robotica.core.CoreConfig;

/**
 * Quantum Link Mk1 (Age 2, the network's home dimension only), Mk2 (Age 3, any dimension) and Mk3 (Age 4).
 * {@code rate} is FE per tick each way, {@code stacks} item stacks (64 items) per second, upkeep FE per tick while the
 * Link is connected and switched on.
 */
public enum LinkTier {
    MK1(1, 64_000, 1, 20, true),
    MK2(2, 256_000, 4, 100, false),
    MK3(3, 1_000_000, 16, 400, false);

    public final int mk;
    public final int rate;
    public final int stacks;
    private final int upkeep;
    /** True when the Link only works in its network's home dimension. */
    public final boolean homeOnly;

    LinkTier(int mk, int rate, int stacks, int upkeep, boolean homeOnly) {
        this.mk = mk;
        this.rate = rate;
        this.stacks = stacks;
        this.upkeep = upkeep;
        this.homeOnly = homeOnly;
    }

    /** Items per second (a stack is 64). */
    public int itemsPerSecond() {
        return stacks * 64;
    }

    /** FE per tick while connected and on, scaled by the core energy multiplier. */
    public int upkeep() {
        return CoreConfig.scaleEnergy(upkeep);
    }

    /** Age of the recipe: Mk1 Age 2, Mk2 Age 3, Mk3 Age 4. */
    public int age() {
        return mk + 1;
    }
}
