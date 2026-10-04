package com.arno.robotica.warp.pad;

import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.item.RiftUpgradeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Warp Pad (Age 2): a slab-height block. Right-click while standing on it opens the destination list, sneak-right-click
 * with an empty hand opens the owner GUI (rename, public / private), a Rift Upgrade in hand installs the upgrade.
 */
public class WarpPadBlock extends Block implements EntityBlock {
    public static final BooleanProperty RIFT = BooleanProperty.create("rift");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public WarpPadBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(RIFT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIFT);
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

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof RiftUpgradeItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad)) {
            return ItemInteractionResult.FAIL;
        }
        if (state.getValue(RIFT)) {
            WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.rift_installed"));
            return ItemInteractionResult.CONSUME;
        }
        if (!pad.canEdit(player)) {
            WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.not_owner", pad.ownerName()));
            return ItemInteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(RIFT, true), Block.UPDATE_ALL);
        pad.ensureRegistered();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.2F);
        WarpTravel.message(serverPlayer, Component.translatable("message.robotica.warp.rift_done", pad.padName()));
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WarpPadBlockEntity pad && !level.isClientSide) {
                java.util.UUID id = pad.padId();
                if (id != null && level.getServer() != null) WarpPads.get(level.getServer()).remove(id);
                if (state.getValue(RIFT)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(WarpRegistry.RIFT_UPGRADE.get()));
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        level.addParticle(state.getValue(RIFT) ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.ELECTRIC_SPARK,
                x, pos.getY() + 0.55, z, 0.0, 0.04, 0.0);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 2, Component.translatable("age.robotica.2")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.warp_pad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.warp_pad_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
