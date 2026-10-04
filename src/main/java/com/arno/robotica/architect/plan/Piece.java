package com.arno.robotica.architect.plan;

import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * What a module wants at one position. Style pieces resolve to the block of the chosen style, fixed pieces are
 * vanilla states (furniture, crops), stairs follow the style's stair block and AIR clears the cell.
 */
public record Piece(Kind kind, Role role, Direction.Axis axis, Direction facing, BlockState fixed) {
    public enum Kind { STYLE, FIXED, STAIRS, AIR }

    public static final Piece AIR = new Piece(Kind.AIR, null, null, null, null);
    public static final Piece WALL = style(Role.WALL);
    public static final Piece FLOOR = style(Role.FLOOR);
    public static final Piece ROOF = style(Role.ROOF);
    public static final Piece WINDOW = style(Role.WINDOW);
    public static final Piece LIGHT = style(Role.LIGHT);
    public static final Piece PILLAR_Y = pillar(Direction.Axis.Y);

    public static Piece style(Role role) {
        return new Piece(Kind.STYLE, role, Direction.Axis.Y, null, null);
    }

    public static Piece pillar(Direction.Axis axis) {
        return new Piece(Kind.STYLE, Role.PILLAR, axis, null, null);
    }

    public static Piece fixed(BlockState state) {
        return new Piece(Kind.FIXED, null, null, null, state);
    }

    public static Piece stairs(Direction facing) {
        return new Piece(Kind.STAIRS, null, null, facing, null);
    }

    public boolean isAir() {
        return kind == Kind.AIR;
    }

    /** True for pieces that run on a tick later in the same layer (liquids settle after the floor around them exists). */
    public boolean isLiquid() {
        return kind == Kind.FIXED && fixed != null && !fixed.getFluidState().isEmpty();
    }

    public BlockState resolve(BuildStyle style) {
        return switch (kind) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case FIXED -> fixed;
            case STAIRS -> style.stairs().defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
            case STYLE -> {
                BlockState state = ArchitectRegistry.styleBlock(style, role).get().defaultBlockState();
                if (role == Role.PILLAR) state = state.setValue(RotatedPillarBlock.AXIS, axis);
                yield state;
            }
        };
    }
}
