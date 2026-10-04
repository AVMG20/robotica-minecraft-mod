package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.power.menu.MetalPressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MetalPressScreen extends MachineScreen<MetalPressMenu> {
    public MetalPressScreen(MetalPressMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int titleMaxWidth() {
        return 150;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return switch (slot.index) {
            case 0 -> new ItemStack(Items.IRON_INGOT);
            case 1 -> icon("iron_plate");
            default -> icon("upgrade_speed_1");
        };
    }

    @Override
    protected Component slotHint(Slot slot) {
        return Component.translatable(switch (slot.index) {
            case 0 -> "gui.robotica.slot_input";
            case 1 -> "gui.robotica.slot_output";
            default -> "gui.robotica.slot_upgrade";
        });
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 12, y + 17, 14, 52, menu.energy(), menu.capacity());
        drawArrow(g, x + 74, y + 37, menu.progress());
        if (menu.progress() > 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.working"), x + 85, y + 58, 80, Tone.GOOD);
        } else if (menu.energy() <= 0) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.no_energy"), x + 85, y + 58, 80, Tone.BAD);
        } else {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.idle"), x + 85, y + 58, 80, Tone.WARN);
        }
    }
}
