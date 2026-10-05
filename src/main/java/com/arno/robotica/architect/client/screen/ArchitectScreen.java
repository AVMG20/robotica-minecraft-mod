package com.arno.robotica.architect.client.screen;

import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.menu.ArchitectMenu;
import com.arno.robotica.architect.net.ArchitectActionPayload;
import com.arno.robotica.architect.plan.Layout;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Architect Table screen, drawn procedurally and almost without text. Left: the 5x5 plot grid (click a plot to queue or
 * unqueue it, click the edge of a planned plot to put a door there, shift-click a built plot to forget it). Right:
 * status, matter and energy bars, progress, Build and Cancel. Below: casing slot, style chips, upgrade slots, clear terrain,
 * the material input and the player inventory. Tooltips carry the words.
 */
public class ArchitectScreen extends MachineScreen<ArchitectMenu> {
    private static final int GX = ArchitectMenu.GRID_X;
    private static final int GY = ArchitectMenu.GRID_Y;
    private static final int CELL = ArchitectMenu.CELL;
    private static final int RX = 104;
    private static final int RW = 66;
    /** Clicks this close to the edge of a planned plot toggle a door on that side. */
    private static final int EDGE = 4;

    public static final int[] STYLE_COLORS = {0xFFB98550, 0xFFD8813F, 0xFFDCE6EA, 0xFF2FB8A0};
    private static final int[] MATTER_COLORS = {0xFF9A7B4F, 0xFFD08850, 0xFFB070E0};
    private static final int GRID_BG = 0xFF2B2B2B;
    private static final int CELL_EMPTY = 0xFF4A4A4A;
    private static final int CELL_HOVER = 0xFF6A6A6A;
    private static final int DOOR = 0xFFFFE9A8;

    private final StyleChip[] chips = new StyleChip[BuildStyle.values().length];
    private Button buildButton, cancelButton;
    private IconButton clearButton;

    public ArchitectScreen(ArchitectMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = ArchitectMenu.WIDTH;
        this.imageHeight = ArchitectMenu.HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = ArchitectMenu.INV_X;
        this.inventoryLabelY = ArchitectMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        for (BuildStyle style : BuildStyle.values()) {
            chips[style.ordinal()] = addRenderableWidget(new StyleChip(leftPos + ArchitectMenu.CHIPS_X + 18 * style.ordinal(),
                    topPos + ArchitectMenu.SLOT_ROW_Y - 1, style));
        }
        buildButton = addRenderableWidget(new FitButton(leftPos + RX, topPos + 88, 32, 16, Component.translatable("gui.robotica.architect_build"),
                b -> send(ArchitectTableBlockEntity.ACTION_BUILD, 0, 0), Component.translatable("gui.robotica.architect_build_tip")));
        cancelButton = addRenderableWidget(new FitButton(leftPos + RX + 34, topPos + 88, 32, 16, Component.translatable("gui.robotica.architect_cancel"),
                b -> send(ArchitectTableBlockEntity.ACTION_CANCEL, 0, 0), Component.translatable("gui.robotica.architect_cancel_tip")));
        clearButton = addRenderableWidget(new IconButton(leftPos + ArchitectMenu.CLEAR_X, topPos + ArchitectMenu.SLOT_ROW_Y - 1, 18,
                new ItemStack(Items.IRON_PICKAXE), true, b -> send(ArchitectTableBlockEntity.ACTION_CLEAR, menu.clearTerrain() ? 0 : 1, 0)));
    }

    private void send(int action, int a, int b) {
        PacketDistributor.sendToServer(new ArchitectActionPayload(menu.pos(), action, a, b));
    }

    private boolean lastClear;
    private boolean clearHinted;

    private void updateWidgets() {
        buildButton.active = menu.hasWork() && !menu.running();
        cancelButton.active = menu.hasQueued();
        boolean clear = menu.clearTerrain();
        clearButton.setOn(clear);
        if (!clearHinted || clear != lastClear) {
            clearHinted = true;
            lastClear = clear;
            clearButton.hint(Component.translatable(clear ? "gui.robotica.architect_clear_on" : "gui.robotica.architect_clear_off")
                    .append(Component.literal("\n")).append(Component.translatable("gui.robotica.architect_clear_tip").withStyle(ChatFormatting.GRAY)));
        }
        for (StyleChip chip : chips) chip.update();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected int titleMaxWidth() {
        return imageWidth - 16;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        int casing = ArchitectTableBlockEntity.INPUT_SLOTS;
        if (slot.index == casing) return icon("iron_casing");
        if (slot.index == casing + 1 || slot.index == casing + 2) return icon("upgrade_speed");
        if (slot.index == casing + 3) return icon("mainspring");
        if (slot.index < casing) return slot.index == 0 ? new ItemStack(Items.COBBLESTONE) : ItemStack.EMPTY;
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        int casing = ArchitectTableBlockEntity.INPUT_SLOTS;
        if (slot.index < casing) return Component.translatable("gui.robotica.architect_storage_hint");
        if (slot.index == casing) return Component.translatable("gui.robotica.architect_casing");
        if (slot.index < casing + 3) return Component.translatable("gui.robotica.slot_upgrade");
        if (slot.index == casing + 3) return Component.translatable("gui.robotica.architect_battery");
        return null;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawGrid(g, mouseX, mouseY);
        drawSide(g, x + RX, y);
    }

    private void drawSide(GuiGraphics g, int rx, int top) {
        int status = menu.status();
        Tone tone = status == ArchitectTableBlockEntity.ST_BUILDING ? Tone.GOOD
                : status == ArchitectTableBlockEntity.ST_IDLE || status == ArchitectTableBlockEntity.ST_READY || status == ArchitectTableBlockEntity.ST_UNLOADED
                ? Tone.WARN : Tone.BAD;
        drawStatus(g, Component.translatable("gui.robotica.architect_status_" + status), rx, top + 18, RW, 1, tone);
        if (status >= ArchitectTableBlockEntity.ST_NO_RUSTIC && status <= ArchitectTableBlockEntity.ST_LOCKED) {
            addTooltip(rx, top + 17, RW, 10, Component.translatable("gui.robotica.architect_status_tip_" + status));
        }

        int cap = Math.max(1, menu.matterCap());
        Matter.Grade[] grades = Matter.Grade.values();
        for (int i = 0; i < grades.length; i++) {
            int by = top + 30 + i * 8;
            int value = menu.matter(grades[i]);
            int color = MATTER_COLORS[i];
            drawMatterBar(g, rx, by, RW, 5, (float) value / cap, color);
            addTooltip(rx - 1, by - 1, RW + 2, 8, Component.translatable(grades[i].langKey()).withStyle(s -> s.withColor(color & 0xFFFFFF)),
                    Component.literal(value + " / " + cap).withStyle(ChatFormatting.GRAY));
        }
        drawEnergyBarWide(g, rx, top + 60, 42, 6, menu.energy(), menu.capacity());

        int current = menu.currentPlot();
        drawProgress(g, rx, top + 79, RW, 4, current >= 0 ? menu.progress() : 0);
        if (current >= 0) {
            addTooltip(rx - 1, top + 77, RW + 2, 8, Component.translatable("gui.robotica.architect_progress", Math.round(menu.progress() * 100)));
        }
    }

    private void drawMatterBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, blend(color, 0xFF000000, 0.7F));
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) g.fillGradient(x, y, x + filled, y + h, blend(color, 0xFFFFFFFF, 0.3F), blend(color, 0xFF000000, 0.25F));
    }

    private void drawGrid(GuiGraphics g, int mouseX, int mouseY) {
        int gx0 = leftPos + GX, gy0 = topPos + GY;
        int size = CELL * Plots.GRID;
        drawInset(g, gx0, gy0, size, size);
        g.fill(gx0, gy0, gx0 + size, gy0 + size, GRID_BG);
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0);
        int hoverPlot = plotAt(mouseX, mouseY);
        int hoverDoor = hoverPlot >= 0 ? doorSideAt(hoverPlot, mouseX, mouseY) : -1;
        int current = menu.currentPlot();

        for (int plot = 0; plot < Plots.COUNT; plot++) {
            int x = cellX(plot), y = cellY(plot);
            int state = menu.plotState(plot);
            boolean hover = plot == hoverPlot && hoverDoor < 0;
            if (state == Layout.EMPTY) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, hover ? CELL_HOVER : CELL_EMPTY);
                continue;
            }
            int color = STYLE_COLORS[menu.plotStyle(plot).ordinal()];
            // Planned neighbours merge with this cell across the 2 px gap, so a group reads as one hall.
            int l = x + (joined(plot, Plots.W) ? 0 : 1), r = x + CELL - (joined(plot, Plots.E) ? 0 : 1);
            int t = y + (joined(plot, Plots.N) ? 0 : 1), b = y + CELL - (joined(plot, Plots.S) ? 0 : 1);
            if (state == Layout.BUILT) {
                int fill = hover ? blend(color, 0xFFFFFFFF, 0.2F) : color;
                g.fill(l, t, r, b, fill);
                if (!joined(plot, Plots.N)) g.fill(l, t, r, t + 1, blend(color, 0xFFFFFFFF, 0.45F));
                if (!joined(plot, Plots.S)) g.fill(l, b - 1, r, b, blend(color, 0xFF000000, 0.35F));
                if (menu.plotNeedsWork(plot) && plot != current) g.fill(x + 7, y + 7, x + 11, y + 11, blend(0xFFFFFFFF, color, pulse * 0.6F));
            } else {
                g.fill(l, t, r, b, hover ? 0xFF3C3C3C : 0xFF303030);
                if (!joined(plot, Plots.N)) g.fill(l, t, r, t + 2, color);
                if (!joined(plot, Plots.S)) g.fill(l, b - 2, r, b, color);
                if (!joined(plot, Plots.W)) g.fill(l, t, l + 2, b, color);
                if (!joined(plot, Plots.E)) g.fill(r - 2, t, r, b, color);
            }
            if (plot == current) {
                int a = Math.round(40 + 140 * pulse);
                g.fill(l, t, r, b, (a << 24) | (color & 0xFFFFFF));
                int h = Math.round((CELL - 4) * menu.progress());
                g.fill(x + 2, y + CELL - 2 - h, x + 4, y + CELL - 2, 0xC0FFFFFF);
            }
        }
        // Doors and the hovered door slot on top of the cells.
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            if (!menu.planned(plot)) continue;
            int doors = menu.plotDoors(plot);
            for (int side = 0; side < 4; side++) {
                if (joined(plot, side)) continue;
                boolean on = (doors & Plots.bit(side)) != 0;
                boolean hovered = plot == hoverPlot && side == hoverDoor;
                if (on || hovered) drawDoor(g, plot, side, on ? (hovered ? 0xFFFFFFFF : DOOR) : 0xA0FFFFFF);
            }
        }
        // The table: middle of the centre plot.
        int cx = cellX(Plots.CENTER) + CELL / 2, cy = cellY(Plots.CENTER) + CELL / 2;
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF1B4A52);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, ENERGY);

        if (hoverPlot >= 0) plotTooltip(hoverPlot, hoverDoor);
    }

    private void drawDoor(GuiGraphics g, int plot, int side, int color) {
        int x = cellX(plot), y = cellY(plot);
        int m = CELL / 2;
        switch (side) {
            case Plots.N -> g.fill(x + m - 3, y, x + m + 3, y + 3, color);
            case Plots.S -> g.fill(x + m - 3, y + CELL - 3, x + m + 3, y + CELL, color);
            case Plots.W -> g.fill(x, y + m - 3, x + 3, y + m + 3, color);
            default -> g.fill(x + CELL - 3, y + m - 3, x + CELL, y + m + 3, color);
        }
    }

    private void plotTooltip(int plot, int doorSide) {
        int x = cellX(plot), y = cellY(plot);
        List<Component> lines = new ArrayList<>();
        int state = menu.plotState(plot);
        if (doorSide >= 0) {
            boolean on = (menu.plotDoors(plot) & Plots.bit(doorSide)) != 0;
            lines.add(Component.translatable(on ? "gui.robotica.architect_door_remove" : "gui.robotica.architect_door_add"));
        } else if (state == Layout.EMPTY) {
            lines.add(Component.translatable(plot == Plots.CENTER ? "gui.robotica.architect_plot_table" : "gui.robotica.architect_plot_free"));
            lines.add(Component.translatable(menu.selectedStyle().langKey()).withStyle(s -> s.withColor(STYLE_COLORS[menu.selectedStyle().ordinal()] & 0xFFFFFF)));
        } else {
            BuildStyle style = menu.plotStyle(plot);
            lines.add(Component.translatable(style.langKey()).withStyle(s -> s.withColor(STYLE_COLORS[style.ordinal()] & 0xFFFFFF)));
            String key = plot == menu.currentPlot() ? "building" : state == Layout.QUEUED ? "queued" : menu.plotNeedsWork(plot) ? "changed" : "built";
            lines.add(Component.translatable("gui.robotica.architect_plot_" + key).withStyle(ChatFormatting.GRAY));
            if (state == Layout.BUILT) lines.add(Component.translatable("gui.robotica.architect_plot_forget").withStyle(ChatFormatting.DARK_GRAY));
        }
        addTooltip(x, y, CELL, CELL, lines.toArray(Component[]::new));
    }

    // ---------------------------------------------------------------- geometry

    private int cellX(int plot) {
        return leftPos + GX + (Plots.px(plot) + Plots.RADIUS) * CELL;
    }

    private int cellY(int plot) {
        return topPos + GY + (Plots.pz(plot) + Plots.RADIUS) * CELL;
    }

    private int plotAt(double mouseX, double mouseY) {
        int gx = (int) Math.floor(mouseX) - leftPos - GX;
        int gy = (int) Math.floor(mouseY) - topPos - GY;
        if (gx < 0 || gy < 0 || gx >= CELL * Plots.GRID || gy >= CELL * Plots.GRID) return -1;
        return Plots.index(gx / CELL - Plots.RADIUS, gy / CELL - Plots.RADIUS);
    }

    /** Side whose door zone the mouse is over: only outside edges of planned plots. -1 for none. */
    private int doorSideAt(int plot, double mouseX, double mouseY) {
        if (!menu.planned(plot)) return -1;
        int lx = (int) Math.floor(mouseX) - cellX(plot);
        int ly = (int) Math.floor(mouseY) - cellY(plot);
        int[] dist = {ly, CELL - 1 - lx, CELL - 1 - ly, lx};
        int best = -1;
        for (int side = 0; side < 4; side++) {
            if (dist[side] >= EDGE || joined(plot, side)) continue;
            if (best < 0 || dist[side] < dist[best]) best = side;
        }
        return best;
    }

    private boolean joined(int plot, int side) {
        int n = Plots.neighbour(plot, side);
        return n >= 0 && menu.planned(n) && menu.planned(plot);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int plot = button == 0 ? plotAt(mouseX, mouseY) : -1;
        if (plot >= 0) {
            int door = doorSideAt(plot, mouseX, mouseY);
            if (door >= 0) send(ArchitectTableBlockEntity.ACTION_DOOR, plot, door);
            else send(ArchitectTableBlockEntity.ACTION_TOGGLE, plot, hasShiftDown() ? 1 : 0);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /** Style chip: the style's wall block as the icon, a gold frame when selected, dimmed when locked. */
    private final class StyleChip extends Button {
        private final BuildStyle style;
        private final ItemStack icon;
        private int shown = -1;

        StyleChip(int x, int y, BuildStyle style) {
            super(x, y, 18, 18, Component.translatable(style.langKey()), b -> send(ArchitectTableBlockEntity.ACTION_STYLE, style.ordinal(), 0), DEFAULT_NARRATION);
            this.style = style;
            this.icon = new ItemStack(ArchitectRegistry.styleItem(style, Role.WALL).get());
        }

        void update() {
            active = menu.styleUnlocked(style);
            int key = (active ? 1 : 0) | menu.baseEnergy() << 1;
            if (key == shown) return;
            shown = key;
            Matter c = style.cost;
            Component tip = Component.translatable(style.langKey()).withStyle(s -> s.withColor(STYLE_COLORS[style.ordinal()] & 0xFFFFFF))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("gui.robotica.architect_cost_tip", c.rustic(), c.refined(), c.exotic(),
                            style.energyPerBlock(menu.baseEnergy())).withStyle(ChatFormatting.GRAY));
            if (!active) tip = tip.copy().append(Component.literal("\n"))
                    .append(Component.translatable("gui.robotica.architect_needs_casing_" + style.casingLevel).withStyle(ChatFormatting.RED));
            setTooltip(Tooltip.create(tip));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY();
            boolean selected = menu.selectedStyle() == style;
            g.fill(x, y, x + 18, y + 18, selected ? 0xFFFFD04A : isHoveredOrFocused() && active ? 0xFFFFFFFF : SLOT_DARK);
            g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
            g.renderItem(icon, x + 1, y + 1);
            if (!active) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x + 1, y + 1, x + 17, y + 17, 0xB0303030);
                g.pose().popPose();
            }
        }
    }
}
