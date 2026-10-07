package com.arno.robotica.exo.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoItems;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.core.module.ModuleItem;
import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.module.ModuleText;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Module screen: one row per armor piece (all four worn pieces with J, or the held piece) with the piece, its battery,
 * up to four module slots (locked ones show which mark opens them), a switch and level pips per module, the core
 * socket on the chestplate and the pooled suit battery on the right. Switch clicks are menu buttons (the server flips
 * the bit). Carrying a module over a slot that refuses it explains why.
 */
public class ExoScreen extends MachineScreen<ExoMenu> {
    private static final int SWITCH_W = 8;
    private static final int SWITCH_H = 18;
    private static final int POOL_X = 181;

    public ExoScreen(ExoMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = ExoMenu.WIDTH;
        this.imageHeight = ExoMenu.inventoryY(menu.sections.size()) + 83;
        this.inventoryLabelX = (ExoMenu.WIDTH - 162) / 2;
        this.inventoryLabelY = ExoMenu.inventoryY(menu.sections.size()) - 11;
    }

    private int switchX(int slot) {
        return leftPos + ExoMenu.SLOT_X + slot * ExoMenu.SLOT_STEP + 18;
    }

    private int rowY(int row) {
        return topPos + ExoMenu.rowY(row);
    }

    @Override
    protected int titleMaxWidth() {
        return 90;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot instanceof ExoMenu.CoreSlot) return new ItemStack(CoreItems.SERVO_CORE.get());
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot instanceof ExoMenu.ModuleSlot m) {
            return Component.translatable("exo.robotica.slot_hint", Component.translatable("exo.robotica.piece." + m.piece().getName()));
        }
        if (slot instanceof ExoMenu.CoreSlot) return Component.translatable("exo.robotica.core_hint");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var player = minecraft.player;
        if (player == null) return;
        boolean worn = menu.sections.size() > 1;
        renderSetBonus(g, x, y, player, worn);
        for (int r = 0; r < menu.sections.size(); r++) {
            ExoMenu.Section section = menu.sections.get(r);
            ItemStack piece = section.resolve(player);
            int ry = rowY(r);
            if (r > 0) g.fill(x + 8, ry - 1, x + POOL_X - 4, ry, 0x55555555);
            if (piece.isEmpty()) {
                drawGhost(g, new ItemStack(ExoItems.piece(1, section.slot()).get()), x + 8, ry + 3);
                drawFitted(g, font, Component.translatable("exo.robotica.not_worn", Component.translatable("exo.robotica.piece." + section.slot().getName())),
                        x + 34, ry + 7, POOL_X - 40, TEXT_MUTED, -1, false, 0.75F);
                continue;
            }
            ExoArmorItem armor = (ExoArmorItem) piece.getItem();
            g.renderItem(piece, x + 8, ry + 3);
            addTooltip(x + 8, ry + 3, 16, 16, piece.getHoverName(),
                    Component.translatable("exo.robotica.tooltip.slots", armor.mk, Modules.slots(piece)).withStyle(ChatFormatting.GRAY));
            drawEnergyBar(g, x + 27, ry + 3, 3, 16, ItemEnergy.get(piece), ItemEnergy.capacity(piece));
            for (int i = 0; i < ExoArmorItem.MAX_SLOTS; i++) {
                int fx = x + ExoMenu.SLOT_X + i * ExoMenu.SLOT_STEP - 1;
                if (i >= section.count()) {
                    drawLocked(g, fx, ry + 2);
                    addTooltip(fx, ry + 2, 18, 18, Component.translatable("exo.robotica.locked", Modules.tierForSlot(ModuleTarget.armor(section.slot()), i)).withStyle(ChatFormatting.GRAY));
                    continue;
                }
                renderModule(g, piece, r, i, fx, ry);
            }
        }
        if (worn) {
            Component extra = Component.translatable(ExoSuit.fullSet(player) ? "exo.robotica.pool_full" : "exo.robotica.pool_partial");
            int h = menu.sections.size() * ExoMenu.ROW_HEIGHT - 6;
            drawEnergyBar(g, x + POOL_X, y + ExoMenu.FIRST_ROW_Y + 3, 7, h, ExoSuit.totalEnergy(player), ExoSuit.totalCapacity(player), false, extra);
        }
    }

    private void renderModule(GuiGraphics g, ItemStack piece, int row, int slot, int fx, int ry) {
        ItemStack module = Modules.module(piece, slot);
        if (!(module.getItem() instanceof ModuleItem m)) return;
        boolean on = Modules.enabled(piece, slot);
        boolean shadowed = menu.isShadowed(row, slot);
        boolean misplaced = menu.isMisplaced(row, slot);
        // Level pips under the slot.
        if (m.kind.leveled()) {
            for (int l = 1; l <= m.kind.maxLevel(); l++) {
                int px = fx + 2 + (l - 1) * 4;
                g.fill(px, ry + 21, px + 3, ry + 23, l <= m.level ? 0xFFF2C14A : 0xFF6E6E6E);
            }
        }
        // Vertical switch right of the slot: knob up and green when on.
        int sx = switchX(slot);
        int sy = ry + 2;
        g.fill(sx, sy, sx + SWITCH_W, sy + SWITCH_H, BORDER);
        int body = misplaced ? 0xFF7A2A2A : shadowed ? 0xFF9A5A2A : on ? 0xFF2E8B45 : 0xFF6A6A6A;
        g.fill(sx + 1, sy + 1, sx + SWITCH_W - 1, sy + SWITCH_H - 1, body);
        int knobY = on ? sy + 2 : sy + SWITCH_H - 9;
        g.fill(sx + 2, knobY, sx + SWITCH_W - 2, knobY + 7, 0xFFE6E6E6);
        g.fill(sx + 2, knobY + 6, sx + SWITCH_W - 2, knobY + 7, 0xFF9A9A9A);
        List<Component> lines = new ArrayList<>();
        lines.add(module.getHoverName());
        lines.add(Component.translatable(on ? "exo.robotica.on" : "exo.robotica.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        Component cost = ModuleText.cost(m.kind, m.level);
        if (cost != null) lines.add(cost.copy().withStyle(ChatFormatting.AQUA));
        lines.add(ModuleText.describe(m.kind, m.level).withStyle(ChatFormatting.GRAY));
        lines.add(m.kind.needsLine(m.level).withStyle(ChatFormatting.DARK_AQUA));
        if (misplaced) lines.add(Component.translatable("exo.robotica.misplaced").withStyle(ChatFormatting.RED));
        if (shadowed) lines.add(Component.translatable("exo.robotica.shadowed").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("exo.robotica.click_toggle").withStyle(ChatFormatting.DARK_GRAY));
        addTooltip(sx, sy, SWITCH_W, SWITCH_H, lines.toArray(Component[]::new));
    }

    /** A slot frame the piece's mark does not have yet: darker, with a small padlock. */
    private static void drawLocked(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF5A5A5A);
        g.fill(x + 6, y + 5, x + 12, y + 6, 0xFF8B8B8B);
        g.fill(x + 6, y + 5, x + 7, y + 9, 0xFF8B8B8B);
        g.fill(x + 11, y + 5, x + 12, y + 9, 0xFF8B8B8B);
        g.fill(x + 5, y + 9, x + 13, y + 14, 0xFF8B8B8B);
        g.fill(x + 8, y + 11, x + 10, y + 12, 0xFF5A5A5A);
    }

    /** Set bonus status right of the title: which core works, or what is missing. */
    private void renderSetBonus(GuiGraphics g, int x, int y, net.minecraft.client.player.LocalPlayer player, boolean worn) {
        ItemStack chest = worn ? ExoSuit.piece(player, net.minecraft.world.entity.EquipmentSlot.CHEST) : menu.sections.get(0).resolve(player);
        ItemStack core = ExoData.core(chest);
        if (core.isEmpty()) return;
        ExoData.Core kind = ExoData.coreKind(core);
        boolean active = worn && ExoSuit.setBonus(player) == kind;
        String id = kind.name().toLowerCase(java.util.Locale.ROOT);
        Component label = Component.translatable("exo.robotica.bonus." + id);
        int color = active ? 0xFF1E6B2A : TEXT_MUTED;
        drawFitted(g, font, label, x + POOL_X + 8, y + 6, 84, color, 1, false, 0.75F);
        int w = Math.min(84, (int) (font.width(label) * 0.75F) + 2);
        addTooltip(x + POOL_X + 8 - w, y + 5, w, 9,
                Component.translatable("exo.robotica.bonus." + id),
                Component.translatable("exo.robotica.bonus." + id + ".desc", ExoConfig.bonusArgs(id)).withStyle(ChatFormatting.GRAY),
                Component.translatable(active ? "exo.robotica.bonus.active" : "exo.robotica.bonus.needs", ExoConfig.setBonusMinMark())
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GOLD));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // A module in the wrong piece or below its mark does nothing: dim it over the item.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int r = 0; r < menu.sections.size(); r++) {
            for (int i = 0; i < menu.sections.get(r).count(); i++) {
                if (!menu.isMisplaced(r, i)) continue;
                int sx = leftPos + ExoMenu.SLOT_X + i * ExoMenu.SLOT_STEP;
                int sy = topPos + ExoMenu.rowY(r) + 3;
                g.fill(sx, sy, sx + 16, sy + 16, 0xA0301010);
            }
        }
        g.pose().popPose();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || hoveredSlot == null) return;
        Component reason = null;
        if (hoveredSlot instanceof ExoMenu.ModuleSlot m && !hoveredSlot.hasItem()) reason = m.refusal(carried);
        else if (hoveredSlot instanceof ExoMenu.CoreSlot c && !hoveredSlot.hasItem()) reason = c.refusal(carried);
        if (reason != null) {
            g.renderComponentTooltip(font, List.of(Component.translatable("exo.robotica.refuse").withStyle(ChatFormatting.RED),
                    reason.copy().withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var player = minecraft.player;
        if (button == 0 && player != null && minecraft.gameMode != null) {
            for (int r = 0; r < menu.sections.size(); r++) {
                ExoMenu.Section section = menu.sections.get(r);
                ItemStack piece = section.resolve(player);
                for (int i = 0; i < section.count(); i++) {
                    if (piece.isEmpty() || Modules.kind(piece, i) == null) continue;
                    int sx = switchX(i);
                    int sy = rowY(r) + 2;
                    if (mouseX >= sx && mouseX < sx + SWITCH_W && mouseY >= sy && mouseY < sy + SWITCH_H) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, r * ExoArmorItem.MAX_SLOTS + i);
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ExoKeys.OPEN_MODULES.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
