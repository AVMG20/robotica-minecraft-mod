package com.arno.robotica.link.network;

import com.arno.robotica.link.block.LinkPriority;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One named network: owner, name, colour, visibility, and its buffer (FE and item stacks). The buffer lives in saved
 * data so it keeps what senders put in while the receivers are unloaded. It holds 5 s of FE and 9 item slots per sending
 * Link; the senders are listed by position so the size stays right while they are unloaded.
 *
 * <p>Runtime state (not saved): the Links that worked this server tick (for the shared upkeep), the receiving ones
 * bucketed by priority, and the loaded Links of the network (for colour changes). Links register themselves every tick
 * from their own (loaded) chunk, so nothing here ever reaches into an unloaded chunk.
 *
 * <p>Upkeep is paid by the network as a whole: every tick the Links that worked owe the sum of their upkeep, paid from
 * any of their own buffers and then from the network's. One powered Link keeps every Link of the network running, in
 * any dimension. When the sum can not be paid, nothing is taken and every Link waits a tick.
 */
public final class LinkNetwork {
    public static final int MAX_NAME = 24;
    public static final int COLORS = 16;
    /** Item slots of the buffer per sending Link. */
    public static final int SLOTS_PER_SENDER = 9;
    /** Ticks of each sender's rate the FE buffer holds (5 s). */
    public static final int BUFFER_TICKS = 100;

    /** Core, window and swatch colour per colour index, in dye order (white, orange, ... black), brightened to glow. */
    private static final int[] RGB = {
            0xF4F6FA, 0xFF9A2E, 0xE05BE0, 0x6CC8FF, 0xFFE04A, 0x86E83A, 0xFF9CC4, 0x8A929C,
            0xC6CCD2, 0x2ED8D0, 0xA45CFF, 0x4A6CFF, 0xB0784A, 0x4FAE3A, 0xFF4A3E, 0x4A4060};

    /** One sending Link: FE per tick it can put in (0 when it does not send energy) and whether it sends items. */
    public record Sender(int energyRate, boolean items) {}

    private final UUID id;
    private final UUID owner;
    private String ownerName;
    private String name;
    private int color;
    private Visibility visibility;
    private long energy;
    private final List<ItemStack> items = new ArrayList<>();
    private final Map<GlobalPos, Sender> senders = new LinkedHashMap<>();
    private long energyCapacity;
    private int itemSlots;
    @Nullable
    LinkNetworks data;

    // ---- runtime ----
    private boolean removed;
    private int accessVersion;
    long activeTick = Long.MIN_VALUE;
    @SuppressWarnings("unchecked")
    final List<QuantumLinkBlockEntity>[] energyReceivers = new List[LinkPriority.COUNT];
    @SuppressWarnings("unchecked")
    final List<QuantumLinkBlockEntity>[] itemReceivers = new List[LinkPriority.COUNT];
    private final int[] itemCursor = new int[LinkPriority.COUNT];
    /** Links that worked this tick; they owe their upkeep at the end of it. */
    final List<QuantumLinkBlockEntity> upkeepLinks = new ArrayList<>();
    /** Whether the last tick's upkeep was paid (Links wait while it was not), and what it came to. */
    private boolean powered;
    private long upkeepDue;
    private final Set<QuantumLinkBlockEntity> loaded = new HashSet<>();
    int validateCursor;

    public LinkNetwork(UUID id, UUID owner, String ownerName, String name, int color, Visibility visibility) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.name = name;
        this.color = Math.floorMod(color, COLORS);
        this.visibility = visibility;
        for (int i = 0; i < LinkPriority.COUNT; i++) {
            energyReceivers[i] = new ArrayList<>();
            itemReceivers[i] = new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- identity

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public String name() {
        return name;
    }

    /** Colour index 0-15 (dye order). */
    public int color() {
        return color;
    }

    public Visibility visibility() {
        return visibility;
    }

    /** 0xRRGGBB of a colour index. */
    public static int rgb(int color) {
        return RGB[Math.floorMod(color, COLORS)];
    }

    /** True once the owner deleted it; Links holding it go idle. */
    public boolean removed() {
        return removed;
    }

    /** Changes whenever who may use the network can have changed; Links check their access again. */
    public int accessVersion() {
        return accessVersion;
    }

    void markRemoved() {
        removed = true;
        accessVersion++;
        for (QuantumLinkBlockEntity link : List.copyOf(loaded)) link.networkChanged();
        loaded.clear();
        clearActive();
    }

    void setName(String value) {
        if (value.equals(name)) return;
        name = value;
        dirty();
        for (QuantumLinkBlockEntity link : loaded) link.networkChanged();
    }

    void setColor(int value) {
        int next = Math.floorMod(value, COLORS);
        if (next == color) return;
        color = next;
        dirty();
        for (QuantumLinkBlockEntity link : loaded) link.networkChanged();
    }

    void setVisibility(Visibility value) {
        if (value == visibility) return;
        visibility = value;
        accessVersion++;
        dirty();
    }

    void setOwnerName(String value) {
        if (value.equals(ownerName)) return;
        ownerName = value;
        dirty();
    }

    private void dirty() {
        if (data != null) data.setDirty();
    }

    // ---------------------------------------------------------------- loaded Links

    public void addLoaded(QuantumLinkBlockEntity link) {
        loaded.add(link);
    }

    public void removeLoaded(QuantumLinkBlockEntity link) {
        loaded.remove(link);
    }

    public int loadedCount() {
        return loaded.size();
    }

    // ---------------------------------------------------------------- senders and capacity

    /** Adds or updates a sending Link. Does nothing (and stays clean) when the entry is unchanged. */
    public void putSender(GlobalPos pos, int energyRate, boolean sendsItems) {
        Sender next = new Sender(Math.max(0, energyRate), sendsItems);
        if (next.energyRate() == 0 && !sendsItems) {
            removeSender(pos);
            return;
        }
        if (next.equals(senders.get(pos))) return;
        senders.put(pos, next);
        recalc();
        dirty();
    }

    public void removeSender(GlobalPos pos) {
        if (senders.remove(pos) != null) {
            recalc();
            dirty();
        }
    }

    public Map<GlobalPos, Sender> senders() {
        return Collections.unmodifiableMap(senders);
    }

    private void recalc() {
        long cap = 0;
        int slots = 0;
        for (Sender s : senders.values()) {
            cap += (long) s.energyRate() * BUFFER_TICKS;
            if (s.items()) slots += SLOTS_PER_SENDER;
        }
        energyCapacity = cap;
        itemSlots = slots;
        while (items.size() < itemSlots) items.add(ItemStack.EMPTY);
        compact();
    }

    // ---------------------------------------------------------------- energy buffer

    public long energy() {
        return energy;
    }

    public long energyCapacity() {
        return energyCapacity;
    }

    public long energySpace() {
        return Math.max(0, energyCapacity - energy);
    }

    /** Puts up to {@code max} FE in, as far as there is room. Returns what went in. */
    public int insertEnergy(int max) {
        int n = (int) Math.min(Math.max(0, max), energySpace());
        if (n > 0) {
            energy += n;
            dirty();
        }
        return n;
    }

    /** Takes up to {@code max} FE out. Returns what came out. */
    public int extractEnergy(int max) {
        int n = (int) Math.min(Math.max(0, max), energy);
        if (n > 0) {
            energy -= n;
            dirty();
        }
        return n;
    }

    /** Test and command use: sets the stored FE (clamped to 0, not to the capacity). */
    public void setEnergy(long value) {
        energy = Math.max(0, value);
        dirty();
    }

    // ---------------------------------------------------------------- item buffer

    /** Slots the senders give (9 each). */
    public int itemSlots() {
        return itemSlots;
    }

    /** Stored stacks, including slots past {@link #itemSlots} that still hold items after a sender left. */
    public int slotCount() {
        return items.size();
    }

    public ItemStack slot(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : ItemStack.EMPTY;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : items) if (!stack.isEmpty()) used++;
        return used;
    }

    /** Puts a stack in (merging first, then empty slots up to {@link #itemSlots}); returns what did not fit. */
    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int left = stack.getCount();
        int max = stack.getMaxStackSize();
        for (int i = 0; i < items.size() && left > 0; i++) {
            ItemStack in = items.get(i);
            if (in.isEmpty() || !ItemStack.isSameItemSameComponents(in, stack)) continue;
            int n = Math.min(left, max - in.getCount());
            if (n <= 0) continue;
            if (!simulate) in.grow(n);
            left -= n;
        }
        for (int i = 0; i < itemSlots && left > 0; i++) {
            if (!items.get(i).isEmpty()) continue;
            int n = Math.min(left, max);
            if (!simulate) items.set(i, stack.copyWithCount(n));
            left -= n;
        }
        if (!simulate && left != stack.getCount()) dirty();
        return left == stack.getCount() ? stack : left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
    }

    /** Takes up to {@code amount} items out of one slot. Slots keep their index until the next server tick. */
    public ItemStack extractItem(int index, int amount, boolean simulate) {
        if (index < 0 || index >= items.size() || amount <= 0) return ItemStack.EMPTY;
        ItemStack in = items.get(index);
        if (in.isEmpty()) return ItemStack.EMPTY;
        int n = Math.min(amount, Math.min(in.getCount(), in.getMaxStackSize()));
        ItemStack out = in.copyWithCount(n);
        if (!simulate) {
            in.shrink(n);
            if (in.isEmpty()) items.set(index, ItemStack.EMPTY);
            dirty();
        }
        return out;
    }

    /** Everything in the buffer, emptied (used when the network is deleted). */
    List<ItemStack> drainItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : items) if (!stack.isEmpty()) out.add(stack);
        items.clear();
        while (items.size() < itemSlots) items.add(ItemStack.EMPTY);
        dirty();
        return out;
    }

    /** Drops empty slots past the senders' slots. */
    private void compact() {
        for (int i = items.size() - 1; i >= itemSlots && i >= 0; i--) {
            if (items.get(i).isEmpty()) items.remove(i);
        }
    }

    // ---------------------------------------------------------------- per tick (receivers register, the server tick distributes)

    void clearActive() {
        for (int i = 0; i < LinkPriority.COUNT; i++) {
            energyReceivers[i].clear();
            itemReceivers[i].clear();
        }
        upkeepLinks.clear();
    }

    /** True when the last tick's upkeep was paid: the network's Links may work. */
    public boolean powered() {
        return powered;
    }

    /** FE per tick the network's working Links owed in the last tick. */
    public long upkeep() {
        return upkeepDue;
    }

    /**
     * Charges the upkeep of every Link that worked this tick, as one sum: from the Links' own buffers in turn, then from
     * the network's buffer. All or nothing; the result decides whether the Links work next tick.
     */
    void payUpkeep() {
        long due = 0, funds = energy;
        for (int i = 0; i < upkeepLinks.size(); i++) {
            QuantumLinkBlockEntity link = upkeepLinks.get(i);
            if (link.isRemoved()) continue;
            due += link.tier().upkeep();
            funds += link.upkeepFunds();
        }
        upkeepDue = due;
        if (funds < due) {
            powered = false;
            return;
        }
        powered = true;
        long left = due;
        for (int i = 0; i < upkeepLinks.size() && left > 0; i++) {
            QuantumLinkBlockEntity link = upkeepLinks.get(i);
            if (!link.isRemoved()) left -= link.payUpkeep((int) Math.min(Integer.MAX_VALUE, left));
        }
        if (left > 0) {
            energy -= left;
            dirty();
        }
    }

    /**
     * Hands out the stored FE to this tick's energy receivers: higher priority first, equal priority by fair share
     * (each gets the same part, what one can not take goes to the others). No allocation.
     */
    void distributeEnergy() {
        long available = energy;
        if (available <= 0) return;
        for (int p = 0; p < LinkPriority.COUNT && available > 0; p++) {
            List<QuantumLinkBlockEntity> list = energyReceivers[p];
            int n = list.size();
            if (n == 0) continue;
            long wanted = 0;
            int open = 0;
            for (int i = 0; i < n; i++) {
                QuantumLinkBlockEntity link = list.get(i);
                link.want = link.energyDemand();
                wanted += link.want;
                if (link.want > 0) open++;
            }
            if (wanted <= available) {
                for (int i = 0; i < n; i++) {
                    QuantumLinkBlockEntity link = list.get(i);
                    if (link.want > 0) link.acceptEnergy(link.want);
                }
                available -= wanted;
                continue;
            }
            while (available > 0 && open > 0) {
                long share = Math.max(1, available / open);
                for (int i = 0; i < n && available > 0; i++) {
                    QuantumLinkBlockEntity link = list.get(i);
                    if (link.want <= 0) continue;
                    int give = (int) Math.min(Math.min(share, link.want), available);
                    link.acceptEnergy(give);
                    link.want -= give;
                    available -= give;
                    if (link.want <= 0) open--;
                }
            }
        }
        if (available != energy) {
            energy = available;
            dirty();
        }
    }

    /**
     * Hands out stored stacks to this tick's item receivers: higher priority first; inside one priority the receivers
     * take turns (the next stack starts after the one that took the last), so equal priorities share.
     */
    void distributeItems() {
        boolean changed = false;
        for (int s = 0; s < items.size(); s++) {
            ItemStack stack = items.get(s);
            if (stack.isEmpty()) continue;
            for (int p = 0; p < LinkPriority.COUNT && !stack.isEmpty(); p++) {
                List<QuantumLinkBlockEntity> list = itemReceivers[p];
                int n = list.size();
                if (n == 0) continue;
                int start = Math.floorMod(itemCursor[p], n);
                for (int k = 0; k < n && !stack.isEmpty(); k++) {
                    int index = (start + k) % n;
                    if (list.get(index).deliver(stack) > 0) {
                        itemCursor[p] = index + 1;
                        changed = true;
                    }
                }
            }
            if (stack.isEmpty()) items.set(s, ItemStack.EMPTY);
        }
        if (changed) {
            compact();
            dirty();
        }
    }

    boolean hasItems() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return true;
        return false;
    }

    // ---------------------------------------------------------------- save

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putString("name", name);
        tag.putByte("color", (byte) color);
        tag.putByte("visibility", (byte) visibility.ordinal());
        tag.putLong("energy", energy);
        ListTag stacks = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            entry.put("item", stack.save(registries));
            stacks.add(entry);
        }
        tag.put("items", stacks);
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, Sender> e : senders.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(e.getKey().pos()));
            entry.putString("dim", e.getKey().dimension().location().toString());
            entry.putInt("rate", e.getValue().energyRate());
            entry.putBoolean("items", e.getValue().items());
            list.add(entry);
        }
        tag.put("senders", list);
        return tag;
    }

    /** Returns null when the tag is damaged. */
    @Nullable
    static LinkNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner")) return null;
        LinkNetwork net = new LinkNetwork(tag.getUUID("id"), tag.getUUID("owner"), tag.getString("ownerName"), tag.getString("name"),
                tag.getByte("color"), Visibility.byId(tag.getByte("visibility")));
        for (Tag t : tag.getList("senders", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            BlockPos pos = NbtUtils.readBlockPos(entry, "pos").orElse(null);
            ResourceLocation dim = ResourceLocation.tryParse(entry.getString("dim"));
            if (pos == null || dim == null) continue;
            net.senders.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), pos),
                    new Sender(entry.getInt("rate"), entry.getBoolean("items")));
        }
        net.recalc();
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            int slot = entry.getInt("slot");
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
            if (stack.isEmpty() || slot < 0 || slot > 4096) continue;
            while (net.items.size() <= slot) net.items.add(ItemStack.EMPTY);
            net.items.set(slot, stack);
        }
        net.compact();
        net.energy = Math.max(0, tag.getLong("energy"));
        return net;
    }
}
