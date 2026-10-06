package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.power.menu.SolarPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Solar Panel: wide charge bar, stored / capacity, sun state, live FE/t, two card slots. */
public class SolarPanelScreen extends MachineScreen<SolarPanelMenu> {
    public SolarPanelScreen(SolarPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index == 0 ? icon("upgrade_speed") : slot.index == 1 ? icon("upgrade_efficiency") : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        return Component.translatable(slot.index == 0 ? "gui.robotica.solar_speed_slot" : "gui.robotica.solar_efficiency_slot");
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        long stored = menu.stored(), capacity = menu.capacity();
        drawEnergyBarWide(g, x + 8, y + 20, 160, 14, stored, capacity);
        drawLabel(g, Component.literal(Fmt.energy(stored) + " / " + Fmt.energy(capacity)), x + 8, y + 38, 120);
        int rate = menu.rate();
        Component status = Component.translatable(menu.sun() ? "gui.robotica.solar_sun" : rate > 0 ? "gui.robotica.solar_moon" : "gui.robotica.solar_dark");
        drawStatus(g, status, x + 8, y + 52, 64, 1, rate > 0 ? Tone.GOOD : Tone.WARN);
        drawLabelRight(g, Component.literal("+" + Fmt.compact(rate) + " FE/t"), x + 128, y + 52, 52);
        addTooltip(x + 132, y + 44, 38, 20, Component.translatable("gui.robotica.solar_cards_tip"));
    }
}
