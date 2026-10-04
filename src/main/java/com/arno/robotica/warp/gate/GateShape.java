package com.arno.robotica.warp.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Geometry and validation of a Portal Gate: a frame 4 wide and 5 tall (axis = direction of the width) with a 2x3 opening
 * inside. The Gate Controller is one of the two middle blocks of the bottom row. Pure logic over a {@link Probe}, so it
 * can be tested without a world.
 */
public record GateShape(Direction.Axis axis, BlockPos corner, int controllerIndex) {
    public static final int WIDTH = 4;
    public static final int HEIGHT = 5;
    public static final int OPEN_WIDTH = 2;
    public static final int OPEN_HEIGHT = 3;

    /** What the validator needs to know about the world. */
    public interface Probe {
        /** A Gate Frame block. */
        boolean isFrame(BlockPos pos);

        /** Free for the portal: air or an existing Gate Portal block. */
        boolean isOpen(BlockPos pos);
    }

    /** Positive direction along the width. */
    public Direction along() {
        return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }

    /** The axis a traveller moves along to pass through the opening. */
    public Direction.Axis normalAxis() {
        return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** Cell (i along the width 0..3, j up 0..4) of the frame rectangle. */
    public BlockPos cell(int i, int j) {
        return corner.relative(along(), i).above(j);
    }

    public BlockPos controllerPos() {
        return cell(controllerIndex, 0);
    }

    /** Opening cell (i 0..1, j 0..2). */
    public BlockPos inner(int i, int j) {
        return cell(1 + i, 1 + j);
    }

    public List<BlockPos> innerPositions() {
        List<BlockPos> list = new ArrayList<>(OPEN_WIDTH * OPEN_HEIGHT);
        for (int j = 0; j < OPEN_HEIGHT; j++) {
            for (int i = 0; i < OPEN_WIDTH; i++) list.add(inner(i, j));
        }
        return list;
    }

    /** Frame positions without the controller. */
    public List<BlockPos> framePositions() {
        List<BlockPos> list = new ArrayList<>();
        for (int j = 0; j < HEIGHT; j++) {
            for (int i = 0; i < WIDTH; i++) {
                if (isPerimeter(i, j) && !(i == controllerIndex && j == 0)) list.add(cell(i, j));
            }
        }
        return list;
    }

    private static boolean isPerimeter(int i, int j) {
        return i == 0 || i == WIDTH - 1 || j == 0 || j == HEIGHT - 1;
    }

    /** True when the position is inside the 2x3 opening. */
    public boolean isInner(BlockPos pos) {
        return innerPositions().contains(pos);
    }

    /** Checks the frame, the opening and the controller position. */
    public boolean isValid(Probe probe) {
        for (BlockPos pos : framePositions()) {
            if (!probe.isFrame(pos)) return false;
        }
        for (BlockPos pos : innerPositions()) {
            if (!probe.isOpen(pos)) return false;
        }
        return true;
    }

    /**
     * Finds the frame around a controller. The controller is the second or third block of the bottom row, along the X or
     * the Z axis. {@code preferredAxis} (the axis of the width) is tried first, so the controller's facing decides when
     * two frames would fit.
     */
    public static Optional<GateShape> find(Probe probe, BlockPos controller, @Nullable Direction.Axis preferredAxis) {
        Direction.Axis first = preferredAxis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X;
        Direction.Axis second = first == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        for (Direction.Axis axis : new Direction.Axis[]{first, second}) {
            Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            for (int index = 1; index <= 2; index++) {
                GateShape shape = new GateShape(axis, controller.relative(along, -index), index);
                if (shape.isValid(probe)) return Optional.of(shape);
            }
        }
        return Optional.empty();
    }
}
