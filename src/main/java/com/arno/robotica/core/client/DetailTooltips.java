package com.arno.robotica.core.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.HasDetails;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * "Hold Shift for details" for every Robotica item that has details: from {@link HasDetails} and/or the lang key
 * {@code tooltip.robotica.<id>.details} (lines split by a newline). Keeps the normal tooltip short.
 */
final class DetailTooltips {
    private DetailTooltips() {}

    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!Robotica.MODID.equals(id.getNamespace())) return;
        List<Component> details = new ArrayList<>();
        if (stack.getItem() instanceof HasDetails d) d.appendDetails(stack, event.getContext(), details);
        String langKey = "tooltip.robotica." + id.getPath() + ".details";
        if (I18n.exists(langKey)) {
            for (String line : I18n.get(langKey, com.arno.robotica.core.item.DetailArgs.get(id.getPath())).split("\n")) details.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        if (details.isEmpty()) return;
        List<Component> tooltip = event.getToolTip();
        // Advanced tooltips (F3+H) end with the item id and the component count in dark gray: keep those last.
        int at = tooltip.size();
        if (event.getFlags().isAdvanced()) {
            String idText = id.toString();
            while (at > 1 && isAdvancedLine(tooltip.get(at - 1), idText)) at--;
        }
        if (Screen.hasShiftDown()) {
            tooltip.addAll(at, details);
        } else {
            tooltip.add(at, Component.translatable("tooltip.robotica.hold_shift",
                    Component.literal("Shift").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean isAdvancedLine(Component line, String idText) {
        if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            return tc.getKey().equals("item.components") || tc.getKey().equals("item.durability");
        }
        return line.getString().equals(idText);
    }
}
