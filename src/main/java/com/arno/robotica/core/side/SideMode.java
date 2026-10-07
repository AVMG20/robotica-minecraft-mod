package com.arno.robotica.core.side;

import java.util.Locale;

/** What pipes, hoppers and the machine's own auto-transfer may do through one face. */
public enum SideMode {
    /** Nothing connects: no item capability on this face. */
    NONE(false, false, 0xFF5A5A5A),
    /** Insert only (auto-input pulls from here). */
    INPUT(true, false, 0xFF3C78D8),
    /** Extract only (auto-eject pushes out here). */
    OUTPUT(false, true, 0xFFE08A2C),
    /** Both ways, with the machine's normal slot rules. */
    BOTH(true, true, 0xFF3DBE5A);

    private static final SideMode[] VALUES = values();

    public final boolean input;
    public final boolean output;
    /** GUI colour of the face square. */
    public final int color;

    SideMode(boolean input, boolean output, int color) {
        this.input = input;
        this.output = output;
        this.color = color;
    }

    public SideMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public SideMode previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static SideMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BOTH;
    }

    public String translationKey() {
        return "gui.robotica.side_mode." + name().toLowerCase(Locale.ROOT);
    }
}
