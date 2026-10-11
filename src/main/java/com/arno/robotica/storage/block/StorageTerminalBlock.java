package com.arno.robotica.storage.block;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.storage.StorageContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** Creative players get no loot: hand them the terminal with everything in it, like a shulker box. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal
                && terminal.hasContents()) {
            ItemStack stack = new ItemStack(this);
            stack.applyComponents(terminal.collectComponents());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal ? terminal.signal() : 0;
    }

    /** Block item with a short description and, when it holds items, what is inside. */
    public static class TerminalItem extends BlockItem {
        private static final int SUMMARY_LINES = 5;
        /** The summary shown last: a hovered tooltip redraws every frame, the stored items do not change. */
        private static volatile Summary lastSummary;

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
            HolderLookup.Provider registries = ctx.registries();
            if (contents == null) {
                tooltip.add(Component.translatable("tooltip.robotica.storage_terminal_keeps").withStyle(ChatFormatting.DARK_GRAY));
            } else if (registries != null) {
                tooltip.addAll(summary(contents, registries));
            }
        }

        /** Every stored item with its total count, biggest first, then how many more kinds there are. */
        private static List<Component> summary(CompoundTag contents, HolderLookup.Provider registries) {
            Summary last = lastSummary;
            if (last != null && last.tag() == contents) return last.lines();
            Map<Item, Integer> counts = new HashMap<>();
            count(contents.getCompound("items"), registries, counts);
            count(contents.getCompound("craft"), registries, counts);
            count(contents.getCompound("expansions"), registries, counts);
            count(contents.getCompound("battery"), registries, counts);
            List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
            List<Component> lines = new ArrayList<>();
            for (int i = 0; i < Math.min(SUMMARY_LINES, sorted.size()); i++) {
                Map.Entry<Item, Integer> e = sorted.get(i);
                lines.add(Component.translatable("tooltip.robotica.storage_terminal_item", e.getKey().getDescription(), e.getValue())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (sorted.size() > SUMMARY_LINES) {
                lines.add(Component.translatable("tooltip.robotica.storage_terminal_more", sorted.size() - SUMMARY_LINES)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            lastSummary = new Summary(contents, List.copyOf(lines));
            return lines;
        }

        /** Adds the stacks of one saved inventory ("Items" list, as item handlers and the crafting grid save it). */
        private static void count(CompoundTag inventory, HolderLookup.Provider registries, Map<Item, Integer> counts) {
            ListTag list = inventory.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
                if (!s.isEmpty()) counts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }

        private record Summary(CompoundTag tag, List<Component> lines) {}
    }
}
