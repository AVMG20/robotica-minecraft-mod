package com.arno.robotica.link.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.compat.OwnerNames;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.side.SideConfig;
import com.arno.robotica.link.LinkRegistry;
import com.arno.robotica.link.menu.LinkMenu;
import com.arno.robotica.link.menu.NetworkEntry;
import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.LinkNetworks;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.power.util.Owners;
import com.arno.robotica.power.util.SidedEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One Quantum Link: joins a named network and sends or receives FE and items through it. Senders fill the network's
 * buffer while their own chunk ticks; receivers register every tick from their own chunk and the network hands its
 * buffer out after all levels ticked ({@link LinkNetworks#tick}), so no Link ever reaches into an unloaded chunk.
 *
 * <p>FE goes in and out of every face: a receiver gives it out of all six (auto-eject pushes it), anything else takes it
 * in on all six (auto-input pulls it while sending). Items follow the Sides tab: Input faces take them in (auto-input
 * pulls from them while sending), Output faces give them out (auto-eject pushes while receiving). Pipes, cables,
 * hoppers and a Tesla Relay or Emitter sitting on top work through the capabilities. The network pays the upkeep of
 * all its Links together ({@link LinkNetwork#payUpkeep}), so one powered Link runs them all. No loss and no FE per item.
 */
public class QuantumLinkBlockEntity extends SyncedBlockEntity implements MenuProvider, InfoSource {
    public enum Status {
        NO_NETWORK, NO_ACCESS, NO_POWER, OFF, IDLE, ACTIVE;

        private static final Status[] VALUES = values();

        public boolean connected() {
            return this == IDLE || this == ACTIVE;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_NETWORK;
        }

        public String translationKey() {
            return "gui.robotica.link.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int FILTER_SLOTS = ItemPipeBlockEntity.FILTER_SLOTS;
    /** Block event ids: something went into (send) or came out of (receive) the network. Param: 0 energy, 1 items. */
    public static final int EVENT_SEND = 1, EVENT_RECEIVE = 2;
    private static final int ACCESS_CHECK_TICKS = 100;
    private static final int PULSE_GAP = 10;
    private static final int HUM_PERIOD = 80;
    /** A receiver whose inventories took nothing in a hand-out sits out this long (or until a neighbour changes). */
    private static final int ITEM_BACKOFF_TICKS = 30;
    private static final Direction[] DIRS = Direction.values();

    private final LinkTier tier;
    public final MachineEnergyStorage energy;
    /** Faces and the auto-input / auto-eject switches. Every face starts both ways, both switches on. */
    public final SideConfig sides = new SideConfig(this, this::itemRules);
    private final ItemStackHandler filter = new ItemStackHandler(FILTER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    @Nullable
    private UUID networkId;
    /** Name of the network when it was last seen, for the item tooltip. */
    private String networkName = "";
    private TransferMode energyMode = TransferMode.OFF;
    private TransferMode itemMode = TransferMode.OFF;
    private LinkPriority priority = LinkPriority.NORMAL;
    private boolean whitelist;
    @Nullable
    private UUID owner;
    private String ownerName = "";

    // ---- server runtime ----
    @Nullable
    private LinkNetwork net;
    private int seenAccessVersion = -1;
    private long nextAccessCheck;
    private boolean accessOk;
    private Status status = Status.NO_NETWORK;
    /** Set while the block is swapped for its next Mk in place: the network entry stays. */
    public boolean keepContents;
    @Nullable
    private LinkNetwork registeredNet;
    private int registeredRate;
    private boolean registeredItems;
    /** Scratch value of the network's energy hand-out. */
    public int want;
    private int itemBudget;
    /** Server tick of the last item hand-out that reached the inventories, and whether it tried and placed anything. */
    private long itemPassTick = Long.MIN_VALUE / 2;
    private boolean itemPassTried;
    private boolean itemPassTook;
    private long itemBackoffUntil;
    private boolean energyDirty;
    private long feWindow;
    private int itemWindow;
    private int feRate;
    private int itemRate;
    private long lastPulse = Long.MIN_VALUE / 2;
    private long activeUntil;
    private int syncedColor = -2;
    private boolean syncedOnline;
    private boolean recheckNeighbours = true;
    private final boolean[] linkNeighbour = new boolean[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IEnergyStorage, Direction>[] energyCaches = new BlockCapabilityCache[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IItemHandler, Direction>[] itemCaches = new BlockCapabilityCache[6];
    private final IEnergyStorage[] energyViews = new IEnergyStorage[2];
    private final IItemHandler items = new LinkItems();

    // ---- client ----
    private int clientColor = -1;
    private boolean clientOnline;
    private long clientPulse = Long.MIN_VALUE / 2;
    private @Nullable AABB renderBox;

    public QuantumLinkBlockEntity(BlockPos pos, BlockState state) {
        this(LinkRegistry.LINK_BE.get(), pos, state);
    }

    public QuantumLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = state.getBlock() instanceof QuantumLinkBlock block ? block.tier() : LinkTier.MK1;
        this.energy = new MachineEnergyStorage(tier.rate, tier.rate, tier.rate, () -> energyDirty = true);
        this.itemBudget = tier.itemsPerSecond();
        sides.setAutoInput(true);
        sides.setAutoEject(true);
    }

    // ---------------------------------------------------------------- getters

    public LinkTier tier() {
        return tier;
    }

    @Nullable
    public UUID networkId() {
        return networkId;
    }

    public String networkName() {
        return networkName;
    }

    @Nullable
    public LinkNetwork network() {
        return net;
    }

    public TransferMode energyMode() {
        return energyMode;
    }

    public TransferMode itemMode() {
        return itemMode;
    }

    public LinkPriority priority() {
        return priority;
    }

    public boolean whitelist() {
        return whitelist;
    }

    public ItemStackHandler filter() {
        return filter;
    }

    public Status status() {
        return status;
    }

    /** FE per tick moved over the last second. */
    public int feRate() {
        return feRate;
    }

    /** Items moved over the last second. */
    public int itemRate() {
        return itemRate;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** True when this Link sends anything into the network {@code id} (for the stale sender check). */
    public boolean sendsTo(UUID id) {
        if (!id.equals(networkId) || (energyMode != TransferMode.SEND && itemMode != TransferMode.SEND)) return false;
        return status != Status.NO_ACCESS;
    }

    private GlobalPos key() {
        return GlobalPos.of(Objects.requireNonNull(level).dimension(), worldPosition);
    }

    // ---------------------------------------------------------------- owner and settings

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Owner, the owner's team or an operator may open it. Without an owner (placed by commands): everyone. */
    public boolean canOpen(Player player) {
        return player.hasPermissions(2) || Owners.allied(level, owner, ownerName, player);
    }

    public void setEnergyMode(TransferMode mode) {
        if (mode == energyMode) return;
        energyMode = mode;
        settingsChanged();
    }

    public void setItemMode(TransferMode mode) {
        if (mode == itemMode) return;
        itemMode = mode;
        settingsChanged();
    }

    public void setPriority(LinkPriority value) {
        priority = value;
        setChanged();
    }

    public void setWhitelist(boolean on) {
        whitelist = on;
        setChanged();
    }

    private void settingsChanged() {
        setChanged();
        resetItemBackoff();
        // Cables and pipes cache capabilities: the views of every face change with the modes.
        if (level != null && !level.isClientSide) level.invalidateCapabilities(worldPosition);
    }

    /** Joins a network (null leaves). The caller checked that the player and the Link's owner may use it. */
    public void joinNetwork(@Nullable LinkNetwork next) {
        UUID id = next == null ? null : next.id();
        if (Objects.equals(id, networkId) && net == next) return;
        boolean hadNetwork = networkId != null;
        leaveRuntime(true);
        networkId = id;
        networkName = next == null ? "" : next.name();
        seenAccessVersion = -1;
        nextAccessCheck = 0;
        status = Status.NO_NETWORK;
        if (next != null && !next.removed()) attach(next);
        setChanged();
        syncVisuals();
        if (level != null) {
            if (next != null) CoreSounds.play(level, worldPosition, CoreSounds.LINK_JOIN, SoundSource.BLOCKS, 0.8F, 1.0F);
            else if (hadNetwork) CoreSounds.play(level, worldPosition, CoreSounds.LINK_LEAVE, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    /** The network was renamed, recoloured or deleted: look again on the next tick. */
    public void networkChanged() {
        if (net != null && net.removed()) net = null;
        seenAccessVersion = -1;
        if (net != null) networkName = net.name();
        syncVisuals();
    }

    private void attach(LinkNetwork found) {
        net = found;
        found.addLoaded(this);
        networkName = found.name();
    }

    /** Forgets the runtime network; {@code unregister} also takes this Link out of its sender list. */
    private void leaveRuntime(boolean unregister) {
        if (unregister && registeredNet != null && level != null) registeredNet.removeSender(key());
        if (unregister) {
            registeredNet = null;
            registeredRate = 0;
            registeredItems = false;
        }
        if (net != null) net.removeLoaded(this);
        net = null;
    }

    /** The block was broken (not unloaded): leave the sender list. The item keeps the network and settings. */
    public void onBroken() {
        if (!(level instanceof ServerLevel server)) return;
        if (registeredNet == null && networkId != null) {
            LinkNetwork stored = LinkNetworks.get(server.getServer()).get(networkId);
            if (stored != null) stored.removeSender(key());
        }
        leaveRuntime(true);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Broken or unloaded: either way it no longer ticks. An unloaded sender keeps its place (the buffer size stays).
        leaveRuntime(false);
    }

    // ---------------------------------------------------------------- capabilities

    /** FE capability of every face: out while receiving, in otherwise (the Sides tab is for items only). */
    public IEnergyStorage energyFor(@Nullable Direction side) {
        boolean out = energyMode == TransferMode.RECEIVE;
        int index = out ? 1 : 0;
        if (energyViews[index] == null) energyViews[index] = new SidedEnergy(energy, !out, out);
        return energyViews[index];
    }

    /** Item capability of a face, as the Sides tab allows. */
    @Nullable
    public IItemHandler itemsFor(@Nullable Direction side) {
        return sides.access(side);
    }

    private IItemHandler itemRules() {
        return items;
    }

    // ---------------------------------------------------------------- server tick

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        long now = level.getServer().getTickCount();
        if (recheckNeighbours) refreshNeighbours(level);
        if (Math.floorMod(now + pos.asLong(), 20) == 0) {
            itemBudget = tier.itemsPerSecond();
            feRate = (int) Math.min(Integer.MAX_VALUE, feWindow / 20);
            itemRate = itemWindow;
            feWindow = 0;
            itemWindow = 0;
        }
        status = resolve(level, now);
        if (status == Status.IDLE) work(level, pos, now);
        if (status.connected() && now < activeUntil) {
            status = Status.ACTIVE;
            if (CoreSounds.due(level, pos, HUM_PERIOD)) CoreSounds.play(level, pos, CoreSounds.LINK_HUM, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        if (energyDirty) {
            energyDirty = false;
            setChanged();
        }
        syncVisuals();
    }

    /** Checks the network and access. IDLE means: connected, ready to work this tick. */
    private Status resolve(ServerLevel level, long now) {
        if (networkId == null) return Status.NO_NETWORK;
        MinecraftServer server = level.getServer();
        if (net == null || net.removed() || !net.id().equals(networkId)) {
            if (net != null) leaveRuntime(false);
            LinkNetwork found = LinkNetworks.get(server).get(networkId);
            if (found == null) {
                // deleted while this Link was away: forget it and idle
                leaveRuntime(true);
                networkId = null;
                networkName = "";
                setChanged();
                return Status.NO_NETWORK;
            }
            attach(found);
        }
        if (seenAccessVersion != net.accessVersion() || now >= nextAccessCheck) {
            accessOk = LinkNetworks.canUse(server, net, owner, ownerName);
            seenAccessVersion = net.accessVersion();
            nextAccessCheck = now + ACCESS_CHECK_TICKS;
        }
        if (!accessOk) {
            syncSender(false);
            return Status.NO_ACCESS;
        }
        syncSender(true);
        if (energyMode == TransferMode.OFF && itemMode == TransferMode.OFF) return Status.OFF;
        return Status.IDLE;
    }

    /** Keeps this Link's entry in the network's sender list in step with its modes (map work only on a change). */
    private void syncSender(boolean active) {
        int rate = active && energyMode == TransferMode.SEND ? tier.rate : 0;
        boolean sendsItems = active && itemMode == TransferMode.SEND;
        LinkNetwork target = rate > 0 || sendsItems ? net : null;
        if (target == registeredNet && rate == registeredRate && sendsItems == registeredItems) return;
        GlobalPos key = key();
        if (registeredNet != null && registeredNet != target) registeredNet.removeSender(key);
        if (target != null) target.putSender(key, rate, sendsItems);
        registeredNet = target;
        registeredRate = rate;
        registeredItems = sendsItems;
    }

    private void work(ServerLevel level, BlockPos pos, long now) {
        LinkNetwork network = Objects.requireNonNull(net);
        LinkNetworks data = LinkNetworks.get(level.getServer());
        if (energyMode == TransferMode.SEND && sides.autoInput()) pullEnergy(level, pos);
        // The network charges this tick's upkeep after all levels ticked; until it was paid nothing moves.
        data.addUpkeep(network, this, now);
        if (!network.powered()) {
            status = Status.NO_POWER;
            return;
        }
        if (energyMode == TransferMode.SEND) {
            int n = (int) Math.min(Math.min(energy.getEnergyStored(), tier.rate), network.energySpace());
            if (n > 0 && energy.consume(n)) {
                network.insertEnergy(n);
                moved(true, false, n);
            }
        } else if (energyMode == TransferMode.RECEIVE) {
            if (sides.autoEject()) pushEnergy(level, pos);
            data.addEnergyReceiver(network, this, now);
        }
        if (itemMode == TransferMode.SEND) {
            if (sides.autoInput() && Math.floorMod(now + pos.asLong(), 20) == 0) pullItems(level, pos, network);
        } else if (itemMode == TransferMode.RECEIVE) {
            data.addItemReceiver(network, this, now);
        }
        data.touch(network, now);
    }

    // ---------------------------------------------------------------- neighbours

    /**
     * Looks for Links next to this one. Every face gets its capability cache here, so a Link placed or broken next to it
     * (even on a face it never used) triggers the next check.
     */
    private void refreshNeighbours(ServerLevel level) {
        recheckNeighbours = false;
        for (Direction dir : DIRS) {
            energyCache(level, dir);
            BlockPos at = worldPosition.relative(dir);
            linkNeighbour[dir.ordinal()] = level.isLoaded(at) && level.getBlockEntity(at) instanceof QuantumLinkBlockEntity;
        }
    }

    /** Next to another Link nothing moves: the two would only pass the same things back and forth. */
    private boolean usable(ServerLevel level, Direction dir) {
        return !linkNeighbour[dir.ordinal()] && level.isLoaded(worldPosition.relative(dir));
    }

    private BlockCapabilityCache<IEnergyStorage, Direction> energyCache(ServerLevel level, Direction dir) {
        int i = dir.ordinal();
        if (energyCaches[i] == null) {
            energyCaches[i] = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, worldPosition.relative(dir), dir.getOpposite(),
                    () -> !isRemoved(), () -> recheckNeighbours = true);
        }
        return energyCaches[i];
    }

    @Nullable
    private IEnergyStorage energyCap(ServerLevel level, Direction dir) {
        return energyCache(level, dir).getCapability();
    }

    @Nullable
    private IItemHandler itemCap(ServerLevel level, Direction dir) {
        int i = dir.ordinal();
        if (itemCaches[i] == null) {
            itemCaches[i] = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, worldPosition.relative(dir), dir.getOpposite(),
                    () -> !isRemoved(), () -> {
                        recheckNeighbours = true;
                        resetItemBackoff();
                    });
        }
        return itemCaches[i].getCapability();
    }

    private void pullEnergy(ServerLevel level, BlockPos pos) {
        for (Direction dir : DIRS) {
            int space = energy.getSpace();
            if (space <= 0) return;
            if (!usable(level, dir)) continue;
            IEnergyStorage source = energyCap(level, dir);
            if (source == null || !source.canExtract()) continue;
            int got = source.extractEnergy(space, false);
            if (got > 0) energy.generate(got);
        }
    }

    private void pushEnergy(ServerLevel level, BlockPos pos) {
        int budget = Math.min(energy.getEnergyStored(), tier.rate);
        for (Direction dir : DIRS) {
            if (budget <= 0) return;
            if (!usable(level, dir)) continue;
            IEnergyStorage target = energyCap(level, dir);
            if (target == null || !target.canReceive()) continue;
            int sent = target.receiveEnergy(budget, false);
            if (sent > 0 && energy.consume(sent)) budget -= sent;
        }
    }

    /** Once a second while sending items: up to the per-second budget from inventories on Input faces into the network. */
    private void pullItems(ServerLevel level, BlockPos pos, LinkNetwork network) {
        int moved = 0;
        for (Direction dir : DIRS) {
            if (itemBudget <= 0) break;
            if (!sides.mode(dir).input || !usable(level, dir)) continue;
            IItemHandler source = itemCap(level, dir);
            if (source == null) continue;
            for (int slot = 0; slot < source.getSlots() && itemBudget > 0; slot++) {
                ItemStack sim = source.extractItem(slot, itemBudget, true);
                if (sim.isEmpty()) continue;
                int fits = sim.getCount() - network.insertItem(sim, true).getCount();
                if (fits <= 0) continue;
                ItemStack taken = source.extractItem(slot, fits, false);
                if (taken.isEmpty()) continue;
                ItemStack left = network.insertItem(taken, false);
                int done = taken.getCount() - left.getCount();
                itemBudget -= done;
                moved += done;
                if (!left.isEmpty()) giveBack(level, source, slot, left);
            }
        }
        if (moved > 0) moved(true, true, moved);
    }

    private void giveBack(ServerLevel level, IItemHandler source, int slot, ItemStack stack) {
        ItemStack rest = source.insertItem(slot, stack, false);
        if (!rest.isEmpty()) rest = ItemHandlerHelper.insertItemStacked(source, rest, false);
        if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, rest);
    }

    // ---------------------------------------------------------------- called by the network hand-out

    /** FE in this Link's own buffer that can go to the network's upkeep. */
    public int upkeepFunds() {
        return energy.getEnergyStored();
    }

    /** Pays up to {@code max} FE of the network's upkeep from this Link's own buffer. Returns what it paid. */
    public int payUpkeep(int max) {
        int n = Math.min(Math.max(0, max), energy.getEnergyStored());
        return n > 0 && energy.consume(n) ? n : 0;
    }

    /** FE this receiver takes this tick: the room in its own buffer (one tick of its rate). */
    public int energyDemand() {
        if (isRemoved() || energyMode != TransferMode.RECEIVE) return 0;
        return energy.getSpace();
    }

    public void acceptEnergy(int amount) {
        int added = energy.generate(amount);
        if (added > 0) moved(false, false, added);
    }

    /**
     * Pushes as much of {@code stack} as passes the filter and fits (this second's budget) into the inventories on
     * Output faces. Shrinks the stack by what went in and returns that. After a hand-out where the inventories took
     * nothing, this receiver sits out {@link #ITEM_BACKOFF_TICKS} so full ones are not scanned on every hand-out.
     */
    public int deliver(ItemStack stack) {
        if (isRemoved() || itemMode != TransferMode.RECEIVE || itemBudget <= 0 || !sides.autoEject()) return 0;
        if (!(level instanceof ServerLevel server) || !passes(stack)) return 0;
        long now = server.getServer().getTickCount();
        if (now != itemPassTick) {
            if (itemPassTried && !itemPassTook) itemBackoffUntil = itemPassTick + ITEM_BACKOFF_TICKS;
            itemPassTick = now;
            itemPassTried = false;
            itemPassTook = false;
        }
        if (now < itemBackoffUntil) return 0;
        int moved = 0;
        for (Direction dir : DIRS) {
            if (stack.isEmpty() || itemBudget <= 0) break;
            if (!sides.mode(dir).output || !usable(server, dir)) continue;
            IItemHandler target = itemCap(server, dir);
            if (target == null) continue;
            itemPassTried = true;
            int n = Math.min(stack.getCount(), itemBudget);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copyWithCount(n), false);
            int done = n - rest.getCount();
            if (done <= 0) continue;
            stack.shrink(done);
            itemBudget -= done;
            moved += done;
        }
        if (moved > 0) {
            itemPassTook = true;
            moved(false, true, moved);
        }
        return moved;
    }

    /** An inventory next to it changed (or its settings did): hand it items again on the next hand-out. */
    public void resetItemBackoff() {
        itemBackoffUntil = 0;
        itemPassTried = false;
    }

    /** True while this receiver sits out the item hand-out after its inventories took nothing. */
    public boolean itemBackedOff() {
        return level instanceof ServerLevel server && server.getServer().getTickCount() < itemBackoffUntil;
    }

    /** Whitelist: only listed items. Blacklist: all but listed. Empty: everything (the pipe arm rule). */
    public boolean passes(ItemStack stack) {
        return ItemPipeBlockEntity.passes(filter, whitelist, stack);
    }

    /** Book-keeping, the core pulse (a block event, at most every {@link #PULSE_GAP} ticks) and the hum. */
    private void moved(boolean send, boolean isItems, int amount) {
        if (isItems) itemWindow += amount;
        else feWindow += amount;
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getServer().getTickCount();
        activeUntil = now + 20;
        if (now - lastPulse >= PULSE_GAP) {
            lastPulse = now;
            server.blockEvent(worldPosition, getBlockState().getBlock(), send ? EVENT_SEND : EVENT_RECEIVE, isItems ? 1 : 0);
        }
    }

    // ---------------------------------------------------------------- item capability

    /**
     * Sending: one slot that takes items into the network buffer (per-second budget). Receiving: the network's slots,
     * filtered, to take items out of (same budget). Off or not connected: no slots.
     */
    private final class LinkItems implements IItemHandler {
        private boolean live() {
            return net != null && !net.removed() && status.connected();
        }

        @Override
        public int getSlots() {
            if (!live()) return 0;
            return itemMode == TransferMode.SEND ? 1 : itemMode == TransferMode.RECEIVE ? net.slotCount() : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (!live() || itemMode != TransferMode.RECEIVE) return ItemStack.EMPTY;
            ItemStack stack = net.slot(slot);
            return passes(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !live() || itemMode != TransferMode.SEND || itemBudget <= 0) return stack;
            int n = Math.min(stack.getCount(), itemBudget);
            ItemStack rest = net.insertItem(stack.copyWithCount(n), simulate);
            int accepted = n - rest.getCount();
            if (accepted <= 0) return stack;
            if (!simulate) {
                itemBudget -= accepted;
                moved(true, true, accepted);
            }
            return accepted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!live() || itemMode != TransferMode.RECEIVE || itemBudget <= 0 || amount <= 0) return ItemStack.EMPTY;
            ItemStack in = net.slot(slot);
            if (in.isEmpty() || !passes(in)) return ItemStack.EMPTY;
            ItemStack out = net.extractItem(slot, Math.min(amount, itemBudget), simulate);
            if (!simulate && !out.isEmpty()) {
                itemBudget -= out.getCount();
                moved(false, true, out.getCount());
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return itemMode == TransferMode.SEND;
        }
    }

    // ---------------------------------------------------------------- client visuals

    /** Network colour (-1 none) and whether it is connected, synced only when one changes. */
    private void syncVisuals() {
        int color = net != null && !net.removed() ? net.color() : -1;
        boolean online = status.connected();
        if (color == syncedColor && online == syncedOnline) return;
        syncedColor = color;
        syncedOnline = online;
        setChangedAndSync();
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("crystal", syncedColor < -1 ? -1 : syncedColor);
        tag.putBoolean("online", syncedOnline);
    }

    /** Colour index of the core and windows on the client, -1 without a network. */
    public int clientColor() {
        return clientColor;
    }

    public boolean clientOnline() {
        return clientOnline;
    }

    /** Game time of the last pulse on the client. */
    public long clientPulse() {
        return clientPulse;
    }

    /** The block with a hair of room for the core and windows, for render culling. */
    public AABB renderBox() {
        if (renderBox == null) renderBox = new AABB(worldPosition).inflate(0.01);
        return renderBox;
    }

    /** Server: true so the event reaches watching clients. Client: the renderer flares the core. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_SEND && id != EVENT_RECEIVE) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            clientPulse = level.getGameTime();
        }
        return true;
    }

    // ---------------------------------------------------------------- menu, info

    public void openMenu(ServerPlayer player) {
        List<NetworkEntry> entries = LinkNetworks.get(player.server).entriesFor(player.server, player);
        player.openMenu(this, buf -> LinkMenu.writeOpenData(buf, this, entries));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new LinkMenu(id, inv, this);
    }

    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        info.tier = tier.mk;
        info.status = switch (status) {
            case ACTIVE -> "working";
            case NO_POWER -> "no_energy";
            case NO_NETWORK -> "no_network";
            case NO_ACCESS -> "no_access";
            case OFF -> "off";
            case IDLE -> "idle";
        };
        info.owner = owner == null ? null : OwnerNames.name(level.getServer(), owner);
    }

    // ---------------------------------------------------------------- save, item components

    /** Network, modes, priority, filter and sides: what the dropped item keeps. */
    private CompoundTag writeSettings(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (networkId != null) {
            tag.putUUID("network", networkId);
            tag.putString("networkName", networkName);
        }
        tag.putByte("energyMode", (byte) energyMode.ordinal());
        tag.putByte("itemMode", (byte) itemMode.ordinal());
        tag.putByte("priority", (byte) priority.ordinal());
        tag.putBoolean("whitelist", whitelist);
        tag.put("filter", filter.serializeNBT(registries));
        tag.put("sides", sides.save());
        return tag;
    }

    private void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        UUID id = tag.hasUUID("network") ? tag.getUUID("network") : null;
        if (!Objects.equals(id, networkId)) {
            leaveRuntime(true);
            networkId = id;
        }
        networkName = tag.getString("networkName");
        energyMode = TransferMode.byId(tag.getByte("energyMode"));
        itemMode = TransferMode.byId(tag.getByte("itemMode"));
        priority = tag.contains("priority") ? LinkPriority.byId(tag.getByte("priority")) : LinkPriority.NORMAL;
        whitelist = tag.getBoolean("whitelist");
        if (tag.contains("filter")) {
            ItemStackHandler fresh = new ItemStackHandler(FILTER_SLOTS);
            fresh.deserializeNBT(registries, tag.getCompound("filter"));
            for (int i = 0; i < FILTER_SLOTS; i++) filter.setStackInSlot(i, i < fresh.getSlots() ? fresh.getStackInSlot(i) : ItemStack.EMPTY);
        }
        if (tag.contains("sides")) sides.load(tag.getCompound("sides"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("settings", writeSettings(registries));
        tag.put("energy", energy.serializeNBT(registries));
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("ownerName", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("settings")) readSettings(tag.getCompound("settings"), registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.contains("ownerName")) ownerName = tag.getString("ownerName");
        // client tag
        if (tag.contains("crystal")) clientColor = tag.getInt("crystal");
        if (tag.contains("online")) clientOnline = tag.getBoolean("online");
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level != null) {
            // Settings a fresh Link already has stay off the item, so mined Links stack with new ones.
            CompoundTag settings = writeSettings(level.registryAccess());
            CompoundTag fresh = new QuantumLinkBlockEntity(getType(), worldPosition, getBlockState()).writeSettings(level.registryAccess());
            if (!settings.equals(fresh)) components.set(LinkRegistry.LINK_SETTINGS.get(), settings);
        }
        if (energy.getEnergyStored() > 0) components.set(CoreComponents.ENERGY.get(), energy.getEnergyStored());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        CompoundTag settings = input.get(LinkRegistry.LINK_SETTINGS.get());
        if (settings != null && level != null) readSettings(settings, level.registryAccess());
        Integer stored = input.get(CoreComponents.ENERGY.get());
        if (stored != null) energy.setEnergy(stored);
    }
}
