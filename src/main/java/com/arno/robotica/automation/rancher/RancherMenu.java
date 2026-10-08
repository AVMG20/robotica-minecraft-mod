package com.arno.robotica.automation.rancher;

import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Rancher GUI: battery slot, population target (-/+), shear and milk toggles, status. Buttons use the vanilla menu button channel. */
public class RancherMenu extends MachineMenu {
    public static final int BTN_MINUS = 0;
    public static final int BTN_PLUS = 1;
    public static final int BTN_SHEAR = 2;
    public static final int BTN_MILK = 3;
    public static final int BTN_MINUS_8 = 4;
    public static final int BTN_PLUS_8 = 5;

    public final Rancher rancher;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxTarget;
    private final int idxFlags;
    private final int idxStatus;
    private final int idxAdults;

    public RancherMenu(int id, Inventory inv, int entityId) {
        this(id, inv, resolve(inv, entityId));
    }

    private static Rancher resolve(Inventory inv, int entityId) {
        Entity e = inv.player.level().getEntity(entityId);
        if (e instanceof Rancher r) return r;
        throw new IllegalStateException("No Rancher with id " + entityId);
    }

    public RancherMenu(int id, Inventory inv, Rancher rancher) {
        super(RancherContent.MENU.get(), id);
        this.rancher = rancher;
        addSlot(new SlotItemHandler(rancher.battery, 0, 26, 18));
        addPlayerInventory(inv, 8, 84);
        idxEnergy = track(rancher::getEnergy);
        idxCapacity = track(rancher::getEnergyCapacity);
        idxTarget = track(rancher::target);
        idxFlags = track(() -> (rancher.shearOn() ? 1 : 0) | (rancher.milkOn() ? 2 : 0));
        idxStatus = track(() -> rancher.status().ordinal());
        idxAdults = track(rancher::lastAdults);
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public int target() {
        return synced(idxTarget);
    }

    public boolean shear() {
        return (synced(idxFlags) & 1) != 0;
    }

    public boolean milk() {
        return (synced(idxFlags) & 2) != 0;
    }

    public Rancher.Status status() {
        return Rancher.Status.byId(synced(idxStatus));
    }

    public int adults() {
        return synced(idxAdults);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || !rancher.isAlive() || !rancher.canInteract(player)) return false;
        switch (id) {
            case BTN_MINUS -> rancher.setTarget(rancher.target() - 1);
            case BTN_PLUS -> rancher.setTarget(rancher.target() + 1);
            case BTN_MINUS_8 -> rancher.setTarget(rancher.target() - 8);
            case BTN_PLUS_8 -> rancher.setTarget(rancher.target() + 8);
            case BTN_SHEAR -> rancher.setShear(!rancher.shearOn());
            case BTN_MILK -> rancher.setMilk(!rancher.milkOn());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!rancher.isAlive() || rancher.isRemoved() || !rancher.canInteract(player) || player.distanceToSqr(rancher) > 64.0) return false;
        rancher.holdForGui();
        return true;
    }
}
