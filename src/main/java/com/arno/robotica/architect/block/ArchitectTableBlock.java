package com.arno.robotica.architect.block;

import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.style.BuildStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Architect Table: turns bulk materials into matter and builds 9x9 buildings on a 5x5 plot grid. Age 0. */
public class ArchitectTableBlock extends Block implements EntityBlock {
    public ArchitectTableBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArchitectTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ArchitectRegistry.ARCHITECT_TABLE_BE.get()) return null;
        return (lvl, pos, st, be) -> ((ArchitectTableBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof ArchitectTableBlockEntity be) {
            be.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ArchitectTableBlockEntity be && player instanceof ServerPlayer serverPlayer) {
            be.claimIfFree(serverPlayer);
            if (!be.canUse(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("message.robotica.architect_not_owner", be.ownerName()), true);
                return InteractionResult.CONSUME;
            }
            serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Creative players get no loot: hand them the table with everything in it, like a shulker box. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof ArchitectTableBlockEntity be && be.hasContents()) {
            ItemStack stack = new ItemStack(this);
            stack.applyComponents(be.collectComponents());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArchitectTableBlockEntity be) {
            be.onRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.architect_table",
                BuildStyle.TIMBERFRAME.energyPerBlock(ArchitectConfig.fePerBlock())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.architect_table_matter").withStyle(ChatFormatting.DARK_GRAY));
    }
}
