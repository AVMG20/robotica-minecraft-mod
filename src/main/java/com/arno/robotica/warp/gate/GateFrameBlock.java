package com.arno.robotica.warp.gate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Plain frame block of a Portal Gate. Age 4. */
public class GateFrameBlock extends Block {
    public GateFrameBlock(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 4, Component.translatable("age.robotica.4")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.gate_frame").withStyle(ChatFormatting.GRAY));
    }
}
