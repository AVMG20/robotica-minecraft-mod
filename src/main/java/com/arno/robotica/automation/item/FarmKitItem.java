package com.arno.robotica.automation.item;

import com.arno.robotica.automation.AutomationConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Mk kit for Stumpy and Sprout. Right-click a placed bot; kits must be applied in order (Mk2, Mk3, Mk4).
 * The effect lives in {@code FarmBotBlock#useItemOn}.
 */
public class FarmKitItem extends Item {
    private final int tier;
    private final int age;

    public FarmKitItem(Properties props, int tier, int age) {
        super(props.stacksTo(16));
        this.tier = tier;
        this.age = age;
    }

    /** The Mk level this kit upgrades a bot to (2-4). */
    public int tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age))
                .withStyle(ChatFormatting.DARK_GRAY));
        int size = 2 * AutomationConfig.farmRadius(tier) + 1;
        tooltip.add(Component.translatable("tooltip.robotica.farm_kit", tier, size, size, AutomationConfig.farmInterval(tier),
                String.format(java.util.Locale.ROOT, "%.1f", AutomationConfig.farmGrowth(tier))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.farm_kit_use", tier - 1).withStyle(ChatFormatting.DARK_GRAY));
    }
}
