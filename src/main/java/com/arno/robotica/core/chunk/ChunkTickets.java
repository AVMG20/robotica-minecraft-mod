package com.arno.robotica.core.chunk;

import com.arno.robotica.Robotica;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The mod's only chunk loading. Two NeoForge ticket controllers:
 * <ul>
 * <li>{@link #ANCHORS}: Chunk Anchor tickets, owned by the anchor's position and ticking. They survive a restart; the
 * validation callback ({@link ChunkClaims#validate}) drops the tickets of anchors that are gone.</li>
 * <li>{@link #PLAYERS}: one on-demand chunk per player (the Terminal Link), owned by the player's UUID. Released on close,
 * logout and server stop, and all dropped on the next start.</li>
 * </ul>
 * Scans never call this: only those two features do.
 */
public final class ChunkTickets {
    private ChunkTickets() {}

    public static final TicketController ANCHORS = new TicketController(Robotica.id("chunk_anchor"), ChunkClaims::validate);
    public static final TicketController PLAYERS = new TicketController(Robotica.id("player_view"), (level, helper) -> {
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        for (BlockPos owner : new ArrayList<>(helper.getBlockTickets().keySet())) helper.removeAllTickets(owner);
    });

    /** The chunk each player holds through {@link #PLAYERS}. */
    private record Held(ResourceKey<Level> dimension, long chunk) {}

    private static final Map<UUID, Held> HELD = new HashMap<>();
    /** Mirror of the anchor tickets this session asked for ("dimension|owner pos|chunk"), for tests and status. */
    private static final Set<String> FORCED = new HashSet<>();

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterTicketControllersEvent.class, event -> {
            event.register(ANCHORS);
            event.register(PLAYERS);
        });
    }

    private static String key(ResourceKey<Level> dimension, BlockPos owner, int cx, int cz) {
        return dimension.location() + "|" + owner.asLong() + "|" + ChunkPos.asLong(cx, cz);
    }

    /** Forces (or releases) the square of chunks {@code radius} around the owner's chunk, ticking. */
    public static void forceArea(ServerLevel level, BlockPos owner, int radius, boolean add) {
        ChunkPos center = new ChunkPos(owner);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx, cz = center.z + dz;
                ANCHORS.forceChunk(level, owner, cx, cz, add, true);
                String k = key(level.dimension(), owner, cx, cz);
                if (add) FORCED.add(k);
                else FORCED.remove(k);
            }
        }
    }

    /** True when an anchor at {@code owner} asked for {@code chunk} in this session. */
    public static boolean isForced(ResourceKey<Level> dimension, BlockPos owner, ChunkPos chunk) {
        return FORCED.contains(key(dimension, owner, chunk.x, chunk.z));
    }

    /** Loads one chunk for a player (ticking), releasing any chunk the player held before. */
    public static void holdForPlayer(ServerLevel level, UUID player, ChunkPos chunk) {
        Held old = HELD.get(player);
        if (old != null && old.dimension().equals(level.dimension()) && old.chunk() == chunk.toLong()) return;
        releasePlayer(level.getServer(), player);
        PLAYERS.forceChunk(level, player, chunk.x, chunk.z, true, true);
        HELD.put(player, new Held(level.dimension(), chunk.toLong()));
    }

    /** Lets the player's on-demand chunk go. Safe to call when the player holds none. */
    public static void releasePlayer(MinecraftServer server, UUID player) {
        Held held = HELD.remove(player);
        if (held == null) return;
        ServerLevel level = server.getLevel(held.dimension());
        if (level != null) {
            PLAYERS.forceChunk(level, player, ChunkPos.getX(held.chunk()), ChunkPos.getZ(held.chunk()), false, true);
        }
    }

    /** True when the player holds this chunk. */
    public static boolean holds(UUID player, ResourceKey<Level> dimension, ChunkPos chunk) {
        Held held = HELD.get(player);
        return held != null && held.dimension().equals(dimension) && held.chunk() == chunk.toLong();
    }

    /** True when the player holds any chunk. */
    public static boolean holdsAny(UUID player) {
        return HELD.containsKey(player);
    }

    /** Server stop: the player tickets die with the server (the next start drops them), the mirror resets. */
    static void clear() {
        HELD.clear();
        FORCED.clear();
    }
}
