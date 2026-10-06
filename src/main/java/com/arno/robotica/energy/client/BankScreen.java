package com.arno.robotica.energy.client;

import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.menu.BankMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Capacitor Bank GUI: a big charge gauge, stored / capacity, average input and output over the last second, a 60 s
 * sparkline of the net flow (green: charging, red: draining), the rate limit and the structure line.
 */
public class BankScreen extends ControllerScreen<BankMenu> {
    private static final int SPARK_X = 8, SPARK_Y = 66, SPARK_W = 160, SPARK_H = 36;

    public BankScreen(BankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 144;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        addShowButton(150, 120);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawFitted(g, font, title, titleLabelX, titleLabelY, imageWidth - 16, TEXT, -1, false, 1.0F);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        long stored = menu.energy(), capacity = menu.capacity();
        drawGauge(g, x + 8, y + 18, 160, 16, stored, capacity);
        int pct = capacity <= 0 ? 0 : (int) Math.min(999, Math.floor(100.0 * stored / capacity));
        drawLabel(g, Component.literal(Fmt.energy(stored) + " / " + Fmt.energy(capacity)), x + 8, y + 38, 120);
        drawLabelRight(g, Component.literal(pct + "%"), x + 168, y + 38, 40);
        addTooltip(x + 8, y + 37, 160, 10, Component.translatable("gui.robotica.energy.bank_exact",
                String.format(java.util.Locale.ROOT, "%,d", stored), String.format(java.util.Locale.ROOT, "%,d", capacity)));

        long in = menu.averageIn(), out = menu.averageOut();
        drawFitted(g, font, Component.translatable("gui.robotica.energy.bank_in", Fmt.compact(in)), x + 8, y + 52, 78, 0xFF1E6B2A, -1, false, 1.0F);
        drawFitted(g, font, Component.translatable("gui.robotica.energy.bank_out", Fmt.compact(out)), x + 168, y + 52, 78, 0xFFA01818, 1, false, 1.0F);
        addTooltip(x + 8, y + 51, 160, 10, Component.translatable("gui.robotica.energy.bank_io_tip"),
                Component.translatable("gui.robotica.energy.bank_rate", Fmt.compact(menu.rate())).withStyle(ChatFormatting.GRAY));

        drawSparkline(g, x + SPARK_X, y + SPARK_Y, menu.history());

        small(g, Component.translatable("gui.robotica.energy.bank_parts", menu.capacitors(), menu.coils(), Fmt.compact(menu.rate())), x + 8, y + 107, 160);
        drawStructure(g, x + 8, y + 120, 136, 2);
    }

    /** Big horizontal gauge: dark trough, cyan fill, tick marks every 10%, a soft glare line. */
    private void drawGauge(GuiGraphics g, int gx, int gy, int w, int h, long stored, long capacity) {
        drawInset(g, gx, gy, w, h);
        g.fill(gx, gy, gx + w, gy + h, ENERGY_DARK);
        int filled = capacity <= 0 ? 0 : (int) Math.min(w, Math.round((double) w * stored / capacity));
        if (filled > 0) {
            g.fillGradient(gx, gy, gx + filled, gy + h, ENERGY_TOP, ENERGY_BOTTOM);
            g.fill(gx, gy + 2, gx + filled, gy + 3, 0x50FFFFFF);
        }
        for (int i = 1; i < 10; i++) {
            int tx = gx + w * i / 10;
            g.fill(tx, gy + h - (i == 5 ? 6 : 3), tx + 1, gy + h, 0x66000000);
        }
    }

    /** 60 bars, one per second, oldest left: up and green while charging, down and red while draining. */
    private void drawSparkline(GuiGraphics g, int sx, int sy, long[] history) {
        drawInset(g, sx, sy, SPARK_W, SPARK_H);
        g.fill(sx, sy, sx + SPARK_W, sy + SPARK_H, 0xFF1A1F22);
        int mid = sy + SPARK_H / 2;
        long max = 1;
        for (long v : history) max = Math.max(max, Math.abs(v));
        int n = history.length;
        float barW = (float) SPARK_W / n;
        for (int i = 0; i < n; i++) {
            long v = history[i];
            if (v == 0) continue;
            int x0 = sx + Math.round(i * barW), x1 = sx + Math.round((i + 1) * barW);
            int len = Math.max(1, Math.round((SPARK_H / 2.0F - 1) * Math.abs(v) / max));
            if (v > 0) g.fill(x0, mid - len, Math.max(x0 + 1, x1 - 1), mid, 0xFF4CD964);
            else g.fill(x0, mid, Math.max(x0 + 1, x1 - 1), mid + len, 0xFFE8493A);
        }
        g.fill(sx, mid, sx + SPARK_W, mid + 1, 0x80FFFFFF);
        drawFitted(g, font, Component.literal("+" + Fmt.compact(max)), sx + 2, sy + 2, 60, 0xFF8FA0A8, -1, false, 0.65F);
        drawFitted(g, font, Component.literal("-" + Fmt.compact(max)), sx + 2, sy + SPARK_H - 7, 60, 0xFF8FA0A8, -1, false, 0.65F);
        drawFitted(g, font, Component.translatable("gui.robotica.energy.bank_60s"), sx + SPARK_W - 2, sy + 2, 60, 0xFF8FA0A8, 1, false, 0.65F);
        long sum = 0;
        for (long v : history) sum += v;
        addTooltip(sx, sy, SPARK_W, SPARK_H, Component.translatable("gui.robotica.energy.bank_history_tip"),
                Component.translatable("gui.robotica.energy.bank_history_avg", signed(sum / Math.max(1, n))).withStyle(ChatFormatting.GRAY));
    }

    private static String signed(long v) {
        return (v >= 0 ? "+" : "-") + Fmt.compact(Math.abs(v));
    }
}
