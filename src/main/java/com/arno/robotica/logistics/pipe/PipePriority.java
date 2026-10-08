package com.arno.robotica.logistics.pipe;

import java.util.Locale;

/** Priority of an Insert link, set per face in the pipe GUI: Extract links fill higher ones first. Ordinal 0 is highest. */
public enum PipePriority {
    HIGHEST, HIGH, NORMAL, LOW, LOWEST;

    private static final PipePriority[] VALUES = values();
    public static final PipePriority DEFAULT = HIGH;

    /** One step lower, wrapping from Lowest back to Highest. */
    public PipePriority next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** One step higher, wrapping from Highest to Lowest. */
    public PipePriority previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static PipePriority byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : DEFAULT;
    }

    public String translationKey() {
        return "gui.robotica.pipe.priority." + name().toLowerCase(Locale.ROOT);
    }
}
