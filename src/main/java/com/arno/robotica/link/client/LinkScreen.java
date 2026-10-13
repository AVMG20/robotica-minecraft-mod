package com.arno.robotica.link.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.link.LinkPayloads;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.block.TransferMode;
import com.arno.robotica.link.menu.LinkMenu;
import com.arno.robotica.link.menu.NetworkEntry;
import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.Visibility;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Quantum Link screen. Left: the network list (own, then team, then public, like the Warp Pad destination list; click
 * a row to join) with New / Edit / Leave, or the edit form (name, 16 colours, who sees it). Right: the Energy and
 * Items switches, priority and the network's buffer. Below: status, the filter and its allow / block switch.
 */
public class LinkScreen extends MachineScreen<LinkMenu> {
    private static final int LIST_X = 8, LIST_Y = 18, LIST_W = 148, ROW_H = 18, ROWS = 4;
    private static final int BUTTON_Y = 92;
    private static final int RX = 166, RW = 66;
    private static final int STATUS_Y = 109;
    private static final int SWATCH_Y = 36, SWATCH_W = 16, SWATCH_H = 9;
    private static final int ROW_CURRENT = 0xFF2FA866;

    private int scroll;
    private boolean editing;
    private boolean creating;
    private int editColor = 9;
    private Visibility editVisibility = Visibility.PRIVATE;
    private int deleteArmed;
    private EditBox nameBox;
    private FitButton newButton, editButton, leaveButton;
    private FitButton visibilityButton, saveButton, cancelButton, deleteButton;
    private FitButton energyButton, itemButton, priorityButton, listButton;

    public LinkScreen(LinkMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 234;
        this.inventoryLabelX = LinkMenu.INV_X;
        this.inventoryLabelY = LinkMenu.INV_Y - 10;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.robotica.link." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        newButton = addRenderableWidget(new FitButton(x + 8, y + BUTTON_Y, 48, 14, tr("new"), b -> startEdit(true), tr("new.hint")));
        editButton = addRenderableWidget(new FitButton(x + 58, y + BUTTON_Y, 48, 14, tr("edit"), b -> startEdit(false), tr("edit.hint")));
        leaveButton = addRenderableWidget(new FitButton(x + 108, y + BUTTON_Y, 48, 14, tr("leave"),
                b -> PacketDistributor.sendToServer(new LinkPayloads.Select(menu.pos(), Optional.empty())), tr("leave.hint")));

        nameBox = new EditBox(font, x + 8, y + 18, LIST_W, 14, tr("name"));
        nameBox.setMaxLength(LinkNetwork.MAX_NAME);
        nameBox.setHint(tr("name_hint"));
        addRenderableWidget(nameBox);
        visibilityButton = addRenderableWidget(new FitButton(x + 8, y + 60, LIST_W, 14, Component.empty(), b -> {
            editVisibility = editVisibility.next();
            updateWidgets();
        }));
        saveButton = addRenderableWidget(new FitButton(x + 8, y + BUTTON_Y, 48, 14, tr("save"), b -> save()));
        cancelButton = addRenderableWidget(new FitButton(x + 58, y + BUTTON_Y, 48, 14, tr("cancel"), b -> stopEdit()));
        deleteButton = addRenderableWidget(new FitButton(x + 108, y + BUTTON_Y, 48, 14, tr("delete"), b -> delete(), tr("delete.hint")));

        energyButton = addRenderableWidget(new FitButton(x + RX, y + 26, RW, 14, Component.empty(),
                b -> config(LinkPayloads.SET_ENERGY, menu.energyMode().next().ordinal())));
        itemButton = addRenderableWidget(new FitButton(x + RX, y + 49, RW, 14, Component.empty(),
                b -> config(LinkPayloads.SET_ITEMS, menu.itemMode().next().ordinal())));
        priorityButton = addRenderableWidget(new FitButton(x + RX, y + 72, RW, 14, Component.empty(),
                b -> config(LinkPayloads.SET_PRIORITY, menu.priority().next().ordinal())));
        listButton = addRenderableWidget(new FitButton(x + 174, y + LinkMenu.FILTER_Y, 58, 16, Component.empty(),
                b -> config(LinkPayloads.SET_WHITELIST, menu.whitelist() ? 0 : 1)));
        updateWidgets();
    }

    private void config(int setting, int value) {
        PacketDistributor.sendToServer(new LinkPayloads.Config(menu.pos(), setting, value));
    }

    // ---------------------------------------------------------------- edit form

    private void startEdit(boolean create) {
        NetworkEntry current = menu.currentEntry();
        if (!create && (current == null || !current.manage())) return;
        editing = true;
        creating = create;
        deleteArmed = 0;
        if (create) {
            nameBox.setValue("");
            editColor = 9;
            editVisibility = Visibility.PRIVATE;
        } else {
            nameBox.setValue(current.name());
            editColor = current.color();
            editVisibility = current.visibility();
        }
        setFocused(nameBox);
        nameBox.setFocused(true);
        updateWidgets();
    }

    private void stopEdit() {
        editing = false;
        nameBox.setFocused(false);
        updateWidgets();
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        if (creating) {
            PacketDistributor.sendToServer(new LinkPayloads.Create(menu.pos(), name, editColor, editVisibility.ordinal()));
        } else if (menu.current() != null) {
            PacketDistributor.sendToServer(new LinkPayloads.Edit(menu.pos(), menu.current(), name, editColor, editVisibility.ordinal()));
        }
        stopEdit();
    }

    /** First click arms the button for 3 seconds, the second one deletes. */
    private void delete() {
        if (menu.current() == null) return;
        if (deleteArmed <= 0) {
            deleteArmed = 60;
            updateWidgets();
            return;
        }
        PacketDistributor.sendToServer(new LinkPayloads.Delete(menu.pos(), menu.current()));
        stopEdit();
    }

    private void updateWidgets() {
        NetworkEntry current = menu.currentEntry();
        newButton.visible = editButton.visible = leaveButton.visible = !editing;
        editButton.active = current != null && current.manage();
        leaveButton.active = menu.current() != null;
        nameBox.visible = visibilityButton.visible = saveButton.visible = cancelButton.visible = editing;
        deleteButton.visible = editing && !creating;
        visibilityButton.setMessage(tr("visibility", Component.translatable(editVisibility.translationKey())));
        visibilityButton.setTooltip(Tooltip.create(Component.translatable(editVisibility.translationKey() + ".hint")));
        deleteButton.setMessage(deleteArmed > 0 ? tr("delete.confirm").withStyle(ChatFormatting.RED) : tr("delete"));

        TransferMode energy = menu.energyMode(), items = menu.itemMode();
        energyButton.setMessage(Component.translatable(energy.translationKey()));
        energyButton.setTooltip(Tooltip.create(tr("energy." + energy.name().toLowerCase(Locale.ROOT))));
        itemButton.setMessage(Component.translatable(items.translationKey()));
        itemButton.setTooltip(Tooltip.create(tr("items." + items.name().toLowerCase(Locale.ROOT))));
        priorityButton.setMessage(Component.translatable(menu.priority().translationKey()));
        priorityButton.setTooltip(Tooltip.create(tr("priority.hint")));
        boolean white = menu.whitelist();
        listButton.setMessage(tr(white ? "allow" : "block"));
        listButton.setTooltip(Tooltip.create(Component.translatable(white ? "gui.robotica.pipe.whitelist.hint" : "gui.robotica.pipe.blacklist.hint")));
    }

    private int lastListVersion = -1;

    @Override
    protected void containerTick() {
        super.containerTick();
        if (deleteArmed > 0) deleteArmed--;
        if (lastListVersion != menu.listVersion()) {
            lastListVersion = menu.listVersion();
            scroll = Math.min(scroll, maxScroll());
            // the network being edited was deleted by its owner elsewhere
            if (editing && !creating && menu.currentEntry() == null) editing = false;
        }
        updateWidgets();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            stopEdit();
            return true;
        }
        if (!nameBox.isFocused()) {
            // A form button took the focus: the inventory key must not close the screen with the edit half done.
            if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- list

    private int maxScroll() {
        return Math.max(0, menu.entries().size() - ROWS);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (editing) return -1;
        double rx = mouseX - leftPos - LIST_X;
        double ry = mouseY - topPos - LIST_Y;
        if (rx < 0 || rx >= LIST_W || ry < 0 || ry >= ROWS * ROW_H) return -1;
        int index = scroll + (int) (ry / ROW_H);
        return index < menu.entries().size() ? index : -1;
    }

    private int swatchAt(double mouseX, double mouseY) {
        if (!editing) return -1;
        for (int i = 0; i < LinkNetwork.COLORS; i++) {
            int sx = leftPos + LIST_X + (i % 8) * 18 + 2, sy = topPos + SWATCH_Y + (i / 8) * (SWATCH_H + 2);
            if (mouseX >= sx && mouseX < sx + SWATCH_W && mouseY >= sy && mouseY < sy + SWATCH_H) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        if (editing) renderForm(g, x, y, mouseX, mouseY);
        else renderList(g, x, y, mouseX, mouseY);

        // right column
        drawFitted(g, font, tr("energy"), x + RX, y + 18, RW, TEXT, -1, false, 0.75F);
        drawFitted(g, font, tr("items"), x + RX, y + 41, RW, TEXT, -1, false, 0.75F);
        drawFitted(g, font, tr("priority"), x + RX, y + 64, RW, TEXT, -1, false, 0.75F);
        long stored = menu.networkEnergy(), capacity = menu.networkCapacity();
        drawEnergyBarWide(g, x + RX, y + 90, RW, 6, stored, capacity);
        drawFitted(g, font, tr("slots", menu.usedSlots(), menu.itemSlots()), x + RX, y + 99, RW, TEXT, -1, false, 0.75F);
        addTooltip(x + RX, y + 98, RW, 9, tr("buffer.items", menu.usedSlots(), menu.itemSlots()));

        // status line
        QuantumLinkBlockEntity.Status status = menu.status();
        Tone tone = switch (status) {
            case ACTIVE -> Tone.GOOD;
            case IDLE, OFF -> Tone.WARN;
            default -> Tone.BAD;
        };
        Component text = switch (status) {
            case ACTIVE -> tr("status.active", Fmt.compact(menu.feRate()), menu.itemRate());
            case NO_POWER -> tr("status.no_power", Fmt.compact(menu.tier().upkeep()));
            default -> Component.translatable(status.translationKey());
        };
        drawStatus(g, text, x + 8, y + STATUS_Y, imageWidth - 16, 1, tone);
    }

    private void renderList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<NetworkEntry> entries = menu.entries();
        int hover = rowAt(mouseX, mouseY);
        if (entries.isEmpty()) {
            drawFitted(g, font, tr("none"), x + LIST_X + LIST_W / 2, y + LIST_Y + 30, LIST_W, TEXT, 0, false, 1.0F);
        }
        ResourceLocation here = minecraft != null && minecraft.level != null ? minecraft.level.dimension().location() : null;
        for (int r = 0; r < ROWS; r++) {
            int index = scroll + r;
            if (index >= entries.size()) break;
            NetworkEntry e = entries.get(index);
            int rx = x + LIST_X, ry = y + LIST_Y + r * ROW_H;
            boolean current = e.id().equals(menu.current());
            g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 1, current ? ROW_CURRENT : SLOT_DARK);
            g.fill(rx + 1, ry + 1, rx + LIST_W - 1, ry + ROW_H - 2, index == hover && !current ? 0xFFA6B8BE : SLOT);
            g.fill(rx + 3, ry + 3, rx + 13, ry + 13, 0xFF000000);
            g.fill(rx + 4, ry + 4, rx + 12, ry + 12, 0xFF000000 | LinkNetwork.rgb(e.color()));
            g.fill(rx + 4, ry + 4, rx + 12, ry + 5, 0x60FFFFFF);
            drawFitted(g, font, Component.literal(e.name()), rx + 17, ry + 2, LIST_W - 21, 0xFF202020, -1, false, 1.0F);
            Component who = e.group() == 0 ? tr("yours") : Component.literal(e.ownerName());
            Component sub = here != null && !here.equals(e.home())
                    ? tr("row_away", who, Component.translatable(e.visibility().translationKey()), prettyDimension(e.home()))
                    : tr("row", who, Component.translatable(e.visibility().translationKey()));
            drawFitted(g, font, sub, rx + 17, ry + 11, LIST_W - 21, TEXT, -1, false, 0.75F);
        }
        if (entries.size() > ROWS) {
            int trackH = ROWS * ROW_H - 1;
            int barH = Math.max(8, trackH * ROWS / entries.size());
            int barY = y + LIST_Y + (maxScroll() == 0 ? 0 : (trackH - barH) * scroll / maxScroll());
            g.fill(x + LIST_X + LIST_W + 1, y + LIST_Y, x + LIST_X + LIST_W + 4, y + LIST_Y + trackH, SLOT_DARK);
            g.fill(x + LIST_X + LIST_W + 1, barY, x + LIST_X + LIST_W + 4, barY + barH, ENERGY);
        }
    }

    private void renderForm(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int hover = swatchAt(mouseX, mouseY);
        for (int i = 0; i < LinkNetwork.COLORS; i++) {
            int sx = x + LIST_X + (i % 8) * 18 + 2, sy = y + SWATCH_Y + (i / 8) * (SWATCH_H + 2);
            boolean selected = i == editColor;
            g.fill(sx - 1, sy - 1, sx + SWATCH_W + 1, sy + SWATCH_H + 1, selected ? 0xFFFFFFFF : i == hover ? 0xFF8A8A8A : SLOT_DARK);
            g.fill(sx, sy, sx + SWATCH_W, sy + SWATCH_H, 0xFF000000 | LinkNetwork.rgb(i));
            g.fill(sx, sy, sx + SWATCH_W, sy + 1, 0x50FFFFFF);
            if (selected) g.fill(sx + SWATCH_W / 2 - 1, sy + SWATCH_H / 2 - 1, sx + SWATCH_W / 2 + 1, sy + SWATCH_H / 2 + 1, 0xFFFFFFFF);
            addTooltip(sx, sy, SWATCH_W, SWATCH_H, Component.translatable("color.minecraft." + DyeColor.byId(i).getName()));
        }
        NetworkEntry current = menu.currentEntry();
        ResourceLocation home = creating
                ? (minecraft != null && minecraft.level != null ? minecraft.level.dimension().location() : null)
                : current != null ? current.home() : null;
        if (home != null) drawFitted(g, font, tr("home", prettyDimension(home)), x + LIST_X, y + 79, LIST_W, TEXT, -1, false, 0.75F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hover = rowAt(mouseX, mouseY);
        if (hover >= 0) {
            NetworkEntry e = menu.entries().get(hover);
            g.renderTooltip(font, e.id().equals(menu.current()) ? tr("joined") : tr("click_join"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                NetworkEntry e = menu.entries().get(index);
                if (!e.id().equals(menu.current())) {
                    PacketDistributor.sendToServer(new LinkPayloads.Select(menu.pos(), Optional.of(e.id())));
                }
                return true;
            }
            int swatch = swatchAt(mouseX, mouseY);
            if (swatch >= 0) {
                editColor = swatch;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!editing && mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W + 4
                && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + ROWS * ROW_H) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index < QuantumLinkBlockEntity.FILTER_SLOTS ? new ItemStack(Items.HOPPER) : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        return slot.index < QuantumLinkBlockEntity.FILTER_SLOTS ? tr("slot_filter") : null;
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
