package com.arno.robotica.architect.style;

import java.util.Locale;

/** The six roles every building style provides. */
public enum Role {
    WALL, FLOOR, ROOF, PILLAR, WINDOW, LIGHT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
