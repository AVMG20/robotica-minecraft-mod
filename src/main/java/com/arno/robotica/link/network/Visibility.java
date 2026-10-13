package com.arno.robotica.link.network;

import java.util.Locale;

/** Who may join a network: only its owner, the owner and their team, or everyone. */
public enum Visibility {
    PRIVATE, TEAM, PUBLIC;

    private static final Visibility[] VALUES = values();

    public Visibility next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static Visibility byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PRIVATE;
    }

    public String translationKey() {
        return "gui.robotica.link.visibility." + name().toLowerCase(Locale.ROOT);
    }
}
