package com.arno.robotica.automation.farmbot;

import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI of Stumpy and Sprout (176 x 176): energy bar, battery slot, area toggle, storage check, card slots and the 3x3 pouch. Button 0
 * toggles the area outline (vanilla menu button channel).
 */
public class FarmBotMenu extends MachineMenu {
    public static final int BUTTON_SHOW_AREA = 0;

    public final FarmBot bot;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxStatus;
    private final int idxShow;
    private final int idxSize;
    private final int idxUse;
    private final int idxTier;
    private final int idxStorage;

    public FarmBotMenu(int id, Inventory inv, int entityId) {
        this(id, inv, resolve(inv, entityId));
    }

    private static FarmBot resolve(Inventory inv, int entityId) {
        Entity e = inv.player.level().getEntity(entityId);
        if (e instanceof FarmBot bot) return bot;
        throw new IllegalStateException("No farm robot with id " + entityId);
    }

    public FarmBotMenu(int id, Inventory inv, FarmBot bot) {
        super(FarmBotContent.MENU.get(), id);
        this.bot = bot;
        addSlot(new SlotItemHandler(bot.battery, 0, 24, 18));
        for (int i = 0; i < bot.upgrades.getSlots(); i++) addSlot(bot.upgrades.slot(i, 24 + i * 18, 52));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) addSlot(new SlotItemHandler(bot.pouch, c + r * 3, 118 + c * 18, 18 + r * 18));
        }
        addPlayerInventory(inv, 8, 94);
        idxEnergy = track(bot.energy::getEnergyStored);
        idxCapacity = track(bot.energy::getMaxEnergyStored);
        idxStatus = track(() -> bot.status().ordinal());
        idxShow = track(() -> bot.showArea() ? 1 : 0);
        idxSize = track(bot::areaSize);
        idxUse = track(bot::usePerTick);
        idxTier = track(bot::tier);
        idxStorage = track(() -> bot.hasStorage() ? 1 : 0);
    }

    /** A chest or other storage block was found near home. */
    public boolean hasStorage() {
        return synced(idxStorage) != 0;
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public FarmBot.Status status() {
        return FarmBot.Status.byId(synced(idxStatus));
    }

    public boolean showArea() {
        return synced(idxShow) != 0;
    }

    public int size() {
        return synced(idxSize);
    }

    public int usePerTick() {
        return synced(idxUse);
    }

    public int tier() {
        return synced(idxTier);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || !bot.isAlive() || !bot.canInteract(player)) return false;
        if (id == BUTTON_SHOW_AREA) {
            bot.toggleShowArea();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!bot.isAlive() || bot.isRemoved() || !bot.canInteract(player) || player.distanceToSqr(bot) > 64.0) return false;
        bot.holdForGui();
        return true;
    }
}
