package com.arno.robotica.automation.menu;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity;
import com.arno.robotica.core.chunk.ChunkClaims;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Chunk Anchor GUI (176 x 166): energy bar, battery slot, state, area, upkeep and the owner's chunks against the cap.
 * Slot order: 0 battery, then the player inventory. Button ids: 0 toggles "show area".
 */
public class ChunkAnchorMenu extends MachineMenu {
    public static final int BUTTON_SHOW_AREA = 0;

    public final ChunkAnchorBlockEntity be;
    private final int idxEnergy;
    private final int idxCapacity;
    private final int idxState;
    private final int idxUpkeep;
    private final int idxUsed;
    private final int idxCap;
    private final int idxShow;
    private final int idxLoaded;

    public ChunkAnchorMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf));
    }

    private static ChunkAnchorBlockEntity resolve(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof ChunkAnchorBlockEntity anchor) return anchor;
        throw new IllegalStateException("No chunk anchor at the menu position");
    }

    public ChunkAnchorMenu(int id, Inventory inv, ChunkAnchorBlockEntity be) {
        super(AutomationContent.CHUNK_ANCHOR_MENU.get(), id);
        this.be = be;
        addSlot(new SlotItemHandler(be.battery, 0, 152, 18));
        addPlayerInventory(inv, 8, 84);
        boolean client = inv.player.level().isClientSide;
        idxEnergy = track(() -> be.energy.getEnergyStored());
        idxCapacity = track(() -> be.energy.getMaxEnergyStored());
        idxState = track(() -> be.state().ordinal());
        idxUpkeep = track(be::upkeep);
        idxUsed = track(client ? () -> 0 : be::ownerUsed);
        idxCap = track(client ? () -> 0 : ChunkClaims::cap);
        idxShow = track(() -> be.showArea() ? 1 : 0);
        idxLoaded = track(client ? () -> 0 : be::loadedSeconds);
    }

    public int energy() {
        return synced(idxEnergy);
    }

    public int capacity() {
        return synced(idxCapacity);
    }

    public ChunkAnchorBlockEntity.State state() {
        return ChunkAnchorBlockEntity.State.byOrdinal(synced(idxState));
    }

    public int upkeep() {
        return synced(idxUpkeep);
    }

    /** Chunks the owner's anchors load, all dimensions. */
    public int used() {
        return synced(idxUsed);
    }

    /** Chunk cap per player (0 = anchors off). */
    public int cap() {
        return synced(idxCap);
    }

    public boolean showArea() {
        return synced(idxShow) != 0;
    }

    /** Seconds it has loaded its chunks without a break. */
    public int loadedSeconds() {
        return synced(idxLoaded);
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
        if (!player.level().isClientSide && !be.canUse(player)) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
