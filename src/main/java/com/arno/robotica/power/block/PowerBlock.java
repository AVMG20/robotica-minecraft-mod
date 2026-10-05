package com.arno.robotica.power.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Base of the ticking power blocks: creates its block entity, ticks it on the server only, opens its menu
 * on right click and drops its contents when broken. Blocks with a FACING property are placed facing the player.
 */
public abstract class PowerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Supplier<? extends BlockEntityType<? extends PowerBlockEntity>> beType;

    protected PowerBlock(Properties props, Supplier<? extends BlockEntityType<? extends PowerBlockEntity>> beType) {
        super(props);
        this.beType = beType;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != beType.get()) return null;
        return (lvl, pos, st, be) -> ((PowerBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        if (state.hasProperty(FACING)) {
            state = state.setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.hasProperty(FACING) ? state.setValue(FACING, rotation.rotate(state.getValue(FACING))) : state;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.hasProperty(FACING) ? state.rotate(mirror.getRotation(state.getValue(FACING))) : state;
    }

    /**
     * Quick insert: right-click with fuel, ingots, a chargeable item or an upgrade card puts it straight into the
     * machine. Anything that does not fit opens the GUI as before.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                                  Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof PowerBlockEntity be)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        net.neoforged.neoforge.items.IItemHandler target = stack.getItem() instanceof com.arno.robotica.core.upgrade.UpgradeCardItem
                ? be.quickUpgrades() : be.quickInsertTarget();
        if (target == null) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        net.minecraft.world.item.ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(target, stack.copy(), true);
        if (rest.getCount() >= stack.getCount()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            net.minecraft.world.item.ItemStack moved = stack.copy();
            rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(target, stack.copy(), false);
            moved.shrink(rest.getCount());
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, rest);
            com.arno.robotica.core.CoreSounds.play(level, pos, com.arno.robotica.core.CoreSounds.SPRING_INSERT, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.robotica.machine_inserted",
                    net.minecraft.network.chat.Component.literal(moved.getCount() + "x ").append(moved.getHoverName())), true);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider provider && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PowerBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
