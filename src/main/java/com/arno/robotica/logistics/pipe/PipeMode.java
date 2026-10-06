package com.arno.robotica.logistics.pipe;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/** Mode of a pipe-to-inventory connection, set per face by sneak-right-clicking the pipe arm. */
public enum PipeMode {
    /** Items from the network go in (the default). */
    INSERT(PipeConnection.INSERT, ChatFormatting.AQUA),
    /** The pipe pulls items out and sends them to the Insert connections of its network. */
    EXTRACT(PipeConnection.EXTRACT, ChatFormatting.GOLD),
    /** No connection: nothing goes in or out, no arm is drawn. */
    DISABLED(PipeConnection.NONE, ChatFormatting.GRAY);

    private static final PipeMode[] VALUES = values();

    /** What the face shows when an inventory is there. */
    public final PipeConnection connection;
    public final ChatFormatting color;

    PipeMode(PipeConnection connection, ChatFormatting color) {
        this.connection = connection;
        this.color = color;
    }

    public PipeMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static PipeMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : INSERT;
    }

    public String translationKey() {
        return "message.robotica.pipe_mode." + name().toLowerCase(Locale.ROOT);
    }
}
