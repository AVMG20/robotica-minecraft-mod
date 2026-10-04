package com.arno.robotica.architect.client.screen;

import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.menu.ArchitectMenu;
import com.arno.robotica.architect.net.ArchitectActionPayload;
import com.arno.robotica.architect.plan.ModuleType;
import com.arno.robotica.architect.plan.PlotRecord;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
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
 * Architect Table screen, drawn procedurally. Plan tab: click a plot in the grid, pick a module, queue it.
 * Storage tab: input slots, casing slot, upgrade slots. The right side always shows matter, energy, status and the queue.
 */
public class ArchitectScreen extends MachineScreen<ArchitectMenu> {
    private static final int CELL = 16;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 22;
    private static final int LIST_X = 96;
    private static final int LIST_Y = 22;
    private static final int ROW = 12;
    private static final int RIGHT_X = 180;
    private static final int QUEUE_Y = 98;
    private static final int ACTION_Y = 136;
    private static final int STYLE_Y = 116;

    private static final int[] MODULE_COLORS = {0xFF8A8F98, 0xFFC9A66B, 0xFFD27A3A, 0xFF7A5A3A, 0xFF4F8FB8, 0xFF5FA85F, 0xFFB05AC0, 0xFFD0D0A0};
    private static final int[] STYLE_COLORS = {0xFF8B5E34, 0xFFC87533, 0xFFE6EEF0, 0xFF2E8F7F};
    private static final int[] MATTER_COLORS = {0xFF9A7B4F, 0xFFD08850, 0xFFB070E0};

    private int selectedPlot = -1;
    private ModuleType selectedModule = ModuleType.HALL;
    private final List<Button> planButtons = new ArrayList<>();
    private final Button[] styleButtons = new Button[4];
    private Button planTab, storageTab, queueButton, forgetButton, cancelButton;
    private IconButton clearButton;
    private List<Component> hoverTooltip;

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
        planButtons.clear();
        planTab = addRenderableWidget(new FitButton(leftPos + 186, topPos + 4, 46, 12, Component.translatable("gui.robotica.architect_tab_plan"), b -> menu.tab = 0));
        storageTab = addRenderableWidget(new FitButton(leftPos + 234, topPos + 4, 46, 12, Component.translatable("gui.robotica.architect_tab_storage"), b -> menu.tab = 1));
        for (BuildStyle style : BuildStyle.values()) {
            Button b = addRenderableWidget(new FitButton(leftPos + 8 + 22 * style.ordinal(), topPos + STYLE_Y, 20, 14,
                    Component.empty(), btn -> send(ArchitectTableBlockEntity.ACTION_STYLE, style.ordinal(), 0),
                    Component.translatable(style.langKey())) {
                @Override
                public void renderString(GuiGraphics g, net.minecraft.client.gui.Font f, int color) {
                    int cx = getX() + 5, cy = getY() + 4;
                    boolean sel = menu.selectedStyle() == style;
                    int chip = active ? STYLE_COLORS[style.ordinal()] : 0xFF555555;
                    if (sel) g.fill(cx - 1, cy - 1, cx + 11, cy + 7, 0xFFFFD04A);
                    g.fill(cx, cy, cx + 10, cy + 6, chip);
                    g.fill(cx, cy, cx + 10, cy + 1, 0x40FFFFFF);
                }
            });
            styleButtons[style.ordinal()] = b;
            planButtons.add(b);
        }
        clearButton = addRenderableWidget(new IconButton(leftPos + 8, topPos + ACTION_Y - 3, 20, new ItemStack(Items.IRON_PICKAXE), true,
                b -> send(ArchitectTableBlockEntity.ACTION_CLEAR, menu.clearTerrain() ? 0 : 1, 0)));
        queueButton = addRenderableWidget(new FitButton(leftPos + LIST_X, topPos + ACTION_Y, 76, 14, Component.translatable("gui.robotica.architect_queue"), b -> {
            if (selectedPlot >= 0) send(ArchitectTableBlockEntity.ACTION_QUEUE, selectedPlot, selectedModule.id());
        }));
        forgetButton = addRenderableWidget(new FitButton(leftPos + RIGHT_X, topPos + ACTION_Y, 46, 14, Component.translatable("gui.robotica.architect_forget"), b -> {
            if (selectedPlot >= 0) send(ArchitectTableBlockEntity.ACTION_FORGET, selectedPlot, 0);
        }, Component.translatable("gui.robotica.architect_forget_tip")));
        cancelButton = addRenderableWidget(new FitButton(leftPos + RIGHT_X + 48, topPos + ACTION_Y, 52, 14, Component.translatable("gui.robotica.architect_cancel"),
                b -> send(ArchitectTableBlockEntity.ACTION_CANCEL, -1, 0)));
        planButtons.add(clearButton);
        planButtons.add(queueButton);
        planButtons.add(forgetButton);
        planButtons.add(cancelButton);
    }

    private void send(int action, int a, int b) {
        PacketDistributor.sendToServer(new ArchitectActionPayload(menu.pos(), action, a, b));
    }

    private void updateWidgets() {
        boolean plan = menu.tab == 0;
        for (Button b : planButtons) b.visible = plan;
        planTab.active = !plan;
        storageTab.active = plan;
        BuildStyle selected = menu.selectedStyle();
        for (BuildStyle style : BuildStyle.values()) {
            styleButtons[style.ordinal()].active = menu.styleUnlocked(style);
        }
        clearButton.setOn(menu.clearTerrain());
        clearButton.hint(Component.translatable(menu.clearTerrain() ? "gui.robotica.architect_clear_on" : "gui.robotica.architect_clear_off")
                .append(Component.literal("\n")).append(Component.translatable("gui.robotica.architect_clear_tip").withStyle(ChatFormatting.GRAY)));
        boolean plotFree = selectedPlot >= 0 && menu.plotStatus(selectedPlot) == 0;
        queueButton.active = plotFree && menu.styleUnlocked(selected);
        forgetButton.active = selectedPlot >= 0 && menu.plotStatus(selectedPlot) == PlotRecord.BUILT;
        cancelButton.active = menu.queueSize() > 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        hoverTooltip = null;
        super.render(g, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) g.renderComponentTooltip(font, hoverTooltip, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawFitted(g, font, title, titleLabelX, titleLabelY, 170, TEXT, -1, false, 1.0F);
        drawFitted(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 120, TEXT, -1, false, 1.0F);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) {
            if (!slot.isActive()) continue;
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (!slot.hasItem() && ghostIcon(slot) != ItemStack.EMPTY) drawGhost(g, ghostIcon(slot), leftPos + slot.x, topPos + slot.y);
        }
        drawStatusPanel(g, mouseX, mouseY);
        if (menu.tab == 0) drawPlan(g, mouseX, mouseY);
        else drawStorage(g);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 27) return icon("iron_casing");
        if (slot.index == 28 || slot.index == 29) return icon("upgrade_speed_1");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index < 27) return Component.translatable("gui.robotica.architect_storage_hint");
        if (slot.index == 27) return Component.translatable("gui.robotica.architect_casing");
        if (slot.index < 30) return Component.translatable("gui.robotica.slot_upgrade");
        return null;
    }

    // ---------------------------------------------------------------- right panel

    private void drawStatusPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + RIGHT_X;
        int y = topPos + 22;
        int cap = Math.max(1, menu.matterCap());
        Matter.Grade[] grades = Matter.Grade.values();
        for (int i = 0; i < 3; i++) {
            int value = menu.matter(grades[i]);
            swatch(g, x, y + i * 12 + 1, MATTER_COLORS[i]);
            drawBar(g, x + 14, y + i * 12, 86, 9, (float) value / cap, MATTER_COLORS[i], Fmt.compact(value));
            if (inside(mouseX, mouseY, x, y + i * 12, 100, 9)) {
                hoverTooltip = List.of(Component.translatable(grades[i].langKey()), Component.literal(value + " / " + cap).withStyle(ChatFormatting.GRAY));
            }
        }
        int ey = 58;
        swatch(g, x, topPos + ey + 1, ENERGY);
        drawBar(g, x + 14, topPos + ey, 86, 9, menu.capacity() <= 0 ? 0 : (float) menu.energy() / menu.capacity(), ENERGY, Fmt.compact(menu.energy()));
        if (inside(mouseX, mouseY, x, topPos + ey, 100, 9)) {
            hoverTooltip = List.of(Component.translatable("gui.robotica.architect_energy"),
                    Component.literal(Fmt.energy(menu.energy()) + " / " + Fmt.energy(menu.capacity())).withStyle(ChatFormatting.GRAY));
        }
        int status = menu.status();
        Tone tone = status == ArchitectTableBlockEntity.ST_BUILDING ? Tone.GOOD
                : status >= ArchitectTableBlockEntity.ST_NO_RUSTIC && status != ArchitectTableBlockEntity.ST_UNLOADED ? Tone.BAD : Tone.WARN;
        drawStatus(g, Component.translatable("gui.robotica.architect_status_" + status), leftPos + RIGHT_X, topPos + 74, 100, 1, tone);
        if (status >= ArchitectTableBlockEntity.ST_NO_RUSTIC && inside(mouseX, mouseY, x, topPos + 72, 100, 12)) {
            hoverTooltip = List.of(Component.translatable("gui.robotica.architect_status_tip_" + status));
        }
    }

    /** Small coloured square standing in for a label. */
    private static void swatch(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 9, y + 9, 0xFF373737);
        g.fill(x + 1, y + 1, x + 8, y + 8, color);
        g.fill(x + 1, y + 1, x + 8, y + 2, 0x40FFFFFF);
    }

    /** Label relative to the GUI corner, fitted into maxWidth. */
    private void text(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        drawFitted(g, font, text, leftPos + x, topPos + y, maxWidth, TEXT, -1, false, 1.0F);
    }

    /** Matter / energy bar: framed, gradient fill, the value drawn in the bar. */
    private void drawBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color, String text) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0xFF2B2B2B);
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + h, blend(color, 0xFFFFFFFF, 0.3F), blend(color, 0xFF000000, 0.2F));
        }
        g.drawString(font, text, x + 3, y + 1, 0xFFFFFFFF, true);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- plan tab

    private void drawPlan(GuiGraphics g, int mouseX, int mouseY) {
        int[] current = menu.queueEntry(0);
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            int x = leftPos + GRID_X + (plot % Plots.GRID) * CELL;
            int y = topPos + GRID_Y + (plot / Plots.GRID) * CELL;
            int status = menu.plotStatus(plot);
            int module = menu.plotModule(plot);
            boolean hover = inside(mouseX, mouseY, x, y, CELL, CELL);
            int border = status == 0 ? 0xFF222222 : STYLE_COLORS[menu.plotStyle(plot).ordinal()];
            g.fill(x, y, x + CELL, y + CELL, border);
            int base = status == 0 ? (hover ? 0xFF6A6A6A : 0xFF4A4A4A) : module == 0 ? 0xFF4A4A4A : MODULE_COLORS[module - 1];
            if (status == PlotRecord.QUEUED) base = blend(base, 0xFF303030, 0.55F);
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, base);
            if (status == PlotRecord.BUILDING && current != null && current[0] == plot) {
                int h = Math.round((CELL - 2) * menu.progress());
                g.fill(x + 1, y + CELL - 1 - h, x + CELL - 1, y + CELL - 1, 0x80FFFFFF);
            }
            if (module != 0) {
                String letter = Component.translatable(ModuleType.byId(module).langKey()).getString().substring(0, 1);
                g.drawString(font, letter, x + (CELL - font.width(letter)) / 2, y + 4, status == PlotRecord.BUILT ? 0xFF202020 : 0xFFFFFFFF, false);
            }
            if (plot == Plots.CENTER) g.fill(x + 6, y + 6, x + 10, y + 10, 0xFF5CC8D8);
            if (plot == selectedPlot) {
                g.fill(x - 1, y - 1, x + CELL + 1, y, 0xFFFFFFFF);
                g.fill(x - 1, y + CELL, x + CELL + 1, y + CELL + 1, 0xFFFFFFFF);
                g.fill(x - 1, y, x, y + CELL, 0xFFFFFFFF);
                g.fill(x + CELL, y, x + CELL + 1, y + CELL, 0xFFFFFFFF);
            }
            if (hover) hoverTooltip = plotTooltip(plot);
        }
        Matter c = menu.selectedStyle().cost;
        int[] costs = {c.rustic(), c.refined(), c.exotic()};
        for (int i = 0; i < 3; i++) {
            swatch(g, leftPos + LIST_X + 1 + i * 26, topPos + 121, MATTER_COLORS[i]);
            g.drawString(font, Integer.toString(costs[i]), leftPos + LIST_X + 12 + i * 26, topPos + 122, 0xFF404040, false);
        }
        if (inside(mouseX, mouseY, leftPos + LIST_X, topPos + 120, 80, 11)) {
            hoverTooltip = List.of(Component.translatable("gui.robotica.architect_cost_tip", c.rustic(), c.refined(), c.exotic()));
        }

        for (ModuleType type : ModuleType.values()) {
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y + type.ordinal() * ROW;
            boolean selected = type == selectedModule;
            boolean hover = inside(mouseX, mouseY, x, y, 76, ROW - 1);
            g.fill(x, y, x + 76, y + ROW - 1, selected ? 0xFF3A6E8A : hover ? 0xFF6A6A6A : 0xFF4A4A4A);
            g.fill(x, y, x + 3, y + ROW - 1, MODULE_COLORS[type.ordinal()]);
            g.drawString(font, Component.translatable(type.langKey()), x + 6, y + 2, selected ? 0xFFFFFFFF : 0xFFD8D8D8, false);
        }

        g.fill(leftPos + RIGHT_X, topPos + QUEUE_Y - 5, leftPos + RIGHT_X + 100, topPos + QUEUE_Y - 4, 0xFF8B8B8B);
        int shown = 0;
        for (int i = 0; i < 6; i++) {
            int[] e = menu.queueEntry(i);
            if (e == null) break;
            if (shown >= 3) break;
            shown++;
            ModuleType type = ModuleType.byId(e[1]);
            int y = QUEUE_Y + i * 10;
            g.fill(leftPos + RIGHT_X, topPos + y, leftPos + RIGHT_X + 7, topPos + y + 8, MODULE_COLORS[type.ordinal()]);
            Component name = Component.translatable(type.langKey());
            Component entry = i == 0 ? Component.empty().append(name).append(" " + Math.round(menu.progress() * 100) + "%") : name;
            drawFitted(g, font, entry, leftPos + RIGHT_X + 10, topPos + y, 78, i == 0 ? Tone.GOOD.textColor() : TEXT, -1, false, 1.0F);
            int bx = leftPos + RIGHT_X + 90;
            boolean hover = inside(mouseX, mouseY, bx, topPos + y - 1, 10, 10);
            g.drawString(font, "x", bx + 2, topPos + y, hover ? 0xFFFF5555 : 0xFF802020, false);
            if (inside(mouseX, mouseY, leftPos + RIGHT_X, topPos + y - 1, 90, 10)) {
                hoverTooltip = List.of(name, Component.translatable("gui.robotica.architect_plot", Plots.px(e[0]), Plots.pz(e[0])).withStyle(ChatFormatting.GRAY));
            }
            if (hover) hoverTooltip = List.of(Component.translatable("gui.robotica.architect_cancel_one"));
        }
        if (menu.queueSize() > 3) {
            drawFitted(g, font, Component.translatable("gui.robotica.architect_more", menu.queueSize() - 3), leftPos + RIGHT_X + 100, topPos + QUEUE_Y + 30, 40, TEXT_MUTED, 1, false, 1.0F);
        }
    }

    private List<Component> plotTooltip(int plot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.robotica.architect_plot", Plots.px(plot), Plots.pz(plot)));
        int module = menu.plotModule(plot);
        if (module == 0) {
            lines.add(Component.translatable(plot == Plots.CENTER ? "gui.robotica.architect_plot_table" : "gui.robotica.architect_plot_free").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(ModuleType.byId(module).langKey()).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable(menu.plotStyle(plot).langKey()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.robotica.architect_plot_status_" + menu.plotStatus(plot)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    // ---------------------------------------------------------------- storage tab

    private void drawStorage(GuiGraphics g) {
        // Nothing but slots: ghost icons and hover hints say what goes where.
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.tab == 0 && button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            int gx = mx - leftPos - GRID_X;
            int gy = my - topPos - GRID_Y;
            if (gx >= 0 && gy >= 0 && gx < CELL * Plots.GRID && gy < CELL * Plots.GRID) {
                selectedPlot = gy / CELL * Plots.GRID + gx / CELL;
                click();
                return true;
            }
            int ly = my - topPos - LIST_Y;
            if (mx >= leftPos + LIST_X && mx < leftPos + LIST_X + 76 && ly >= 0 && ly < ROW * ModuleType.values().length) {
                selectedModule = ModuleType.values()[ly / ROW];
                click();
                return true;
            }
            for (int i = 0; i < 3 && menu.queueEntry(i) != null; i++) {
                if (inside(mx, my, leftPos + RIGHT_X + 90, topPos + QUEUE_Y + i * 10 - 1, 10, 10)) {
                    send(ArchitectTableBlockEntity.ACTION_CANCEL, i, 0);
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
