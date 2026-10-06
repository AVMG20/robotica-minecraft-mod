package com.arno.robotica.industry.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.industry.block.ProcessingBlockEntity;
import com.arno.robotica.industry.menu.ProcessingMenu;
import com.arno.robotica.industry.recipe.Machine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Alloy Smelter, Centrifuge and Assembler (every Mk): energy bar with battery slot, inputs, arrow, outputs, status, FE/t, cards. */
public class ProcessingScreen extends MachineScreen<ProcessingMenu> {
    private final Machine machine;

    public ProcessingScreen(ProcessingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.machine = menu.machine;
        this.imageHeight = ProcessingMenu.HEIGHT;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected int titleMaxWidth() {
        return 160;
    }

    private int kind(Slot slot) {
        int i = slot.index;
        if (i < machine.inputs) return 0;
        if (i < machine.slots()) return 1;
        if (i == machine.slots()) return 2;
        return 3;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return switch (kind(slot)) {
            case 0 -> switch (machine) {
                case ALLOY_SMELTER -> slot.index == 0 ? new ItemStack(Items.IRON_INGOT) : slot.index == 1 ? icon("thorium_ingot") : ItemStack.EMPTY;
                case CENTRIFUGE -> icon("depleted_fuel_pellet");
                case ASSEMBLER -> slot.index == 0 ? icon("thorium_plate") : ItemStack.EMPTY;
            };
            case 1 -> slot.index == machine.inputs ? switch (machine) {
                case ALLOY_SMELTER -> icon("ferrothorium_ingot");
                case CENTRIFUGE -> icon("thorium_dust");
                case ASSEMBLER -> icon("thermocouple");
            } : ItemStack.EMPTY;
            case 2 -> icon("copper_cell");
            default -> slot.index == machine.slots() + 1 ? icon("upgrade_speed") : slot.index == machine.slots() + 2 ? icon("upgrade_efficiency") : ItemStack.EMPTY;
        };
    }

    @Override
    protected Component slotHint(Slot slot) {
        return Component.translatable(switch (kind(slot)) {
            case 0 -> "gui.robotica.slot_input";
            case 1 -> "gui.robotica.slot_output";
            case 2 -> "gui.robotica.slot_battery";
            default -> "gui.robotica.slot_upgrade";
        });
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 17, 14, 44, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.industry_using", Fmt.compact(menu.lastUse())).withStyle(ChatFormatting.GRAY));
        drawArrow(g, x + ProcessingMenu.arrowX(machine), y + 30, menu.progress());
        if (menu.needed() > 0 && menu.status() == ProcessingBlockEntity.WORKING) {
            addTooltip(x + ProcessingMenu.arrowX(machine), y + 30, 22, 16,
                    Component.translatable("gui.robotica.industry_time", String.format(java.util.Locale.ROOT, "%.1f", menu.needed() / 20.0)));
        }

        Tone tone;
        String key;
        switch (menu.status()) {
            case ProcessingBlockEntity.WORKING -> { tone = Tone.GOOD; key = "working"; }
            case ProcessingBlockEntity.NO_ENERGY -> { tone = Tone.BAD; key = "no_energy"; }
            case ProcessingBlockEntity.OUTPUT_FULL -> { tone = Tone.WARN; key = "output_full"; }
            default -> { tone = Tone.WARN; key = "idle"; }
        }
        drawStatus(g, Component.translatable("gui.robotica.status." + key), x + 30, y + 58, 92, 1, tone);
        Component use = Component.literal(Fmt.compact(menu.lastUse()) + " FE/t");
        drawFitted(g, font, use, x + 168, y + 58, 50, menu.lastUse() > 0 ? TEXT : TEXT_MUTED, 1, false, 1.0F);
        addTooltip(x + 118, y + 56, 50, 12, Component.translatable("gui.robotica.industry_use_tip"));

        int n = menu.tier + 1;
        int first = 152 - 18 * (n - 1);
        drawLabelRight(g, Component.translatable("gui.robotica.upgrades"), x + first - 3, y + ProcessingMenu.UPGRADE_Y + 4, first - 34);
    }
}
