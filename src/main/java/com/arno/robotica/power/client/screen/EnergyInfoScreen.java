package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.power.menu.EnergyInfoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact status panel: big charge bar, stored / capacity, live FE/t and one status word. */
public class EnergyInfoScreen extends MachineScreen<EnergyInfoMenu> {
    public EnergyInfoScreen(EnergyInfoMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 74;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawFitted(g, font, title, titleLabelX, titleLabelY, imageWidth - 16, TEXT, -1, false, 1.0F);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        long stored = menu.stored(), capacity = menu.capacity();
        drawEnergyBarWide(g, x + 8, y + 20, 160, 14, stored, capacity);
        int pct = capacity <= 0 ? 0 : (int) (100L * stored / capacity);
        drawLabel(g, Component.literal(Fmt.energy(stored) + " / " + Fmt.energy(capacity)), x + 8, y + 40, 112);
        drawLabelRight(g, Component.literal(pct + "%"), x + 168, y + 40, 40);

        int rate = menu.rate();
        String sign = rate > 0 ? "+" : "";
        Component rateText = Component.literal(sign + Fmt.compact(rate) + " FE/t");
        Tone tone;
        Component status;
        if (menu.kind() == EnergyInfoMenu.KIND_SOLAR) {
            tone = menu.flag() ? Tone.GOOD : Tone.WARN;
            status = Component.translatable(menu.flag() ? "gui.robotica.solar_sun" : "gui.robotica.solar_dark");
        } else if (rate > 0) {
            tone = Tone.GOOD;
            status = Component.translatable("gui.robotica.storage_charging");
        } else if (rate < 0) {
            tone = Tone.WARN;
            status = Component.translatable("gui.robotica.storage_draining");
        } else {
            tone = stored >= capacity && capacity > 0 ? Tone.GOOD : Tone.WARN;
            status = Component.translatable(stored >= capacity && capacity > 0 ? "gui.robotica.storage_full" : "gui.robotica.status.idle");
        }
        drawStatus(g, status, x + 8, y + 56, 100, 1, tone);
        drawLabelRight(g, rateText, x + 168, y + 56, 70);
        addTooltip(x + 98, y + 54, 70, 12,
                Component.translatable("gui.robotica.energy_rate_tip"),
                Component.translatable("gui.robotica.energy_io_tip", Fmt.compact(menu.maxIo())));
    }
}
