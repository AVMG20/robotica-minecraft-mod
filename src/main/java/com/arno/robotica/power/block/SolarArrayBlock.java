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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
 * (the broken block drops the item). Rotating or mirroring the block (structures) renumbers the cells.
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
     * The clicked block becomes a corner of the panel: the panel grows forward and to the right of the player, or, when
     * that square is blocked, to the left, back right or back left. Null (no placement) when every square is blocked by
     * a block or an entity.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction forward = context.getHorizontalDirection();
        Direction right = forward.getClockWise();
        Player player = context.getPlayer();
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        int n = size();
        for (Direction depth : new Direction[]{forward, forward.getOpposite()}) {
            for (Direction side : new Direction[]{right, right.getOpposite()}) {
                BlockPos far = clicked.relative(depth, n - 1).relative(side, n - 1);
                BlockPos origin = new BlockPos(Math.min(clicked.getX(), far.getX()), clicked.getY(), Math.min(clicked.getZ(), far.getZ()));
                if (fits(level, origin, clicked, collision, player)) return cell(clicked.getX() - origin.getX(), clicked.getZ() - origin.getZ());
            }
        }
        if (player != null && !level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.robotica.solar_no_room", n), true);
        }
        return null;
    }

    /** Whether the panel can stand with its north-west corner at {@code origin} ({@code skip} is checked by the caller). */
    private boolean fits(Level level, BlockPos origin, BlockPos skip, CollisionContext collision, @Nullable Player player) {
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (p.equals(skip)) continue;
                if (!level.isLoaded(p) || !level.getWorldBorder().isWithinBounds(p) || !level.getBlockState(p).canBeReplaced()
                        || !level.isUnobstructed(cell(x, z), p, collision) || player != null && !level.mayInteract(player, p)) return false;
            }
        }
        return true;
    }

    /**
     * Grows a panel that stands alone (placed as one block before panels covered a square) into its square, when every
     * other block of the square is loaded air inside the world border with no entity in the way. Returns whether it grew.
     */
    public boolean grow(Level level, BlockPos origin) {
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                if ((x != 0 || z != 0) && (!level.isLoaded(p) || !level.getWorldBorder().isWithinBounds(p) || !level.isEmptyBlock(p)
                        || !level.isUnobstructed(cell(x, z), p, CollisionContext.empty()))) return false;
            }
        }
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                if (x != 0 || z != 0) level.setBlock(origin.offset(x, 0, z), cell(x, z), Block.UPDATE_ALL);
            }
        }
        return true;
    }

    /**
     * Removes every block of this panel type in the square at {@code origin}, whatever its cell, without drops: a panel
     * that lost a block or whose block entity ended up in another cell. Skips unloaded blocks.
     */
    public void removePanel(Level level, BlockPos origin) {
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (level.isLoaded(p) && level.getBlockState(p).is(this)) level.destroyBlock(p, false);
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        int n = size(), x = state.getValue(CELL_X), z = state.getValue(CELL_Z);
        if (x >= n || z >= n) return state;
        return switch (rotation) {
            case CLOCKWISE_90 -> cell(n - 1 - z, x);
            case CLOCKWISE_180 -> cell(n - 1 - x, n - 1 - z);
            case COUNTERCLOCKWISE_90 -> cell(z, n - 1 - x);
            case NONE -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        int n = size(), x = state.getValue(CELL_X), z = state.getValue(CELL_Z);
        if (x >= n || z >= n) return state;
        return switch (mirror) {
            case LEFT_RIGHT -> cell(x, n - 1 - z);
            case FRONT_BACK -> cell(n - 1 - x, z);
            case NONE -> state;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.invalidateCapabilities(pos);
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
        if (level.isClientSide) return;
        level.invalidateCapabilities(pos);
        if (state.is(newState.getBlock())) return;
        // one break event: loads a neighbouring chunk if the panel crosses into one, so no part of the panel stays behind
        BlockPos origin = origin(pos, state);
        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (!p.equals(pos) && isCell(level.getBlockState(p), x, z)) level.destroyBlock(p, false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return super.useWithoutItem(state, level, origin(pos, state), player, hit);
    }
}
