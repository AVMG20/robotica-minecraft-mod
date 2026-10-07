package com.arno.robotica.gear.bench;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Tinker's Bench (Age 1): installs and removes cards (Auto-Pickup, Void Filter) and modules in power tools and FE
 * weapons (see {@link TinkersBenchMenu}). Like a crafting table it stores nothing: the item goes back to the player when
 * the screen closes.
 */
public class TinkersBenchBlock extends Block {
    private static final Component TITLE = Component.translatable("container.robotica.tinkers_bench");

    public TinkersBenchBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TinkersBenchMenu(id, inv, ContainerLevelAccess.create(level, pos)), TITLE));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.tinkers_bench").withStyle(ChatFormatting.GRAY));
    }
}
