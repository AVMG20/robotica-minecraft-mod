package com.arno.robotica.core.side;

import net.minecraft.core.Direction;

import java.util.Locale;

/**
 * A machine face relative to its FACING (the front), as seen by a player standing in front of it: LEFT is the
 * player's left. Top and bottom never turn.
 */
public enum RelativeSide {
    FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM;

    private static final RelativeSide[] VALUES = values();

    /** The world direction of this face for a machine whose front points to {@code facing} (horizontal). */
    public Direction toWorld(Direction facing) {
        Direction front = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        return switch (this) {
            case FRONT -> front;
            case BACK -> front.getOpposite();
            case LEFT -> front.getClockWise();
            case RIGHT -> front.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    /** Which face a world direction is for a machine whose front points to {@code facing}. */
    public static RelativeSide of(Direction facing, Direction side) {
        for (RelativeSide rel : VALUES) {
            if (rel.toWorld(facing) == side) return rel;
        }
        return FRONT;
    }

    public static RelativeSide byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }

    public String translationKey() {
        return "gui.robotica.side." + name().toLowerCase(Locale.ROOT);
    }
}
