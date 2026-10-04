package com.arno.robotica.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Crafting component of the balance ladder. Shows its age in the tooltip. */
public class PartItem extends Item {
    private final int age;

    public PartItem(Properties props, int age) {
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
    }
}
