package com.arno.robotica.link.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.link.LinkRegistry;
import com.arno.robotica.link.block.LinkPriority;
import com.arno.robotica.link.block.LinkTier;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.block.TransferMode;
import com.arno.robotica.logistics.menu.PipeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Screen of a Quantum Link: the network list (sent when it opens and again after every change, see
 * {@code LinkPayloads.Networks}), the two mode switches, priority, the filter (pipe arm ghost slots) and the Sides tab.
 * Every change goes through a {@code robotica:link_*} payload that the server checks again.
 */
public class LinkMenu extends MachineMenu {
    public static final int FILTER_X = 8, FILTER_Y = 121;
    public static final int INV_X = 40, INV_Y = 152;
    /** Most rows sent; past this the list is cut (own networks come first). */
    public static final int MAX_ENTRIES = 512;

    private final BlockPos pos;
    private final LinkTier tier;
    @Nullable
    private final QuantumLinkBlockEntity be;
    private List<NetworkEntry> entries;
    @Nullable
    private UUID current;
    /** Bumped when the client gets a new list, so the screen knows to look again. */
    private int listVersion;
    private final int idxEnergyMode, idxItemMode, idxPriority, idxWhitelist, idxStatus, idxStored, idxRate, idxItemRate,
            idxUsedSlots, idxSlots;
    private final int[] idxNetEnergy, idxNetCapacity;

    public static LinkMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        LinkTier tier = LinkTier.values()[Math.floorMod(buf.readByte(), LinkTier.values().length)];
        UUID current = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf).orElse(null);
        List<NetworkEntry> entries = NetworkEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).decode(buf);
        return new LinkMenu(id, inv, pos, tier, null, new ArrayList<>(entries), current);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, QuantumLinkBlockEntity be, List<NetworkEntry> entries) {
        buf.writeBlockPos(be.getBlockPos());
        buf.writeByte(be.tier().ordinal());
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, Optional.ofNullable(be.networkId()));
        NetworkEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).encode(buf, cap(entries));
    }

    /** Server side. */
    public LinkMenu(int id, Inventory inv, QuantumLinkBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.tier(), be, new ArrayList<>(), be.networkId());
    }

    private LinkMenu(int id, Inventory inv, BlockPos pos, LinkTier tier, @Nullable QuantumLinkBlockEntity be, List<NetworkEntry> entries,
                     @Nullable UUID current) {
        super(LinkRegistry.LINK_MENU.get(), id);
        this.pos = pos;
        this.tier = tier;
        this.be = be;
        this.entries = entries;
        this.current = current;
        IItemHandler filter = be != null ? be.filter() : new ItemStackHandler(QuantumLinkBlockEntity.FILTER_SLOTS);
        for (int i = 0; i < QuantumLinkBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new PipeMenu.GhostSlot(filter, i, FILTER_X + i * 18, FILTER_Y));
        }
        addPlayerInventory(inv, INV_X, INV_Y);
        idxEnergyMode = track(be == null ? () -> 0 : () -> be.energyMode().ordinal());
        idxItemMode = track(be == null ? () -> 0 : () -> be.itemMode().ordinal());
        idxPriority = track(be == null ? () -> 0 : () -> be.priority().ordinal());
        idxWhitelist = track(be == null ? () -> 0 : () -> be.whitelist() ? 1 : 0);
        idxStatus = track(be == null ? () -> 0 : () -> be.status().ordinal());
        idxStored = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        idxRate = track(be == null ? () -> 0 : be::feRate);
        idxItemRate = track(be == null ? () -> 0 : be::itemRate);
        idxUsedSlots = track(be == null ? () -> 0 : () -> be.network() == null ? 0 : be.network().usedSlots());
        idxSlots = track(be == null ? () -> 0 : () -> be.network() == null ? 0 : be.network().itemSlots());
        idxNetEnergy = trackLong(be == null ? () -> 0 : () -> be.network() == null ? 0 : be.network().energy());
        idxNetCapacity = trackLong(be == null ? () -> 0 : () -> be.network() == null ? 0 : be.network().energyCapacity());
        trackSides(be == null ? null : be.sides);
    }

    public static List<NetworkEntry> cap(List<NetworkEntry> entries) {
        return entries.size() <= MAX_ENTRIES ? entries : entries.subList(0, MAX_ENTRIES);
    }

    /** A long as two synced ints. */
    private int[] trackLong(LongSupplier server) {
        return new int[]{track(() -> (int) (server.getAsLong() >>> 32)), track(() -> (int) server.getAsLong())};
    }

    private long syncedLong(int[] index) {
        return ((long) synced(index[0]) << 32) | (synced(index[1]) & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------------- client view

    public BlockPos pos() {
        return pos;
    }

    public LinkTier tier() {
        return tier;
    }

    public List<NetworkEntry> entries() {
        return entries;
    }

    @Nullable
    public UUID current() {
        return current;
    }

    @Nullable
    public NetworkEntry currentEntry() {
        if (current == null) return null;
        for (NetworkEntry e : entries) if (e.id().equals(current)) return e;
        return null;
    }

    public int listVersion() {
        return listVersion;
    }

    /** Client: a fresh list from the server. */
    public void setNetworks(@Nullable UUID current, List<NetworkEntry> entries) {
        this.current = current;
        this.entries = new ArrayList<>(entries);
        listVersion++;
    }

    public TransferMode energyMode() {
        return TransferMode.byId(synced(idxEnergyMode));
    }

    public TransferMode itemMode() {
        return TransferMode.byId(synced(idxItemMode));
    }

    public LinkPriority priority() {
        return LinkPriority.byId(synced(idxPriority));
    }

    public boolean whitelist() {
        return synced(idxWhitelist) != 0;
    }

    public QuantumLinkBlockEntity.Status status() {
        return QuantumLinkBlockEntity.Status.byId(synced(idxStatus));
    }

    public int stored() {
        return synced(idxStored);
    }

    public int feRate() {
        return synced(idxRate);
    }

    public int itemRate() {
        return synced(idxItemRate);
    }

    public int usedSlots() {
        return synced(idxUsedSlots);
    }

    public int itemSlots() {
        return synced(idxSlots);
    }

    public long networkEnergy() {
        return syncedLong(idxNetEnergy);
    }

    public long networkCapacity() {
        return syncedLong(idxNetCapacity);
    }

    // ---------------------------------------------------------------- server

    @Nullable
    public QuantumLinkBlockEntity link() {
        return be;
    }

    private boolean canEdit(Player player) {
        return be != null && !be.isRemoved() && be.canOpen(player) && player.mayBuild();
    }

    /** Sides tab: only who may open the Link (checked on the server; the client copy has no block entity). */
    @Override
    public boolean mayConfigure(Player player) {
        return be == null || canEdit(player);
    }

    /** Ghost filter slots: a click copies the carried item or clears the slot (the pipe arm rule). */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof PipeMenu.GhostSlot ghost) {
            if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && canEdit(player)) {
                ItemStack carried = getCarried();
                if (carried.isEmpty() || button == 1 || type == ClickType.QUICK_MOVE) ghost.set(ItemStack.EMPTY);
                else ghost.set(new ItemStack(carried.getItem()));
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Shift-click in the inventory: the item becomes a filter entry and stays where it is. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int filterSlots = QuantumLinkBlockEntity.FILTER_SLOTS;
        if (index < filterSlots || index >= slots.size() || !canEdit(player)) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int free = -1;
        for (int i = 0; i < filterSlots; i++) {
            ItemStack entry = slots.get(i).getItem();
            if (ItemStack.isSameItem(entry, stack)) return ItemStack.EMPTY;
            if (entry.isEmpty() && free < 0) free = i;
        }
        if (free >= 0) slots.get(free).set(new ItemStack(stack.getItem()));
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return true;
        return !be.isRemoved() && player.level().getBlockEntity(pos) == be && player.distanceToSqr(pos.getCenter()) <= 64.0;
    }
}
