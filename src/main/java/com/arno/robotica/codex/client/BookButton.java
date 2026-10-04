package com.arno.robotica.codex.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Flat paper-and-copper button for the Codex: a text button, or a small procedural page-turn arrow. */
final class BookButton extends FitButton {
    enum Kind { LABEL, PREV, NEXT }

    private static final int PAPER_SLOT = 0xFFC9D2CE, PAPER_HOVER = 0xFFDCE3E0, PAPER_OFF = 0xFFDDE3E0;
    private static final int TRIM = 0xFFC87533, TRIM_HOVER = 0xFFA4521C, TRIM_OFF = 0xFFB9C4C0;
    private static final int INK = 0xFF1E2A2A, INK_OFF = 0xFF8A9692;

    private final Kind kind;

    BookButton(int x, int y, int width, int height, Component label, OnPress onPress, Kind kind) {
        super(x, y, width, height, label, onPress);
        this.kind = kind;
    }

    BookButton(int x, int y, int width, int height, Component label, OnPress onPress, Component tooltip) {
        super(x, y, width, height, label, onPress, tooltip);
        this.kind = Kind.LABEL;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int border = !active ? TRIM_OFF : hover ? TRIM_HOVER : TRIM;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, !active ? PAPER_OFF : hover ? PAPER_HOVER : PAPER_SLOT);
        if (active) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x22000000);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x55FFFFFF);
        }
        int ink = active ? INK : INK_OFF;
        if (kind == Kind.LABEL) {
            MachineScreen.drawFitted(g, net.minecraft.client.Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2 + 1,
                    w - 6, ink, 0, false, 1.0F);
            return;
        }
        int cx = x + w / 2, cy = y + h / 2;
        for (int r = -3; r <= 3; r++) {
            int len = 4 - Math.abs(r);
            if (kind == Kind.NEXT) g.fill(cx - 2, cy + r, cx - 2 + len, cy + r + 1, ink);
            else g.fill(cx + 2 - len, cy + r, cx + 2, cy + r + 1, ink);
        }
    }
}
