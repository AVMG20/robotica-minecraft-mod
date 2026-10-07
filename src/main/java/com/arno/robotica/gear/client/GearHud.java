package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.weapon.Lifesteal;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.weapon.EnergyWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD layer for the Robotica tool or weapon in the main hand, bottom right: the mode with its neighbours and the key
 * that switches it ("1x1 [3x3] 5x5  V"), the installed modules, durability and an energy bar.
 */
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
            if (tool.spec.hasAreaModes()) lines.add(modeLine(tool, stack, mc));
            if (!tool.spec.isEnergy()) {
                lines.add(Component.translatable("gear.robotica.hud.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()));
            }
        }
        Component modules = moduleLine(stack, mc);
        if (modules != null) lines.add(modules);
        int capacity = stack.getItem() instanceof EnergyItem ? ItemEnergy.capacity(stack) : 0;
        int stored = ItemEnergy.get(stack);

        Font font = mc.font;
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int barW = 90;
        int right = w - 12;
        int y = h - 40 - lines.size() * 10;
        for (Component line : lines) {
            graphics.drawString(font, line, right - font.width(line), y, 0xFFFFFF, true);
            y += 10;
        }
        if (capacity > 0) {
            int x = right - barW;
            graphics.fill(x - 1, y - 1, x + barW + 1, y + 7, 0xAA000000);
            graphics.fill(x, y, x + barW, y + 6, 0xFF1C2A2E);
            int filled = (int) ((long) barW * stored / capacity);
            boolean empty = stored <= 0;
            graphics.fill(x, y, x + filled, y + 6, empty ? 0xFFC9302A : 0xFF5CC8D8);
            String text = empty ? Component.translatable("gear.robotica.hud.empty").getString() : Fmt.energy(stored) + " / " + Fmt.energy(capacity);
            graphics.drawString(font, text, right - font.width(text), y + 9, empty ? 0xFFFF6B5E : 0xFFD8FBFF, true);
        }
    }

    /** Installed modules, dimmed when switched off, plus the Lifesteal cooldown. Null when none. */
    private static Component moduleLine(ItemStack stack, Minecraft mc) {
        MutableComponent line = null;
        for (ModuleKind kind : ModuleKind.values()) {
            int level = Modules.level(stack, kind);
            if (level <= 0) continue;
            ChatFormatting color = Modules.enabled(stack, kind) ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY;
            MutableComponent name = kind.displayName(level).copy().withStyle(color);
            if (kind == ModuleKind.LIFESTEAL && mc.player != null) {
                float cd = mc.player.getCooldowns().getCooldownPercent(Lifesteal.cooldownItem(), 0.0F);
                if (cd > 0.0F) {
                    int seconds = (int) Math.ceil(cd * GearConfig.lifestealCooldown() / 20.0F);
                    name = Component.translatable("gear.robotica.hud.cooldown", kind.displayName(), seconds).withStyle(ChatFormatting.RED);
                }
            }
            line = line == null ? name : line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY)).append(name);
        }
        return line;
    }

    /** "1x1 [3x3] 5x5  [V]": the active mode between its neighbours, then the key. Sneaking or empty shows 1x1. */
    private static Component modeLine(GearToolItem tool, ItemStack stack, Minecraft mc) {
        AreaMode active = tool.activeMode(stack, mc.player);
        AreaMode selected = tool.mode(stack);
        List<AreaMode> modes = tool.spec.modes;
        MutableComponent line = Component.empty();
        if (active != selected) {
            line.append(Component.literal("[").append(active.displayName()).append("]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            line.append(Component.translatable(tool.hasPower(stack) ? "gear.robotica.hud.sneaking" : "gear.robotica.hud.no_power")
                    .withStyle(ChatFormatting.GRAY));
            return line;
        }
        int i = modes.indexOf(selected);
        if (modes.size() > 2) {
            line.append(modes.get(Math.floorMod(i - 1, modes.size())).displayName().copy().withStyle(ChatFormatting.DARK_GRAY)).append(" ");
        }
        line.append(Component.literal("[").append(selected.displayName()).append("]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (modes.size() > 1) {
            line.append(" ").append(modes.get(Math.floorMod(i + 1, modes.size())).displayName().copy().withStyle(ChatFormatting.DARK_GRAY));
            line.append("  ").append(Component.literal("[").append(GearKeys.CYCLE_MODE.getTranslatedKeyMessage()).append("]")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return line;
    }
}
