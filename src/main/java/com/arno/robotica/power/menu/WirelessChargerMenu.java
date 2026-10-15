package com.arno.robotica.power.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.WirelessChargerBlock;
import com.arno.robotica.power.block.WirelessChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Wireless Charger GUI: energy, two upgrade slots (speed, range), range and rate, FE/t delivered and the players in
 * range. The player list comes from the block entity's synced client data, the numbers from data slots.
 */
public class WirelessChargerMenu extends MachineMenu {
    public static final int SLOT_X = 152, SLOT_Y = 18;

    private final BlockPos pos;
    @Nullable
    private final WirelessChargerBlockEntity be;
    private final int idxEnergy, idxCapacity, idxFlow, idxDrawn, idxRange, idxRate;

    /** Client side. */
    public WirelessChargerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, inv.player.level().getBlockEntity(pos) instanceof WirelessChargerBlockEntity w ? w : null, false);
    }

    /** Server side. */
    public WirelessChargerMenu(int id, Inventory inv, WirelessChargerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be, true);
    }

    private WirelessChargerMenu(int id, Inventory inv, BlockPos pos, @Nullable WirelessChargerBlockEntity be, boolean server) {
        super(PowerRegistry.WIRELESS_CHARGER_MENU.get(), id);
        this.pos = pos;
        this.be = be;
        var upgrades = be != null ? be.upgrades : com.arno.robotica.core.upgrade.Upgrades.forMk(
                () -> inv.player.level().getBlockState(pos).getBlock() instanceof WirelessChargerBlock b ? b.mk() : 1, WirelessChargerBlockEntity.KINDS, () -> {});
        for (int i = 0; i < WirelessChargerBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new SlotItemHandler(upgrades, i, SLOT_X, SLOT_Y + i * 18));
        }
        addPlayerInventory(inv, 8, 84);
        WirelessChargerBlockEntity s = server ? be : null;
        idxEnergy = track(s == null ? () -> 0 : () -> s.energy.getEnergyStored());
        idxCapacity = track(s == null ? () -> 0 : () -> s.energy.getMaxEnergyStored());
        idxFlow = track(s == null ? () -> 0 : s::fePerTick);
        idxDrawn = track(s == null ? () -> 0 : s::drawnPerTick);
        idxRange = track(s == null ? () -> 0 : s::range);
        idxRate = track(s == null ? () -> 0 : s::ratePerPlayer);
    }

    public int energy() { return synced(idxEnergy); }
    public int capacity() { return synced(idxCapacity); }
    public int fePerTick() { return synced(idxFlow); }
    /** FE/t the buffer pays: delivered plus the loss. */
    public int drawnPerTick() { return synced(idxDrawn); }
    public int range() { return synced(idxRange); }
    public int rate() { return synced(idxRate); }

    /** The client copy of the block entity (synced player list), or null if it is gone. */
    @Nullable
    public WirelessChargerBlockEntity charger() {
        return be;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity current = player.level().getBlockEntity(pos);
        if (!(current instanceof WirelessChargerBlockEntity charger) || current.isRemoved()) return false;
        if (!player.level().isClientSide && !charger.canUse(player)) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
