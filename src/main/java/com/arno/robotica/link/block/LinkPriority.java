package com.arno.robotica.link.block;

import java.util.Locale;

/** Order in which receiving Links get energy and items: higher first, equal priorities share. */
public enum LinkPriority {
    HIGHEST, HIGH, NORMAL, LOW, LOWEST;

    public static final int COUNT = 5;
    private static final LinkPriority[] VALUES = values();

    /** Cycles towards Lowest, then back to Highest. */
    public LinkPriority next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static LinkPriority byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NORMAL;
    }

    public String translationKey() {
        return "gui.robotica.link.priority." + name().toLowerCase(Locale.ROOT);
    }
}
