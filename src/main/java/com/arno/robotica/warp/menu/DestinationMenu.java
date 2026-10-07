package com.arno.robotica.warp.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;

import java.util.ArrayList;
import java.util.List;

/** Destination list of a Warp Pad. The list is built on the server when the GUI opens; the travel payload is validated again. */
public class DestinationMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final String padName;
    private final List<DestinationEntry> entries;
    private final boolean canEdit;
    private final int energyIndex;
    private final int capacityIndex;

    /** Client side, from the opening buffer (see {@link #writeOpenData}). */
    public DestinationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readUtf(64), buf.readBoolean(), readEntries(buf), null);
    }

    /** Server side. */
    public DestinationMenu(int id, Inventory inv, WarpPadBlockEntity be, List<DestinationEntry> entries) {
        this(id, inv, be.getBlockPos(), be.padName(), be.canEdit(inv.player), entries, be);
    }

    private DestinationMenu(int id, Inventory inv, BlockPos pos, String padName, boolean canEdit, List<DestinationEntry> entries, WarpPadBlockEntity be) {
        super(WarpRegistry.DESTINATION_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.pos = pos;
        this.padName = padName;
        this.entries = entries;
        this.canEdit = canEdit;
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
    }

    public static void writeOpenData(FriendlyByteBuf buf, WarpPadBlockEntity be, List<DestinationEntry> entries, boolean canEdit) {
        buf.writeBlockPos(be.getBlockPos());
        buf.writeUtf(be.padName(), 64);
        buf.writeBoolean(canEdit);
        buf.writeVarInt(entries.size());
        for (DestinationEntry entry : entries) entry.write(buf);
    }

    private static List<DestinationEntry> readEntries(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<DestinationEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(DestinationEntry.read(buf));
        return list;
    }

    public BlockPos pos() {
        return pos;
    }

    public String padName() {
        return padName;
    }

    /** True when the player may rename the departure pad (owner, operator or unowned pad); the server checks again. */
    public boolean canEdit() {
        return canEdit;
    }

    public List<DestinationEntry> entries() {
        return entries;
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, WarpRegistry.WARP_PAD.get());
    }
}
