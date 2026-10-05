package com.arno.robotica.architect.plan;

import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** What a shell wants at one position: a style block (pillars with an axis) or air (role null). */
public record Piece(@Nullable Role role, Direction.Axis axis) {
    public static final Piece AIR = new Piece(null, Direction.Axis.Y);
    public static final Piece WALL = new Piece(Role.WALL, Direction.Axis.Y);
    public static final Piece FLOOR = new Piece(Role.FLOOR, Direction.Axis.Y);
    public static final Piece ROOF = new Piece(Role.ROOF, Direction.Axis.Y);
    public static final Piece WINDOW = new Piece(Role.WINDOW, Direction.Axis.Y);
    public static final Piece LIGHT = new Piece(Role.LIGHT, Direction.Axis.Y);
    public static final Piece POST = pillar(Direction.Axis.Y);

    public static Piece pillar(Direction.Axis axis) {
        return new Piece(Role.PILLAR, axis);
    }

    public boolean isAir() {
        return role == null;
    }

    /** Matter for one block of this piece: the style's price, nothing for air. */
    public Matter cost(BuildStyle style) {
        return isAir() ? Matter.ZERO : style.cost;
    }

    public BlockState resolve(BuildStyle style) {
        if (role == null) return Blocks.AIR.defaultBlockState();
        BlockState state = ArchitectRegistry.styleBlock(style, role).get().defaultBlockState();
        return role == Role.PILLAR ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
    }
}
