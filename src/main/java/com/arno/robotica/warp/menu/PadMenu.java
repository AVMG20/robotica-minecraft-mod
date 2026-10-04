package com.arno.robotica.warp.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Owner GUI of a Warp Pad: rename and public / private. No slots. Changes are sent with payloads and checked on the server. */
public class PadMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final String name;
    private final String ownerName;
    private final int energyIndex;
    private final int flagsIndex;

    /** Client side, from the opening buffer (see {@link #writeOpenData}). */
    public PadMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readUtf(WarpPadBlockEntity.MAX_NAME), buf.readUtf(64), null);
    }

    /** Server side. */
    public PadMenu(int id, Inventory inv, WarpPadBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.padName(), be.ownerName(), be);
    }

    private PadMenu(int id, Inventory inv, BlockPos pos, String name, String ownerName, WarpPadBlockEntity be) {
        super(WarpRegistry.PAD_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.pos = pos;
        this.name = name;
        this.ownerName = ownerName;
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        flagsIndex = track(be == null ? () -> 0 : () -> (be.isPublic() ? 1 : 0) | (be.hasRift() ? 2 : 0));
    }

    public static void writeOpenData(FriendlyByteBuf buf, WarpPadBlockEntity be) {
        buf.writeBlockPos(be.getBlockPos());
        buf.writeUtf(be.padName(), WarpPadBlockEntity.MAX_NAME);
        buf.writeUtf(be.ownerName(), 64);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Name when the GUI was opened. */
    public String name() {
        return name;
    }

    public String ownerName() {
        return ownerName;
    }

    public int energy() {
        return synced(energyIndex);
    }

    public boolean isPublic() {
        return (synced(flagsIndex) & 1) != 0;
    }

    public boolean hasRift() {
        return (synced(flagsIndex) & 2) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, WarpRegistry.WARP_PAD.get());
    }
}
