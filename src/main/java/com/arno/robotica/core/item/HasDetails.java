package com.arno.robotica.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Items with a second, longer tooltip shown while Shift is held ("Hold Shift for details"). The normal tooltip stays
 * one or two short lines. Items without code can instead define the lang key {@code tooltip.robotica.<id>.details}
 * (lines split by a newline); both are combined by the core client tooltip handler. Common code: no client classes.
 */
public interface HasDetails {
    void appendDetails(ItemStack stack, Item.TooltipContext ctx, List<Component> lines);

    /** The key bound to a key mapping, as the player configured it (resolved on the client). */
    static MutableComponent key(String keyMapping) {
        return Component.literal("[").append(Component.keybind(keyMapping)).append("]").withStyle(ChatFormatting.YELLOW);
    }

    /** A detail line: gray text. */
    static MutableComponent line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }
}
