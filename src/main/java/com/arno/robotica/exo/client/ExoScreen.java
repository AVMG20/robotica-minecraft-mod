package com.arno.robotica.exo.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Module screen: per armor piece a row with the piece icon, its module slots, an on/off switch per installed module
 * and the piece's battery. Switch clicks are menu buttons (the server flips the bit).
 */
public class ExoScreen extends MachineScreen<ExoMenu> {
    private static final int SWITCH_W = 24;
    private static final int SWITCH_H = 16;

    public ExoScreen(ExoMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 117 + ExoMenu.ROW_HEIGHT * menu.sections.size();
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private int switchX(int slot, int guiLeft) {
        return guiLeft + 33 + slot * 56 + 20;
    }

    private int rowY(int row) {
        return topPos + ExoMenu.FIRST_ROW_Y + row * ExoMenu.ROW_HEIGHT;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot instanceof ExoMenu.ModuleSlot m) {
            return Component.translatable("exo.robotica.slot_hint", Component.translatable("exo.robotica.piece." + m.piece().getName()));
        }
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var player = minecraft.player;
        if (player == null) return;
        for (int r = 0; r < menu.sections.size(); r++) {
            ExoMenu.Section section = menu.sections.get(r);
            ItemStack piece = section.resolve(player);
            int ry = rowY(r);
            if (r > 0) g.fill(x + 8, ry - 2, x + imageWidth - 8, ry - 1, 0x55555555);
            if (!piece.isEmpty()) {
                g.renderItem(piece, x + 10, ry + 1);
                drawEnergyBarWide(g, x + 137, ry + 6, 30, 6, ItemEnergy.get(piece), ItemEnergy.capacity(piece));
            }
            for (int i = 0; i < section.count(); i++) {
                ExoModuleKind kind = piece.isEmpty() ? null : ExoData.kind(piece, i);
                if (kind == null) continue;
                boolean on = ExoData.isEnabled(piece, i);
                int sx = switchX(i, x);
                int sy = ry + 1;
                g.fill(sx, sy, sx + SWITCH_W, sy + SWITCH_H, BORDER);
                g.fill(sx + 1, sy + 1, sx + SWITCH_W - 1, sy + SWITCH_H - 1, on ? 0xFF2E8B45 : 0xFF6A6A6A);
                g.fill(sx + 1, sy + 1, sx + SWITCH_W - 1, sy + 2, on ? 0xFF58C878 : 0xFF9A9A9A);
                int knobX = on ? sx + SWITCH_W - 9 : sx + 2;
                g.fill(knobX, sy + 3, knobX + 7, sy + SWITCH_H - 3, 0xFFE6E6E6);
                g.fill(knobX, sy + SWITCH_H - 4, knobX + 7, sy + SWITCH_H - 3, 0xFF9A9A9A);
                addTooltip(sx, sy, SWITCH_W, SWITCH_H,
                        Component.translatable(kind.nameKey()),
                        Component.translatable(on ? "exo.robotica.on" : "exo.robotica.off"),
                        Component.translatable("exo.robotica.click_toggle").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
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
                    if (piece.isEmpty() || ExoData.kind(piece, i) == null) continue;
                    int sx = switchX(i, leftPos);
                    int sy = rowY(r) + 1;
                    if (mouseX >= sx && mouseX < sx + SWITCH_W && mouseY >= sy && mouseY < sy + SWITCH_H) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, r * 2 + i);
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
