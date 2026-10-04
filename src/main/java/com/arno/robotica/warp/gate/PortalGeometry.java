package com.arno.robotica.warp.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where the projected portal is, relative to its Portal Projector. Shared by the server (teleport detection, arrival)
 * and the client renderer, so what you see is what teleports you. Pure maths, no world access.
 *
 * <p>The projector is a 12 pixel tall emitter. The portal is a vertical ellipse, 2 wide and 3 tall, whose bottom edge
 * floats one block above the projector top. Its plane is perpendicular to the projector's facing.
 */
public final class PortalGeometry {
    private PortalGeometry() {}

    /** Height of the projector model (block units); the lens sits on top. */
    public static final double TOP = 0.75;
    /** Gap between the projector top and the bottom edge of the portal. */
    public static final double GAP = 1.0;
    public static final double WIDTH = 2.0;
    public static final double HEIGHT = 3.0;
    /** Centre height of the portal above the projector top (2.5). */
    public static final double CENTER_ABOVE_TOP = GAP + HEIGHT / 2.0;
    /** Half thickness of the trigger volume along the facing axis. */
    public static final double HALF_THICKNESS = 0.6;
    /**
     * Negative: the trigger volume starts 0.2 above the visible portal's bottom edge (1.95 above the floor), so a player
     * walking past on the ground (head at 1.8) is never pulled in. Step onto the projector or jump into the portal to travel.
     */
    public static final double REACH_BELOW = -0.2;
    /** Safe arrival spots start this many blocks in front of the projector (outside its own trigger volume). */
    public static final int ARRIVAL_DISTANCE = 2;
    public static final int ARRIVAL_TRIES = 3;

    public static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + TOP + CENTER_ABOVE_TOP, pos.getZ() + 0.5);
    }

    /** Half extent of the trigger volume along x: the portal plane is perpendicular to {@code facing}. */
    private static double halfX(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? WIDTH / 2.0 : HALF_THICKNESS;
    }

    private static double halfZ(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? HALF_THICKNESS : WIDTH / 2.0;
    }

    /** Volume that sends entities through: the portal's bounding box plus a little reach downwards. */
    public static AABB triggerBox(BlockPos pos, Direction facing) {
        Vec3 c = center(pos);
        return new AABB(c.x - halfX(facing), c.y - HEIGHT / 2.0 - REACH_BELOW, c.z - halfZ(facing),
                c.x + halfX(facing), c.y + HEIGHT / 2.0, c.z + halfZ(facing));
    }

    /** True when a feet position stands in the column of the trigger volume (inflated by the entity half width). */
    public static boolean inColumn(BlockPos pos, Direction facing, Vec3 feet) {
        Vec3 c = center(pos);
        return Math.abs(feet.x - c.x) < halfX(facing) + 0.4 && Math.abs(feet.z - c.z) < halfZ(facing) + 0.4;
    }
}
