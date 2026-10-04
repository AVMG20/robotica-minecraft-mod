package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.menu.AreaWorkerMenu;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI of Stumpy, Sprout and the Excavator. */
public class AreaWorkerScreen extends MachineScreen<AreaWorkerMenu> {
    private Button areaButton;
    private Button leavesButton;

    public AreaWorkerScreen(AreaWorkerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.inventoryLabelY = 83;
    }

    @Override
    protected void init() {
        super.init();
        areaButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(AreaWorkerMenu.BUTTON_SHOW_AREA))
                .bounds(leftPos + 48, topPos + 16, 62, 16).build());
        if (menu.be.hasLeavesToggle()) {
            leavesButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(AreaWorkerMenu.BUTTON_LEAVES))
                    .bounds(leftPos + 48, topPos + 34, 62, 16).build());
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
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 8, y + 18, 10, 52, menu.energy(), menu.capacity());
        AreaWorkerBlockEntity.Status status = menu.status();
        int color = switch (status) {
            case WORKING -> 0xFF2E7D32;
            case NO_ENERGY -> 0xFFB71C1C;
            case OUTPUT_FULL -> 0xFFB26A00;
            case IDLE -> 0xFF404040;
        };
        drawText(g, Component.translatable("gui.robotica.status." + status.name().toLowerCase(java.util.Locale.ROOT)), x + 26, y + 74, color);
        int size = menu.size();
        Component info = isFarmBot()
                ? Component.translatable("gui.robotica.tier_area", menu.be.tier(), size, size)
                : Component.translatable("gui.robotica.area", size, size);
        drawText(g, info, x + imageWidth - 8 - font.width(info), y + 6, 0xFF404040);
        if (menu.extra() != 0) {
            Component depth = Component.translatable("gui.robotica.depth", menu.extra());
            drawText(g, depth, x + imageWidth - 8 - font.width(depth), y + 74, 0xFF404040);
        }
    }

    private boolean isFarmBot() {
        return menu.be instanceof com.arno.robotica.automation.entity.FarmBotBlockEntity;
    }
}
