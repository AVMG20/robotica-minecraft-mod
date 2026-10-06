package com.arno.robotica.industry.block;

import com.arno.robotica.industry.IndustryConfig;
import com.arno.robotica.industry.IndustryRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Radioisotope Generator: quiet passive FE from thorium fuel pellets. Age 2. */
public class RtgBlock extends IndustryMachineBlock {
    public RtgBlock(Properties props) {
        super(props, IndustryRegistry.RTG_BE::get);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.rtg", IndustryConfig.rtgPower()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.rtg_pellet", IndustryConfig.rtgPelletTicks() / 1200).withStyle(ChatFormatting.DARK_GRAY));
    }
}
