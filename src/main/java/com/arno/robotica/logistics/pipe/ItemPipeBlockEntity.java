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
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * One item pipe: the mode of each inventory link, cached item capabilities of its neighbours, and its network.
 * Every {@code pipeInterval} ticks each Extract link pulls up to {@code pipeItems} items and hands them to the Insert
 * links of the network in turn (round robin), through their item capabilities, so machine side configs and other mods'
 * inventories decide what fits. Items move instantly and never go back into the inventory they came from.
 */
public class ItemPipeBlockEntity extends BlockEntity {
    /** Insert simulations per pull at most, so a full network costs little; the start slot rotates when stuck. */
    private static final int MAX_TRIES = 64;

    private final PipeMode[] modes = new PipeMode[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IItemHandler, Direction>[] caches = new BlockCapabilityCache[6];
    @Nullable
    private PipeNetwork network;
    /** Set when a neighbour's item capability changed: the arms are checked on the next tick. */
    private boolean recheckLinks;
    private int nextDestination;
    private int nextSlot;

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsContent.ITEM_PIPE_BE.get(), pos, state);
        Arrays.fill(modes, PipeMode.INSERT);
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

    /** Pulls up to this tier's items from the inventory on {@code side} into the network's Insert links. */
    private void pull(ServerLevel level, Direction side) {
        IItemHandler source = handler(side);
        if (source == null || source.getSlots() == 0) return;
        List<PipeNetwork.Endpoint> targets = network(level).destinations();
        int count = targets.size();
        if (count == 0) return;
        BlockPos from = worldPosition.relative(side);
        int budget = LogisticsConfig.items(tier());
        int moved = 0, tries = 0, slots = source.getSlots();
        int start = Math.floorMod(nextDestination, count), resume = start;
        for (int s = 0; s < slots && moved < budget && tries < MAX_TRIES; s++) {
            int slot = (nextSlot + s) % slots;
            if (source.getStackInSlot(slot).isEmpty()) continue;
            for (int k = 0; k < count && moved < budget && tries < MAX_TRIES; k++) {
                int index = (start + k) % count;
                PipeNetwork.Endpoint target = targets.get(index);
                if (target.target().equals(from) || target.pipe().isRemoved()) continue;
                IItemHandler dest = target.pipe().handler(target.side());
                if (dest == null) continue;
                ItemStack offer = source.extractItem(slot, budget - moved, true);
                if (offer.isEmpty()) break;
                tries++;
                int fits = offer.getCount() - ItemHandlerHelper.insertItemStacked(dest, offer, true).getCount();
                if (fits <= 0) continue;
                ItemStack taken = source.extractItem(slot, fits, false);
                if (taken.isEmpty()) break;
                ItemStack left = ItemHandlerHelper.insertItemStacked(dest, taken, false);
                moved += taken.getCount() - left.getCount();
                if (!left.isEmpty()) giveBack(level, source, slot, left);
                resume = (index + 1) % count;
            }
        }
        nextDestination = resume;
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] bytes = tag.getByteArray("modes");
        for (int i = 0; i < 6; i++) modes[i] = i < bytes.length ? PipeMode.byId(bytes[i]) : PipeMode.INSERT;
    }
}
