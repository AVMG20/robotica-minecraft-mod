package com.arno.robotica.drones.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.CourierDrone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI of the Courier Drone: up to four routes (the client reads them from the drone's synced data), nine ghost filter slots,
 * battery, two upgrade slots, whitelist and tag toggles. Buttons 0-3 remove a route, 4 flips whitelist/blacklist, 5 flips tag matching.
 */
public class CourierDroneMenu extends MachineMenu {
    public static final int BTN_REMOVE_BASE = 0;
    public static final int BTN_MODE = 4;
    public static final int BTN_TAGS = 5;

    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 58;
    public static final int ROW_Y = 80;

    public final CourierDrone drone;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxState;
    private final int idxWhitelist;
    private final int idxTags;

    /** A slot that shows a filter entry but never holds a real item: clicks copy the carried item or clear the slot. */
    public static class GhostSlot extends SlotItemHandler {
        public GhostSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    public CourierDroneMenu(int id, Inventory inv, int entityId) {
        this(id, inv, resolve(inv, entityId));
    }

    private static CourierDrone resolve(Inventory inv, int entityId) {
        Entity e = inv.player.level().getEntity(entityId);
        if (e instanceof CourierDrone drone) return drone;
        throw new IllegalStateException("No courier drone with id " + entityId);
    }

    public CourierDroneMenu(int id, Inventory inv, CourierDrone drone) {
        super(DronesRegistry.COURIER_MENU.get(), id);
        this.drone = drone;
        for (int i = 0; i < CourierDrone.FILTER_SLOTS; i++) addSlot(new GhostSlot(drone.filter, i, FILTER_X + i * 18, FILTER_Y));
        addSlot(new SlotItemHandler(drone.battery, 0, 8, ROW_Y));
        addSlot(new SlotItemHandler(drone.upgrades, 0, 30, ROW_Y));
        addSlot(new SlotItemHandler(drone.upgrades, 1, 48, ROW_Y));
        addPlayerInventory(inv, 8, 124);
        idxEnergy = track(drone::getEnergy);
        idxCapacity = track(drone::getEnergyCapacity);
        idxState = track(() -> drone.state().ordinal());
        idxWhitelist = track(() -> drone.whitelist() ? 1 : 0);
        idxTags = track(() -> drone.matchTags() ? 1 : 0);
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public CourierDrone.State state() {
        return CourierDrone.State.byId(synced(idxState));
    }

    public boolean whitelist() {
        return synced(idxWhitelist) != 0;
    }

    public boolean tags() {
        return synced(idxTags) != 0;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof GhostSlot ghost) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (drone.canInteract(player)) {
                    ItemStack carried = getCarried();
                    if (carried.isEmpty() || button == 1 || type == ClickType.QUICK_MOVE) ghost.set(ItemStack.EMPTY);
                    else ghost.set(carried.copyWithCount(1));
                    broadcastChanges();
                }
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || !drone.isAlive() || !drone.canInteract(player)) return false;
        if (id >= BTN_REMOVE_BASE && id < BTN_REMOVE_BASE + 4) {
            drone.removeRoute(id - BTN_REMOVE_BASE);
            return true;
        }
        switch (id) {
            case BTN_MODE -> drone.toggleWhitelist();
            case BTN_TAGS -> drone.toggleMatchTags();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!drone.isAlive() || drone.isRemoved() || !drone.canInteract(player) || player.distanceToSqr(drone) > 64.0) return false;
        drone.holdForGui();
        return true;
    }
}
