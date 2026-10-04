package com.arno.robotica.warp.teleport;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player cooldowns for pad trips and remotes, keyed by UUID and stored as "ready at" game time, so a relog, a death or
 * a restart does not reset them. Expired entries are pruned by time only (never on logout).
 */
public class WarpCooldowns extends SavedData {
    public static final String NAME = "robotica_warp_cooldowns";
    public static final SavedData.Factory<WarpCooldowns> FACTORY = new SavedData.Factory<>(WarpCooldowns::new, WarpCooldowns::load, null);
    /** A cooldown further away than this belongs to a world with a later clock and is void. */
    private static final long MAX_AHEAD = 72_000;

    private final Map<UUID, Long> padReadyAt = new HashMap<>();
    private final Map<UUID, Long> remoteReadyAt = new HashMap<>();

    public WarpCooldowns() {}

    public static WarpCooldowns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static WarpCooldowns load(CompoundTag tag, HolderLookup.Provider registries) {
        WarpCooldowns data = new WarpCooldowns();
        read(tag.getList("pad", Tag.TAG_COMPOUND), data.padReadyAt);
        read(tag.getList("remote", Tag.TAG_COMPOUND), data.remoteReadyAt);
        return data;
    }

    private static void read(ListTag list, Map<UUID, Long> into) {
        for (Tag entry : list) {
            CompoundTag c = (CompoundTag) entry;
            if (c.hasUUID("id")) into.put(c.getUUID("id"), c.getLong("at"));
        }
    }

    private static ListTag write(Map<UUID, Long> from) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> e : from.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putLong("at", e.getValue());
            list.add(c);
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("pad", write(padReadyAt));
        tag.put("remote", write(remoteReadyAt));
        return tag;
    }

    private static int remaining(Map<UUID, Long> map, UUID player, long gameTime) {
        Long readyAt = map.get(player);
        if (readyAt == null) return 0;
        if (readyAt <= gameTime || readyAt - gameTime > MAX_AHEAD) return 0;
        return (int) (readyAt - gameTime);
    }

    /** Ticks left before the player may use a pad again (0 when ready). */
    public int padRemaining(UUID player, long gameTime) {
        return remaining(padReadyAt, player, gameTime);
    }

    public void startPad(UUID player, long gameTime, int ticks) {
        if (ticks > 0) {
            padReadyAt.put(player, gameTime + ticks);
            setDirty();
        }
    }

    /** Ticks left before the player may use a remote again (0 when ready). */
    public int remoteRemaining(UUID player, long gameTime) {
        return remaining(remoteReadyAt, player, gameTime);
    }

    public void startRemote(UUID player, long gameTime, int ticks) {
        if (ticks > 0) {
            remoteReadyAt.put(player, gameTime + ticks);
            setDirty();
        }
    }

    /** Drops entries that have expired (called now and then so abandoned entries do not pile up). */
    public void prune(long gameTime) {
        boolean changed = padReadyAt.values().removeIf(t -> t <= gameTime || t - gameTime > MAX_AHEAD);
        changed |= remoteReadyAt.values().removeIf(t -> t <= gameTime || t - gameTime > MAX_AHEAD);
        if (changed) setDirty();
    }

    public int size() {
        return padReadyAt.size() + remoteReadyAt.size();
    }
}
