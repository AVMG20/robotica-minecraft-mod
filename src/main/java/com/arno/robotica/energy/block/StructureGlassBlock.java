package com.arno.robotica.energy.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.TransparentBlock;

import java.util.List;

/** Window block of a multiblock wall: behaves like glass (faces between two panes are culled). */
public class StructureGlassBlock extends TransparentBlock {
    public StructureGlassBlock(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica." + BuiltInRegistries.BLOCK.getKey(this).getPath()).withStyle(ChatFormatting.GRAY));
    }
}
