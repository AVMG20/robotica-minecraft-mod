package com.arno.robotica.gear.lamp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Spark Lamp: a small floating electric wisp with torch light (14), no collision, breaks instantly and drops nothing.
 * Only the Lamp Rod and the Lamp Placer module put it down. It needs no supporting block. {@link #FACING} only sets where
 * in the block the wisp floats (up = low, down = high, a side = against that wall). The look is an animated model; sparks, crackles and hum come from
 * {@link #ambientFx}, set by the client (gear.client.SparkLampFx) and limited to the player's surroundings.
 */
public class SparkLampBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final int LIGHT = 14;

    /** Distance of the wisp's core from the block centre, along FACING (the model floats it 5 px off its surface). */
    public static final double CORE_OFFSET = -3 / 16.0;

    /** Client-only ambient effects (no-op on a dedicated server); replaced by the client module at startup. */
    public static AmbientFx ambientFx = (state, level, pos, random) -> {};

    @FunctionalInterface
    public interface AmbientFx {
        void tick(BlockState state, Level level, BlockPos pos, RandomSource random);
    }

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, Block.box(5, 1, 5, 11, 9, 11));
        SHAPES.put(Direction.DOWN, Block.box(5, 7, 5, 11, 15, 11));
        SHAPES.put(Direction.NORTH, Block.box(5, 5, 7, 11, 11, 15));
        SHAPES.put(Direction.SOUTH, Block.box(5, 5, 1, 11, 11, 9));
        SHAPES.put(Direction.WEST, Block.box(7, 5, 5, 15, 11, 11));
        SHAPES.put(Direction.EAST, Block.box(1, 5, 5, 9, 11, 11));
    }

    public SparkLampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public BlockState facing(Direction facing) {
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Client only (called by the level renderer for blocks near the player). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ambientFx.tick(state, level, pos, random);
    }
}
