package com.arno.robotica.replicator.farm;

/**
 * Timeline of one farm cycle of {@code needed} ticks, shared by the server (strike sounds) and the renderer: the mob
 * forms, stands while the three rams strike it at one third, two thirds and the end of the wait, then falls and drops
 * its loot as the cycle ends. Short cycles (speed cards) squeeze every phase.
 */
public final class FarmCycle {
    private FarmCycle() {}

    /** Ticks the mob takes to form at full length. */
    public static final int FORM = 20;
    /** Ticks from the last strike to the end of the cycle (the fall and the drop) at full length. */
    public static final int FALL = 25;

    public static int formTicks(int needed) {
        return Math.min(FORM, needed / 4);
    }

    public static int fallTicks(int needed) {
        return Math.min(FALL, needed * 5 / 16);
    }

    /** Tick of strike {@code i} (0, 1 or 2). */
    public static int strike(int i, int needed) {
        int form = formTicks(needed);
        int last = needed - fallTicks(needed);
        return form + (last - form) * (i + 1) / 3;
    }

    /** Which strike lands on this tick, or -1. */
    public static int strikeAt(int tick, int needed) {
        for (int i = 0; i < FarmStructure.RAMS; i++) {
            if (strike(i, needed) == tick) return i;
        }
        return -1;
    }
}
