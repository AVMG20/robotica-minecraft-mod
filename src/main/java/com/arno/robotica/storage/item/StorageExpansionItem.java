package com.arno.robotica.storage.item;

import com.arno.robotica.core.item.PartItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Goes into an expansion slot of a Storage Terminal and adds slots. One of each Mk fits at a time. */
public class StorageExpansionItem extends PartItem {
    private final int slots;

    public StorageExpansionItem(Properties props, int age, int slots) {
        super(props, age);
        this.slots = slots;
    }

    /** Terminal slots this expansion adds. */
    public int slots() {
        return slots;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.robotica.storage_expansion", slots).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.storage_expansion_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
