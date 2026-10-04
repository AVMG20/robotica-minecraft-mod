package com.arno.robotica.gear.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Smithing template for the tool upgrade path of one age (kit 1 to 4). Consumed by the smithing table. */
public class UpgradeKitItem extends Item {
    private final int age;

    public UpgradeKitItem(Properties props, int age) {
        super(props);
        this.age = age;
    }

    public int getAge() {
        return age;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", age, Component.translatable("age.robotica." + age))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.gear.kit." + age).withStyle(ChatFormatting.GRAY));
    }
}
