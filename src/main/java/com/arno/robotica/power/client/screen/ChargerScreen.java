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
        drawEnergyBar(g, x + 15, y + 17, 12, 56, menu.energy(), menu.capacity());
        ItemStack stack = menu.chargedItem();
        IEnergyStorage item = stack.isEmpty() ? null : stack.getCapability(Capabilities.EnergyStorage.ITEM);
        long stored = item == null ? 0 : item.getEnergyStored();
        long capacity = item == null ? 0 : item.getMaxEnergyStored();
        drawEnergyBar(g, x + 149, y + 17, 12, 56, stored, capacity);
        if (item == null && !stack.isEmpty()) {
            drawText(g, Component.translatable("gui.robotica.not_chargeable"), x + 40, y + 62, 0xFF804040);
        }
    }
}
