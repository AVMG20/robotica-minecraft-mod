package com.arno.robotica.automation.menu;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Survey Rig GUI (176 x 176): energy bar, battery and Magma Core slots, ledger bar, scanner screen, upgrade slots,
 * 3x3 buffer. Slot order: 0 battery, 1 core, 2-5 upgrades, 6-14 buffer, then the player inventory.
 */
public class SurveyRigMenu extends MachineMenu {
    public static final int SLOT_CORE = 1;
    public static final int FIRST_UPGRADE = 2;

    public final SurveyRigBlockEntity be;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxStatus;
    private final int idxState;
    private final int idxLeft;
    private final int idxTotal;
    private final int idxScan;
    private final int idxCost;

    public SurveyRigMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf));
    }

    private static SurveyRigBlockEntity resolve(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof SurveyRigBlockEntity rig) return rig;
        throw new IllegalStateException("No survey rig at the menu position");
    }

    public SurveyRigMenu(int id, Inventory inv, SurveyRigBlockEntity be) {
        super(AutomationContent.SURVEY_RIG_MENU.get(), id);
        this.be = be;
        addSlot(new SlotItemHandler(be.battery, 0, 26, 18));
        addSlot(new SlotItemHandler(be.core, 0, 44, 18));
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
        idxState = track(() -> be.rigState().ordinal());
        idxLeft = track(be::ledgerLeft);
        idxTotal = track(be::ledgerTotal);
        idxScan = track(be::scanPercent);
        idxCost = track(be::energyPerOre);
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

    public SurveyRigBlockEntity.RigState rigState() {
        return SurveyRigBlockEntity.RigState.byOrdinal(synced(idxState));
    }

    public int left() {
        return synced(idxLeft);
    }

    public int total() {
        return synced(idxTotal);
    }

    public int scanPercent() {
        return synced(idxScan);
    }

    public int energyPerOre() {
        return synced(idxCost);
    }

    @Override
    public boolean stillValid(Player player) {
        if (be.isRemoved() || be.getLevel() == null) return false;
        if (!player.level().isClientSide && !be.canUse(player)) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
