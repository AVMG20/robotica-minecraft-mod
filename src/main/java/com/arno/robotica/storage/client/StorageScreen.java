package com.arno.robotica.storage.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.storage.menu.StorageMenu;
import com.arno.robotica.storage.menu.StorageView;
import com.arno.robotica.storage.net.StorageViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Storage Terminal GUI: a 9 x 6 window onto the sorted item list with a scrollbar, a search box and a sort toggle on
 * top, the crafting grid, expansion and battery slots plus the power status on the right, the player inventory below.
 * Scroll, sort and search text go to the server in {@link StorageViewPayload}; clicks are ordinary container clicks.
 */
public class StorageScreen extends MachineScreen<StorageMenu> {
    private static final int SB_X = 173, SB_Y = StorageMenu.VIEW_Y, SB_W = 10, SB_H = StorageMenu.ROWS * 18;
    private static final int RIGHT = 196;

    private EditBox search;
    private FitButton sortButton;
    private boolean dragging;

    public StorageScreen(StorageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 306;
        this.imageHeight = 224;
        this.titleLabelX = RIGHT;
        this.titleLabelY = 6;
        this.inventoryLabelY = 132;
    }

    @Override
    protected int titleMaxWidth() {
        return imageWidth - RIGHT - 8;
    }

    @Override
    protected void init() {
        super.init();
        sortButton = addRenderableWidget(new FitButton(leftPos + 8, topPos + 3, 44, 12, sortText(), b -> {
            menu.setView(0, menu.sort().next(), menu.filter());
            sortButton.setMessage(sortText());
            sortButton.setTooltip(sortTooltip());
            send();
        }));
        sortButton.setTooltip(sortTooltip());

        String old = search != null ? search.getValue() : menu.filter();
        search = new EditBox(font, leftPos + 56, topPos + 3, 127, 12, Component.translatable("gui.robotica.storage.search"));
        search.setMaxLength(StorageView.MAX_FILTER);
        search.setHint(Component.translatable("gui.robotica.storage.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(old);
        search.setResponder(text -> {
            menu.setView(0, menu.sort(), text);
            send();
        });
        addRenderableWidget(search);
    }

    private Component sortText() {
        return Component.translatable("gui.robotica.storage.sort_" + menu.sort().name().toLowerCase(java.util.Locale.ROOT));
    }

    private Tooltip sortTooltip() {
        return Tooltip.create(Component.translatable("gui.robotica.storage.sort_hint_" + menu.sort().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void send() {
        PacketDistributor.sendToServer(new StorageViewPayload(menu.containerId, menu.scrollRow(), menu.sort().ordinal(), menu.filter()));
    }

    private void scrollTo(int row) {
        row = Mth.clamp(row, 0, menu.maxScroll());
        if (row != menu.scrollRow()) {
            menu.setView(row, menu.sort(), menu.filter());
            send();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.scrollRow() > menu.maxScroll()) scrollTo(menu.maxScroll());
    }

    // ------------------------------------------------------------------ input

    private boolean overView(double mx, double my) {
        return mx >= leftPos + StorageMenu.VIEW_X && mx < leftPos + SB_X + SB_W
                && my >= topPos + SB_Y && my < topPos + SB_Y + SB_H;
    }

    private boolean overScrollbar(double mx, double my) {
        return mx >= leftPos + SB_X && mx < leftPos + SB_X + SB_W && my >= topPos + SB_Y && my < topPos + SB_Y + SB_H;
    }

    private int thumbHeight() {
        int rows = (menu.viewTotal() + StorageMenu.COLS - 1) / StorageMenu.COLS;
        return menu.maxScroll() <= 0 ? SB_H : Math.max(14, SB_H * StorageMenu.ROWS / Math.max(1, rows));
    }

    private void dragTo(double my) {
        int max = menu.maxScroll();
        if (max <= 0) return;
        int thumb = thumbHeight();
        double rel = (my - topPos - SB_Y - thumb / 2.0) / (SB_H - thumb);
        scrollTo((int) Math.round(rel * max));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (overView(mx, my) && scrollY != 0) {
            scrollTo(menu.scrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (search != null) {
            if (button == 1 && search.isMouseOver(mx, my)) {
                search.setValue("");
                search.setFocused(true);
                setFocused(search);
                return true;
            }
            if (!search.isMouseOver(mx, my)) search.setFocused(false);
        }
        if (button == 0 && menu.maxScroll() > 0 && overScrollbar(mx, my)) {
            dragging = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing a search, keys (including the inventory key) belong to the search box; Escape still closes.
        if (search != null && search.isFocused() && keyCode != 256) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index >= StorageMenu.EXPANSION_START && slot.index < StorageMenu.EXPANSION_END) return icon("storage_expansion_mk1");
        if (slot.index == StorageMenu.BATTERY) return icon("copper_cell");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index >= StorageMenu.EXPANSION_START && slot.index < StorageMenu.EXPANSION_END) {
            return Component.translatable("gui.robotica.storage.slot_expansion");
        }
        if (slot.index == StorageMenu.BATTERY) return Component.translatable("gui.robotica.storage.slot_battery");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawScrollbar(g, x, y);
        if (menu.viewTotal() == 0) {
            Component empty = Component.translatable(menu.filter().isBlank() ? "gui.robotica.storage.empty" : "gui.robotica.storage.no_match");
            drawFitted(g, font, empty, x + StorageMenu.VIEW_X + 81, y + SB_Y + 50, 150, TEXT_MUTED, 0, false, 1.0F);
        }

        drawLabel(g, Component.translatable("gui.robotica.storage.crafting"), x + RIGHT, y + 18, 100);
        drawArrow(g, x + 255, y + StorageMenu.RESULT_Y + 1, menu.resultItem().isEmpty() ? 0.0F : 1.0F);
        drawLabel(g, Component.translatable("gui.robotica.storage.expansions"), x + RIGHT, y + 89, 60);
        drawLabel(g, Component.translatable("gui.robotica.storage.battery"), x + StorageMenu.BATTERY_X, y + 89, 46);
        for (int i = StorageMenu.EXPANSION_START; i < StorageMenu.EXPANSION_END; i++) {
            if (menu.expansionLocked(i)) {
                Slot s = menu.slots.get(i);
                addTooltip(x + s.x - 1, y + s.y - 1, 18, 18, Component.translatable("gui.robotica.storage.expansion_locked"));
            }
        }

        boolean powered = menu.powered();
        drawStatus(g, Component.translatable(powered ? "gui.robotica.storage.powered" : "gui.robotica.storage.no_power"),
                x + RIGHT, y + 122, 100, 1, powered ? Tone.GOOD : Tone.BAD);
        boolean full = menu.capacity() > 0 && menu.used() >= menu.capacity();
        drawFitted(g, font, Component.translatable("gui.robotica.storage.slots", menu.used(), menu.capacity()),
                x + RIGHT, y + 134, 100, full ? Tone.BAD.textColor() : TEXT, -1, false, 1.0F);
        drawEnergyBarWide(g, x + RIGHT, y + 147, 100, 8, menu.energy(), menu.energyMax());
        drawFitted(g, font, Component.translatable("gui.robotica.storage.drain", menu.drain()),
                x + RIGHT, y + 160, 100, TEXT_MUTED, -1, false, 1.0F);
        if (!powered) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("gui.robotica.storage.no_power_hint",
                    com.arno.robotica.storage.block.StorageTerminalBlockEntity.BASE_SLOTS), 104);
            for (int i = 0; i < Math.min(5, lines.size()); i++) {
                g.drawString(font, lines.get(i), x + RIGHT, y + 172 + i * 9, TEXT_MUTED, false);
            }
        }
    }

    private void drawScrollbar(GuiGraphics g, int x, int y) {
        int bx = x + SB_X, by = y + SB_Y;
        drawInset(g, bx, by, SB_W, SB_H);
        g.fill(bx, by, bx + SB_W, by + SB_H, SLOT_DARK);
        int thumb = thumbHeight();
        int max = menu.maxScroll();
        int ty = by + (max <= 0 ? 0 : (SB_H - thumb) * Mth.clamp(menu.scrollRow(), 0, max) / max);
        int shade = max <= 0 ? SLOT : PANEL;
        g.fill(bx, ty, bx + SB_W, ty + thumb, BORDER);
        g.fill(bx + 1, ty + 1, bx + SB_W - 1, ty + thumb - 1, PANEL_DARK);
        g.fill(bx + 1, ty + 1, bx + SB_W - 2, ty + thumb - 2, PANEL_LIGHT);
        g.fill(bx + 2, ty + 2, bx + SB_W - 2, ty + thumb - 2, shade);
        if (max > 0) {
            int cy = ty + thumb / 2;
            g.fill(bx + 3, cy - 2, bx + SB_W - 3, cy - 1, PANEL_DARK);
            g.fill(bx + 3, cy, bx + SB_W - 3, cy + 1, PANEL_DARK);
            g.fill(bx + 3, cy + 2, bx + SB_W - 3, cy + 3, PANEL_DARK);
        }
        if (max > 0) addTooltip(bx, by, SB_W, SB_H, Component.translatable("gui.robotica.storage.rows", menu.scrollRow() + 1, max + 1));
    }
}
