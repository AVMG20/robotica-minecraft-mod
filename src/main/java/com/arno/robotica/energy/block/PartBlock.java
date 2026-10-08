package com.arno.robotica.energy.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** A plain structure block (casing, amplifier, coolant, coil) with a one-line tooltip {@code tooltip.robotica.<id>}. */
public class PartBlock extends Block {
    public PartBlock(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica." + BuiltInRegistries.BLOCK.getKey(this).getPath()).withStyle(ChatFormatting.GRAY));
    }
}
