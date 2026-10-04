package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.menu.AreaWorkerMenu;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import com.arno.robotica.core.client.FitButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI of Stumpy, Sprout and the Excavator. */
public class AreaWorkerScreen extends MachineScreen<AreaWorkerMenu> {
    private Button areaButton;
    private Button leavesButton;

    public AreaWorkerScreen(AreaWorkerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 176;
        this.inventoryLabelY = 83;
    }

    @Override
    protected void init() {
        super.init();
        areaButton = addRenderableWidget(new FitButton(leftPos + 48, topPos + 18, 64, 16, Component.empty(),
                b -> press(AreaWorkerMenu.BUTTON_SHOW_AREA), Component.translatable("gui.robotica.area_tip")));
        leavesButton = null;
        if (menu.be.hasLeavesToggle()) {
            leavesButton = addRenderableWidget(new FitButton(leftPos + 48, topPos + 36, 64, 16, Component.empty(),
                    b -> press(AreaWorkerMenu.BUTTON_LEAVES), Component.translatable("gui.robotica.leaves_tip")));
        }
        updateButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateButtons() {
        areaButton.setMessage(Component.translatable(menu.showArea() ? "gui.robotica.area_on" : "gui.robotica.area_off"));
        if (leavesButton != null) {
            leavesButton.setMessage(Component.translatable(menu.leaves() ? "gui.robotica.leaves_on" : "gui.robotica.leaves_off"));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index <= menu.be.upgradeSlotCount()) {
            g.renderTooltip(font, Component.translatable(hoveredSlot.index == 0 ? "gui.robotica.slot_battery" : "gui.robotica.slot_upgrade"), mouseX, mouseY);
        }
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 18, 12, 44, menu.energy(), menu.capacity());
        AreaWorkerBlockEntity.Status status = menu.status();
        Tone tone = switch (status) {
            case WORKING -> Tone.GOOD;
            case NO_ENERGY -> Tone.BAD;
            case OUTPUT_FULL, IDLE -> Tone.WARN;
        };
        int size = menu.size();
        Component info = isFarmBot()
                ? Component.translatable("gui.robotica.tier_area", menu.be.tier(), size, size)
                : Component.translatable("gui.robotica.area", size, size);
        drawLabelRight(g, info, x + imageWidth - 8, y + 6, 64);
        Component statusText = Component.translatable("gui.robotica.status." + status.name().toLowerCase(java.util.Locale.ROOT));
        int depthW = 0;
        Component depth = null;
        if (menu.extra() != 0) {
            depth = Component.translatable("gui.robotica.depth", menu.extra());
            depthW = font.width(depth);
            drawLabelRight(g, depth, x + imageWidth - 8, y + 74, 70);
        }
        drawStatus(g, statusText, x + 9, y + 74, imageWidth - 16 - depthW - 8, 1, tone);
    }

    private boolean isFarmBot() {
        return menu.be instanceof com.arno.robotica.automation.entity.FarmBotBlockEntity;
    }
}
