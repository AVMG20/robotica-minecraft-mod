package com.arno.robotica.core.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Every Chunk Anchor of the world, on the overworld data storage, so the per-owner cap counts anchors in every
 * dimension and in unloaded chunks. A claim is the anchor's square of chunks; it is active while its tickets are held.
 * The owner's use is the sum of their active claims. Offline handling: with offline loading off, the owner's claims are
 * paused on logout (tickets released) and taken up again on login.
 */
public class ChunkClaims extends SavedData {
    public static final String NAME = "robotica_chunk_claims";
    public static final SavedData.Factory<ChunkClaims> FACTORY = new SavedData.Factory<>(ChunkClaims::new, ChunkClaims::load, null);

    private static IntSupplier cap = () -> 25;
    private static BooleanSupplier whileOffline = () -> true;

    /** One anchor. Mutable; anchors keep a reference to their own claim. */
    public static final class Claim {
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;
        private UUID owner;
        private int radius;
        private boolean active;
        private boolean paused;

        Claim(ResourceKey<Level> dimension, BlockPos pos, UUID owner, int radius) {
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.owner = owner;
            this.radius = radius;
        }

        public UUID owner() {
            return owner;
        }

        public int radius() {
            return radius;
        }

        /** Chunks of the square: 1, 9, 25. */
        public int chunks() {
            return chunks(radius);
        }

        /** True while its tickets are held. */
        public boolean active() {
            return active;
        }

        /** True when it was let go because its owner logged out (offline loading off). */
        public boolean paused() {
            return paused;
        }
    }

    private final Map<ResourceKey<Level>, Map<BlockPos, Claim>> claims = new HashMap<>();

    public ChunkClaims() {}

    /** Called once by the module that owns the anchors: the cap (0 = anchors off) and the offline switch. */
    public static void configure(IntSupplier chunksPerPlayer, BooleanSupplier loadWhileOffline) {
        cap = chunksPerPlayer;
        whileOffline = loadWhileOffline;
    }

    public static int cap() {
        return cap.getAsInt();
    }

    public static boolean whileOffline() {
        return whileOffline.getAsBoolean();
    }

    public static int chunks(int radius) {
        int side = 2 * radius + 1;
        return side * side;
    }

    public static ChunkClaims get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // ---- claims ----

    @Nullable
    public Claim find(ResourceKey<Level> dimension, BlockPos pos) {
        Map<BlockPos, Claim> inLevel = claims.get(dimension);
        return inLevel == null ? null : inLevel.get(pos);
    }

    /**
     * The anchor's claim, made if missing. A new owner or radius (Mk upgrade) first lets the old chunks go.
     */
    public Claim claim(ServerLevel level, BlockPos pos, UUID owner, int radius) {
        Claim c = find(level.dimension(), pos);
        if (c == null) {
            c = new Claim(level.dimension(), pos, owner, radius);
            claims.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(c.pos, c);
            setDirty();
        } else if (!c.owner.equals(owner) || c.radius != radius) {
            deactivate(level, c);
            c.owner = owner;
            c.radius = radius;
            setDirty();
        }
        return c;
    }

    /** The anchor is gone: tickets released, claim forgotten. */
    public void remove(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Claim> inLevel = claims.get(level.dimension());
        if (inLevel == null) return;
        Claim c = inLevel.remove(pos);
        if (c == null) return;
        if (c.active) ChunkTickets.forceArea(level, c.pos, c.radius, false);
        if (inLevel.isEmpty()) claims.remove(level.dimension());
        setDirty();
    }

    /** Takes the tickets of the claim's chunks. */
    public void activate(ServerLevel level, Claim c) {
        if (!c.active) {
            c.active = true;
            setDirty();
        }
        c.paused = false;
        ChunkTickets.forceArea(level, c.pos, c.radius, true);
    }

    /** Lets the claim's chunks go. */
    public void deactivate(ServerLevel level, Claim c) {
        if (!c.active) return;
        c.active = false;
        ChunkTickets.forceArea(level, c.pos, c.radius, false);
        setDirty();
    }

    /** Chunks the owner's active anchors hold, in every dimension. */
    public int used(UUID owner) {
        int n = 0;
        for (Map<BlockPos, Claim> inLevel : claims.values()) {
            for (Claim c : inLevel.values()) {
                if (c.active && c.owner.equals(owner)) n += c.chunks();
            }
        }
        return n;
    }

    /** Claims of one owner, every dimension. */
    public List<Claim> claimsOf(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (Map<BlockPos, Claim> inLevel : claims.values()) {
            for (Claim c : inLevel.values()) if (c.owner.equals(owner)) out.add(c);
        }
        return out;
    }

    // ---- owner online / offline ----

    /** Logout: with offline loading off, the owner's active claims are paused and their tickets released. */
    public void ownerLeft(MinecraftServer server, UUID owner, boolean loadWhileOffline) {
        if (loadWhileOffline) return;
        for (Claim c : claimsOf(owner)) {
            if (!c.active) continue;
            ServerLevel level = server.getLevel(c.dimension);
            if (level == null) continue;
            deactivate(level, c);
            c.paused = true;
            setDirty();
        }
    }

    /** Login: paused claims take their tickets again; each anchor checks power and the cap on its next tick. */
    public void ownerJoined(MinecraftServer server, UUID owner) {
        for (Claim c : claimsOf(owner)) {
            if (!c.paused) continue;
            ServerLevel level = server.getLevel(c.dimension);
            if (level == null) continue;
            activate(level, c);
        }
    }

    /** Marks a claim paused for its offline owner (the anchor saw it on its own tick). */
    public void pause(ServerLevel level, Claim c) {
        deactivate(level, c);
        if (!c.paused) {
            c.paused = true;
            setDirty();
        }
    }

    // ---- validation on server start ----

    /**
     * Ticket validation of {@link ChunkTickets#ANCHORS} for one level on server start: tickets of anchors that are gone
     * or inactive are dropped; with offline loading off every anchor pauses (nobody is online yet).
     */
    static void validate(ServerLevel level, TicketHelper helper) {
        ChunkClaims data = get(level.getServer());
        boolean offline = whileOffline();
        for (BlockPos owner : new ArrayList<>(helper.getBlockTickets().keySet())) {
            Claim c = data.find(level.dimension(), owner);
            if (c == null || !c.active) {
                helper.removeAllTickets(owner);
            } else if (!offline) {
                helper.removeAllTickets(owner);
                c.active = false;
                c.paused = true;
                data.setDirty();
            }
        }
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
    }

    // ---- persistence ----

    private static ChunkClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkClaims data = new ChunkClaims();
        for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("dim"));
            if (dim == null || !e.hasUUID("owner")) continue;
            BlockPos pos = NbtUtils.readBlockPos(e, "pos").orElse(null);
            if (pos == null) continue;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
            Claim c = new Claim(key, pos, e.getUUID("owner"), Math.max(0, e.getInt("radius")));
            c.active = e.getBoolean("active");
            c.paused = e.getBoolean("paused");
            data.claims.computeIfAbsent(key, k -> new HashMap<>()).put(c.pos, c);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map<BlockPos, Claim> inLevel : claims.values()) {
            for (Claim c : inLevel.values()) {
                CompoundTag e = new CompoundTag();
                e.putString("dim", c.dimension.location().toString());
                e.put("pos", NbtUtils.writeBlockPos(c.pos));
                e.putUUID("owner", c.owner);
                e.putInt("radius", c.radius);
                e.putBoolean("active", c.active);
                e.putBoolean("paused", c.paused);
                list.add(e);
            }
        }
        tag.put("claims", list);
        return tag;
    }
}
