package com.arno.robotica.drones.client.screen;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.client.IconButton;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.CourierRoute;
import com.arno.robotica.drones.menu.CourierDroneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** GUI of the Courier Drone: route list with remove buttons, nine ghost filter slots, whitelist and tag toggles, battery and upgrades. */
public class CourierDroneScreen extends MachineScreen<CourierDroneMenu> {
    private final List<FitButton> removeButtons = new ArrayList<>();
    private IconButton mode;
    private IconButton tags;

    public CourierDroneScreen(CourierDroneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 208;
        this.inventoryLabelY = 114;
    }

    @Override
    protected void init() {
        super.init();
        removeButtons.clear();
        for (int i = 0; i < CourierRoute.MAX_ROUTES; i++) {
            final int index = i;
            FitButton b = addRenderableWidget(new FitButton(leftPos + 154, topPos + 16 + i * 10, 14, 10, Component.literal("x"),
                    btn -> press(CourierDroneMenu.BTN_REMOVE_BASE + index), Component.translatable("gui.robotica.courier.remove")));
            removeButtons.add(b);
        }
        mode = addRenderableWidget(new IconButton(leftPos + 78, topPos + CourierDroneMenu.ROW_Y - 2, 20, new ItemStack(Items.PAPER), true, b -> press(CourierDroneMenu.BTN_MODE)));
        tags = addRenderableWidget(new IconButton(leftPos + 100, topPos + CourierDroneMenu.ROW_Y - 2, 20, new ItemStack(Items.NAME_TAG), true, b -> press(CourierDroneMenu.BTN_TAGS)));
        update();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void update() {
        int count = menu.drone.syncedRoutes().size();
        for (int i = 0; i < removeButtons.size(); i++) removeButtons.get(i).visible = i < count;
        mode.setOn(menu.whitelist());
        mode.hint(Component.translatable(menu.whitelist() ? "gui.robotica.courier.whitelist" : "gui.robotica.courier.blacklist"));
        tags.setOn(menu.tags());
        tags.hint(Component.translatable(menu.tags() ? "gui.robotica.courier.tags_on" : "gui.robotica.courier.tags_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index < CourierDrone.FILTER_SLOTS) return new ItemStack(Items.HOPPER);
        if (slot.index == CourierDrone.FILTER_SLOTS) return icon("copper_cell");
        return icon("upgrade_speed_1");
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index < CourierDrone.FILTER_SLOTS) return Component.translatable("gui.robotica.courier.slot_filter");
        if (slot.index == CourierDrone.FILTER_SLOTS) return Component.translatable("gui.robotica.slot_battery");
        return Component.translatable("gui.robotica.slot_upgrade");
    }

    private static String pos(net.minecraft.core.BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<CourierRoute> routes = menu.drone.syncedRoutes();
        if (routes.isEmpty()) {
            drawLabel(g, Component.translatable("gui.robotica.courier.no_route"), x + 8, y + 18, 140);
        }
        for (int i = 0; i < routes.size(); i++) {
            CourierRoute r = routes.get(i);
            drawLabel(g, Component.translatable("gui.robotica.courier.route", pos(r.source()), pos(r.target())), x + 8, y + 17 + i * 10, 142);
        }
        drawEnergyBarWide(g, x + 126, y + CourierDroneMenu.ROW_Y + 4, 42, 8, menu.energy(), menu.capacity());
        CourierDrone.State state = menu.state();
        Tone tone = switch (state) {
            case LOW -> Tone.BAD;
            case IDLE -> Tone.WARN;
            default -> Tone.GOOD;
        };
        drawStatus(g, Component.translatable("gui.robotica.courier.state." + (state == CourierDrone.State.BACK ? "back" : state.name().toLowerCase(Locale.ROOT))),
                x + 8, y + 102, 160, 1, tone);
    }
}
