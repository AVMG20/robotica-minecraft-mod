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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/** Common block behaviour of the area workers: ticking, owner on placement, GUI, dropping contents. */
public abstract class AreaWorkerBlock extends BaseEntityBlock {
    protected AreaWorkerBlock(Properties props) {
        super(props);
    }

    protected abstract BlockEntityType<? extends AreaWorkerBlockEntity> beType();

    /** Mk of this block, for workers that come as one block per Mk (Excavator, Survey Rig); 0 for the others. */
    public int mkTier() {
        return 0;
    }

    /** True when {@code stack} is this worker's next Mk, which upgrades it in place on right-click. */
    public boolean isNextMk(ItemStack stack) {
        return mkTier() > 0 && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AreaWorkerBlock next
                && next.getClass() == getClass() && next.mkTier() == mkTier() + 1;
    }

    /**
     * Swaps the placed worker for the next Mk, keeping battery, cards, buffer, energy, owner and progress, and gives the
     * old block back (the next Mk's recipe consumed one, so the player keeps the same total). Server side.
     */
    public static boolean upgradeInPlace(Level level, BlockPos pos, BlockState state, AreaWorkerBlock next, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity old)) return false;
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        old.keepContents = true;
        BlockState fresh = next.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (fresh.hasProperty(property)) fresh = copy(state, fresh, property);
        }
        level.setBlock(pos, fresh, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker) {
            worker.loadCustomOnly(saved, level.registryAccess());
            worker.afterUpgrade();
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

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

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
        if (isNextMk(stack)) {
            // Swapping the block is building: adventure mode and protected spots may not.
            if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
            if (!level.isClientSide && !worker.canUse(player)) {
                player.displayClientMessage(Component.translatable("message.robotica.worker_locked"), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!level.isClientSide && upgradeInPlace(level, pos, state, (AreaWorkerBlock) ((BlockItem) stack.getItem()).getBlock(), player)
                    && !player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (mkTier() > 0 && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AreaWorkerBlock other
                && other.getClass() == getClass() && other.mkTier() > mkTier() + 1) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.robotica.worker_order", mkTier() + 1), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
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
        if (stack.getItem() instanceof UpgradeCardItem card) {
            if (worker.upgrades.insertOne(stack, level.isClientSide)) {
                if (!level.isClientSide) {
                    stack.consume(1, player);
                    CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.robotica.card_inserted", card.getDescription(),
                            worker.upgrades.level(card.getKind()), worker.upgrades.cap(card.getKind())), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                int cap = worker.upgrades.cap(card.getKind());
                player.displayClientMessage(cap <= 0 ? Component.translatable("message.robotica.card_refused")
                        : Component.translatable("message.robotica.card_full", card.getDescription(), cap), true);
            }
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
            if (level.getBlockEntity(pos) instanceof AreaWorkerBlockEntity worker && !worker.keepContents) {
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
