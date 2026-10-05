package com.arno.robotica.drones.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.SentryDrone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI of the Sentry Drone: battery and two upgrade slots, mode and stance buttons, patrol radius. Buttons use the vanilla
 * menu button channel, validated by the server on every click.
 */
public class SentryDroneMenu extends MachineMenu {
    public static final int BTN_GUARD = 0;
    public static final int BTN_FOLLOW = 1;
    public static final int BTN_STAY = 2;
    public static final int BTN_STANCE = 3;
    public static final int BTN_RADIUS = 4;

    public final SentryDrone drone;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxMode;
    private final int idxAggressive;
    private final int idxRadius;
    private final int idxHealth;
    private final int idxMaxHealth;
    private final int idxTarget;
    private final int idxLow;

    public SentryDroneMenu(int id, Inventory inv, int entityId) {
        this(id, inv, resolve(inv, entityId));
    }

    private static SentryDrone resolve(Inventory inv, int entityId) {
        Entity e = inv.player.level().getEntity(entityId);
        if (e instanceof SentryDrone drone) return drone;
        throw new IllegalStateException("No sentry drone with id " + entityId);
    }

    public SentryDroneMenu(int id, Inventory inv, SentryDrone drone) {
        super(DronesRegistry.SENTRY_MENU.get(), id);
        this.drone = drone;
        addSlot(new SlotItemHandler(drone.battery, 0, 26, 18));
        addSlot(new SlotItemHandler(drone.upgrades, 0, 26, 44));
        addSlot(new SlotItemHandler(drone.upgrades, 1, 44, 44));
        addPlayerInventory(inv, 8, 84);
        idxEnergy = track(drone::getEnergy);
        idxCapacity = track(drone::getEnergyCapacity);
        idxMode = track(() -> drone.mode().ordinal());
        idxAggressive = track(() -> drone.aggressive() ? 1 : 0);
        idxRadius = track(drone::patrolRadius);
        idxHealth = track(() -> Math.round(drone.getHealth()));
        idxMaxHealth = track(() -> Math.round(drone.getMaxHealth()));
        idxTarget = track(() -> drone.currentTarget() != null ? 1 : 0);
        idxLow = track(() -> drone.isEnergyLow() ? 1 : 0);
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public SentryDrone.Mode mode() {
        return SentryDrone.Mode.byId(synced(idxMode));
    }

    public boolean aggressive() {
        return synced(idxAggressive) != 0;
    }

    public int radius() {
        return synced(idxRadius);
    }

    public int health() {
        return synced(idxHealth);
    }

    public int maxHealth() {
        return synced(idxMaxHealth);
    }

    public boolean engaged() {
        return synced(idxTarget) != 0;
    }

    public boolean low() {
        return synced(idxLow) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || !drone.isAlive() || !drone.canInteract(player)) return false;
        switch (id) {
            case BTN_GUARD -> drone.setMode(SentryDrone.Mode.GUARD);
            case BTN_FOLLOW -> drone.setMode(SentryDrone.Mode.FOLLOW);
            case BTN_STAY -> drone.setMode(SentryDrone.Mode.STAY);
            case BTN_STANCE -> drone.toggleStance();
            case BTN_RADIUS -> drone.cycleRadius();
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
