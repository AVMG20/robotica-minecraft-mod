package com.arno.robotica.core.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.side.RelativeSide;
import com.arno.robotica.core.side.SideConfig;
import com.arno.robotica.core.side.SideConfigPayload;
import com.arno.robotica.core.side.SideMode;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally drawn machine GUI (no textures). Draws the panel and a frame behind every slot.
 * Subclasses draw extras in {@link #renderMachine} with the helpers below: {@link #drawLabel} (fitted section labels),
 * {@link #drawStatus} (coloured dot plus text), {@link #drawEnergyBar} (framed bar with FE readout and hover tooltip),
 * {@link #drawProgress} and {@link #drawArrow}. All helper coordinates are absolute screen coordinates.
 * Layout rules: 8 px margins, section labels in {@link #TEXT}, nothing but slots below y = 70 on a 166 px high panel
 * (the "Inventory" label sits at y = 72).
 * Menus that call {@code MachineMenu.trackSides} get a side config tab on the right edge of the panel, drawn here.
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
    public static final int ENERGY_TOP = 0xFF9BF0F8;
    public static final int ENERGY_BOTTOM = 0xFF2594A8;
    public static final int PROGRESS = 0xFFE8A060;
    public static final int PROGRESS_TOP = 0xFFF7C282;
    public static final int PROGRESS_BOTTOM = 0xFFD2812F;

    /** Section label gray (same as the vanilla title colour). */
    public static final int TEXT = 0xFF404040;
    public static final int TEXT_MUTED = 0xFF6E6E6E;

    /** Status colours: green working, amber idle or waiting, red problem. */
    public enum Tone {
        GOOD(0xFF3DBE5A, 0xFF1E6B2A),
        WARN(0xFFE8A020, 0xFF8A5600),
        BAD(0xFFD83A30, 0xFFA01818);

        final int dot;
        final int text;

        Tone(int dot, int text) {
            this.dot = dot;
            this.text = text;
        }

        public int textColor() {
            return text;
        }
    }

    private final List<int[]> energyBars = new ArrayList<>();
    private final List<long[]> energyValues = new ArrayList<>();
    private final List<Component> energyExtra = new ArrayList<>();
    private final List<int[]> tipRects = new ArrayList<>();
    private final List<List<Component>> tipLines = new ArrayList<>();

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
        energyExtra.clear();
        tipRects.clear();
        tipLines.clear();
        drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            if (!slot.hasItem() && isMachineSlot(slot)) {
                ItemStack ghost = ghostIcon(slot);
                if (!ghost.isEmpty()) drawGhost(g, ghost, leftPos + slot.x, topPos + slot.y);
            }
        }
        renderMachine(g, leftPos, topPos, mouseX, mouseY);
        renderSideTab(g);
    }

    /** True for machine slots, false for the player's inventory. */
    protected static boolean isMachineSlot(Slot slot) {
        return !(slot.container instanceof Inventory);
    }

    /** Faint icon shown in an empty slot to say what goes in it. Return {@link ItemStack#EMPTY} for none. */
    protected ItemStack ghostIcon(Slot slot) {
        return ItemStack.EMPTY;
    }

    /** Tooltip of an empty slot (name of what goes in it), or null. */
    protected Component slotHint(Slot slot) {
        return null;
    }

    /** A Robotica item by registry name, for ghost icons. */
    protected static ItemStack icon(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id(name)));
    }

    /** Draws the item faded, like a ghost, at slot position x/y. */
    public static void drawGhost(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + 16, y + 16, 0xB88B8B8B);
        g.pose().popPose();
    }

    /** Hover tooltip for a rectangle (absolute coordinates). Call from renderMachine. */
    protected void addTooltip(int x, int y, int w, int h, Component... lines) {
        tipRects.add(new int[]{x, y, w, h});
        tipLines.add(List.of(lines));
    }

    /** Draw machine-specific parts. x/y are the GUI's top-left corner. */
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
    }

    /** Widest the title may get before it is shrunk. Override when something sits right of the title. */
    protected int titleMaxWidth() {
        return imageWidth - 16;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawFitted(g, font, title, titleLabelX, titleLabelY, titleMaxWidth(), TEXT, -1, false, 1.0F);
        drawFitted(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth - 16, TEXT, -1, false, 1.0F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && isMachineSlot(hoveredSlot) && menu.getCarried().isEmpty()) {
            Component hint = slotHint(hoveredSlot);
            if (hint != null) g.renderTooltip(font, font.split(hint, 200), mouseX, mouseY);
        }
        for (int i = 0; i < tipRects.size(); i++) {
            int[] r = tipRects.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                g.renderComponentTooltip(font, tipLines.get(i), mouseX, mouseY);
            }
        }
        for (int i = 0; i < energyBars.size(); i++) {
            int[] r = energyBars.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                long[] v = energyValues.get(i);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(Fmt.energy(v[0]) + " / " + Fmt.energy(v[1])));
                if (energyExtra.get(i) != null) lines.add(energyExtra.get(i));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    // ---------------------------------------------------------------- side config tab

    /** Open or closed, shared by every machine screen so it stays the way the player left it. */
    private static boolean sideTabOpen;
    private static final int TAB_CLOSED = 22, TAB_W = 64, TAB_H = 94, CELL = 14, GRID_X = 11, GRID_Y = 22, TOGGLE_Y = 68;
    /** Grid position (column, row) of each face: Thermal's layout, the front in the middle. */
    private static final int[][] CELLS = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};

    @Nullable
    private SideConfig sideConfig() {
        return menu instanceof MachineMenu machine ? machine.sides() : null;
    }

    /** The tab's rectangle (absolute), or null when this machine has no side config. */
    @Nullable
    private Rect2i sideTab() {
        if (sideConfig() == null) return null;
        int w = sideTabOpen ? TAB_W : TAB_CLOSED, h = sideTabOpen ? TAB_H : TAB_CLOSED;
        return new Rect2i(leftPos + imageWidth, topPos + 4, w, h);
    }

    /** Areas outside the panel this screen draws on (for JEI, so it does not cover them). */
    public List<Rect2i> extraAreas() {
        Rect2i tab = sideTab();
        return tab == null ? List.of() : List.of(tab);
    }

    private void renderSideTab(GuiGraphics g) {
        SideConfig sides = sideConfig();
        Rect2i tab = sideTab();
        if (sides == null || tab == null) return;
        int x = tab.getX(), y = tab.getY();
        drawPanel(g, x - 3, y, tab.getWidth() + 3, tab.getHeight());
        g.fill(x - 3, y + 1, x, y + tab.getHeight() - 3, PANEL);
        g.renderItem(new ItemStack(Items.HOPPER), x + 2, y + 3);
        if (!sideTabOpen) {
            addTooltip(x, y, TAB_CLOSED, TAB_CLOSED, Component.translatable("gui.robotica.sides"));
            return;
        }
        drawFitted(g, font, Component.translatable("gui.robotica.sides"), x + 20, y + 7, TAB_W - 24, TEXT, -1, false, 1.0F);
        for (RelativeSide side : RelativeSide.values()) {
            int cx = x + GRID_X + CELLS[side.ordinal()][0] * CELL, cy = y + GRID_Y + CELLS[side.ordinal()][1] * CELL;
            SideMode mode = sides.mode(side);
            drawInset(g, cx, cy, CELL - 2, CELL - 2);
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, mode.color);
            g.fill(cx, cy, cx + CELL - 2, cy + 1, 0x50FFFFFF);
            if (side == RelativeSide.FRONT) g.fill(cx + 4, cy + 4, cx + 8, cy + 8, 0xC0FFFFFF);
            addTooltip(cx, cy, CELL - 2, CELL - 2,
                    Component.translatable(side.translationKey()).append(": ").append(Component.translatable(mode.translationKey())),
                    Component.translatable("gui.robotica.sides.click").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        drawToggle(g, x + 6, y + TOGGLE_Y, sides.autoInput(), "gui.robotica.sides.auto_input");
        drawToggle(g, x + 6, y + TOGGLE_Y + 12, sides.autoEject(), "gui.robotica.sides.auto_eject");
    }

    private void drawToggle(GuiGraphics g, int x, int y, boolean on, String key) {
        drawInset(g, x, y, 8, 8);
        g.fill(x, y, x + 8, y + 8, on ? Tone.GOOD.dot : SLOT);
        if (on) g.fill(x + 2, y + 2, x + 6, y + 6, 0xFFFFFFFF);
        drawFitted(g, font, Component.translatable(key), x + 11, y, TAB_W - 20, TEXT, -1, false, 0.75F);
        addTooltip(x - 1, y - 1, TAB_W - 8, 10, Component.translatable(key + ".hint"));
    }

    /** Side tab click: header opens or closes it, faces cycle (right-click backwards), boxes toggle. */
    private boolean clickSideTab(double mouseX, double mouseY, int button) {
        SideConfig sides = sideConfig();
        Rect2i tab = sideTab();
        if (sides == null || tab == null || !tab.contains((int) mouseX, (int) mouseY)) return false;
        int mx = (int) mouseX - tab.getX(), my = (int) mouseY - tab.getY();
        int action = -1;
        if (!sideTabOpen || my < GRID_Y - 2) {
            sideTabOpen = !sideTabOpen;
            playClick();
            return true;
        }
        for (RelativeSide side : RelativeSide.values()) {
            int cx = GRID_X + CELLS[side.ordinal()][0] * CELL, cy = GRID_Y + CELLS[side.ordinal()][1] * CELL;
            if (mx >= cx - 1 && mx < cx + CELL - 1 && my >= cy - 1 && my < cy + CELL - 1) {
                action = button == 1 ? SideConfig.ACTION_PREVIOUS + side.ordinal() : side.ordinal();
            }
        }
        if (my >= TOGGLE_Y - 2 && my < TOGGLE_Y + 10) action = SideConfig.ACTION_AUTO_INPUT;
        else if (my >= TOGGLE_Y + 10 && my < TOGGLE_Y + 22) action = SideConfig.ACTION_AUTO_EJECT;
        if (action >= 0 && menu instanceof MachineMenu machine && minecraft != null && minecraft.player != null
                && !machine.mayConfigure(minecraft.player)) action = -1;
        if (action >= 0) {
            sides.handleAction(action);   // the client copy shows it at once, the server sync confirms
            PacketDistributor.sendToServer(new SideConfigPayload(menu.containerId, action));
            playClick();
        }
        return true;
    }

    private void playClick() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && clickSideTab(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        Rect2i tab = sideTab();
        if (tab != null && tab.contains((int) mouseX, (int) mouseY)) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // ---------------------------------------------------------------- frames

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

    /** Sunken 1 px frame around the area x..x+w, y..y+h (the area itself is not touched). */
    public static void drawInset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_DARK);
        g.fill(x, y, x + w + 1, y + h + 1, PANEL_LIGHT);
        g.fill(x, y, x + w, y + h, SLOT_DARK);
    }

    // ---------------------------------------------------------------- bars

    /** Vertical energy bar, 1 px dark frame, gradient fill from the bottom, FE readout below it and a hover tooltip. */
    protected void drawEnergyBar(GuiGraphics g, int x, int y, int w, int h, long stored, long capacity) {
        drawEnergyBar(g, x, y, w, h, stored, capacity, false, null);
    }

    /** As above; {@code extra} is a second tooltip line (for example the FE/t), {@code readout} draws the compact FE value below. */
    protected void drawEnergyBar(GuiGraphics g, int x, int y, int w, int h, long stored, long capacity, boolean readout, @Nullable Component extra) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, ENERGY_DARK);
        int filled = capacity <= 0 ? 0 : (int) Math.min(h, Math.round((double) h * stored / capacity));
        if (filled > 0) {
            g.fillGradient(x, y + h - filled, x + w, y + h, ENERGY_TOP, ENERGY_BOTTOM);
            g.fill(x, y + h - filled, x + 1, y + h, 0x40FFFFFF);
        }
        for (int i = 1; i < 4; i++) {
            int ty = y + h * i / 4;
            g.fill(x + w - Math.max(2, w / 3), ty, x + w, ty + 1, 0x66000000);
        }
        if (readout) {
            drawFitted(g, font, Component.literal(readoutText(stored)), x + w / 2, y + h + 3, 26, TEXT, 0, false, 0.75F);
        }
        energyBars.add(new int[]{x, y, w, h});
        energyValues.add(new long[]{stored, capacity});
        energyExtra.add(extra);
    }

    /** Short FE value for the readout under a bar: 950, 402k, 2.3M. */
    static String readoutText(long fe) {
        if (fe >= 1_000_000_000L) return String.format(java.util.Locale.ROOT, "%.1fG", fe / 1e9);
        if (fe >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", fe / 1e6);
        if (fe >= 10_000L) return (fe / 1000) + "k";
        return Long.toString(fe);
    }

    /** Horizontal energy bar; the exact "stored / capacity FE" is in its hover tooltip. */
    protected void drawEnergyBarWide(GuiGraphics g, int x, int y, int w, int h, long stored, long capacity) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, ENERGY_DARK);
        int filled = capacity <= 0 ? 0 : (int) Math.min(w, Math.round((double) w * stored / capacity));
        if (filled > 0) g.fillGradient(x, y, x + filled, y + h, ENERGY_TOP, ENERGY_BOTTOM);
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y + h - Math.max(2, h / 3), x + w * i / 4 + 1, y + h, 0x66000000);
        energyBars.add(new int[]{x, y, w, h});
        energyValues.add(new long[]{stored, capacity});
        energyExtra.add(null);
    }

    /** Horizontal progress bar (0..1), framed, gradient fill. */
    protected void drawProgress(GuiGraphics g, int x, int y, int w, int h, float progress) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, SLOT);
        int filled = Math.round(w * Math.max(0, Math.min(1, progress)));
        if (filled > 0) g.fillGradient(x, y, x + filled, y + h, PROGRESS_TOP, PROGRESS_BOTTOM);
    }

    /** Furnace style progress arrow, 22x15, filling left to right. */
    protected void drawArrow(GuiGraphics g, int x, int y, float progress) {
        int filled = Math.round(22 * Math.max(0, Math.min(1, progress)));
        for (int cx = 0; cx < 22; cx++) {
            int top = cx < 14 ? 4 : cx - 14;
            int bottom = cx < 14 ? 10 : 28 - cx;
            g.fill(x + cx, y + top + 1, x + cx + 1, y + bottom + 2, PANEL_LIGHT);
            g.fill(x + cx, y + top, x + cx + 1, y + bottom + 1, cx < filled ? PROGRESS : SLOT);
            if (cx < filled) g.fillGradient(x + cx, y + top, x + cx + 1, y + bottom + 1, PROGRESS_TOP, PROGRESS_BOTTOM);
        }
    }

    // ---------------------------------------------------------------- text

    /**
     * Draws text that always fits: wider than {@code maxWidth} it is shrunk to at most 65 %, then shortened with "...".
     * {@code align}: -1 left, 0 centred, 1 right of x. {@code baseScale} is the normal size (0.75 for small print).
     */
    public static void drawFitted(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color, int align, boolean shadow, float baseScale) {
        FormattedText shown = text;
        float scale = baseScale;
        int width = font.width(text);
        if (width * scale > maxWidth) {
            scale = Math.max(Math.min(baseScale, 0.65F), (float) maxWidth / width);
            if (width * scale > maxWidth) {
                int room = Math.max(1, (int) (maxWidth / scale) - font.width("..."));
                shown = FormattedText.composite(font.substrByWidth(text, room), FormattedText.of("..."));
                width = font.width(shown);
            }
        }
        FormattedCharSequence seq = Language.getInstance().getVisualOrder(shown);
        float drawn = width * scale;
        float startX = align < 0 ? x : align == 0 ? x - drawn / 2 : x - drawn;
        float startY = y + (8 - 8 * scale) / 2;
        g.pose().pushPose();
        g.pose().translate(startX, startY, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, seq, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** A section label (gray), fitted into {@code maxWidth}. Absolute coordinates; call from renderMachine. */
    protected void drawLabel(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        drawFitted(g, font, text, x, y, maxWidth, TEXT, -1, false, 1.0F);
    }

    protected void drawLabelCentered(GuiGraphics g, Component text, int centerX, int y, int maxWidth) {
        drawFitted(g, font, text, centerX, y, maxWidth, TEXT, 0, false, 1.0F);
    }

    protected void drawLabelRight(GuiGraphics g, Component text, int rightX, int y, int maxWidth) {
        drawFitted(g, font, text, rightX, y, maxWidth, TEXT, 1, false, 1.0F);
    }

    /**
     * Status line: a coloured dot, then the text, wrapped to at most {@code maxLines} lines inside {@code maxWidth}.
     * Returns the height used.
     */
    protected int drawStatus(GuiGraphics g, Component text, int x, int y, int maxWidth, int maxLines, Tone tone) {
        drawDot(g, x, y, tone);
        List<FormattedCharSequence> lines = font.split(text, maxWidth - 8);
        if (lines.size() > maxLines) {
            drawFitted(g, font, text, x + 8, y, maxWidth - 8, tone.text, -1, false, 1.0F);
            return 9;
        }
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + 8, y + i * 9, tone.text, false);
        }
        return Math.max(1, lines.size()) * 9;
    }

    /** Single line status centred on {@code centerX} (dot included). */
    protected void drawStatusCentered(GuiGraphics g, Component text, int centerX, int y, int maxWidth, Tone tone) {
        int textW = Math.min(font.width(text), maxWidth - 8);
        int x = centerX - (textW + 8) / 2;
        drawDot(g, x, y, tone);
        drawFitted(g, font, text, x + 8, y, maxWidth - 8, tone.text, -1, false, 1.0F);
    }

    private static void drawDot(GuiGraphics g, int x, int y, Tone tone) {
        g.fill(x + 1, y + 1, x + 4, y + 6, tone.dot);
        g.fill(x, y + 2, x + 5, y + 5, tone.dot);
        g.fill(x + 1, y + 2, x + 2, y + 3, 0x80FFFFFF);
        g.fill(x + 1, y + 5, x + 4, y + 6, 0x30000000);
    }

    /** Small text inside the GUI, relative to its corner. Call from renderMachine. Prefer {@link #drawLabel}. */
    protected void drawText(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }
}
