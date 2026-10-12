package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpPayloads;
import com.arno.robotica.warp.menu.RiftEntry;
import com.arno.robotica.warp.menu.RiftRemoteMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Pad list of the Rift Remote: name, position and dimension, FE cost. Click a row to start the 3 second charge, click
 * the cross at the right end of a row to forget that pad. Scroll for more.
 */
public class RiftRemoteScreen extends MachineScreen<RiftRemoteMenu> {
    private static final int ROW_H = 24;
    private static final int ROWS = 5;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 224;
    private static final int X_SIZE = 11;

    private int scroll;

    public RiftRemoteScreen(RiftRemoteMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 166;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawFitted(g, font, title, titleLabelX, titleLabelY, imageWidth - 60, TEXT, -1, false, 1.0F);
        drawFitted(g, font, Component.literal(menu.entries().size() + " / " + com.arno.robotica.warp.WarpComponents.MAX_RIFT_PADS), imageWidth - 8, titleLabelY, 50, TEXT_MUTED, 1, false, 1.0F);
    }

    private int maxScroll() {
        return Math.max(0, menu.entries().size() - ROWS);
    }

    private int rowAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - LIST_X;
        double ry = mouseY - topPos - LIST_Y;
        if (rx < 0 || rx >= LIST_W || ry < 0 || ry >= ROWS * ROW_H || ry % ROW_H >= ROW_H - 2) return -1;
        int index = scroll + (int) (ry / ROW_H);
        return index < menu.entries().size() ? index : -1;
    }

    /** True when the mouse is on the forget cross of the row it is over. */
    private boolean onCross(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - LIST_X;
        double ry = (mouseY - topPos - LIST_Y) % ROW_H;
        return rx >= LIST_W - X_SIZE - 4 && rx < LIST_W - 4 && ry >= (ROW_H - 2 - X_SIZE) / 2.0 && ry < (ROW_H - 2 + X_SIZE) / 2.0;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<RiftEntry> entries = menu.entries();
        int hover = rowAt(mouseX, mouseY);
        boolean crossHover = hover >= 0 && onCross(mouseX, mouseY);
        int energy = menu.energy();
        if (entries.isEmpty()) {
            drawLabelCentered(g, Component.translatable("gui.robotica.warp.rift_empty"), x + imageWidth / 2, y + LIST_Y + 50, LIST_W);
        }
        for (int r = 0; r < ROWS; r++) {
            int index = scroll + r;
            if (index >= entries.size()) break;
            RiftEntry e = entries.get(index);
            int rx = x + LIST_X;
            int ry = y + LIST_Y + r * ROW_H;
            g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 2, SLOT_DARK);
            g.fill(rx + 1, ry + 1, rx + LIST_W - 1, ry + ROW_H - 3, index == hover && !crossHover && e.usable() ? 0xFFA6B8BE : SLOT);

            int crossX = rx + LIST_W - X_SIZE - 4;
            int crossY = ry + (ROW_H - 2 - X_SIZE) / 2;
            g.fill(crossX, crossY, crossX + X_SIZE, crossY + X_SIZE, index == hover && crossHover ? 0xFFB03030 : 0xFF6E6E6E);
            drawFitted(g, font, Component.literal("x"), crossX + X_SIZE / 2 + 1, crossY + 1, X_SIZE, 0xFFFFFFFF, 0, false, 1.0F);

            Component right;
            int rightColor;
            if (e.status() == RiftEntry.GONE) {
                right = Component.translatable("gui.robotica.warp.rift_gone");
                rightColor = 0xFFB03030;
            } else if (e.status() == RiftEntry.PRIVATE) {
                right = Component.translatable("gui.robotica.warp.rift_private");
                rightColor = 0xFFB03030;
            } else {
                right = Component.literal(Fmt.energy(e.cost()));
                rightColor = energy >= e.cost() ? 0xFF1B6E7A : 0xFFB03030;
            }
            int textRight = crossX - 4;
            int rightW = Math.min(70, font.width(right));
            int leftW = textRight - rx - 5 - rightW - 6;
            drawFitted(g, font, Component.literal(e.name()), rx + 5, ry + 4, leftW, e.usable() ? 0xFF202020 : 0xFF606060, -1, false, 1.0F);
            drawFitted(g, font, where(e), rx + 5, ry + 13, leftW, TEXT, -1, false, 1.0F);
            drawFitted(g, font, right, textRight, ry + 8, 70, rightColor, 1, false, 1.0F);
        }
        if (entries.size() > ROWS) {
            int trackH = ROWS * ROW_H - 2;
            int barH = Math.max(8, trackH * ROWS / entries.size());
            int barY = y + LIST_Y + (maxScroll() == 0 ? 0 : (trackH - barH) * scroll / maxScroll());
            g.fill(x + LIST_X + LIST_W + 1, y + LIST_Y, x + LIST_X + LIST_W + 4, y + LIST_Y + trackH, SLOT_DARK);
            g.fill(x + LIST_X + LIST_W + 1, barY, x + LIST_X + LIST_W + 4, barY + barH, ENERGY);
        }
        drawEnergyBarWide(g, x + 8, y + LIST_Y + ROWS * ROW_H + 6, LIST_W, 11, energy, menu.capacity());
    }

    private static Component where(RiftEntry e) {
        if (e.pos() == null || e.dimension() == null) return Component.literal("?");
        return Component.literal(e.pos().getX() + ", " + e.pos().getY() + ", " + e.pos().getZ() + "  "
                + DestinationScreen.prettyDimension(e.dimension().location()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hover = rowAt(mouseX, mouseY);
        if (hover < 0) return;
        RiftEntry e = menu.entries().get(hover);
        Component tip;
        if (onCross(mouseX, mouseY)) tip = Component.translatable("gui.robotica.warp.rift_forget");
        else if (e.status() == RiftEntry.GONE) tip = Component.translatable("gui.robotica.warp.rift_gone_tooltip");
        else if (e.status() == RiftEntry.PRIVATE) tip = Component.translatable("gui.robotica.warp.rift_private");
        else if (menu.energy() < e.cost()) tip = Component.translatable("gui.robotica.warp.rift_not_enough", Fmt.energy(e.cost() - menu.energy()));
        else tip = Component.translatable("gui.robotica.warp.click_to_travel");
        g.renderTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                RiftEntry e = menu.entries().get(index);
                if (onCross(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new WarpPayloads.RiftForget(e.id()));
                    menu.forgetLocally(e.id());
                    scroll = Math.min(scroll, maxScroll());
                    return true;
                }
                if (e.usable()) {
                    PacketDistributor.sendToServer(new WarpPayloads.RiftTravel(e.id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        return true;
    }
}
