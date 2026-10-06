package com.arno.robotica.logistics.pipe;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a pipe face shows (block state): nothing, a link to the next pipe, or an inventory it inserts into or pulls from. */
public enum PipeConnection implements StringRepresentable {
    NONE, PIPE, INSERT, EXTRACT;

    /** True when an arm is drawn and the face is part of the shape. */
    public boolean hasArm() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
