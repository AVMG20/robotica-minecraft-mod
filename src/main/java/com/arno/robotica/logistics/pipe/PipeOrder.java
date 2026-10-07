package com.arno.robotica.logistics.pipe;

import java.util.Locale;

/** Where an Extract link sends items first, set per face in the pipe GUI. */
public enum PipeOrder {
    /** Each pull starts at the Insert link after the one used last, so items spread evenly. */
    ROUND_ROBIN,
    /** Each pull fills the nearest Insert link (fewest pipes away) that takes the item. */
    CLOSEST_FIRST;

    private static final PipeOrder[] VALUES = values();

    public PipeOrder next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static PipeOrder byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ROUND_ROBIN;
    }

    public String translationKey() {
        return "gui.robotica.pipe.order." + name().toLowerCase(Locale.ROOT);
    }
}
