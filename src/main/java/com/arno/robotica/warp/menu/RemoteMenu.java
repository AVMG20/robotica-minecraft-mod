package com.arno.robotica.warp.menu;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.warp.WarpRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pad list of a Warp Remote held in {@code hand}. No slots. The list is built on the server when the GUI opens; travel,
 * home and forget requests are payloads that the server checks against the remote again.
 */
public class RemoteMenu extends MachineMenu {
    private final InteractionHand hand;
    private final List<RemoteEntry> entries;
    private final int energyIndex;
    private final int capacityIndex;

    /** Client side, from the opening buffer (see {@link #writeOpenData}). */
    public RemoteMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class), readEntries(buf), false);
    }

    /** Server side. */
    public RemoteMenu(int id, Inventory inv, InteractionHand hand, List<RemoteEntry> entries) {
        this(id, inv, hand, entries, true);
    }

    private RemoteMenu(int id, Inventory inv, InteractionHand hand, List<RemoteEntry> entries, boolean server) {
        super(WarpRegistry.REMOTE_MENU.get(), id);
        this.hand = hand;
        this.entries = new ArrayList<>(entries);
        Player player = inv.player;
        energyIndex = track(server ? () -> ItemEnergy.get(player.getItemInHand(hand)) : () -> 0);
        capacityIndex = track(server ? () -> ItemEnergy.capacity(player.getItemInHand(hand)) : () -> 0);
    }

    public static void writeOpenData(FriendlyByteBuf buf, InteractionHand hand, List<RemoteEntry> entries) {
        buf.writeEnum(hand);
        buf.writeVarInt(entries.size());
        for (RemoteEntry entry : entries) entry.write(buf);
    }

    private static List<RemoteEntry> readEntries(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<RemoteEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(RemoteEntry.read(buf));
        return list;
    }

    public InteractionHand hand() {
        return hand;
    }

    public List<RemoteEntry> entries() {
        return entries;
    }

    /** Client side: drops a row right away, the server removes it from the remote. */
    public void forgetLocally(UUID id) {
        entries.removeIf(e -> e.id().equals(id));
    }

    /** Client side: moves a row to the top right away, the server makes it home on the remote. */
    public void homeLocally(UUID id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                entries.add(0, entries.remove(i));
                return;
            }
        }
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && player.getItemInHand(hand).is(WarpRegistry.WARP_REMOTE.get());
    }
}
