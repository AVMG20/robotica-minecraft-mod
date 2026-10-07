package com.arno.robotica.automation.menu;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyOrePool;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Survey Rig GUI (176 x 176): energy bar, battery and Magma Core slots, progress bar, scanner screen with the last ore,
 * upgrade slots, 3x3 buffer. Slot order: 0 battery, 1 core, 2-5 upgrades, 6-14 buffer, then the player inventory.
 */
public class SurveyRigMenu extends MachineMenu {
    public static final int SLOT_CORE = 1;
    public static final int FIRST_UPGRADE = 2;

    public final SurveyRigBlockEntity be;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxStatus;
    private final int idxState;
    private final int idxProgress;
    private final int idxCost;
    private final int idxInterval;
    private final int idxLastOre;
    private final int idxChance;

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
        idxProgress = track(be::guiProgress);
        idxCost = track(be::energyPerTick);
        idxInterval = track(be::actionInterval);
        idxLastOre = track(be::lastOreId);
        idxChance = track(() -> chanceBasisPoints(be));
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

    /** Server side: chance of the last ore's kind per roll, in 1/10000. */
    private static int chanceBasisPoints(SurveyRigBlockEntity be) {
        Item ore = be.lastOre();
        if (ore == null) return 0;
        SurveyOrePool pool = SurveyOrePool.get();
        SurveyOrePool.Kind kind = pool.kind(ore);
        return kind == null ? 0 : (int) Math.round(pool.chance(kind, be.hasCore()) * 10_000);
    }

    /** Percent of the way to the next ore. */
    public int progress() {
        return synced(idxProgress);
    }

    public int energyPerTick() {
        return synced(idxCost);
    }

    public int interval() {
        return synced(idxInterval);
    }

    @Nullable
    public Item lastOre() {
        int id = synced(idxLastOre);
        return id < 0 ? null : BuiltInRegistries.ITEM.byId(id);
    }

    /** Chance of the last ore's kind per roll, in 1/10000. */
    public int lastOreChance() {
        return synced(idxChance);
    }

    @Override
    public boolean stillValid(Player player) {
        if (be.isRemoved() || be.getLevel() == null) return false;
        if (!player.level().isClientSide && !be.canUse(player)) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
