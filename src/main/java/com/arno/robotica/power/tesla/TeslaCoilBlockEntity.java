package com.arno.robotica.power.tesla;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.PowerBlockEntity;
import com.arno.robotica.power.menu.EnergyInfoMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Links of one Tesla Coil plus its per-tick bookkeeping. A coil whose support block gives out FE is a root: every tick it
 * runs {@link TeslaNetwork#pushFromSource}. Every other coil only forwards what roots send it. The links and a coarse
 * flow level (0-4, how hard the coil works) are synced to the client for the link glow; nothing else is.
 */
public class TeslaCoilBlockEntity extends PowerBlockEntity implements MenuProvider, EnergyInfoMenu.Source {
    private final List<TeslaLink> links = new ArrayList<>();
    /** Capability caches parallel to {@link #links}; null for coil links and until first use. Server only. */
    private final List<BlockCapabilityCache<IEnergyStorage, Direction>> caches = new ArrayList<>();
    @Nullable
    private UUID owner;
    private String ownerName = "";

    @Nullable
    private BlockCapabilityCache<IEnergyStorage, Direction> sourceSided, sourceAny;
    private long sentTick = Long.MIN_VALUE;
    private int sentThisTick;
    private long windowSent;
    private int lastRate;
    private boolean root;
    private int rotation;
    /** Synced: 0 idle, 1-4 how much of the tier rate went out over the last second. */
    private int flow;
    /** Server: a new non-zero flow level must show up twice in a row before it is synced, so a rate on an edge does not resend. */
    private int pendingFlow;
    private long nextHum;

    /** Client: area the arcs cover, for frustum culling. */
    @Nullable
    private AABB renderBox;
    /** Client: link animation state of the renderer: last frame time, how far the motes have travelled, eased flow level. */
    public double fxTime = -1, fxTravel;
    public float fxFlow;

    public TeslaCoilBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.TESLA_COIL_BE.get(), pos, state);
    }

    public TeslaTier tier() {
        return getBlockState().getBlock() instanceof TeslaCoilBlock coil ? coil.tier() : TeslaTier.I;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(TeslaCoilBlock.FACING) ? state.getValue(TeslaCoilBlock.FACING) : Direction.UP;
    }

    /** The block the coil sits on. */
    public BlockPos supportPos() {
        return worldPosition.relative(facing().getOpposite());
    }

    public List<TeslaLink> links() {
        return Collections.unmodifiableList(links);
    }

    public int linkCount() {
        return links.size();
    }

    /** Link {@code index} without the list wrapper (the renderer reads links every frame). */
    public TeslaLink link(int index) {
        return links.get(index);
    }

    public int maxLinks() {
        return tier().maxLinks;
    }

    /** FE/t sent over the last second (including what hops lose). */
    public int sentRate() {
        return lastRate;
    }

    /** True while the support block gives out FE. */
    public boolean isRoot() {
        return root;
    }

    /** Synced: energy went out during the last second. */
    public boolean isActive() {
        return flow > 0;
    }

    /** Synced: 0 idle, 1-4 how much of the tier rate went out over the last second. */
    public int flowLevel() {
        return flow;
    }

    /** 0 when nothing went out, else 1-4 by the share of {@code max} that {@code rate} uses. */
    static int flowLevel(int rate, int max) {
        if (rate <= 0) return 0;
        double f = (double) rate / Math.max(1, max);
        return f < 0.1 ? 1 : f < 0.35 ? 2 : f < 0.7 ? 3 : 4;
    }

    // ---- owner ----

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Owner, the owner's team, or an operator. A coil without owner (placed by commands) is open to everyone. */
    public boolean canEdit(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        Team mine = player.getTeam();
        if (mine == null || !(level instanceof ServerLevel serverLevel)) return false;
        String name = ownerName;
        MinecraftServer server = serverLevel.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) name = online.getGameProfile().getName();
        Team theirs = serverLevel.getScoreboard().getPlayersTeam(name);
        return theirs != null && mine.isAlliedTo(theirs);
    }

    // ---- links ----

    public int indexOf(BlockPos pos) {
        for (int i = 0; i < links.size(); i++) {
            if (links.get(i).pos().equals(pos)) return i;
        }
        return -1;
    }

    void addLink(TeslaLink link) {
        links.add(link);
        caches.add(null);
        setChangedAndSync();
    }

    void removeLink(int index) {
        links.remove(index);
        caches.remove(index);
        setChangedAndSync();
    }

    void setLink(int index, TeslaLink link) {
        links.set(index, link);
        caches.set(index, null);
        setChangedAndSync();
    }

    /** The FE capability of machine link {@code index} on its face, or null (not loaded, gone, or a coil link). */
    @Nullable
    IEnergyStorage targetCapability(int index) {
        TeslaLink link = links.get(index);
        if (link.coil() || !(level instanceof ServerLevel serverLevel)) return null;
        BlockCapabilityCache<IEnergyStorage, Direction> cache = caches.get(index);
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, link.pos(), link.face());
            caches.set(index, cache);
        }
        return cache.getCapability();
    }

    /** The support block's FE storage if it gives energy out: first through the touching face, else its internal view. */
    @Nullable
    IEnergyStorage source() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        if (sourceSided == null || sourceAny == null) {
            BlockPos support = supportPos();
            sourceSided = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, support, facing());
            sourceAny = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, support, null);
        }
        IEnergyStorage sided = sourceSided.getCapability();
        if (sided != null && sided.canExtract()) return sided;
        IEnergyStorage any = sourceAny.getCapability();
        return any != null && any.canExtract() ? any : null;
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        sourceSided = null;
        sourceAny = null;
    }

    /** FE this coil may still send in tick {@code now}. */
    int remainingRate(long now) {
        if (sentTick != now) {
            sentTick = now;
            sentThisTick = 0;
        }
        return Math.max(0, tier().rate() - sentThisTick);
    }

    void recordSent(long now, int amount) {
        if (amount <= 0) return;
        remainingRate(now);
        sentThisTick += amount;
        windowSent += amount;
    }

    /** Start index for this tick's fair split, so the rounding remainder moves round the links. */
    int nextRotation(int n) {
        rotation = (rotation + 1) % Math.max(1, n);
        return rotation;
    }

    /** Drops links whose target is loaded but gone: no coil there any more, or no FE capability at all. */
    void validateLinks() {
        if (level == null) return;
        boolean changed = false;
        for (int i = links.size() - 1; i >= 0; i--) {
            TeslaLink link = links.get(i);
            if (!level.isLoaded(link.pos())) continue;
            boolean gone = link.coil()
                    ? !(level.getBlockEntity(link.pos()) instanceof TeslaCoilBlockEntity)
                    : level.getCapability(Capabilities.EnergyStorage.BLOCK, link.pos(), link.face()) == null
                    && level.getCapability(Capabilities.EnergyStorage.BLOCK, link.pos(), null) == null;
            if (gone) {
                links.remove(i);
                caches.remove(i);
                changed = true;
            }
        }
        if (changed) setChangedAndSync();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        IEnergyStorage source = links.isEmpty() ? null : source();
        root = source != null;
        if (source != null) TeslaNetwork.pushFromSource(this, source, now);
        if ((now + (pos.asLong() & 31)) % 40 == 0) validateLinks();
        if (now % 20 == 0) {
            lastRate = (int) Math.min(Integer.MAX_VALUE, windowSent / 20);
            windowSent = 0;
            // At most one sync per second, only on change; steps between two busy levels need two windows in a row.
            int target = flowLevel(lastRate, tier().rate());
            if (target == flow) {
                pendingFlow = flow;
            } else if (target == 0 || flow == 0 || target == pendingFlow) {
                flow = target;
                pendingFlow = target;
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            } else {
                pendingFlow = target;
            }
        }
        // A soft hum only for players standing right next to the coil, and rarely. No zaps from idle networks.
        if (flow > 0 && now >= nextHum) {
            nextHum = now + 240 + level.random.nextInt(240);
            if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5.0, false) != null) {
                CoreSounds.play(level, pos, CoreSounds.CHARGER_HUM, SoundSource.BLOCKS, 0.05F, 1.5F + level.random.nextFloat() * 0.2F);
            }
        }
    }

    // ---- rendering helpers (client and server) ----

    /** World position an arc of this link ends at: the other coil's tip, or the middle of the linked face. */
    public static Vec3 endPoint(Level level, TeslaLink link) {
        BlockPos p = link.pos();
        if (link.coil()) {
            BlockState state = level.getBlockState(p);
            Direction f = state.hasProperty(TeslaCoilBlock.FACING) ? state.getValue(TeslaCoilBlock.FACING) : Direction.UP;
            return TeslaCoilBlock.tipOffset(f).add(p.getX(), p.getY(), p.getZ());
        }
        Direction face = link.face() == null ? Direction.UP : link.face();
        return new Vec3(p.getX() + 0.5 + face.getStepX() * 0.52, p.getY() + 0.5 + face.getStepY() * 0.52, p.getZ() + 0.5 + face.getStepZ() * 0.52);
    }

    /** Client: box around the coil and every link end, with room for the sag of the strands. */
    public AABB renderBox() {
        if (renderBox == null) {
            AABB box = new AABB(worldPosition);
            for (TeslaLink link : links) box = box.minmax(new AABB(link.pos()));
            renderBox = box.inflate(0.5).expandTowards(0, -1.0, 0);
        }
        return renderBox;
    }

    // ---- menu ----

    @Override
    public int stored() {
        return links.size();
    }

    @Override
    public int capacity() {
        return maxLinks();
    }

    @Override
    public int rate() {
        return lastRate;
    }

    @Override
    public int maxIo() {
        return tier().rate();
    }

    @Override
    public int flag() {
        return root ? 1 : 0;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new EnergyInfoMenu(id, inv, getBlockPos(), EnergyInfoMenu.KIND_TESLA, this);
    }

    // ---- save / sync ----

    private ListTag saveLinks() {
        ListTag list = new ListTag();
        for (TeslaLink link : links) list.add(link.save());
        return list;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("links", saveLinks());
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("ownerName", ownerName);
        }
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("links", saveLinks());
        tag.putByte("flow", (byte) flow);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("links", Tag.TAG_LIST)) {
            links.clear();
            caches.clear();
            ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                TeslaLink link = TeslaLink.load(list.getCompound(i));
                if (link != null) {
                    links.add(link);
                    caches.add(null);
                }
            }
            renderBox = null;
        }
        if (tag.hasUUID("owner")) {
            owner = tag.getUUID("owner");
            ownerName = tag.getString("ownerName");
        }
        if (tag.contains("flow")) flow = tag.getByte("flow");
    }
}
