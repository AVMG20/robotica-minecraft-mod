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
    private static final int QUEUE_Y = 105;
    private static final int ACTION_Y = 136;
    private static final int STYLE_Y = 116;

    private static final int[] MODULE_COLORS = {0xFF8A8F98, 0xFFC9A66B, 0xFFD27A3A, 0xFF7A5A3A, 0xFF4F8FB8, 0xFF5FA85F, 0xFFB05AC0, 0xFFD0D0A0};
    private static final int[] STYLE_COLORS = {0xFF8B5E34, 0xFFC87533, 0xFFE6EEF0, 0xFF2E8F7F};
    private static final int[] MATTER_COLORS = {0xFF9A7B4F, 0xFFD08850, 0xFFB070E0};

    private int selectedPlot = -1;
    private ModuleType selectedModule = ModuleType.HALL;
    private final List<Button> planButtons = new ArrayList<>();
    private final Button[] styleButtons = new Button[4];
    private Button planTab, storageTab, clearButton, queueButton, forgetButton, cancelButton;
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
            Button b = addRenderableWidget(new FitButton(leftPos + 8 + 22 * style.ordinal(), topPos + STYLE_Y, 20, 12,
                    Component.literal(style.name().substring(0, 1)), btn -> send(ArchitectTableBlockEntity.ACTION_STYLE, style.ordinal(), 0),
                    Component.translatable(style.langKey())));
            styleButtons[style.ordinal()] = b;
            planButtons.add(b);
        }
        clearButton = addRenderableWidget(new FitButton(leftPos + 8, topPos + ACTION_Y, 80, 14, Component.empty(),
                b -> send(ArchitectTableBlockEntity.ACTION_CLEAR, menu.clearTerrain() ? 0 : 1, 0), Component.translatable("gui.robotica.architect_clear_tip")));
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
            Button b = styleButtons[style.ordinal()];
            boolean unlocked = menu.styleUnlocked(style);
            ChatFormatting color = style == selected ? ChatFormatting.GOLD : unlocked ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
            b.setMessage(Component.literal(style.name().substring(0, 1)).withStyle(color));
            b.active = unlocked;
        }
        clearButton.setMessage(Component.translatable(menu.clearTerrain() ? "gui.robotica.architect_clear_on" : "gui.robotica.architect_clear_off"));
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
            if (slot.isActive()) drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        drawStatusPanel(g, mouseX, mouseY);
        if (menu.tab == 0) drawPlan(g, mouseX, mouseY);
        else drawStorage(g);
    }

    // ---------------------------------------------------------------- right panel

    private void drawStatusPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + RIGHT_X;
        int y = topPos + 22;
        int cap = Math.max(1, menu.matterCap());
        Matter.Grade[] grades = Matter.Grade.values();
        for (int i = 0; i < 3; i++) {
            int value = menu.matter(grades[i]);
            text(g, Component.translatable(grades[i].langKey()), RIGHT_X, 22 + i * 12 + 1, 40);
            drawBar(g, x + 44, y + i * 12, 56, 9, (float) value / cap, MATTER_COLORS[i], Fmt.compact(value));
            if (inside(mouseX, mouseY, x + 44, y + i * 12, 56, 9)) {
                hoverTooltip = List.of(Component.translatable(grades[i].langKey()), Component.literal(value + " / " + cap).withStyle(ChatFormatting.GRAY));
            }
        }
        int ey = 58;
        text(g, Component.translatable("gui.robotica.architect_energy"), RIGHT_X, ey + 1, 40);
        drawBar(g, x + 44, topPos + ey, 56, 9, menu.capacity() <= 0 ? 0 : (float) menu.energy() / menu.capacity(), ENERGY, Fmt.compact(menu.energy()));
        if (inside(mouseX, mouseY, x + 44, topPos + ey, 56, 9)) {
            hoverTooltip = List.of(Component.literal(Fmt.energy(menu.energy()) + " / " + Fmt.energy(menu.capacity())));
        }
        int status = menu.status();
        Tone tone = status == ArchitectTableBlockEntity.ST_BUILDING ? Tone.GOOD
                : status >= ArchitectTableBlockEntity.ST_NO_RUSTIC && status != ArchitectTableBlockEntity.ST_UNLOADED ? Tone.BAD : Tone.WARN;
        drawStatus(g, Component.translatable("gui.robotica.architect_status_" + status), leftPos + RIGHT_X, topPos + 72, 100, 2, tone);
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
        text(g, Component.translatable("gui.robotica.architect_cost", c.rustic(), c.refined(), c.exotic()), LIST_X + 2, 121, 80);
        text(g, selectedPlot < 0 ? Component.translatable("gui.robotica.architect_no_plot")
                : Component.translatable("gui.robotica.architect_plot", Plots.px(selectedPlot), Plots.pz(selectedPlot)), GRID_X, 106, 80);

        for (ModuleType type : ModuleType.values()) {
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y + type.ordinal() * ROW;
            boolean selected = type == selectedModule;
            boolean hover = inside(mouseX, mouseY, x, y, 76, ROW - 1);
            g.fill(x, y, x + 76, y + ROW - 1, selected ? 0xFF3A6E8A : hover ? 0xFF6A6A6A : 0xFF4A4A4A);
            g.fill(x, y, x + 3, y + ROW - 1, MODULE_COLORS[type.ordinal()]);
            g.drawString(font, Component.translatable(type.langKey()), x + 6, y + 2, selected ? 0xFFFFFFFF : 0xFFD8D8D8, false);
        }

        text(g, Component.translatable("gui.robotica.architect_queue_title", menu.queueSize()), RIGHT_X, 94, 60);
        if (menu.queueSize() > 3) {
            Component more = Component.translatable("gui.robotica.architect_more", menu.queueSize() - 3);
            drawFitted(g, font, more, leftPos + RIGHT_X + 100, topPos + 94, 40, TEXT_MUTED, 1, false, 1.0F);
        }
        int shown = 0;
        for (int i = 0; i < 6; i++) {
            int[] e = menu.queueEntry(i);
            if (e == null) break;
            if (shown >= 3) break;
            shown++;
            ModuleType type = ModuleType.byId(e[1]);
            Component name = Component.translatable(type.langKey());
            String line = (i + 1) + ". " + name.getString() + " " + Plots.px(e[0]) + "," + Plots.pz(e[0]) + (e[2] == 1 ? " *" : "");
            int y = QUEUE_Y + i * 9;
            Component entry = Component.literal(i == 0 ? line + " " + Math.round(menu.progress() * 100) + "%" : line);
            drawFitted(g, font, entry, leftPos + RIGHT_X, topPos + y, 88, i == 0 ? Tone.GOOD.textColor() : TEXT, -1, false, 1.0F);
            int bx = leftPos + RIGHT_X + 90;
            boolean hover = inside(mouseX, mouseY, bx, topPos + y - 1, 10, 9);
            g.drawString(font, "x", bx + 2, topPos + y, hover ? 0xFFFF5555 : 0xFF802020, false);
            if (hover) hoverTooltip = List.of(Component.translatable("gui.robotica.architect_cancel_one"));
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
        text(g, Component.translatable("gui.robotica.architect_input"), 8, 19, 160);
        text(g, Component.translatable("gui.robotica.architect_casing"), ArchitectMenu.STYLE_X + 22, ArchitectMenu.STYLE_Y + 5, 50);
        text(g, Component.translatable("gui.robotica.architect_upgrades"), ArchitectMenu.UPGRADE_X + 42, ArchitectMenu.UPGRADE_Y + 5, 60);
        g.drawWordWrap(font, Component.translatable("gui.robotica.architect_storage_hint"), leftPos + 8, topPos + 124, 272, TEXT_MUTED);
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
                if (inside(mx, my, leftPos + RIGHT_X + 90, topPos + QUEUE_Y + i * 9 - 1, 10, 9)) {
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
