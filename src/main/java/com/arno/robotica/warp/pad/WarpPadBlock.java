package com.arno.robotica.warp.pad;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.particle.CoreParticles;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Warp Pad Mk1 (Age 1) and Mk2 (Age 3, also other dimensions). Right-click while standing on it: destination list.
 * Sneak-right-click with an empty hand: owner GUI. A Mk2 pad in hand upgrades a placed Mk1 in place.
 */
public class WarpPadBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    private final boolean mk2;

    public WarpPadBlock(Properties props, boolean mk2) {
        super(props);
        this.mk2 = mk2;
    }

    public boolean isMk2() {
        return mk2;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WarpRegistry.WARP_PAD_BE.get().create(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad) {
            pad.initPlacement(placer instanceof Player player ? player : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }
        pad.ensureRegistered();
        if (player.isShiftKeyDown()) {
            if (pad.canEdit(player)) {
                WarpTravel.openPadSettings(serverPlayer, pad);
            } else {
                WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.pad_info", pad.padName(), pad.ownerName()));
            }
        } else {
            WarpTravel.openDestinations(serverPlayer, pad);
        }
        return InteractionResult.CONSUME;
    }

    /** A Warp Pad Mk2 in hand on a placed Mk1: upgrade in place. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (mk2 || !stack.is(WarpRegistry.WARP_PAD_MK2_ITEM.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad)) {
            return ItemInteractionResult.FAIL;
        }
        if (!pad.canEdit(player)) {
            WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.not_owner", pad.ownerName()));
            return ItemInteractionResult.CONSUME;
        }
        if (upgradeInPlace(level, pos)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack back = new ItemStack(WarpRegistry.WARP_PAD_ITEM.get());
                if (!player.getInventory().add(back)) player.drop(back, false);
            }
            WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.upgraded", pad.padName()));
        }
        return ItemInteractionResult.CONSUME;
    }

    /** Swaps the Mk1 pad at {@code pos} for a Mk2 and keeps its identity, owner, name, visibility and energy. */
    public static boolean upgradeInPlace(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof WarpPadBlockEntity old)) return false;
        CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        if (!level.setBlock(pos, WarpRegistry.WARP_PAD_MK2.get().defaultBlockState(), Block.UPDATE_ALL)) return false;
        if (level.getBlockEntity(pos) instanceof WarpPadBlockEntity fresh) {
            fresh.loadCustomOnly(saved, level.registryAccess());
            fresh.setChanged();
            fresh.ensureRegistered();
        }
        CoreSounds.play(level, pos, CoreSounds.WARP_RIFT, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getServer() != null
                && level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad) {
            java.util.UUID id = pad.padId();
            if (id != null) WarpPads.get(level.getServer()).remove(id);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Client only: now and then a faint mote rises from the pad surface. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(CoreParticles.mote(mk2 ? CoreParticles.WARP_VIOLET : CoreParticles.WARP_CYAN, 0.6F),
                x, pos.getY() + 0.55, z, 0.0, 0.015, 0.0);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int age = mk2 ? 3 : 1;
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(mk2 ? "tooltip.robotica.warp_pad_mk2" : "tooltip.robotica.warp_pad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(mk2 ? "tooltip.robotica.warp_pad_mk2_use" : "tooltip.robotica.warp_pad_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
