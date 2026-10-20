package com.arno.robotica.replicator.client;

import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.client.ControllerScreen;
import com.arno.robotica.replicator.farm.FarmControllerBlockEntity.State;
import com.arno.robotica.replicator.farm.FarmMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Mob farm GUI: FE buffer, the vial slots with their mob's spawn egg as ghost, the cycle bar, the mobs made, status and
 * structure lines; boost and upgrade slots on the right.
 */
public class FarmScreen extends ControllerScreen<FarmMenu> {
    private static final int TEXT_X = 26, TEXT_W = 118;

    public FarmScreen(FarmMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = FarmMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = FarmMenu.PLAYER_Y - 11;
    }

    @Override
    protected int titleMaxWidth() {
        return 140;
    }

    @Override
    protected void init() {
        super.init();
        addShowButton(150, 74);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        int vials = menu.kind().vialSlots();
        if (slot.index < vials) return menu.kind().vial(slot.index).icon();
        if (slot.index == vials) return icon("plasma_actuator");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        int vials = menu.kind().vialSlots();
        if (slot.index < vials) return Component.translatable("gui.robotica.farm.slot_" + menu.kind().vial(slot.index).key());
        if (slot.index == vials) return Component.translatable("gui.robotica.replicator.slot_boost");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Component cost = Component.translatable("gui.robotica.replicator.cost", Fmt.energy(menu.fePerTick())).withStyle(ChatFormatting.GRAY);
        drawEnergyBar(g, x + 8, y + 18, 10, 52, menu.energy(), menu.capacity(), false, cost);

        int tx = x + TEXT_X;
        drawProgress(g, tx, y + 41, TEXT_W, 6, menu.progress());
        addTooltip(tx - 1, y + 40, TEXT_W + 2, 8,
                Component.translatable("gui.robotica.replicator.cycle", Fmt.duration(menu.needed()), menu.speed()), cost);
        small(g, Component.translatable("gui.robotica.farm.made", Fmt.compact(menu.cycles()), menu.kind().mob().getDescription()), tx, y + 51, TEXT_W);

        State state = menu.state();
        Tone tone = switch (state) {
            case RUNNING -> Tone.GOOD;
            case OUTPUT_FULL -> Tone.WARN;
            default -> Tone.BAD;
        };
        String key = "gui.robotica.farm.state." + state.name().toLowerCase(Locale.ROOT);
        drawStatus(g, Component.translatable(key), tx, y + 61, TEXT_W, 1, tone);
        addTooltip(tx - 1, y + 60, TEXT_W + 2, 10, Component.translatable(key + ".tip"));
        drawStructure(g, x + 8, y + 76, 136, 2);
    }
}
