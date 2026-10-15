package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Solar Panel Mk1-Mk4: one item that fills a {@link #SIZE}x{@link #SIZE} square around the block it is placed on. Every
 * block of the square is this block; {@link #CELL_X} / {@link #CELL_Z} give its place in the square, counted from the
 * north-west corner, which holds the {@link SolarPanelBlockEntity}. Any block opens the GUI and hands out the FE; breaking
 * one removes the whole panel (the broken block drops the item). Rotating or mirroring the block (structures) renumbers
 * the cells. Right-click a panel with the next Mk to upgrade it in place.
 * <p>
 * {@link #SINGLE}: a panel saved as one block by an older version (Mk1, or a 2x2 Mk2 cut down to its corner) that keeps
 * its full output on its own. Cells 3 on either axis only exist on a 4x4 Mk4 saved by 0.9.7 until it shrinks to 3x3.
 */
public class SolarArrayBlock extends SolarPanelBlock {
    /** Width of every panel, in blocks. */
    public static final int SIZE = 3;
    /** Highest cell number: 0.9.7 saved Mk4 panels of 4x4. */
    private static final int MAX_CELL = 3;
    public static final IntegerProperty CELL_X = IntegerProperty.create("cell_x", 0, MAX_CELL);
    public static final IntegerProperty CELL_Z = IntegerProperty.create("cell_z", 0, MAX_CELL);
    public static final BooleanProperty SINGLE = BooleanProperty.create("single");

    /** Set while a panel swaps or trims its own blocks: their removal does not take the rest of the panel with it. */
    private static boolean quiet;

    public SolarArrayBlock(Properties props, Tier tier) {
        super(props, tier);
        registerDefaultState(stateDefinition.any().setValue(CELL_X, 0).setValue(CELL_Z, 0).setValue(SINGLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL_X, CELL_Z, SINGLE);
    }

    /** The north-west corner of the panel that {@code state} at {@code pos} belongs to (the block with the block entity). */
    public static BlockPos origin(BlockPos pos, BlockState state) {
        if (state.getValue(SINGLE)) return pos;
        return pos.offset(-state.getValue(CELL_X), 0, -state.getValue(CELL_Z));
    }

    public BlockState cell(int x, int z) {
        return defaultBlockState().setValue(CELL_X, x).setValue(CELL_Z, z);
    }

    /** The one-block panel of an older version. */
    public BlockState single() {
        return defaultBlockState().setValue(SINGLE, true);
    }

    /** Whether {@code state} is the block at (x, z) of a 3x3 panel of this block. */
    public boolean isCell(BlockState state, int x, int z) {
        return state.is(this) && !state.getValue(SINGLE) && state.getValue(CELL_X) == x && state.getValue(CELL_Z) == z;
    }

    /** Places the whole panel with its north-west corner at {@code origin} (tests, showcase). */
    public void placeAll(Level level, BlockPos origin) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                level.setBlock(origin.offset(x, 0, z), cell(x, z), Block.UPDATE_ALL);
            }
        }
    }

    /** Sets a block of a panel without the removal of the old block taking the rest of its panel with it. */
    static void setQuietly(Level level, BlockPos pos, BlockState state) {
        quiet = true;
        try {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        } finally {
            quiet = false;
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SINGLE) || state.getValue(CELL_X) == 0 && state.getValue(CELL_Z) == 0 ? super.newBlockEntity(pos, state) : null;
    }

    /** The placed block is the middle of the panel. Null (no placement) when the square is blocked by a block or an entity. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Player player = context.getPlayer();
        BlockPos origin = clicked.offset(-1, 0, -1);
        if (fits(level, origin, null, player)) return cell(1, 1);
        noRoom(level, player);
        return null;
    }

    private static void noRoom(Level level, @Nullable Player player) {
        if (player != null && !level.isClientSide) player.displayClientMessage(Component.translatable("message.robotica.solar_no_room", SIZE), true);
    }

    /**
     * Whether a panel can stand with its north-west corner at {@code origin}: every block is free (or, with {@code old},
     * already part of that panel), loaded, inside the world border, clear of entities and, with a player, not protected.
     * The middle block of a fresh placement is checked by the caller.
     */
    private boolean fits(Level level, BlockPos origin, @Nullable BlockPos old, @Nullable Player player) {
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (old == null && x == 1 && z == 1) continue;
                if (!level.isLoaded(p) || !level.getWorldBorder().isWithinBounds(p) || player != null && !level.mayInteract(player, p)) return false;
                BlockState there = level.getBlockState(p);
                if (old != null && there.is(this) && origin(p, there).equals(old)) continue;
                if (!there.canBeReplaced() || !level.isUnobstructed(cell(x, z), p, collision)) return false;
            }
        }
        return true;
    }

    /**
     * Grows a panel that stands alone (a block of a panel moved on its own by another mod, or placed as one block) into
     * its square, when every other block of the square is loaded air inside the world border with no entity in the way.
     * Returns whether it grew.
     */
    public boolean grow(Level level, BlockPos origin) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                BlockPos p = origin.offset(x, 0, z);
                if ((x != 0 || z != 0) && (!level.isLoaded(p) || !level.getWorldBorder().isWithinBounds(p) || !level.isEmptyBlock(p)
                        || !level.isUnobstructed(cell(x, z), p, CollisionContext.empty()))) return false;
            }
        }
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (x != 0 || z != 0) level.setBlock(origin.offset(x, 0, z), cell(x, z), Block.UPDATE_ALL);
            }
        }
        return true;
    }

    /**
     * Removes every block of this panel type in the square at {@code origin}, whatever its cell, without drops: a panel
     * that lost a block or whose block entity ended up in another cell. Also the outer ring of a 0.9.7 Mk4 that has not
     * shrunk yet. Skips unloaded blocks and old single panels.
     */
    public void removePanel(Level level, BlockPos origin) {
        for (int x = 0; x <= MAX_CELL; x++) {
            for (int z = 0; z <= MAX_CELL; z++) {
                BlockPos p = origin.offset(x, 0, z);
                if (!level.isLoaded(p)) continue;
                BlockState state = level.getBlockState(p);
                boolean ring = x >= SIZE || z >= SIZE;
                if (ring ? isCell(state, x, z) : state.is(this) && !state.getValue(SINGLE)) level.destroyBlock(p, false);
            }
        }
    }

    /**
     * Swaps the panel at {@code origin} for the next Mk, keeping its energy, and gives the old panel back (the next Mk's
     * recipe consumed one). An old single panel becomes a full panel around itself, so its square must be free.
     */
    public boolean upgradeInPlace(Level level, BlockPos origin, SolarArrayBlock next, @Nullable Player player) {
        if (!(level.getBlockEntity(origin) instanceof SolarPanelBlockEntity old) || !old.getBlockState().is(this)) return false;
        BlockPos target = old.getBlockState().getValue(SINGLE) ? origin.offset(-1, 0, -1) : origin;
        if (!fits(level, target, origin, player)) {
            noRoom(level, player);
            return false;
        }
        if (player != null) {
            // the blocks the panel grows into are placed by the player: claim mods may refuse them
            java.util.List<net.neoforged.neoforge.common.util.BlockSnapshot> grown = new java.util.ArrayList<>();
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    BlockPos p = target.offset(x, 0, z);
                    BlockState there = level.getBlockState(p);
                    if (!there.is(this) || !origin(p, there).equals(origin)) grown.add(net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, p));
                }
            }
            if (!grown.isEmpty() && net.neoforged.neoforge.event.EventHooks.onMultiBlockPlace(player, grown, net.minecraft.core.Direction.UP)) return false;
        }
        int energy = old.energy.getEnergyStored();
        for (int x = SIZE - 1; x >= 0; x--) {
            for (int z = SIZE - 1; z >= 0; z--) setQuietly(level, target.offset(x, 0, z), next.cell(x, z));   // the corner with the block entity last
        }
        if (level.getBlockEntity(target) instanceof SolarPanelBlockEntity fresh) fresh.energy.setEnergy(Math.min(energy, fresh.energy.getMaxEnergyStored()));
        if (player != null) {
            ItemStack back = new ItemStack(asItem());
            if (!player.getInventory().add(back)) player.drop(back, false);
            player.displayClientMessage(Component.translatable("message.robotica.machine_upgraded", next.getName()), true);
        }
        CoreSounds.play(level, target.offset(1, 0, 1), CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof SolarArrayBlock next && next.tier().ordinal() == tier().ordinal() + 1) {
            // Swapping the blocks is building: adventure mode and protected spots may not.
            if (!player.mayBuild()) return ItemInteractionResult.FAIL;
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            // consumed even when it fails, so the item is not placed as a new panel on top instead
            if (upgradeInPlace(level, origin(pos, state), next, player) && !player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.CONSUME;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        int n = SIZE, x = state.getValue(CELL_X), z = state.getValue(CELL_Z);
        if (state.getValue(SINGLE) || x >= n || z >= n) return state;
        return switch (rotation) {
            case CLOCKWISE_90 -> cell(n - 1 - z, x);
            case CLOCKWISE_180 -> cell(n - 1 - x, n - 1 - z);
            case COUNTERCLOCKWISE_90 -> cell(z, n - 1 - x);
            case NONE -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        int n = SIZE, x = state.getValue(CELL_X), z = state.getValue(CELL_Z);
        if (state.getValue(SINGLE) || x >= n || z >= n) return state;
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
        if (level.isClientSide || state.getValue(SINGLE)) return;
        BlockPos origin = origin(pos, state);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
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
        if (quiet || state.is(newState.getBlock()) || state.getValue(SINGLE)) return;
        // one break event: loads a neighbouring chunk if the panel crosses into one, so no part of the panel stays behind;
        // up to cell 3 so a 0.9.7 Mk4 that has not shrunk yet goes whole
        BlockPos origin = origin(pos, state);
        for (int x = 0; x <= MAX_CELL; x++) {
            for (int z = 0; z <= MAX_CELL; z++) {
                BlockPos p = origin.offset(x, 0, z);
                if ((x >= SIZE || z >= SIZE) && !level.isLoaded(p)) continue;   // never load a chunk for an old Mk4's ring
                if (!p.equals(pos) && isCell(level.getBlockState(p), x, z)) level.destroyBlock(p, false);
            }
        }
    }

    /**
     * Brings a panel saved by an older version to the current layout without placing any block: a 4x4 Mk4 loses its
     * outer blocks ({@code oldSize} 4), a 2x2 Mk2 keeps only its corner as a single panel ({@code oldSize} 2; a lone block
     * that never formed stays as it is) and a one-block panel turns single ({@code oldSize} 1). Returns false while an old
     * block to remove is not loaded (try again later).
     */
    boolean migrate(Level level, BlockPos origin, int oldSize) {
        boolean square = oldSize != 2;
        for (int i = 1; i < 4 && !square; i++) {
            BlockPos p = origin.offset(i & 1, 0, i >> 1);
            square = !level.isLoaded(p) || isCell(level.getBlockState(p), i & 1, i >> 1);
        }
        if (!square) return true;
        if (oldSize <= 2 && !level.getBlockState(origin).getValue(SINGLE)) setQuietly(level, origin, single());
        boolean done = true;
        for (int x = 0; x < oldSize; x++) {
            for (int z = 0; z < oldSize; z++) {
                if (x == 0 && z == 0 || oldSize == 4 && x < SIZE && z < SIZE) continue;
                BlockPos p = origin.offset(x, 0, z);
                if (!level.isLoaded(p)) done = false;
                else if (isCell(level.getBlockState(p), x, z)) setQuietly(level, p, Blocks.AIR.defaultBlockState());
            }
        }
        return done;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return super.useWithoutItem(state, level, origin(pos, state), player, hit);
    }
}
