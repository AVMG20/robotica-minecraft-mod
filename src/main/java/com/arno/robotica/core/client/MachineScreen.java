package com.arno.robotica.core.client;

import com.arno.robotica.core.util.Fmt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally drawn machine GUI (no textures). Draws the panel and a frame behind every slot.
 * Subclasses draw extras in {@link #renderMachine} with the helpers below; energy bars get hover tooltips.
 */
public abstract class MachineScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int PANEL_LIGHT = 0xFFFFFFFF;
    public static final int PANEL_DARK = 0xFF555555;
    public static final int BORDER = 0xFF000000;
    public static final int SLOT = 0xFF8B8B8B;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int ENERGY = 0xFF5CC8D8;
    public static final int ENERGY_DARK = 0xFF1B4A52;
    public static final int PROGRESS = 0xFFE8A060;

    private final List<int[]> energyBars = new ArrayList<>();
    private final List<long[]> energyValues = new ArrayList<>();

    protected MachineScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        energyBars.clear();
        energyValues.clear();
        drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        renderMachine(g, leftPos, topPos, mouseX, mouseY);
    }

    /** Draw machine-specific parts. x/y are the GUI's top-left corner. */
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        for (int i = 0; i < energyBars.size(); i++) {
            int[] r = energyBars.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                long[] v = energyValues.get(i);
                g.renderTooltip(font, Component.literal(Fmt.energy(v[0]) + " / " + Fmt.energy(v[1])), mouseX, mouseY);
            }
        }
    }

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, PANEL_DARK);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
    }

    /** 18x18 slot frame; x/y is the frame's top-left (slot position minus 1). */
    public static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    /** Vertical energy bar filling from the bottom, with a hover tooltip. */
    protected void drawEnergyBar(GuiGraphics g, int x, int y, int w, int h, long stored, long capacity) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_DARK);
        g.fill(x, y, x + w, y + h, ENERGY_DARK);
        int filled = capacity <= 0 ? 0 : (int) Math.min(h, Math.round((double) h * stored / capacity));
        g.fill(x, y + h - filled, x + w, y + h, ENERGY);
        energyBars.add(new int[]{x, y, w, h});
        energyValues.add(new long[]{stored, capacity});
    }

    /** Horizontal progress bar (0..1). */
    protected void drawProgress(GuiGraphics g, int x, int y, int w, int h, float progress) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_DARK);
        g.fill(x, y, x + w, y + h, SLOT);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, progress))), y + h, PROGRESS);
    }

    /** Small text inside the GUI, relative to its corner. Call from renderMachine. */
    protected void drawText(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }
}
