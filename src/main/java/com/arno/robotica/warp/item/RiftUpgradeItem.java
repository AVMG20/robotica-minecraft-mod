package com.arno.robotica.warp.item;

import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Age 3 upgrade for a Warp Pad. The install happens in {@code WarpPadBlock#useItemOn}. */
public class RiftUpgradeItem extends Item {
    public RiftUpgradeItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", 3, Component.translatable("age.robotica.3")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.rift_upgrade", Fmt.energy(WarpConfig.padRiftCost())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.rift_upgrade_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
