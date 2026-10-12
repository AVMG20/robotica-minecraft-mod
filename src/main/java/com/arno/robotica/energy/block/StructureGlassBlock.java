package com.arno.robotica.energy.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.List;

/**
 * Window block of a multiblock wall. Connected glass: a side joins a neighbour of the same glass, so a pane of
 * several blocks draws one frame around its outer edge (the model picks each face's texture from the four sides
 * around it). FORMED (set by the controller while the structure stands) also joins the opaque blocks around it, so a
 * window runs straight into the formed frame.
 */
public class StructureGlassBlock extends TransparentBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public StructureGlassBlock(Properties props) {
        super(props);
        BlockState s = stateDefinition.any().setValue(FORMED, false);
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(new BooleanProperty[0]));
    }

    public static BooleanProperty side(Direction side) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(side);
    }

    private boolean joins(BlockState self, BlockState other, BlockGetter level, BlockPos otherPos) {
        return other.is(this) || (self.getValue(FORMED) && other.isSolidRender(level, otherPos));
    }

    /** The state with every side set from the blocks around it. */
    public BlockState connect(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            p.setWithOffset(pos, d);
            state = state.setValue(side(d), joins(state, level.getBlockState(p), level, p));
        }
        return state;
    }

    /** {@code state} formed or loose, with its sides joined to match. */
    public BlockState withFormed(BlockGetter level, BlockPos pos, BlockState state, boolean formed) {
        return connect(level, pos, state.setValue(FORMED, formed));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    /**
     * Placed by code without its sides (setBlock with the default state): join up right away. A full update, as the
     * placing setBlock skips its neighbour updates once the state changed under it.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || oldState.is(this)) return;
        BlockState joined = connect(level, pos, state);
        if (joined != state) level.setBlock(pos, joined, Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(side(side), joins(state, neighbour, level, neighbourPos));
    }

    /** Structures placed turned or mirrored: the joined sides turn with them. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.values()) out = out.setValue(side(rotation.rotate(d)), state.getValue(side(d)));
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.values()) out = out.setValue(side(mirror.mirror(d)), state.getValue(side(d)));
        return out;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica." + BuiltInRegistries.BLOCK.getKey(this).getPath()).withStyle(ChatFormatting.GRAY));
    }
}
