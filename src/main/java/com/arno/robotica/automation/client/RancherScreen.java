package com.arno.robotica.automation.client;

import com.arno.robotica.automation.rancher.Rancher;
import com.arno.robotica.automation.rancher.RancherMenu;
import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Rancher GUI: energy bar and battery, herd size with -/+ (Shift: 8), shear and milk toggles, adult count and status. */
public class RancherScreen extends MachineScreen<RancherMenu> {
    private IconButton shear;
    private IconButton milk;

    public RancherScreen(RancherMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new FitButton(leftPos + 80, topPos + 17, 16, 18, Component.literal("-"),
                b -> press(Screen.hasShiftDown() ? RancherMenu.BTN_MINUS_8 : RancherMenu.BTN_MINUS)));
        addRenderableWidget(new FitButton(leftPos + 128, topPos + 17, 16, 18, Component.literal("+"),
                b -> press(Screen.hasShiftDown() ? RancherMenu.BTN_PLUS_8 : RancherMenu.BTN_PLUS)));
        shear = addRenderableWidget(new IconButton(leftPos + 80, topPos + 40, 20, new ItemStack(Items.SHEARS), true, b -> press(RancherMenu.BTN_SHEAR)));
        milk = addRenderableWidget(new IconButton(leftPos + 102, topPos + 40, 20, new ItemStack(Items.MILK_BUCKET), true, b -> press(RancherMenu.BTN_MILK)));
        update();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void update() {
        shear.setOn(menu.shear());
        milk.setOn(menu.milk());
        shear.hint(Component.translatable(menu.shear() ? "gui.robotica.rancher.shear_on" : "gui.robotica.rancher.shear_off"));
        milk.hint(Component.translatable(menu.milk() ? "gui.robotica.rancher.milk_on" : "gui.robotica.rancher.milk_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index == 0 ? icon("copper_cell") : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        return slot.index == 0 ? Component.translatable("gui.robotica.slot_battery") : null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 18, 12, 46, menu.energy(), menu.capacity());
        drawLabel(g, Component.translatable("gui.robotica.rancher.herd"), x + 48, y + 22, 30);
        drawInset(g, x + 97, y + 17, 30, 18);
        drawFitted(g, font, Component.literal(Integer.toString(menu.target())), x + 112, y + 22, 28, 0xFFFFFFFF, 0, true, 1.0F);
        addTooltip(x + 97, y + 17, 30, 18, Component.translatable("gui.robotica.rancher.target", menu.target()),
                Component.translatable("gui.robotica.rancher.shift_step"));
        drawLabelRight(g, Component.translatable("gui.robotica.rancher.adults", menu.adults()), x + imageWidth - 8, y + 46, 44);
        Rancher.Status status = menu.status();
        Tone tone = switch (status) {
            case WORKING -> Tone.GOOD;
            case IDLE -> Tone.WARN;
            default -> Tone.BAD;
        };
        drawStatus(g, Component.translatable(status.key()), x + 26, y + 62, 140, 1, tone);
    }
}
