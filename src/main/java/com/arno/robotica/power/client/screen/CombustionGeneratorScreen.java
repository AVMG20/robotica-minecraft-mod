package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.menu.CombustionGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CombustionGeneratorScreen extends MachineScreen<CombustionGeneratorMenu> {
    public CombustionGeneratorScreen(CombustionGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 15, y + 17, 12, 56, menu.energy(), menu.capacity());
        int total = menu.burnTotal();
        float burn = total <= 0 ? 0 : (float) menu.burnTime() / total;
        drawProgress(g, x + 62, y + 62, 52, 5, burn);
        if (menu.burnTime() > 0) {
            drawText(g, Component.translatable("gui.robotica.generating", CoreConfig.scaleGeneration(PowerConfig.generatorOutput())),
                    x + 62, y + 24, 0xFF404040);
        }
    }
}
