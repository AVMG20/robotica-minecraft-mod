package com.arno.robotica.automation.block;

import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import com.arno.robotica.core.CoreSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Chest-like storage block, one block per Mk. Right-click a placed crate with the next Mk to upgrade it in place. */
public class SupplyCrateBlock extends BaseEntityBlock {
    public static final MapCodec<SupplyCrateBlock> CODEC = simpleCodec(p -> new SupplyCrateBlock(p, 1));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Slot grid per Mk (index = Mk - 1): 27, 54, 84 and 108 slots, never more than 6 rows so the GUI fits small screens. */
    private static final int[] COLUMNS = {9, 9, 14, 18};
    private static final int[] ROWS = {3, 6, 6, 6};

    private final int tier;

    public SupplyCrateBlock(Properties props, int tier) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public int tier() {
        return tier;
    }

    public int columns() {
        return COLUMNS[tier - 1];
    }

    public int rows() {
        return ROWS[tier - 1];
    }

    public int slots() {
        return columns() * rows();
    }

    @Override
    protected MapCodec<SupplyCrateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyCrateBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof SupplyCrateBlock next) || next.tier <= tier) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (next.tier > tier + 1) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.robotica.worker_order", tier + 1), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Swapping the block is building: adventure mode and protected spots may not.
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
        if (!level.isClientSide && upgradeInPlace(level, pos, state, next, player) && !player.getAbilities().instabuild) stack.shrink(1);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Swaps the placed crate for {@code next}, keeping its contents and facing, and gives the old crate back (the next
     * Mk's recipe consumed one, so the player keeps the same total). Server side.
     */
    public static boolean upgradeInPlace(Level level, BlockPos pos, BlockState state, SupplyCrateBlock next, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity old)) return false;
        // Empty the old crate first so its removal drops nothing; the stacks move into the new one.
        List<ItemStack> contents = new ArrayList<>(old.items.getSlots());
        for (int i = 0; i < old.items.getSlots(); i++) {
            contents.add(old.items.getStackInSlot(i));
            old.items.setStackInSlot(i, ItemStack.EMPTY);
        }
        if (!level.setBlock(pos, next.defaultBlockState().setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL)) {
            for (int i = 0; i < contents.size(); i++) old.items.setStackInSlot(i, contents.get(i));
            return false;
        }
        if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) {
            for (int i = 0; i < contents.size(); i++) crate.items.setStackInSlot(i, contents.get(i));
        } else {
            for (ItemStack stack : contents) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        if (player != null) {
            ItemStack back = new ItemStack(state.getBlock().asItem());
            if (!player.getInventory().add(back)) player.drop(back, false);
            player.displayClientMessage(Component.translatable("message.robotica.worker_upgraded", next.getName()), true);
        }
        CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.35, 0.3, 0.35, 0.05);
        }
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate && player instanceof ServerPlayer sp) {
            sp.openMenu(crate, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) {
            crate.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate)) return 0;
        float fill = 0;
        for (int i = 0; i < crate.items.getSlots(); i++) {
            var stack = crate.items.getStackInSlot(i);
            if (!stack.isEmpty()) fill += (float) stack.getCount() / stack.getMaxStackSize();
        }
        fill /= crate.items.getSlots();
        return Mth.lerpDiscrete(fill, 0, 15);
    }
}
