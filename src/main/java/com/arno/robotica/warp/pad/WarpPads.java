package com.arno.robotica.warp.pad;

import com.arno.robotica.warp.WarpRegistry;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Registry of every Warp Pad of the world, stored on the overworld data storage so pads keep working while their
 * chunk is unloaded. Entries are added on placement, updated on rename / visibility / rift changes and removed when the
 * pad is broken. {@link #validated} drops entries whose block is missing when it can see the block.
 */
public class WarpPads extends SavedData {
    public static final String NAME = "robotica_warp_pads";
    public static final SavedData.Factory<WarpPads> FACTORY = new SavedData.Factory<>(WarpPads::new, WarpPads::load, null);

    private final Map<UUID, PadRecord> pads = new LinkedHashMap<>();

    public WarpPads() {}

    public static WarpPads get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static WarpPads load(CompoundTag tag, HolderLookup.Provider registries) {
        WarpPads data = new WarpPads();
        for (Tag entry : tag.getList("pads", Tag.TAG_COMPOUND)) {
            PadRecord rec = PadRecord.load((CompoundTag) entry);
            if (rec != null) data.pads.put(rec.id(), rec);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PadRecord rec : pads.values()) list.add(rec.save());
        tag.put("pads", list);
        return tag;
    }

    // ---- Registry ----

    public void put(PadRecord rec) {
        pads.put(rec.id(), rec);
        setDirty();
    }

    @Nullable
    public PadRecord remove(UUID id) {
        PadRecord old = pads.remove(id);
        if (old != null) setDirty();
        return old;
    }

    @Nullable
    public PadRecord get(UUID id) {
        return pads.get(id);
    }

    public Collection<PadRecord> all() {
        return Collections.unmodifiableCollection(pads.values());
    }

    public int size() {
        return pads.size();
    }

    @Nullable
    public PadRecord findAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (PadRecord rec : pads.values()) {
            if (rec.pos().equals(pos) && rec.dimension().equals(dimension)) return rec;
        }
        return null;
    }

    /** Changes the stored owner name of all pads of one owner (after a name change). */
    public void refreshOwnerName(UUID owner, String ownerName) {
        boolean changed = false;
        for (Map.Entry<UUID, PadRecord> e : pads.entrySet()) {
            PadRecord rec = e.getValue();
            if (owner.equals(rec.owner()) && !ownerName.equals(rec.ownerName())) {
                e.setValue(rec.withOwnerName(ownerName));
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    /**
     * Returns the pad if it is still believed to exist. When the pad's chunk is loaded and the block is gone, the entry is
     * removed and null is returned. Pads in unloaded chunks are trusted (they are checked again on arrival).
     */
    @Nullable
    public PadRecord validated(MinecraftServer server, UUID id) {
        PadRecord rec = pads.get(id);
        if (rec == null) return null;
        ServerLevel level = server.getLevel(rec.dimension());
        if (level == null) return null;
        if (level.isLoaded(rec.pos()) && !level.getBlockState(rec.pos()).is(WarpRegistry.WARP_PAD.get())) {
            remove(id);
            return null;
        }
        return rec;
    }

    // ---- Permissions ----

    /** Own pads, public pads and pads of players in the same team. Pads without an owner are open to everybody. */
    public static boolean canUse(PadRecord rec, UUID user, @Nullable String userTeam, @Nullable String ownerTeam) {
        if (rec.owner() == null || rec.owner().equals(user)) return true;
        if (rec.isPublic()) return true;
        return userTeam != null && userTeam.equals(ownerTeam);
    }

    /** Pads the user may use, sorted by name. {@code teamOf} maps a player name to a team name (or null). */
    public List<PadRecord> usableBy(UUID user, @Nullable String userTeam, Function<String, String> teamOf) {
        return usableByRecord(user, userTeam, rec -> teamOf.apply(rec.ownerName()));
    }

    /** Like {@link #usableBy(UUID, String, Function)}, but the owner's team is looked up per record (so the name can be resolved by UUID). */
    public List<PadRecord> usableByRecord(UUID user, @Nullable String userTeam, Function<PadRecord, String> ownerTeamOf) {
        List<PadRecord> out = new ArrayList<>();
        for (PadRecord rec : pads.values()) {
            String ownerTeam = rec.owner() == null ? null : ownerTeamOf.apply(rec);
            if (canUse(rec, user, userTeam, ownerTeam)) out.add(rec);
        }
        out.sort(Comparator.comparing(PadRecord::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ---- Server helpers (scoreboard teams) ----

    @Nullable
    public static String teamOfName(MinecraftServer server, String playerName) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(playerName);
        return team == null ? null : team.getName();
    }

    /**
     * The owner's current name: the online player first, then the server's profile cache (both by UUID), else the stored
     * fallback. Stored names go stale when a player renames; scoreboard teams are keyed by the current name.
     */
    public static String currentName(MinecraftServer server, @Nullable UUID owner, String fallback) {
        if (owner == null) return fallback;
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(owner);
            if (profile.isPresent() && !profile.get().getName().isEmpty()) return profile.get().getName();
        }
        return fallback;
    }

    @Nullable
    public static String ownerTeam(MinecraftServer server, PadRecord rec) {
        return rec.owner() == null ? null : teamOfName(server, currentName(server, rec.owner(), rec.ownerName()));
    }

    public static boolean canUse(MinecraftServer server, PadRecord rec, ServerPlayer player) {
        Team userTeam = player.getTeam();
        String ownerTeam = ownerTeam(server, rec);
        return canUse(rec, player.getUUID(), userTeam == null ? null : userTeam.getName(), ownerTeam);
    }

    public List<PadRecord> usableBy(MinecraftServer server, ServerPlayer player) {
        Team userTeam = player.getTeam();
        return usableByRecord(player.getUUID(), userTeam == null ? null : userTeam.getName(), rec -> ownerTeam(server, rec));
    }
}
