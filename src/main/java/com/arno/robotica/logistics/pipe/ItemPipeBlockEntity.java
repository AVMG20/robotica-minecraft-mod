package com.arno.robotica.logistics.pipe;

import com.arno.robotica.logistics.LogisticsConfig;
import com.arno.robotica.logistics.LogisticsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * One item pipe: the mode, filter and order of each inventory link, cached item capabilities of its neighbours, and its
 * network. Every {@code pipeInterval} ticks each Extract link pulls up to {@code pipeItems} items that pass its filter and
 * hands them to the Insert links of the network whose filter takes them, highest priority first and within a priority in
 * turn (round robin) or nearest first, through their item capabilities, so machine side configs and other mods'
 * inventories decide what fits. Items move instantly and never go back into the inventory they came from.
 */
public class ItemPipeBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;

    private final PipeMode[] modes = new PipeMode[6];
    private final PipeOrder[] orders = new PipeOrder[6];
    private final boolean[] whitelist = new boolean[6];
    private final PipePriority[] priorities = new PipePriority[6];
    /** Per face: ghost entries only, never real items. Empty filter: everything passes. */
    private final ItemStackHandler[] filters = new ItemStackHandler[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IItemHandler, Direction>[] caches = new BlockCapabilityCache[6];
    @Nullable
    private PipeNetwork network;
    /** Set when a neighbour's item capability changed: the arms are checked on the next tick. */
    private boolean recheckLinks;
    /** Round robin position per priority. */
    private final int[] nextDestination = new int[PipePriority.values().length];
    private int nextSlot;

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsContent.ITEM_PIPE_BE.get(), pos, state);
        Arrays.fill(modes, PipeMode.INSERT);
        Arrays.fill(orders, PipeOrder.ROUND_ROBIN);
        Arrays.fill(priorities, PipePriority.DEFAULT);
        for (int i = 0; i < 6; i++) {
            filters[i] = new ItemStackHandler(FILTER_SLOTS) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }

                @Override
                public int getSlotLimit(int slot) {
                    return 1;
                }
            };
        }
    }

    public int tier() {
        return getBlockState().getBlock() instanceof ItemPipeBlock pipe ? pipe.tier() : 1;
    }

    public PipeMode mode(Direction side) {
        return modes[side.ordinal()];
    }

    public void setMode(Direction side, PipeMode mode) {
        modes[side.ordinal()] = mode;
        setChanged();
    }

    /** Sets the mode and shows it on the arm at once (server). */
    public void changeMode(Direction side, PipeMode mode) {
        setMode(side, mode);
        if (!(level instanceof ServerLevel server)) return;
        BlockState state = getBlockState();
        EnumProperty<PipeConnection> prop = ItemPipeBlock.prop(side);
        if (state.getValue(prop) == PipeConnection.PIPE || !ItemPipeBlock.hasInventory(server, worldPosition, side)) return;
        if (state.getValue(prop) != mode.connection) server.setBlock(worldPosition, state.setValue(prop, mode.connection), Block.UPDATE_ALL);
    }

    public PipeOrder order(Direction side) {
        return orders[side.ordinal()];
    }

    public void setOrder(Direction side, PipeOrder order) {
        orders[side.ordinal()] = order;
        setChanged();
    }

    public PipePriority priority(Direction side) {
        return priorities[side.ordinal()];
    }

    /** Sets the priority of an Insert face; the network re-sorts its Insert links on next use. */
    public void setPriority(Direction side, PipePriority priority) {
        if (priorities[side.ordinal()] == priority) return;
        priorities[side.ordinal()] = priority;
        setChanged();
        invalidateNetwork();
    }

    public boolean whitelist(Direction side) {
        return whitelist[side.ordinal()];
    }

    public void setWhitelist(Direction side, boolean on) {
        whitelist[side.ordinal()] = on;
        setChanged();
    }

    public ItemStackHandler filter(Direction side) {
        return filters[side.ordinal()];
    }

    /** Whitelist: only items matching an entry. Blacklist: everything else. An empty filter lets everything through. */
    public boolean passes(Direction side, ItemStack stack) {
        ItemStackHandler filter = filters[side.ordinal()];
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getStackInSlot(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (ItemStack.isSameItem(entry, stack)) return whitelist[side.ordinal()];
        }
        return !any || !whitelist[side.ordinal()];
    }

    // ---------------------------------------------------------------- network

    void joinNetwork(PipeNetwork net) {
        network = net;
    }

    /** The network this pipe is in, built when missing or invalid. Server only. */
    public PipeNetwork network(ServerLevel level) {
        if (network == null || !network.isValid()) network = PipeNetwork.build(level, this);
        return network;
    }

    /** Marks this pipe's network stale; every pipe in it rebuilds on its next use. */
    public void invalidateNetwork() {
        if (network != null) network.invalidate();
        network = null;
    }

    /** The item capability of the block on {@code side} (as seen from the pipe), or null. Server only, cached. */
    @Nullable
    public IItemHandler handler(Direction side) {
        if (!(level instanceof ServerLevel server)) return null;
        int i = side.ordinal();
        if (caches[i] == null) {
            caches[i] = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, server, worldPosition.relative(side), side.getOpposite(),
                    () -> !isRemoved(), () -> recheckLinks = true);
        }
        return caches[i].getCapability();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel server)) return;
        recheckLinks = true;
        // A pipe that loads next to loaded pipes joins their network.
        for (Direction dir : Direction.values()) {
            BlockPos at = worldPosition.relative(dir);
            if (server.isLoaded(at) && server.getBlockEntity(at) instanceof ItemPipeBlockEntity pipe) pipe.invalidateNetwork();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        invalidateNetwork();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }

    // ---------------------------------------------------------------- tick

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (recheckLinks) {
            recheckLinks = false;
            state = recheckLinks(level, pos, state);
        }
        if (Math.floorMod(level.getGameTime() + pos.asLong(), LogisticsConfig.interval(tier())) != 0) return;
        for (Direction dir : Direction.values()) {
            if (state.getValue(ItemPipeBlock.prop(dir)) == PipeConnection.EXTRACT) pull(level, dir);
        }
    }

    /**
     * Every face from scratch: next pipes, and arms to inventories as their capabilities say (a machine face set to
     * None loses its arm). Also fixes pipes placed without neighbour updates (structures, commands).
     */
    private BlockState recheckLinks(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState next = state;
        for (Direction dir : Direction.values()) {
            BlockPos at = pos.relative(dir);
            if (!level.isLoaded(at)) continue;
            PipeConnection link = level.getBlockState(at).getBlock() instanceof ItemPipeBlock ? PipeConnection.PIPE
                    : handler(dir) != null ? mode(dir).connection : PipeConnection.NONE;
            next = next.setValue(ItemPipeBlock.prop(dir), link);
        }
        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        return next;
    }

    /**
     * Pulls up to this tier's items that pass the filter from the inventory on {@code side} into the network's Insert
     * links. Targets come sorted by priority; a lower priority only gets what every higher one refuses.
     */
    private void pull(ServerLevel level, Direction side) {
        IItemHandler source = handler(side);
        if (source == null || source.getSlots() == 0) return;
        boolean closest = order(side) == PipeOrder.CLOSEST_FIRST;
        PipeNetwork net = network(level);
        List<PipeNetwork.Endpoint> targets = closest ? net.byDistance(level, this) : net.destinations();
        int count = targets.size();
        if (count == 0) return;
        int[] ends = net.groupEnds(targets);
        BlockPos from = worldPosition.relative(side);
        int budget = LogisticsConfig.items(tier());
        int maxTries = LogisticsConfig.maxInsertTries(), maxVisits = LogisticsConfig.maxTargetVisits();
        int moved = 0, tries = 0, visits = 0, slots = source.getSlots();
        for (int s = 0; s < slots && moved < budget && tries < maxTries && visits < maxVisits; s++) {
            int slot = (nextSlot + s) % slots;
            ItemStack inSlot = source.getStackInSlot(slot);
            if (inSlot.isEmpty() || !passes(side, inSlot)) continue;
            ItemStack item = inSlot.copyWithCount(1);
            ItemStack offer = null;
            groups:
            for (int g = 0, group = 0; group < count && moved < budget; group = ends[g++]) {
                int end = ends[g];
                int rank = targets.get(group).priority().ordinal();
                int size = end - group;
                int start = closest ? 0 : Math.floorMod(nextDestination[rank], size);
                for (int k = 0; k < size && moved < budget; k++) {
                    if (tries >= maxTries || visits >= maxVisits) {
                        // Out of checks: this priority's round robin resumes here on the next pull.
                        if (!closest) nextDestination[rank] = (start + k) % size;
                        break groups;
                    }
                    visits++;
                    PipeNetwork.Endpoint target = targets.get(group + (start + k) % size);
                    if (target.target().equals(from) || target.pipe().isRemoved() || !target.pipe().passes(target.side(), item)) continue;
                    IItemHandler dest = target.pipe().handler(target.side());
                    if (dest == null) continue;
                    if (offer == null) {
                        offer = source.extractItem(slot, budget - moved, true);
                        if (offer.isEmpty()) break groups;
                    }
                    tries++;
                    int fits = offer.getCount() - ItemHandlerHelper.insertItemStacked(dest, offer, true).getCount();
                    if (fits <= 0) continue;
                    ItemStack taken = source.extractItem(slot, fits, false);
                    if (taken.isEmpty()) break groups;
                    ItemStack left = ItemHandlerHelper.insertItemStacked(dest, taken, false);
                    moved += taken.getCount() - left.getCount();
                    if (!left.isEmpty()) giveBack(level, source, slot, left);
                    offer = null;
                    if (!closest) nextDestination[rank] = (start + k + 1) % size;
                }
            }
        }
        // Nothing could move: start at the next slot next time, so one stuck item does not block the rest.
        if (moved == 0) nextSlot = (nextSlot + 1) % slots;
    }

    /** What a destination refused after all (it said it would fit): back where it came from, else dropped. */
    private void giveBack(ServerLevel level, IItemHandler source, int slot, ItemStack stack) {
        ItemStack rest = source.insertItem(slot, stack, false);
        if (!rest.isEmpty()) rest = ItemHandlerHelper.insertItemStacked(source, rest, false);
        if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, rest);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) bytes[i] = (byte) modes[i].ordinal();
        tag.putByteArray("modes", bytes);
        byte[] order = new byte[6];
        byte[] white = new byte[6];
        CompoundTag filterTag = new CompoundTag();
        for (int i = 0; i < 6; i++) {
            order[i] = (byte) orders[i].ordinal();
            white[i] = (byte) (whitelist[i] ? 1 : 0);
            if (!isEmpty(filters[i])) filterTag.put(String.valueOf(i), filters[i].serializeNBT(registries));
        }
        byte[] prio = new byte[6];
        for (int i = 0; i < 6; i++) prio[i] = (byte) priorities[i].ordinal();
        tag.putByteArray("orders", order);
        tag.putByteArray("priorities", prio);
        tag.putByteArray("whitelist", white);
        if (!filterTag.isEmpty()) tag.put("filters", filterTag);
    }

    private static boolean isEmpty(ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) if (!handler.getStackInSlot(i).isEmpty()) return false;
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] bytes = tag.getByteArray("modes");
        for (int i = 0; i < 6; i++) modes[i] = i < bytes.length ? PipeMode.byId(bytes[i]) : PipeMode.INSERT;
        byte[] order = tag.getByteArray("orders");
        byte[] white = tag.getByteArray("whitelist");
        byte[] prio = tag.getByteArray("priorities");
        CompoundTag filterTag = tag.getCompound("filters");
        for (int i = 0; i < 6; i++) {
            orders[i] = i < order.length ? PipeOrder.byId(order[i]) : PipeOrder.ROUND_ROBIN;
            whitelist[i] = i < white.length && white[i] != 0;
            priorities[i] = i < prio.length ? PipePriority.byId(prio[i]) : PipePriority.DEFAULT;
            ItemStackHandler fresh = new ItemStackHandler(FILTER_SLOTS);
            if (filterTag.contains(String.valueOf(i))) fresh.deserializeNBT(registries, filterTag.getCompound(String.valueOf(i)));
            for (int slot = 0; slot < FILTER_SLOTS; slot++) filters[i].setStackInSlot(slot, slot < fresh.getSlots() ? fresh.getStackInSlot(slot) : ItemStack.EMPTY);
        }
    }
}
