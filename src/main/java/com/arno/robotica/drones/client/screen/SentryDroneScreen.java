package com.arno.robotica.drones.client.screen;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.drones.entity.SentryDrone;
import com.arno.robotica.drones.menu.SentryDroneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** GUI of the Sentry Drone: battery, two upgrade slots, mode and stance buttons, patrol radius, health and a status dot. */
public class SentryDroneScreen extends MachineScreen<SentryDroneMenu> {
    private IconButton guard;
    private IconButton follow;
    private IconButton stay;
    private IconButton stance;
    private FitButton radius;

    public SentryDroneScreen(SentryDroneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        guard = addRenderableWidget(new IconButton(leftPos + 80, topPos + 17, 20, new ItemStack(Items.SHIELD), true, b -> press(SentryDroneMenu.BTN_GUARD)));
        follow = addRenderableWidget(new IconButton(leftPos + 102, topPos + 17, 20, new ItemStack(Items.LEAD), true, b -> press(SentryDroneMenu.BTN_FOLLOW)));
        stay = addRenderableWidget(new IconButton(leftPos + 124, topPos + 17, 20, new ItemStack(Items.ANVIL), true, b -> press(SentryDroneMenu.BTN_STAY)));
        stance = addRenderableWidget(new IconButton(leftPos + 80, topPos + 41, 20, new ItemStack(Items.IRON_SWORD), true, b -> press(SentryDroneMenu.BTN_STANCE)));
        radius = addRenderableWidget(new FitButton(leftPos + 102, topPos + 41, 20, 20, Component.literal("8"), b -> press(SentryDroneMenu.BTN_RADIUS)));
        guard.hint(Component.translatable("gui.robotica.drone.guard"));
        follow.hint(Component.translatable("gui.robotica.drone.follow"));
        stay.hint(Component.translatable("gui.robotica.drone.stay"));
        update();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void update() {
        SentryDrone.Mode mode = menu.mode();
        guard.setOn(mode == SentryDrone.Mode.GUARD);
        follow.setOn(mode == SentryDrone.Mode.FOLLOW);
        stay.setOn(mode == SentryDrone.Mode.STAY);
        stance.setOn(menu.aggressive());
        stance.hint(Component.translatable(menu.aggressive() ? "gui.robotica.drone.aggressive" : "gui.robotica.drone.defensive"));
        radius.setMessage(Component.literal(Integer.toString(menu.radius())));
        radius.setTooltip(Tooltip.create(Component.translatable("gui.robotica.drone.radius", menu.radius())));
        radius.active = mode == SentryDrone.Mode.GUARD;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("copper_cell");
        if (slot.index <= 2) return icon("upgrade_speed");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.slot_battery");
        if (slot.index <= 2) return Component.translatable("gui.robotica.slot_upgrade");
        return null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 18, 12, 46, menu.energy(), menu.capacity());
        drawLabelRight(g, Component.translatable("gui.robotica.drone.hp", menu.health(), menu.maxHealth()), x + imageWidth - 8, y + 6, 56);
        Tone tone = menu.low() ? Tone.BAD : menu.engaged() ? Tone.GOOD : Tone.WARN;
        Component text = menu.low() ? Component.translatable("gui.robotica.drone.low")
                : menu.engaged() ? Component.translatable("gui.robotica.drone.engaged")
                : Component.translatable("gui.robotica.drone.mode." + menu.mode().name().toLowerCase(Locale.ROOT));
        drawStatus(g, text, x + 26, y + 66, 140, 1, tone);
    }
}
