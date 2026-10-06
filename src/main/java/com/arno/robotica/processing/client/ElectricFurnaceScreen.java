package com.arno.robotica.processing.client;

import com.arno.robotica.processing.block.ElectricFurnaceBlockEntity;
import com.arno.robotica.processing.menu.ElectricFurnaceMenu;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElectricFurnaceScreen extends MachineScreen<ElectricFurnaceMenu> {
    private static final int LANES = ElectricFurnaceBlockEntity.MAX_LANES;

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = ElectricFurnaceMenu.HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index < LANES) return slot.index == 0 ? icon("iron_dust") : ItemStack.EMPTY;
        if (slot.index < LANES * 2) return slot.index == LANES ? new ItemStack(Items.IRON_INGOT) : ItemStack.EMPTY;
        if (slot.index == ElectricFurnaceBlockEntity.BATTERY) return icon("copper_cell");
        return icon("upgrade_speed");
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index < LANES) return Component.translatable("gui.robotica.processing.slot_smelt");
        if (slot.index < LANES * 2) return Component.translatable("gui.robotica.slot_output");
        if (slot.index == ElectricFurnaceBlockEntity.BATTERY) return Component.translatable("gui.robotica.slot_battery");
        return Component.translatable("gui.robotica.processing.slot_upgrade");
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 160, y + 18, 8, 70, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.processing.using", menu.lastUse()));
        for (int lane = 0; lane < menu.lanes(); lane++) {
            int lx = x + menu.laneX() + lane * 18;
            drawProgress(g, lx + 1, y + 38, 14, 4, menu.laneProgress(lane));
        }
        if (menu.storedXp() > 0) {
            drawFitted(g, font, Component.translatable("gui.robotica.processing.xp", menu.storedXp()), x + 156, y + 94, 50, TEXT_MUTED, 1, false, 0.75F);
        }
        Tone tone = switch (menu.status()) {
            case WORKING -> Tone.GOOD;
            case NO_ENERGY -> Tone.BAD;
            default -> Tone.WARN;
        };
        Component text = menu.status() == com.arno.robotica.processing.block.ProcessingMachineBlockEntity.Status.WORKING
                ? Component.translatable("gui.robotica.processing.status.working_fe", menu.lastUse())
                : Component.translatable("gui.robotica.processing.status." + menu.status().name().toLowerCase(java.util.Locale.ROOT));
        drawStatus(g, text, x + 8, y + 93, 100, 1, tone);
    }
}
