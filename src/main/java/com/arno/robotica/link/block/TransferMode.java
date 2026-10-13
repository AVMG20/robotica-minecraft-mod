package com.arno.robotica.link.block;

import java.util.Locale;

/** What a Quantum Link does with energy or items: send into its network, receive from it, or nothing. */
public enum TransferMode {
    OFF, SEND, RECEIVE;

    private static final TransferMode[] VALUES = values();

    public TransferMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static TransferMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }

    public String translationKey() {
        return "gui.robotica.link.mode." + name().toLowerCase(Locale.ROOT);
    }
}
