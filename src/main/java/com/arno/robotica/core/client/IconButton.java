package com.arno.robotica.core.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Small square vanilla-style button showing an item as its icon. A toggle shows a green dot when on and a dimmed icon when off.
 * The explanation lives in the tooltip ({@link #hint}), not on the button.
 */
public class IconButton extends Button {
    private ItemStack icon;
    private final boolean toggle;
    private boolean on;

    public IconButton(int x, int y, int size, ItemStack icon, boolean toggle, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.toggle = toggle;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    /** Sets the tooltip (also used as the narration). */
    public void hint(Component text) {
        setTooltip(Tooltip.create(text));
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int color) {
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        int ix = getX() + (width - 16) / 2, iy = getY() + (height - 16) / 2;
        g.renderItem(icon, ix, iy);
        if (toggle) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (!on) g.fill(ix, iy, ix + 16, iy + 16, 0x90707070);
            int dx = getX() + width - 6, dy = getY() + 2;
            g.fill(dx, dy, dx + 4, dy + 4, 0xFF000000);
            g.fill(dx + 1, dy + 1, dx + 3, dy + 3, on ? 0xFF3DBE5A : 0xFF8A8A8A);
            g.pose().popPose();
        }
    }
}
