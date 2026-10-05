package com.arno.robotica.automation.block;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.automation.entity.FarmBotBlockEntity;
import com.arno.robotica.automation.item.FarmKitItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Stumpy and Sprout. Faces the player on placement. The Mk tier lives in the block state (the models show it as a
 * coloured band and bulb) and is raised in order with Farm Kits used on the placed bot.
 */
public abstract class FarmBotBlock extends AreaWorkerBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 4);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    protected FarmBotBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }

    @Nullable
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FarmKitItem kit)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        int tier = state.getValue(TIER);
        if (kit.tier() != tier + 1) {
            if (!level.isClientSide) {
                Component msg = kit.tier() <= tier
                        ? Component.translatable("message.robotica.kit_already", tier)
                        : Component.translatable("message.robotica.kit_order", tier + 1);
                player.displayClientMessage(msg, true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(TIER, kit.tier()), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof FarmBotBlockEntity bot) {
                bot.onTierChanged();
                bot.startPreview();
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.kit_applied", kit.tier()), true);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) com.arno.robotica.core.progress.Milestones.award(sp, com.arno.robotica.core.progress.Milestones.FARM_KIT);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void dropExtras(Level level, BlockPos pos, BlockState state) {
        // Give back the spent kits so breaking a Mk4 bot does not eat the investment.
        for (int tier = 2; tier <= state.getValue(TIER); tier++) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(AutomationContent.FARM_KITS.get(tier - 2).get()));
        }
    }
}
