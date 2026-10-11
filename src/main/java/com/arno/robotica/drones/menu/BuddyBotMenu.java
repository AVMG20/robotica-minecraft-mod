package com.arno.robotica.drones.menu;

import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.BuddyBot;
import com.arno.robotica.drones.item.DroneItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI of the Buddy Bot: the cargo grid (18, 27, 36 or 54 slots by tier), the battery slot, a status line with an energy
 * bar and one row of buttons. Button ids are the vanilla menu button channel, validated by the server on every click.
 */
public class BuddyBotMenu extends MachineMenu {
    public static final int BTN_FOLLOW = 0;
    public static final int BTN_PATROL = 1;
    public static final int BTN_STAY = 2;
    public static final int BTN_STANCE = 3;
    public static final int BTN_PICKUP = 4;
    public static final int BTN_LIGHTS = 5;
    public static final int BTN_VOID = 6;
    public static final int BTN_DIG = 7;
    /** Player inventory top, below the cargo row (rowB). */
    public static final int INVENTORY_OFFSET = 53;

    public final BuddyBot bot;
    public final int cargoSlots;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxHealth;
    private final int idxMaxHealth;
    private final int idxMode;
    private final int idxStance;
    private final int idxFlags;
    private final int idxProgress;
    private final int idxJob;

    public BuddyBotMenu(int id, Inventory inv, int entityId) {
        this(id, inv, resolve(inv, entityId));
    }

    private static BuddyBot resolve(Inventory inv, int entityId) {
        Entity e = inv.player.level().getEntity(entityId);
        if (e instanceof BuddyBot bot) return bot;
        throw new IllegalStateException("No Buddy Bot with id " + entityId);
    }

    public BuddyBotMenu(int id, Inventory inv, BuddyBot bot) {
        super(DronesRegistry.BUDDY_MENU.get(), id);
        this.bot = bot;
        this.cargoSlots = bot.cargoSlots();
        for (int i = 0; i < cargoSlots; i++) {
            addSlot(new SlotItemHandler(bot.storage, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        addSlot(new SlotItemHandler(bot.battery, 0, 8, rowB(cargoSlots)));
        addPlayerInventory(inv, 8, rowB(cargoSlots) + INVENTORY_OFFSET);
        idxEnergy = track(bot::getEnergy);
        idxCapacity = track(bot::getEnergyCapacity);
        idxHealth = track(() -> Math.round(bot.getHealth()));
        idxMaxHealth = track(() -> Math.round(bot.getMaxHealth()));
        idxMode = track(() -> bot.isReturning() ? 3 : bot.mode().ordinal());
        idxStance = track(() -> bot.stance().ordinal());
        idxFlags = track(() -> (bot.pickupOn() ? 1 : 0) | (bot.lightsOn() ? 2 : 0) | (bot.voidOn() ? 4 : 0)
                | (bot.hasTunnel() ? 8 : 0) | (bot.isEnergyLow() ? 16 : 0) | (bot.currentTarget() != null ? 32 : 0));
        idxProgress = track(bot::progress);
        idxJob = track(bot::jobLength);
    }

    /** Y of the row under the cargo grid (battery slot, status, energy bar). */
    public static int rowB(int cargoSlots) {
        return 18 + (cargoSlots / 9) * 18 + 4;
    }

    public int batterySlotIndex() {
        return cargoSlots;
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public int health() {
        return synced(idxHealth);
    }

    public int maxHealth() {
        return synced(idxMaxHealth);
    }

    /** The mode, or null while it flies back after a tunnel. */
    public BuddyBot.Mode mode() {
        int m = synced(idxMode);
        return m == 3 ? null : BuddyBot.Mode.byId(m);
    }

    public BuddyBot.Stance stance() {
        return BuddyBot.Stance.byId(synced(idxStance));
    }

    private boolean flag(int f) {
        return (synced(idxFlags) & f) != 0;
    }

    public boolean pickupOn() {
        return flag(1);
    }

    public boolean lightsOn() {
        return flag(2);
    }

    public boolean voidOn() {
        return flag(4);
    }

    public boolean digging() {
        return flag(8);
    }

    public boolean low() {
        return flag(16);
    }

    public boolean engaged() {
        return flag(32);
    }

    public int progress() {
        return synced(idxProgress);
    }

    public int jobLength() {
        return synced(idxJob);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !bot.isAlive() || !bot.canInteract(player)) return false;
        switch (id) {
            case BTN_FOLLOW -> bot.commandFollow();
            case BTN_PATROL -> bot.setMode(BuddyBot.Mode.PATROL);
            case BTN_STAY -> bot.setMode(BuddyBot.Mode.STAY);
            case BTN_STANCE -> bot.cycleStance();
            case BTN_PICKUP -> bot.setPickup(!bot.pickupOn());
            case BTN_LIGHTS -> bot.setLights(!bot.lightsOn());
            case BTN_VOID -> bot.setVoid(!bot.voidOn());
            case BTN_DIG -> {
                if (bot.hasTunnel()) bot.commandReturn();
                else bot.commandTunnel(sp, sp.getDirection());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot.hasItem() && index > batterySlotIndex()) {
            ItemStack stack = slot.getItem();
            int target = batterySlotIndex();
            if (EnergyUtil.isEnergyItem(stack) && !(stack.getItem() instanceof DroneItem) && !slots.get(target).hasItem() && slots.get(target).mayPlace(stack)) {
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
        if (!bot.isAlive() || bot.isRemoved() || !bot.canInteract(player) || player.distanceToSqr(bot) > 64.0) return false;
        bot.holdForGui();
        return true;
    }
}
