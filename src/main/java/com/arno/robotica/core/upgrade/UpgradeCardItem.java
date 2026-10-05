package com.arno.robotica.core.upgrade;

import com.arno.robotica.core.item.HasDetails;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** One card per kind. Stackable kinds stack in a machine's upgrade slot, every card adds a step. See {@link Upgrades}. */
public class UpgradeCardItem extends Item implements HasDetails {
    private final UpgradeKind kind;

    public UpgradeCardItem(Properties props, UpgradeKind kind) {
        super(props.stacksTo(16));
        this.kind = kind;
    }

    public UpgradeKind getKind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", kind.age, Component.translatable("age.robotica." + kind.age))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.upgrade." + kind.id()).withStyle(ChatFormatting.GRAY));
        if (kind.stackable()) {
            tooltip.add(Component.translatable("tooltip.robotica.upgrade_stacks", kind.maxStack).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public void appendDetails(ItemStack stack, TooltipContext ctx, List<Component> lines) {
        lines.add(HasDetails.line("tooltip.robotica.upgrade_hint"));
        lines.add(HasDetails.line("tooltip.robotica.upgrade." + kind.id() + ".steps"));
        lines.add(HasDetails.line("tooltip.robotica.upgrade_caps"));
    }
}
