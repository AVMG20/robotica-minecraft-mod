package com.arno.robotica.industry.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.industry.menu.RtgMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** RTG: energy bar, pellet slot, decay bar of the current pellet, waste slot, FE/t. */
public class RtgScreen extends MachineScreen<RtgMenu> {
    public RtgScreen(RtgMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index == 0 ? icon("thorium_fuel_pellet") : icon("depleted_fuel_pellet");
    }

    @Override
    protected Component slotHint(Slot slot) {
        return Component.translatable(slot.index == 0 ? "gui.robotica.slot_fuel" : "gui.robotica.slot_waste");
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int gen = menu.generating();
        drawEnergyBar(g, x + 12, y + 17, 14, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.generating", gen).withStyle(ChatFormatting.GRAY));
        drawProgress(g, x + 78, y + 35, 32, 6, menu.remaining());
        addTooltip(x + 78, y + 33, 32, 10, Component.translatable("gui.robotica.rtg_left", Fmt.duration(menu.decay())));
        if (gen > 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.rtg_decaying", Fmt.compact(gen)), x + 94, y + 58, 130, Tone.GOOD);
        } else if (menu.decay() > 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.rtg_full"), x + 94, y + 58, 130, Tone.WARN);
        } else {
            drawStatusCentered(g, Component.translatable("gui.robotica.rtg_no_fuel"), x + 94, y + 58, 130, Tone.WARN);
        }
    }
}
