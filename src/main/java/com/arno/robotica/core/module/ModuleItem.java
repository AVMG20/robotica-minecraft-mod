package com.arno.robotica.core.module;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A module, one item per kind and level. Installed at the Tinker's Bench (Exo pieces also in the J screen). */
public class ModuleItem extends Item {
    public final ModuleKind kind;
    /** 1 based. */
    public final int level;

    public ModuleItem(Properties props, ModuleKind kind, int level) {
        super(props.stacksTo(16));
        this.kind = kind;
        this.level = level;
    }

    public int minTier() {
        return kind.minTier(level);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ModuleText.describe(kind, level).withStyle(ChatFormatting.GRAY));
        tooltip.add(kind.fitsLine().withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(kind.needsLine(level).withStyle(ChatFormatting.DARK_AQUA));
        Component cost = ModuleText.cost(kind, level);
        if (cost != null) tooltip.add(cost.copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(kind.armorOnly() ? "module.robotica.install_armor" : "module.robotica.install")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
