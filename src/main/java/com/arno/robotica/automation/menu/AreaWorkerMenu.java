package com.arno.robotica.automation.menu;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI of Stumpy, Sprout and the Excavator. Layout (176 x 176): energy bar, battery slot, buttons, upgrade slots,
 * 3x3 buffer. Button ids: 0 toggles "show area".
 */
public class AreaWorkerMenu extends MachineMenu {
    public static final int BUTTON_SHOW_AREA = 0;

    public final AreaWorkerBlockEntity be;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxStatus;
    private final int idxShow;
    private final int idxSize;
    private final int idxExtra;
    private final int idxProgress;

    public AreaWorkerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf));
    }

    private static AreaWorkerBlockEntity resolve(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof AreaWorkerBlockEntity worker) return worker;
        throw new IllegalStateException("No area worker at the menu position");
    }

    public AreaWorkerMenu(int id, Inventory inv, AreaWorkerBlockEntity be) {
        super(AutomationContent.WORKER_MENU.get(), id);
        this.be = be;
        addSlot(new SlotItemHandler(be.battery, 0, 26, 18));
        for (int i = 0; i < be.upgradeSlotCount(); i++) {
            addSlot(new SlotItemHandler(be.upgrades, i, 26 + i * 18, 52));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new SlotItemHandler(be.buffer, c + r * 3, 117 + c * 18, 18 + r * 18));
            }
        }
        addPlayerInventory(inv, 8, 94);
        idxEnergy = track(() -> be.energy.getEnergyStored());
        idxCapacity = track(() -> be.energy.getMaxEnergyStored());
        idxStatus = track(() -> be.status().ordinal());
        idxShow = track(() -> be.showArea() ? 1 : 0);
        idxSize = track(be::areaSize);
        idxExtra = track(be::guiExtra);
        idxProgress = track(be::guiProgress);
        trackSides(inv.player.level().isClientSide ? null : be.sides);
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public AreaWorkerBlockEntity.Status status() {
        return AreaWorkerBlockEntity.Status.byOrdinal(synced(idxStatus));
    }

    public boolean showArea() {
        return synced(idxShow) != 0;
    }

    public int size() {
        return synced(idxSize);
    }

    public int extra() {
        return synced(idxExtra);
    }

    /** Percent of the job done, -1 when the worker has no finite job. */
    public int progress() {
        return synced(idxProgress);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be.isRemoved()) return false;
        if (id == BUTTON_SHOW_AREA) {
            be.toggleShowArea();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be.isRemoved() || be.getLevel() == null) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
