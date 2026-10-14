package com.arno.robotica.power.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Solar Panel Mk2-Mk4: one item that fills a {@code size x size} square when placed. Every block of the square is this
 * block; {@link #CELL_X} / {@link #CELL_Z} give its place in the square, counted from the north-west corner, which holds
 * the {@link SolarPanelBlockEntity}. Any block opens the GUI and hands out the FE; breaking one removes the whole panel
 * (the broken block drops the item).
 */
public class SolarArrayBlock extends SolarPanelBlock {
    public static final IntegerProperty CELL_X = IntegerProperty.create("cell_x", 0, 3);
    public static final IntegerProperty CELL_Z = IntegerProperty.create("cell_z", 0, 3);

    public SolarArrayBlock(Properties props, Tier tier) {
        super(props, tier);
        registerDefaultState(stateDefinition.any().setValue(CELL_X, 0).setValue(CELL_Z, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL_X, CELL_Z);
    }

    public int size() {
        return tier().size;
    }

    /** The north-west corner of the panel that {@code state} at {@code pos} belongs to (the block with the block entity). */
    public static BlockPos origin(BlockPos pos, BlockState state) {
        return pos.offset(-state.getValue(CELL_X), 0, -state.getValue(CELL_Z));
    }

    public BlockState cell(int x, int z) {
        return defaultBlockState().setValue(CELL_X, x).setValue(CELL_Z, z);
    }

    /** Whether {@code state} is the block at (x, z) of a panel of this block. */
    public boolean isCell(BlockState state, int x, int z) {
        return state.is(this) && state.getValue(CELL_X) == x && state.getValue(CELL_Z) == z;
    }

    /** Places the whole panel with its north-west corner at {@code origin} (tests, showcase). */
    public void placeAll(Level level, BlockPos origin) {
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                level.setBlock(origin.offset(x, 0, z), cell(x, z), Block.UPDATE_ALL);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CELL_X) == 0 && state.getValue(CELL_Z) == 0 ? super.newBlockEntity(pos, state) : null;
    }

    /**
     * The clicked block becomes the corner nearest the player on their left; the panel grows forward and to the right.
     * Null (no placement) when a block or an entity is in the way.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction forward = context.getHorizontalDirection();
        int n = size();
        BlockPos far = clicked.relative(forward, n - 1).relative(forward.getClockWise(), n - 1);
        BlockPos origin = new BlockPos(Math.min(clicked.getX(), far.getX()), clicked.getY(), Math.min(clicked.getZ(), far.getZ()));
        Player player = context.getPlayer();
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (p.equals(clicked)) continue;
                if (!level.getWorldBorder().isWithinBounds(p) || !level.getBlockState(p).canBeReplaced()
                        || !level.isUnobstructed(cell(x, z), p, collision)) {
                    if (player != null && !level.isClientSide) {
                        player.displayClientMessage(Component.translatable("message.robotica.solar_no_room", n), true);
                    }
                    return null;
                }
            }
        }
        return cell(clicked.getX() - origin.getX(), clicked.getZ() - origin.getZ());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BlockPos origin = origin(pos, state);
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (!p.equals(pos)) level.setBlock(p, cell(x, z), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level.isClientSide || state.is(newState.getBlock())) return;
        BlockPos origin = origin(pos, state);
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (!p.equals(pos) && level.isLoaded(p) && isCell(level.getBlockState(p), x, z)) level.destroyBlock(p, false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return super.useWithoutItem(state, level, origin(pos, state), player, hit);
    }
}
