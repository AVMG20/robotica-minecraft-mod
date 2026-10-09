package com.arno.robotica.energy.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Casing of a cuboid multiblock. Its controller sets {@link #FRAME} while the structure stands, so the finished build
 * reads as one machine: beams along the edges (x, y, z by their axis), caps on the corners and plain panels in the
 * walls. {@code none} is the loose block.
 */
public class FramedPartBlock extends PartBlock {
    public enum Shape implements StringRepresentable {
        NONE, WALL, X, Y, Z, CORNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Shape> FRAME = EnumProperty.create("frame", Shape.class);

    public FramedPartBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FRAME, Shape.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FRAME);
    }
}
