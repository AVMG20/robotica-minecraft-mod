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
    protected int titleMaxWidth() {
        return 108;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 12, y + 17, 14, 44, menu.energy(), menu.capacity());
        drawArrow(g, x + 74, y + 37, menu.progress());
        if (menu.progress() > 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.working"), x + 85, y + 58, 80, Tone.GOOD);
        } else if (menu.energy() <= 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.no_energy"), x + 85, y + 58, 80, Tone.BAD);
        } else {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.idle"), x + 85, y + 58, 80, Tone.WARN);
        }
        drawLabelCentered(g, Component.translatable("gui.robotica.upgrades"), x + 142, y + 8, 48);
    }
}
