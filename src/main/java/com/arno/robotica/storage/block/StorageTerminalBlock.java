package com.arno.robotica.storage.block;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.storage.StorageContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
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

/** Storage Terminal: a big sorted chest with search and a crafting grid. LIT = powered (the screen glows). */
public class StorageTerminalBlock extends BaseEntityBlock {
    public static final MapCodec<StorageTerminalBlock> CODEC = simpleCodec(StorageTerminalBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public StorageTerminalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<StorageTerminalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return new StorageTerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, StorageContent.TERMINAL_BE.get(), StorageTerminalBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal && player instanceof ServerPlayer sp) {
            sp.openMenu(terminal, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /** Right-click with a Carry card installs it: from then on the terminal keeps everything when picked up. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof UpgradeCardItem card) || UpgradeRules.Fixed.STORAGE_TERMINAL.cap(card.getKind()) <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (terminal.installCarry()) {
            stack.consume(1, player);
            CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.storage_carry_installed"), true);
            return ItemInteractionResult.CONSUME;
        }
        // already installed: open the terminal as usual
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Creative players get no loot: hand them the terminal with everything in it, like a shulker box. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal
                && terminal.hasCarry()) {
            ItemStack stack = new ItemStack(this);
            stack.applyComponents(terminal.collectComponents());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal) {
            terminal.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal ? terminal.signal() : 0;
    }

    /** Block item with a short description. */
    public static class TerminalItem extends BlockItem {
        public TerminalItem(Block block, Properties props) {
            super(block, props);
        }

        /** A terminal full of items never goes into another container item (no endlessly nested contents). */
        @Override
        public boolean canFitInsideContainerItems() {
            return false;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.robotica.age", 1, Component.translatable("age.robotica.1"))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.robotica.storage_terminal", StorageTerminalBlockEntity.BASE_SLOTS)
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.robotica.storage_terminal_power").withStyle(ChatFormatting.DARK_GRAY));
            CompoundTag contents = stack.get(CoreComponents.CONTENTS.get());
            if (contents != null) {
                int stacks = contents.getCompound("items").getList("Items", Tag.TAG_COMPOUND).size();
                tooltip.add(Component.translatable("tooltip.robotica.storage_terminal_carried", stacks).withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable("tooltip.robotica.storage_terminal_carry").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
