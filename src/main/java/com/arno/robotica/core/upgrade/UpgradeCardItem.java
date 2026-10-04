package com.arno.robotica.core.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class UpgradeCardItem extends Item {
    private final UpgradeKind kind;
    private final int level;

    public UpgradeCardItem(Properties props, UpgradeKind kind, int level) {
        super(props.stacksTo(16));
        this.kind = kind;
        this.level = level;
    }

    public UpgradeKind getKind() {
        return kind;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.upgrade." + kind.id() + "." + level).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.upgrade_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
