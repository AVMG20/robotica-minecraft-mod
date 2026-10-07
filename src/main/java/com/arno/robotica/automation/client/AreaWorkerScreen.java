package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.menu.AreaWorkerMenu;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** GUI of Stumpy, Sprout and the Excavator: battery, upgrades, buffer, an area toggle, a status dot. */
public class AreaWorkerScreen extends MachineScreen<AreaWorkerMenu> {
    private IconButton areaButton;

    public AreaWorkerScreen(AreaWorkerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 176;
        this.inventoryLabelY = 83;
    }

    @Override
    protected void init() {
        super.init();
        areaButton = addRenderableWidget(new IconButton(leftPos + 52, topPos + 17, 20, new ItemStack(Items.SPYGLASS), true,
                b -> press(AreaWorkerMenu.BUTTON_SHOW_AREA)));
        updateButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateButtons() {
        areaButton.setOn(menu.showArea());
        areaButton.hint(Component.translatable(menu.showArea() ? "gui.robotica.area_on" : "gui.robotica.area_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("copper_cell");
        if (slot.index <= menu.be.upgradeSlotCount()) return icon("upgrade_speed");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.slot_battery");
        if (slot.index <= menu.be.upgradeSlotCount()) return Component.translatable("gui.robotica.slot_upgrade");
        return null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 18, 12, 52, menu.energy(), menu.capacity());
        AreaWorkerBlockEntity.Status status = menu.status();
        Tone tone = switch (status) {
            case WORKING -> Tone.GOOD;
            case NO_ENERGY -> Tone.BAD;
            case OUTPUT_FULL, IDLE -> Tone.WARN;
        };
        int size = menu.size();
        Component info = Component.translatable("gui.robotica.area", size, size);
        drawLabelRight(g, info, x + imageWidth - 8, y + 6, 40);
        Component tierTip = isFarmBot() || menu.be instanceof com.arno.robotica.automation.entity.ExcavatorBlockEntity
                ? Component.translatable("gui.robotica.tier_area", menu.be.tier(), size, size)
                : Component.translatable("gui.robotica.area", size, size);
        addTooltip(x + imageWidth - 50, y + 4, 44, 11, tierTip);
        boolean finished = menu.progress() >= 100 && status == AreaWorkerBlockEntity.Status.IDLE;
        String statusKey = finished ? "finished" : status.name().toLowerCase(Locale.ROOT);
        Component statusText = Component.translatable("gui.robotica.status." + statusKey);
        drawStatus(g, statusText, x + 9, y + 74, 90, 1, finished ? Tone.GOOD : tone);
        addTooltip(x + 9, y + 72, 90, 11, Component.translatable("gui.robotica.status_hint." + statusKey));
        if (menu.progress() >= 0 && !finished) {
            Component progress = menu.extra() != 0
                    ? Component.translatable("gui.robotica.depth_progress", menu.extra(), menu.progress())
                    : Component.translatable("gui.robotica.progress", menu.progress());
            drawLabelRight(g, progress, x + imageWidth - 8, y + 74, 66);
            drawProgress(g, x + 117, y + 84, 52, 2, menu.progress() / 100.0F);
        }
    }

    private boolean isFarmBot() {
        return menu.be instanceof com.arno.robotica.automation.entity.FarmBotBlockEntity;
    }
}
