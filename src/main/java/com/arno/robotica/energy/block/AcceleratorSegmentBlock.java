package com.arno.robotica.energy.block;

import com.arno.robotica.energy.EnergyRegistry;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One block of a Ring Collider's loop: a magnet yoke around a beam pipe. The pipe joins the segments (and the
 * Collider Controller) north, south, east and west of it, like a fence; the Collider Controller walks those joins.
 * Tier 1 is the Accelerator Segment, tier 2 the Resonant Segment (more power, more Strange Matter).
 */
public class AcceleratorSegmentBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    private final int tier;

    public AcceleratorSegmentBlock(Properties props, int tier) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    public int tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    public static BooleanProperty property(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            default -> WEST;
        };
    }

    /** A ring block: a segment or the Collider Controller. */
    public static boolean isRing(BlockState state) {
        return state.getBlock() instanceof AcceleratorSegmentBlock || state.is(EnergyRegistry.COLLIDER_CONTROLLER.get());
    }

    private BlockState connect(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.Plane.HORIZONTAL) state = state.setValue(property(dir), isRing(level.getBlockState(pos.relative(dir))));
        return state;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!dir.getAxis().isHorizontal()) return state;
        return state.setValue(property(dir), isRing(neighbor));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica." + BuiltInRegistries.BLOCK.getKey(this).getPath()).withStyle(ChatFormatting.GRAY));
    }
}
