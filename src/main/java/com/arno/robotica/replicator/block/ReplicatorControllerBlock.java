package com.arno.robotica.replicator.block;

import com.arno.robotica.replicator.ReplicatorConfig;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.logic.Essence;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Centre of one face of the 3x3x3 Mob Replicator. FACING points outward (away from the cube).
 * FORMED shows that the structure check passed, LIT that it is working.
 */
public class ReplicatorControllerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ReplicatorControllerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Clicking the outside of a frame block: the controller faces away from it, so it ends up in the face centre.
        Direction clicked = context.getClickedFace();
        BlockState behind = context.getLevel().getBlockState(context.getClickedPos().relative(clicked.getOpposite()));
        boolean onShell = behind.is(ReplicatorRegistry.REPLICATOR_FRAME.get()) || behind.is(ReplicatorRegistry.REPLICATOR_GLASS.get());
        Direction facing = onShell ? clicked : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof ReplicatorControllerBlockEntity be) {
            be.setOwner(player.getUUID());
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ReplicatorRegistry.CONTROLLER_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ReplicatorRegistry.CONTROLLER_BE.get()) return null;
        return (lvl, pos, st, be) -> ((ReplicatorControllerBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReplicatorControllerBlockEntity be) {
            be.requestValidation();
        }
    }

    /** Right-click with a finished vial puts it into the empty vial slot. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (Essence.isUsable(stack) && level.getBlockEntity(pos) instanceof ReplicatorControllerBlockEntity be
                && be.vial.getStackInSlot(0).isEmpty()) {
            if (!level.isClientSide) {
                be.vial.setStackInSlot(0, stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Upgrade cards and the plasma boost go straight in too.
        if (level.getBlockEntity(pos) instanceof ReplicatorControllerBlockEntity be) {
            boolean put = false;
            if (stack.getItem() instanceof com.arno.robotica.core.upgrade.UpgradeCardItem) {
                put = be.upgrades.insertOne(stack, level.isClientSide);
            } else if (stack.is(com.arno.robotica.core.item.CoreItems.PLASMA_ACTUATOR.get()) && be.boost.getStackInSlot(0).isEmpty()) {
                if (!level.isClientSide) be.boost.setStackInSlot(0, stack.copyWithCount(1));
                put = true;
            }
            if (put) {
                if (!level.isClientSide) {
                    stack.consume(1, player);
                    com.arno.robotica.core.CoreSounds.play(level, pos, com.arno.robotica.core.CoreSounds.UPGRADE_INSTALL,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ReplicatorControllerBlockEntity be && player instanceof ServerPlayer serverPlayer) {
            be.releaseXp(level, serverPlayer.position());
            serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ReplicatorControllerBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.replicator_controller").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.replicator_controller_power",
                ReplicatorConfig.energyPerTick(), ReplicatorConfig.cycleTicks() / 20).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.replicator_controller_shape").withStyle(ChatFormatting.DARK_GRAY));
    }
}
