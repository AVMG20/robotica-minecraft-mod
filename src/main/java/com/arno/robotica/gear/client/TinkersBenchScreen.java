package com.arno.robotica.gear.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import com.arno.robotica.gear.tool.ToggleKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Tinker's Bench: tool slot, an arrow, one slot per module card. */
public class TinkersBenchScreen extends MachineScreen<TinkersBenchMenu> {
    public TinkersBenchScreen(TinkersBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hasTool = !menu.tool().isEmpty();
        drawArrow(g, x + 67, y + TinkersBenchMenu.SLOT_Y, hasTool ? 1.0F : 0.0F);
        Component status = hasTool ? Component.translatable("gui.robotica.bench.modules")
                : Component.translatable("gui.robotica.bench.insert_tool");
        drawStatusCentered(g, status, x + 88, y + 60, 160, hasTool ? Tone.GOOD : Tone.WARN);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("bore_drill");
        if (slot.index <= TinkersBenchMenu.MODULES.length) return icon(TinkersBenchMenu.MODULES[slot.index - 1].module.itemName());
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.bench.slot_tool");
        if (slot.index <= TinkersBenchMenu.MODULES.length) {
            ToggleKind kind = TinkersBenchMenu.MODULES[slot.index - 1];
            return Component.translatable("gui.robotica.bench.slot_module", icon(kind.module.itemName()).getHoverName(), kind.displayName());
        }
        return null;
    }
}
