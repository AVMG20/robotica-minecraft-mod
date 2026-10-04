package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.WarpPayloads;
import com.arno.robotica.warp.menu.DestinationEntry;
import com.arno.robotica.warp.menu.DestinationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** Destination list: name, owner, distance or dimension and FE cost per row. Click a row to travel (scroll for more). */
public class DestinationScreen extends MachineScreen<DestinationMenu> {
    private static final int ROW_H = 24;
    private static final int ROWS = 5;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 22;
    private static final int LIST_W = 224;

    private int scroll;

    public DestinationScreen(DestinationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 168;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("gui.robotica.warp.from", menu.padName()), titleLabelX, titleLabelY, 0x404040, false);
    }

    private int maxScroll() {
        return Math.max(0, menu.entries().size() - ROWS);
    }

    private int rowAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - LIST_X;
        double ry = mouseY - topPos - LIST_Y;
        if (rx < 0 || rx >= LIST_W || ry < 0 || ry >= ROWS * ROW_H) return -1;
        int index = scroll + (int) (ry / ROW_H);
        return index < menu.entries().size() ? index : -1;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<DestinationEntry> entries = menu.entries();
        int hover = rowAt(mouseX, mouseY);
        int energy = menu.energy();
        for (int r = 0; r < ROWS; r++) {
            int index = scroll + r;
            if (index >= entries.size()) break;
            DestinationEntry e = entries.get(index);
            int rx = x + LIST_X;
            int ry = y + LIST_Y + r * ROW_H;
            boolean affordable = e.available() && energy >= e.cost();
            g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 2, SLOT_DARK);
            g.fill(rx + 1, ry + 1, rx + LIST_W - 1, ry + ROW_H - 3, index == hover && e.available() ? 0xFFA6B8BE : SLOT);
            drawText(g, Component.literal(e.name()), rx + 5, ry + 4, e.available() ? 0xFF202020 : 0xFF606060);
            drawText(g, Component.translatable("gui.robotica.warp.by", e.ownerName()), rx + 5, ry + 13, 0xFF404040);

            String where = e.distance() < 0 ? prettyDimension(e.dimension().location()) : e.distance() + " m";
            int whereW = font.width(where);
            drawText(g, Component.literal(where), rx + LIST_W - 5 - whereW, ry + 4, 0xFF303030);
            Component cost = e.available() ? Component.literal(Fmt.energy(e.cost())) : Component.translatable("gui.robotica.warp.needs_rift");
            int costColor = !e.available() ? 0xFF6A3FB0 : affordable ? 0xFF1B6E7A : 0xFFB03030;
            drawText(g, cost, rx + LIST_W - 5 - font.width(cost), ry + 13, costColor);
        }
        if (entries.size() > ROWS) {
            int trackH = ROWS * ROW_H - 2;
            int barH = Math.max(8, trackH * ROWS / entries.size());
            int barY = y + LIST_Y + (maxScroll() == 0 ? 0 : (trackH - barH) * scroll / maxScroll());
            g.fill(x + LIST_X + LIST_W + 1, y + LIST_Y, x + LIST_X + LIST_W + 4, y + LIST_Y + trackH, SLOT_DARK);
            g.fill(x + LIST_X + LIST_W + 1, barY, x + LIST_X + LIST_W + 4, barY + barH, ENERGY);
        }
        drawText(g, Component.translatable("gui.robotica.warp.pad_energy", Fmt.energy(energy), Fmt.energy(menu.capacity())), x + 8, y + LIST_Y + ROWS * ROW_H + 6, 0xFF1B4A52);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hover = rowAt(mouseX, mouseY);
        if (hover >= 0) {
            DestinationEntry e = menu.entries().get(hover);
            if (!e.available()) {
                g.renderTooltip(font, Component.translatable("gui.robotica.warp.needs_rift_tooltip"), mouseX, mouseY);
            } else if (menu.energy() < e.cost()) {
                g.renderTooltip(font, Component.translatable("gui.robotica.warp.not_enough", Fmt.energy(e.cost() - menu.energy())), mouseX, mouseY);
            } else {
                g.renderTooltip(font, Component.translatable("gui.robotica.warp.click_to_travel"), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0 && menu.entries().get(index).available()) {
                PacketDistributor.sendToServer(new WarpPayloads.Travel(menu.entries().get(index).id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    /** "minecraft:the_nether" becomes "The Nether". */
    static String prettyDimension(ResourceLocation id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.getPath().split("[_/]")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
