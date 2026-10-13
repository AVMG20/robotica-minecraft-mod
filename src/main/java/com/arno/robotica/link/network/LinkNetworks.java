package com.arno.robotica.link.network;

import com.arno.robotica.compat.OwnerNames;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.menu.NetworkEntry;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Quantum Link network of the world, stored on the overworld data storage next to the Warp Pad registry, keyed
 * by network UUID. A network belongs to its owner's UUID and stays when all its Links are broken; only the owner
 * deletes it. {@link #tick} runs once per server tick and hands each network's buffer to the receivers that ticked.
 */
public class LinkNetworks extends SavedData {
    public static final String NAME = "robotica_link_networks";
    public static final SavedData.Factory<LinkNetworks> FACTORY = new SavedData.Factory<>(LinkNetworks::new, LinkNetworks::load, null);
    /** Items are handed out every this many ticks (receivers have a per-second budget). */
    private static final int ITEM_INTERVAL = 5;
    /** One stored sender of one network is checked every this many ticks (stale entries from blocks removed by commands). */
    private static final int VALIDATE_INTERVAL = 40;

    private final Map<UUID, LinkNetwork> networks = new LinkedHashMap<>();
    /** Networks with Links that ticked during the current server tick. Runtime only. */
    private final List<LinkNetwork> touched = new ArrayList<>();

    public LinkNetworks() {}

    public static LinkNetworks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static LinkNetworks load(CompoundTag tag, HolderLookup.Provider registries) {
        LinkNetworks data = new LinkNetworks();
        for (Tag entry : tag.getList("networks", Tag.TAG_COMPOUND)) {
            LinkNetwork net = LinkNetwork.load((CompoundTag) entry, registries);
            if (net != null) {
                net.data = data;
                data.networks.put(net.id(), net);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (LinkNetwork net : networks.values()) list.add(net.save(registries));
        tag.put("networks", list);
        return tag;
    }

    // ---------------------------------------------------------------- registry

    @Nullable
    public LinkNetwork get(@Nullable UUID id) {
        return id == null ? null : networks.get(id);
    }

    public Collection<LinkNetwork> all() {
        return Collections.unmodifiableCollection(networks.values());
    }

    public int ownedBy(UUID owner) {
        int n = 0;
        for (LinkNetwork net : networks.values()) if (net.owner().equals(owner)) n++;
        return n;
    }

    /** A new network; {@code home} is the dimension it is made in (Mk1 Links only work there). */
    public LinkNetwork create(UUID owner, String ownerName, String name, int color, Visibility visibility, ResourceKey<Level> home) {
        LinkNetwork net = new LinkNetwork(UUID.randomUUID(), owner, ownerName, name, color, visibility, home);
        net.data = this;
        networks.put(net.id(), net);
        setDirty();
        return net;
    }

    public void edit(LinkNetwork net, String name, int color, Visibility visibility) {
        net.setName(name);
        net.setColor(color);
        net.setVisibility(visibility);
    }

    /** Deletes a network: its Links go idle, the items still in its buffer are returned. */
    public List<ItemStack> delete(LinkNetwork net) {
        if (networks.remove(net.id()) == null) return List.of();
        List<ItemStack> left = net.drainItems();
        net.markRemoved();
        touched.remove(net);
        setDirty();
        return left;
    }

    /** Owners rename themselves; networks show the current name. */
    public void refreshOwnerName(UUID owner, String ownerName) {
        for (LinkNetwork net : networks.values()) if (net.owner().equals(owner)) net.setOwnerName(ownerName);
    }

    // ---------------------------------------------------------------- per tick

    /**
     * Called by a Link that ticked in tick {@code now}: the first one of a network clears last tick's receiver lists.
     * The lists are reused, so nothing is allocated.
     */
    public void touch(LinkNetwork net, long now) {
        if (net.activeTick == now) return;
        net.clearActive();
        net.activeTick = now;
        touched.add(net);
    }

    public void addEnergyReceiver(LinkNetwork net, QuantumLinkBlockEntity link, long now) {
        touch(net, now);
        net.energyReceivers[link.priority().ordinal()].add(link);
    }

    public void addItemReceiver(LinkNetwork net, QuantumLinkBlockEntity link, long now) {
        touch(net, now);
        net.itemReceivers[link.priority().ordinal()].add(link);
    }

    /** After all levels ticked: energy every tick, items every {@link #ITEM_INTERVAL} ticks, one sender check now and then. */
    public void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (int i = 0; i < touched.size(); i++) {
            LinkNetwork net = touched.get(i);
            if (net.removed()) continue;
            net.distributeEnergy();
            if (Math.floorMod(now + net.id().hashCode(), ITEM_INTERVAL) == 0 && net.hasItems()) net.distributeItems();
            if (Math.floorMod(now + net.id().hashCode(), VALIDATE_INTERVAL) == 0) validateOneSender(server, net);
        }
        touched.clear();
    }

    /**
     * Drops one stored sender whose block is gone or no longer sends to this network, only when its chunk is loaded
     * (nothing is loaded for this). Links unregister themselves when broken; this catches blocks removed by commands.
     */
    private static void validateOneSender(MinecraftServer server, LinkNetwork net) {
        int n = net.senders().size();
        if (n == 0) return;
        int target = Math.floorMod(net.validateCursor++, n);
        Iterator<GlobalPos> it = net.senders().keySet().iterator();
        GlobalPos pos = null;
        for (int i = 0; i <= target && it.hasNext(); i++) pos = it.next();
        if (pos == null) return;
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null) {
            net.removeSender(pos);
            return;
        }
        if (!level.isLoaded(pos.pos())) return;
        if (!(level.getBlockEntity(pos.pos()) instanceof QuantumLinkBlockEntity link) || !link.sendsTo(net.id())) net.removeSender(pos);
    }

    /** Server stopped or the data was replaced: drop runtime lists. */
    public void clearRuntime() {
        touched.clear();
        for (LinkNetwork net : networks.values()) net.clearActive();
    }

    // ---------------------------------------------------------------- access

    /**
     * Whether {@code user} may join the network: its owner always, everyone for Public, the owner's team for Team.
     * A null user (a Link placed by commands) may use any network.
     */
    public static boolean canUse(MinecraftServer server, LinkNetwork net, @Nullable UUID user, String userName) {
        if (user == null || net.owner().equals(user)) return true;
        return switch (net.visibility()) {
            case PUBLIC -> true;
            case TEAM -> allied(server, net, teamOf(server, user, userName));
            case PRIVATE -> false;
        };
    }

    public static boolean canUse(MinecraftServer server, LinkNetwork net, Player player) {
        if (net.owner().equals(player.getUUID()) || net.visibility() == Visibility.PUBLIC) return true;
        return net.visibility() == Visibility.TEAM && allied(server, net, player.getTeam());
    }

    /** Only the owner renames, recolours, changes the visibility of or deletes a network. */
    public static boolean canManage(LinkNetwork net, Player player) {
        return net.owner().equals(player.getUUID());
    }

    private static boolean allied(MinecraftServer server, LinkNetwork net, @Nullable Team userTeam) {
        if (userTeam == null) return false;
        Team ownerTeam = teamOf(server, net.owner(), net.ownerName());
        return ownerTeam != null && userTeam.isAlliedTo(ownerTeam);
    }

    /** The scoreboard team of a player by UUID: the current name (online, then profile cache), else the stored one. */
    @Nullable
    private static PlayerTeam teamOf(MinecraftServer server, UUID player, String fallbackName) {
        String name = OwnerNames.name(server, player);
        if (name == null) name = fallbackName;
        if (name == null || name.isEmpty()) return null;
        return server.getScoreboard().getPlayersTeam(name);
    }

    /** 0 own, 1 shared by a team mate, 2 public: the order of the network list. */
    private static int group(MinecraftServer server, LinkNetwork net, Player player) {
        if (net.owner().equals(player.getUUID())) return 0;
        return allied(server, net, player.getTeam()) ? 1 : 2;
    }

    /** The networks a player may join, own first, then their team's, then public ones, each by name. */
    public List<NetworkEntry> entriesFor(MinecraftServer server, Player player) {
        List<NetworkEntry> out = new ArrayList<>();
        for (LinkNetwork net : networks.values()) {
            if (!canUse(server, net, player)) continue;
            String ownerName = OwnerNames.name(server, net.owner());
            out.add(new NetworkEntry(net.id(), net.name(), ownerName != null ? ownerName : net.ownerName(), net.color(),
                    net.visibility(), net.home().location(), group(server, net, player), canManage(net, player)));
        }
        out.sort(Comparator.comparingInt(NetworkEntry::group).thenComparing(NetworkEntry::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
