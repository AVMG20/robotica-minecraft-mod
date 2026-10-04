package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToolSettings;
import com.arno.robotica.gear.weapon.EnergyWeaponItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;
import java.util.List;

/** HUD layer: mode and energy of the Robotica tool or weapon in the main hand. */
final class GearHud {
    private GearHud() {}

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Robotica.id("gear_hud"), GearHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof GearToolItem) && !(stack.getItem() instanceof EnergyWeaponItem)) return;

        List<Component> lines = new ArrayList<>();
        if (stack.getItem() instanceof GearToolItem tool) {
            if (tool.spec.modes.size() > 1 || tool.spec.modes.get(0) != com.arno.robotica.gear.tool.AreaMode.SINGLE) {
                Component mode = tool.activeMode(stack, mc.player).displayName();
                lines.add(Component.translatable("gear.robotica.hud.mode", mode));
            }
            int enchant = ToolSettings.enchantMode(stack);
            if (tool.spec.fortuneLevel > 0 && enchant != ToolSettings.ENCHANT_NONE) {
                lines.add(Component.translatable("gear.robotica.enchant." + (enchant == ToolSettings.ENCHANT_SILK ? "silk" : "fortune")));
            }
            if (!tool.spec.isEnergy()) {
                lines.add(Component.translatable("gear.robotica.hud.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()));
            }
        }
        int capacity = stack.getItem() instanceof EnergyItem ? ItemEnergy.capacity(stack) : 0;
        int stored = ItemEnergy.get(stack);

        Font font = mc.font;
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int barW = 90;
        int x = w - barW - 12;
        int y = h - 40 - lines.size() * 10;
        for (Component line : lines) {
            graphics.drawString(font, line, x, y, 0xFFFFFF, true);
            y += 10;
        }
        if (capacity > 0) {
            graphics.fill(x - 1, y - 1, x + barW + 1, y + 7, 0xAA000000);
            graphics.fill(x, y, x + barW, y + 6, 0xFF1C2A2E);
            int filled = (int) ((long) barW * stored / capacity);
            boolean empty = stored <= 0;
            graphics.fill(x, y, x + filled, y + 6, empty ? 0xFFC9302A : 0xFF5CC8D8);
            graphics.drawString(font, Fmt.energy(stored) + " / " + Fmt.energy(capacity), x, y + 9, empty ? 0xFFFF6B5E : 0xFFD8FBFF, true);
        }
    }
}
