package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.power.menu.ChargerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class ChargerScreen extends MachineScreen<ChargerMenu> {
    public ChargerScreen(ChargerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 12, y + 17, 14, 44, menu.energy(), menu.capacity());
        ItemStack stack = menu.chargedItem();
        IEnergyStorage item = stack.isEmpty() ? null : stack.getCapability(Capabilities.EnergyStorage.ITEM);
        long stored = item == null ? 0 : item.getEnergyStored();
        long capacity = item == null ? 0 : item.getMaxEnergyStored();
        drawEnergyBar(g, x + 150, y + 17, 14, 44, stored, capacity);
        if (stack.isEmpty()) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.idle"), x + 88, y + 24, 110, Tone.WARN);
        } else if (item == null) {
            drawStatusCentered(g, Component.translatable("gui.robotica.not_chargeable"), x + 88, y + 24, 110, Tone.BAD);
        } else if (stored >= capacity) {
            drawStatusCentered(g, Component.translatable("gui.robotica.charged"), x + 88, y + 24, 110, Tone.WARN);
        } else if (menu.energy() <= 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.no_energy"), x + 88, y + 24, 110, Tone.BAD);
        } else {
            drawStatusCentered(g, Component.translatable("gui.robotica.charging"), x + 88, y + 24, 110, Tone.GOOD);
        }
    }
}
