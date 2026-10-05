package com.arno.robotica.drones.menu;

import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.item.DroneItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI of the Mining Drone: the loot grid (18 slots, 27 on Mk2), battery and torch slot, a status line with an energy bar
 * and a row of icon buttons. Button ids are the vanilla menu button channel, validated by the server on every click.
 */
public class MiningDroneMenu extends MachineMenu {
    public static final int BTN_TUNNEL = 0;
    public static final int BTN_FOLLOW = 1;
    public static final int BTN_STAY = 2;
    public static final int BTN_RETURN = 3;
    public static final int BTN_TURN = 4;
    public static final int BTN_LENGTH = 5;
    public static final int BTN_VOID = 6;

    public final MiningDrone drone;
    public final int storageSlots;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxMode;
    private final int idxProgress;
    private final int idxJob;
    private final int idxLength;
    private final int idxVoid;
    private final int idxHeading;
    private final int idxStop;
    private final int idxStopBlocks;
    private final int idxFree;

    public MiningDroneMenu(int id, Inventory inv, int entityId) {
        this(id, inv, resolve(inv, entityId));
    }

    private static MiningDrone resolve(Inventory inv, int entityId) {
        Entity e = inv.player.level().getEntity(entityId);
        if (e instanceof MiningDrone drone) return drone;
        throw new IllegalStateException("No mining drone with id " + entityId);
    }

    public MiningDroneMenu(int id, Inventory inv, MiningDrone drone) {
        super(DronesRegistry.MINING_MENU.get(), id);
        this.drone = drone;
        this.storageSlots = drone.storageSlots();
        for (int i = 0; i < storageSlots; i++) {
            addSlot(new SlotItemHandler(drone.storage, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        int rows = storageSlots / 9;
        int rowB = 18 + rows * 18 + 4;
        addSlot(new SlotItemHandler(drone.torch, 0, 26, rowB));
        addSlot(new SlotItemHandler(drone.battery, 0, 8, rowB));
        addPlayerInventory(inv, 8, rowB + 54);
        idxEnergy = track(drone::getEnergy);
        idxCapacity = track(drone::getEnergyCapacity);
        idxMode = track(() -> drone.mode().ordinal());
        idxProgress = track(drone::progress);
        idxJob = track(drone::jobLength);
        idxLength = track(drone::tunnelLength);
        idxVoid = track(() -> drone.voidOn() ? 1 : 0);
        idxHeading = track(() -> drone.heading().get2DDataValue());
        idxStop = track(() -> drone.lastStop() == null ? 0 : drone.lastStop().ordinal() + 1);
        idxStopBlocks = track(drone::lastStopBlocks);
        idxFree = track(drone::freeSlots);
    }

    public int torchSlotIndex() {
        return storageSlots;
    }

    public int batterySlotIndex() {
        return storageSlots + 1;
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public MiningDrone.Mode mode() {
        return MiningDrone.Mode.byId(synced(idxMode));
    }

    public int progress() {
        return synced(idxProgress);
    }

    public int jobLength() {
        return synced(idxJob);
    }

    public int length() {
        return synced(idxLength);
    }

    public boolean voidOn() {
        return synced(idxVoid) != 0;
    }

    public net.minecraft.core.Direction heading() {
        return net.minecraft.core.Direction.from2DDataValue(synced(idxHeading));
    }

    public MiningDrone.Stop lastStop() {
        return MiningDrone.Stop.byId(synced(idxStop) - 1);
    }

    public int lastStopBlocks() {
        return synced(idxStopBlocks);
    }

    public int freeSlots() {
        return synced(idxFree);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !drone.isAlive() || !drone.canInteract(player)) return false;
        switch (id) {
            case BTN_TUNNEL -> drone.commandTunnel(sp, null);
            case BTN_FOLLOW -> drone.commandFollow();
            case BTN_STAY -> drone.commandStay();
            case BTN_RETURN -> drone.commandReturn();
            case BTN_TURN -> drone.turn();
            case BTN_LENGTH -> drone.cycleLength();
            case BTN_VOID -> drone.toggleVoid();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot.hasItem() && index >= storageSlots + 2) {
            ItemStack stack = slot.getItem();
            int target = MiningDrone.isTorch(stack) ? torchSlotIndex()
                    : EnergyUtil.isEnergyItem(stack) && !(stack.getItem() instanceof DroneItem) ? batterySlotIndex() : -1;
            if (target >= 0 && !slots.get(target).hasItem() && slots.get(target).mayPlace(stack)) {
                ItemStack before = stack.copy();
                if (moveItemStackTo(stack, target, target + 1, false)) {
                    slot.setChanged();
                    return before;
                }
            }
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!drone.isAlive() || drone.isRemoved() || !drone.canInteract(player) || player.distanceToSqr(drone) > 64.0) return false;
        drone.holdForGui();
        return true;
    }
}
