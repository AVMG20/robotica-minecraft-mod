package com.arno.robotica.architect.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Trim that joins its neighbours: a side connects to a pillar of the same style when either block's axis runs along
 * that side, so a post and the beam it carries turn a corner, while two beams stacked on each other stay apart. The
 * model draws each face from the four sides around it.
 */
public class ConnectedPillarBlock extends StylePillarBlock {
    public ConnectedPillarBlock(Properties props) {
        super(props);
        BlockState s = defaultBlockState();
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(new BooleanProperty[0]));
    }

    private boolean joins(BlockState self, Direction side, BlockState other) {
        if (!other.is(this)) return false;
        Direction.Axis axis = side.getAxis();
        return self.getValue(AXIS) == axis || other.getValue(AXIS) == axis;
    }

    /** The state with every side set from the blocks around it. */
    public BlockState connect(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), joins(state, side, level.getBlockState(pos.relative(side))));
        }
        return state;
    }

    /** {@code state} with its connections when it is connected trim, else unchanged: call before placing it. */
    public static BlockState placed(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof ConnectedPillarBlock block ? block.connect(level, pos, state) : state;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : connect(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), joins(state, side, neighbour));
    }

    /** Structures placed turned or mirrored: the axis turns (RotatedPillarBlock) and the joined sides turn with it. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = super.rotate(state, rotation);
        for (Direction side : Direction.values()) {
            out = out.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(side)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction side : Direction.values()) {
            out = out.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(side)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side)));
        }
        return out;
    }
}
