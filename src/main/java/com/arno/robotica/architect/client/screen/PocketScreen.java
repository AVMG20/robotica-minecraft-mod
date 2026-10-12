package com.arno.robotica.architect.client.screen;

import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.pocket.PocketActionPayload;
import com.arno.robotica.architect.pocket.PocketArchitectItem;
import com.arno.robotica.architect.pocket.PocketMenu;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * Pocket Architect screen. Left: every building block, one row per style (click: take one, shift-click: a stack,
 * right-click: pick it for right-click placing). Right: stored matter, the junk toggle and the feed slot. Below: the
 * junk list (click with an item to add it, click an entry to remove it) and the player inventory.
 */
public class PocketScreen extends MachineScreen<PocketMenu> {
    private static final int CELL = 18;
    private static final int[] MATTER_COLORS = {0xFF9A7B4F, 0xFFD08850};
    private static final int SELECTED = 0xFFFFD04A;

    private final ItemStack[] icons = new ItemStack[PocketArchitectItem.BLOCKS];
    private IconButton junkButton;
    private int junkShown = -1;

    public PocketScreen(PocketMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PocketMenu.WIDTH;
        this.imageHeight = PocketMenu.HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = PocketMenu.INV_X;
        this.inventoryLabelY = PocketMenu.INV_Y - 11;
        for (int i = 0; i < icons.length; i++) icons[i] = new ItemStack(PocketArchitectItem.blockAt(i));
    }

    @Override
    protected void init() {
        super.init();
        junkButton = addRenderableWidget(new IconButton(leftPos + PocketMenu.SIDE_X, topPos + PocketMenu.FEED_Y - 1, 18,
                new ItemStack(Items.LAVA_BUCKET), true, b -> send(PocketMenu.ACTION_EAT_JUNK, menu.eatJunk() ? 0 : 1, 0)));
    }

    private void send(int action, int a, int b) {
        PacketDistributor.sendToServer(new PocketActionPayload(menu.containerId, action, a, b));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int on = menu.eatJunk() ? 1 : 0;
        junkButton.setOn(on == 1);
        if (on != junkShown) {
            junkShown = on;
            junkButton.hint(Component.translatable(on == 1 ? "gui.robotica.pocket_architect_junk_on" : "gui.robotica.pocket_architect_junk_off")
                    .append(Component.literal("\n")).append(Component.translatable("gui.robotica.pocket_architect_junk_tip").withStyle(ChatFormatting.GRAY)));
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == menu.feedSlot()) return new ItemStack(menu.item().mk2() ? Items.IRON_INGOT : Items.OAK_LOG);
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == menu.feedSlot()) {
            return Component.translatable(menu.item().mk2() ? "gui.robotica.pocket_architect_feed_mk2" : "gui.robotica.pocket_architect_feed");
        }
        if (slot instanceof PocketMenu.GhostSlot) return Component.translatable("gui.robotica.pocket_architect_junk_slot");
        return null;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawBlocks(g, mouseX, mouseY);
        drawMatter(g, x + PocketMenu.SIDE_X, y + 18);
        drawLabel(g, Component.translatable("gui.robotica.pocket_architect_junk"), x + PocketMenu.JUNK_X, y + PocketMenu.JUNK_Y - 10, 160);
        // the locked hotbar slot holds the pocket itself
        if (menu.lockedSlot() >= 0) {
            Slot locked = menu.slots.get(menu.lockedSlot());
            g.fill(x + locked.x - 1, y + locked.y - 1, x + locked.x + 17, y + locked.y + 17, 0x60FFD04A);
        }
    }

    private void drawBlocks(GuiGraphics g, int mouseX, int mouseY) {
        PocketArchitectItem item = menu.item();
        int hover = blockAt(mouseX, mouseY);
        int selected = menu.selected();
        for (int i = 0; i < PocketArchitectItem.BLOCKS; i++) {
            int cx = cellX(i), cy = cellY(i);
            boolean open = item.makes(PocketArchitectItem.styleAt(i));
            int frame = i == selected ? SELECTED : i == hover && open ? 0xFFFFFFFF : SLOT_DARK;
            g.fill(cx, cy, cx + CELL, cy + CELL, frame);
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, SLOT);
            g.renderItem(icons[i], cx + 1, cy + 1);
            if (!open || PocketArchitectItem.styleAt(i).cost.rustic() > menu.matter(Matter.Grade.RUSTIC)
                    || PocketArchitectItem.styleAt(i).cost.refined() > menu.matter(Matter.Grade.REFINED)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, open ? 0x70303030 : 0xB0303030);
                g.pose().popPose();
            }
        }
        if (hover >= 0) blockTooltip(hover, item);
    }

    private void blockTooltip(int index, PocketArchitectItem item) {
        BuildStyle style = PocketArchitectItem.styleAt(index);
        List<Component> lines = new ArrayList<>();
        lines.add(icons[index].getHoverName());
        Matter c = style.cost;
        lines.add((c.refined() > 0 ? Component.translatable("gui.robotica.pocket_architect_cost_refined", c.rustic(), c.refined())
                : Component.translatable("gui.robotica.pocket_architect_cost", c.rustic())).withStyle(ChatFormatting.GRAY));
        if (!item.makes(style)) {
            lines.add(Component.translatable("gui.robotica.pocket_architect_needs_mk2").withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("gui.robotica.pocket_architect_take").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable(index == menu.selected() ? "gui.robotica.pocket_architect_unpick" : "gui.robotica.pocket_architect_pick")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        addTooltip(cellX(index), cellY(index), CELL, CELL, lines.toArray(Component[]::new));
    }

    private void drawMatter(GuiGraphics g, int x, int y) {
        Matter.Grade[] grades = Matter.Grade.values();
        boolean mk2 = menu.item().mk2();
        for (int i = 0; i < grades.length; i++) {
            int top = y + i * 22;
            int value = menu.matter(grades[i]);
            boolean shown = mk2 || grades[i] == Matter.Grade.RUSTIC || value > 0;
            int color = shown ? MATTER_COLORS[i] : 0xFF8B8B8B;
            g.fill(x, top + 1, x + 6, top + 7, 0xFF000000);
            g.fill(x + 1, top + 2, x + 5, top + 6, color);
            Component name = Component.translatable(grades[i].langKey());
            drawFitted(g, font, name, x + 9, top, 36, shown ? TEXT : TEXT_MUTED, -1, false, 0.75F);
            drawFitted(g, font, Component.literal(shown ? Fmt.compact(value) : "-"), x, top + 9, 44, shown ? TEXT : TEXT_MUTED, -1, false, 1.0F);
            Component detail = shown ? Component.literal(String.format(java.util.Locale.ROOT, "%,d", value)).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("gui.robotica.pocket_architect_needs_mk2").withStyle(ChatFormatting.RED);
            addTooltip(x - 1, top - 1, 46, 20, name.copy().withStyle(s -> s.withColor(color & 0xFFFFFF)), detail);
        }
    }

    // ---------------------------------------------------------------- geometry and input

    private int cellX(int index) {
        return leftPos + PocketMenu.GRID_X + (index % Role.values().length) * CELL;
    }

    private int cellY(int index) {
        return topPos + PocketMenu.GRID_Y + (index / Role.values().length) * CELL;
    }

    private int blockAt(double mouseX, double mouseY) {
        int gx = (int) Math.floor(mouseX) - leftPos - PocketMenu.GRID_X;
        int gy = (int) Math.floor(mouseY) - topPos - PocketMenu.GRID_Y;
        if (gx < 0 || gy < 0) return -1;
        int col = gx / CELL, row = gy / CELL;
        if (col >= Role.values().length || row >= BuildStyle.values().length) return -1;
        return row * Role.values().length + col;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = button <= 1 ? blockAt(mouseX, mouseY) : -1;
        if (index >= 0 && menu.getCarried().isEmpty()) {
            if (!menu.item().makes(PocketArchitectItem.styleAt(index))) return true;
            if (button == 0) send(PocketMenu.ACTION_TAKE, index, hasShiftDown() ? 1 : 0);
            else send(PocketMenu.ACTION_SELECT, index == menu.selected() ? -1 : index, 0);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
