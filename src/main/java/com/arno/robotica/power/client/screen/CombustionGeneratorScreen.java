package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.menu.CombustionGeneratorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CombustionGeneratorScreen extends MachineScreen<CombustionGeneratorMenu> {
    public CombustionGeneratorScreen(CombustionGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int output = CoreConfig.scaleGeneration(PowerConfig.generatorOutput());
        boolean burning = menu.burnTime() > 0;
        drawEnergyBar(g, x + 12, y + 17, 14, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.generating", output).withStyle(ChatFormatting.GRAY));
        int total = menu.burnTotal();
        float burn = total <= 0 ? 0 : (float) menu.burnTime() / total;
        if (burning) {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.working"), x + 88, y + 24, 110, Tone.GOOD);
            addTooltip(x + 58, y + 20, 60, 14, Component.translatable("gui.robotica.generating", output));
        } else {
            drawStatusCentered(g, Component.translatable("gui.robotica.status.idle"), x + 88, y + 24, 110, Tone.WARN);
        }
        drawProgress(g, x + 62, y + 62, 52, 5, burn);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return new ItemStack(Items.COAL);
    }

    @Override
    protected Component slotHint(Slot slot) {
        return Component.translatable("gui.robotica.slot_fuel");
    }
}
