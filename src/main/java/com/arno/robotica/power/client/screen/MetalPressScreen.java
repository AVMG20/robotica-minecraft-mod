package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.power.menu.MetalPressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MetalPressScreen extends MachineScreen<MetalPressMenu> {
    public MetalPressScreen(MetalPressMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 15, y + 17, 12, 56, menu.energy(), menu.capacity());
        drawProgress(g, x + 68, y + 40, 32, 8, menu.progress());
        drawText(g, Component.translatable("gui.robotica.upgrades"), x + 124, y + 8, 0xFF404040);
    }
}
