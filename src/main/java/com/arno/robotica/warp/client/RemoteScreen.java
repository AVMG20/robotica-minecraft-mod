package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpPayloads;
import com.arno.robotica.warp.menu.RemoteEntry;
import com.arno.robotica.warp.menu.RemoteMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Pad list of the Warp Remote: name, position and dimension, FE cost. Click a row to start the 3 second charge, the H
 * button makes a pad home (first row), the cross forgets it. Scroll for more.
 */
public class RemoteScreen extends MachineScreen<RemoteMenu> {
    private static final int ROW_H = 24;
    private static final int ROWS = 5;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 224;
    private static final int BTN = 11;

    private int scroll;

    public RemoteScreen(RemoteMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 166;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawFitted(g, font, title, titleLabelX, titleLabelY, imageWidth - 60, TEXT, -1, false, 1.0F);
        drawFitted(g, font, Component.literal(menu.entries().size() + " / " + WarpComponents.MAX_PADS), imageWidth - 8, titleLabelY, 50, TEXT_MUTED, 1, false, 1.0F);
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

    /** Left edge (relative to the list) of the button {@code slot}: 0 = forget cross, 1 = home. */
    private static int buttonX(int slot) {
        return LIST_W - BTN - 4 - slot * (BTN + 3);
    }

    /** The button of the hovered row under the mouse: 0 = forget, 1 = home, -1 = none. */
    private int buttonAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - LIST_X;
        double ry = (mouseY - topPos - LIST_Y) % ROW_H;
        if (ry < (ROW_H - 2 - BTN) / 2.0 || ry >= (ROW_H - 2 + BTN) / 2.0) return -1;
        for (int slot = 0; slot < 2; slot++) {
            if (rx >= buttonX(slot) && rx < buttonX(slot) + BTN) return slot;
        }
        return -1;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<RemoteEntry> entries = menu.entries();
        int hover = rowAt(mouseX, mouseY);
        int button = hover >= 0 ? buttonAt(mouseX, mouseY) : -1;
        int energy = menu.energy();
        if (entries.isEmpty()) {
            drawLabelCentered(g, Component.translatable("gui.robotica.warp.remote_empty"), x + imageWidth / 2, y + LIST_Y + 50, LIST_W);
        }
        for (int r = 0; r < ROWS; r++) {
            int index = scroll + r;
            if (index >= entries.size()) break;
            RemoteEntry e = entries.get(index);
            int rx = x + LIST_X;
            int ry = y + LIST_Y + r * ROW_H;
            g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 2, SLOT_DARK);
            g.fill(rx + 1, ry + 1, rx + LIST_W - 1, ry + ROW_H - 3, index == hover && button < 0 && e.usable() ? 0xFFA6B8BE : SLOT);

            int by = ry + (ROW_H - 2 - BTN) / 2;
            int crossX = rx + buttonX(0);
            g.fill(crossX, by, crossX + BTN, by + BTN, index == hover && button == 0 ? 0xFFB03030 : 0xFF6E6E6E);
            drawFitted(g, font, Component.literal("x"), crossX + BTN / 2 + 1, by + 1, BTN, 0xFFFFFFFF, 0, false, 1.0F);
            int homeX = rx + buttonX(1);
            int homeColor = index == 0 ? 0xFF2E8F5A : index == hover && button == 1 ? 0xFF3A7FA0 : 0xFF6E6E6E;
            g.fill(homeX, by, homeX + BTN, by + BTN, homeColor);
            drawFitted(g, font, Component.literal("H"), homeX + BTN / 2 + 1, by + 1, BTN, 0xFFFFFFFF, 0, false, 1.0F);

            Component right;
            int rightColor;
            if (e.status() == RemoteEntry.GONE) {
                right = Component.translatable("gui.robotica.warp.remote_gone");
                rightColor = 0xFFB03030;
            } else if (e.status() == RemoteEntry.PRIVATE) {
                right = Component.translatable("gui.robotica.warp.remote_private");
                rightColor = 0xFFB03030;
            } else if (e.status() == RemoteEntry.NEEDS_MK2) {
                right = Component.translatable("gui.robotica.warp.needs_mk2");
                rightColor = 0xFFB03030;
            } else {
                right = Component.literal(Fmt.energy(e.cost()));
                rightColor = energy >= e.cost() ? 0xFF1B6E7A : 0xFFB03030;
            }
            int textRight = homeX - 4;
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

    private static Component where(RemoteEntry e) {
        if (e.pos() == null || e.dimension() == null) return Component.literal("?");
        return Component.literal(e.pos().getX() + ", " + e.pos().getY() + ", " + e.pos().getZ() + "  "
                + DestinationScreen.prettyDimension(e.dimension().location()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hover = rowAt(mouseX, mouseY);
        if (hover < 0) return;
        RemoteEntry e = menu.entries().get(hover);
        int button = buttonAt(mouseX, mouseY);
        Component tip;
        if (button == 0) tip = Component.translatable("gui.robotica.warp.remote_forget");
        else if (button == 1) tip = Component.translatable(hover == 0 ? "gui.robotica.warp.remote_is_home" : "gui.robotica.warp.remote_set_home");
        else if (e.status() == RemoteEntry.GONE) tip = Component.translatable("gui.robotica.warp.remote_gone_tooltip");
        else if (e.status() == RemoteEntry.PRIVATE) tip = Component.translatable("gui.robotica.warp.remote_private");
        else if (e.status() == RemoteEntry.NEEDS_MK2) tip = Component.translatable("gui.robotica.warp.remote_needs_mk2_tooltip");
        else if (menu.energy() < e.cost()) tip = Component.translatable("gui.robotica.warp.remote_not_enough", Fmt.energy(e.cost() - menu.energy()));
        else tip = Component.translatable("gui.robotica.warp.click_to_travel");
        g.renderTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                RemoteEntry e = menu.entries().get(index);
                int hit = buttonAt(mouseX, mouseY);
                if (hit == 0) {
                    PacketDistributor.sendToServer(new WarpPayloads.RemoteForget(e.id()));
                    menu.forgetLocally(e.id());
                    scroll = Math.min(scroll, maxScroll());
                    return true;
                }
                if (hit == 1) {
                    if (index > 0) {
                        PacketDistributor.sendToServer(new WarpPayloads.RemoteHome(e.id()));
                        menu.homeLocally(e.id());
                    }
                    return true;
                }
                if (e.usable()) {
                    PacketDistributor.sendToServer(new WarpPayloads.RemoteTravel(e.id()));
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
