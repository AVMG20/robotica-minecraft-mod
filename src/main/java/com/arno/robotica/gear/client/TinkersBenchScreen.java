package com.arno.robotica.gear.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Tinker's Bench: the tool, weapon or Exo piece, an arrow and five module slots. Slots the item's Age or Mk has not
 * opened yet are shaded and say which tier opens them; carrying a module over a slot that refuses it says why.
 */
public class TinkersBenchScreen extends MachineScreen<TinkersBenchMenu> {
    public TinkersBenchScreen(TinkersBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        ItemStack tool = menu.tool();
        boolean hasTool = !tool.isEmpty();
        drawArrow(g, x + 37, y + TinkersBenchMenu.TOOL_Y, hasTool ? 1.0F : 0.0F);
        if (hasTool) {
            drawLabelCentered(g, Component.translatable("gui.robotica.bench.module_slots"), x + 107, y + 24, 90);
            for (Slot slot : menu.slots) {
                if (slot instanceof TinkersBenchMenu.ModuleSlot m && m.locked() && !slot.hasItem()) {
                    int sx = x + slot.x, sy = y + slot.y;
                    g.fill(sx, sy, sx + 16, sy + 16, 0xC0373737);
                    g.fill(sx + 5, sy + 7, sx + 11, sy + 13, 0xFF8B8B8B);
                    g.fill(sx + 6, sy + 4, sx + 7, sy + 7, 0xFF8B8B8B);
                    g.fill(sx + 9, sy + 4, sx + 10, sy + 7, 0xFF8B8B8B);
                    g.fill(sx + 6, sy + 3, sx + 10, sy + 4, 0xFF8B8B8B);
                }
            }
        }
        Component status;
        Tone tone;
        if (!hasTool) {
            status = Component.translatable("gui.robotica.bench.insert_tool");
            tone = Tone.WARN;
        } else {
            status = Component.translatable("gui.robotica.bench.status", Modules.tierName(tool), Modules.slots(tool));
            tone = Tone.GOOD;
        }
        drawStatusCentered(g, status, x + 88, y + 63, 160, tone);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || hoveredSlot == null || hoveredSlot.hasItem()) return;
        Component reason = hoveredSlot instanceof TinkersBenchMenu.ModuleSlot m ? m.refusal(carried) : null;
        if (reason != null) {
            g.renderComponentTooltip(font, List.of(Component.translatable("module.robotica.refuse").withStyle(ChatFormatting.RED),
                    reason.copy().withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == TinkersBenchMenu.TOOL_SLOT) return icon("bore_drill");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == TinkersBenchMenu.TOOL_SLOT) return Component.translatable("gui.robotica.bench.slot_tool");
        if (slot instanceof TinkersBenchMenu.ModuleSlot m) {
            if (!m.locked()) return Component.translatable("gui.robotica.bench.slot_module");
            ModuleTarget target = Modules.target(menu.tool());
            int tier = target == null ? 0 : Modules.tierForSlot(target, m.module);
            if (tier <= 0) return Component.translatable("module.robotica.refuse.no_slot");
            return Component.translatable(target.isArmor() ? "module.robotica.refuse.locked_mark" : "module.robotica.refuse.locked_age", tier);
        }
        return null;
    }
}
