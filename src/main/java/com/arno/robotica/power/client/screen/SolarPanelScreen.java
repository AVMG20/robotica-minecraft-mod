package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.power.menu.SolarPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Solar Panel: wide charge bar, stored / capacity, sun state, live FE/t. */
public class SolarPanelScreen extends MachineScreen<SolarPanelMenu> {
    public SolarPanelScreen(SolarPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        long stored = menu.stored(), capacity = menu.capacity();
        drawEnergyBarWide(g, x + 8, y + 20, 160, 14, stored, capacity);
        drawLabel(g, Component.literal(Fmt.energy(stored) + " / " + Fmt.energy(capacity)), x + 8, y + 38, 120);
        int rate = menu.rate();
        Component status = Component.translatable(menu.sun() ? "gui.robotica.solar_sun" : "gui.robotica.solar_dark");
        drawStatus(g, status, x + 8, y + 52, 64, 1, rate > 0 ? Tone.GOOD : Tone.WARN);
        drawLabelRight(g, Component.literal("+" + Fmt.compact(rate) + " FE/t"), x + 128, y + 52, 52);
    }
}
