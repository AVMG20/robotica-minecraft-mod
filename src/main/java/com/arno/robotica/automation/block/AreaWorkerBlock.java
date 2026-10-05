package com.arno.robotica.automation.block;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Common block behaviour of the area workers: ticking, owner on placement, GUI, dropping contents. */
public abstract class AreaWorkerBlock extends BaseEntityBlock {
    protected AreaWorkerBlock(Properties props) {
        super(props);
    }

    protected abstract BlockEntityType<? extends AreaWorkerBlockEntity> beType();

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != beType()) return null;
        return (lvl, pos, st, be) -> ((AreaWorkerBlockEntity) be).serverTick((ServerLevel) lvl);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) {
            worker.setOwner(placer.getUUID());
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) worker.startPreview();
    }

    /**
     * Quick insert: right-click with a battery (cell or Mainspring) swaps it into the battery slot, with an upgrade card
     * puts it into a free upgrade slot. Anything else opens the GUI as usual.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (AreaWorkerBlockEntity.canUseAsBattery(stack)) {
            if (!level.isClientSide) {
                ItemStack old = worker.battery.getStackInSlot(0).copy();
                worker.battery.setStackInSlot(0, stack.copyWithCount(1));
                stack.consume(1, player);
                if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
                CoreSounds.play(level, pos, CoreSounds.SPRING_INSERT, SoundSource.BLOCKS, 0.8F, 1.1F);
                player.displayClientMessage(Component.translatable("message.robotica.battery_inserted", worker.battery.getStackInSlot(0).getHoverName()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof UpgradeCardItem) {
            for (int i = 0; i < worker.upgrades.getSlots(); i++) {
                if (worker.upgrades.getStackInSlot(i).isEmpty() && worker.upgrades.isItemValid(i, stack)) {
                    if (!level.isClientSide) {
                        worker.upgrades.setStackInSlot(i, stack.copyWithCount(1));
                        stack.consume(1, player);
                        CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
                        player.displayClientMessage(Component.translatable("message.robotica.card_inserted", worker.upgrades.getStackInSlot(i).getHoverName()), true);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.robotica.card_refused"), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                // Sneak + empty hand: a quick status check and the work area for a few seconds, without the GUI.
                worker.startPreview();
                sp.displayClientMessage(worker.statusLine(), true);
            } else {
                sp.openMenu(worker, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) {
                worker.dropContents();
                dropExtras(level, pos, state);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Extra items to give back when broken (spent Mk kits). */
    protected void dropExtras(Level level, BlockPos pos, BlockState state) {
    }
}
