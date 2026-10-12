package com.arno.robotica.logistics.pipe;

import com.arno.robotica.logistics.LogisticsConfig;
import com.arno.robotica.logistics.net.PipeGlowPayload;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A group of linked item pipes and the Insert connections they reach. Built once by a walk over the loaded pipes and
 * shared by all of them; any pipe that is placed, removed, relinked, loaded or unloaded marks it invalid, and the next
 * pipe that needs it builds a fresh one. Nothing walks the network per tick.
 * <p>
 * It also tells nearby players which way items went ({@link #glow}): the route between two pipes is found once and
 * kept, and each Extract face to Insert face pair is sent at most every {@link #GLOW_GAP} ticks, so a busy line only
 * refreshes its light.
 */
public final class PipeNetwork {
    /** An Insert connection: the pipe, its face, the inventory block behind it, and the face's priority. */
    public record Endpoint(ItemPipeBlockEntity pipe, Direction side, BlockPos target, PipePriority priority) {
        Endpoint(ItemPipeBlockEntity pipe, Direction side, BlockPos target) {
            this(pipe, side, target, pipe.priority(side));
        }
    }

    /** Highest priority first; the sort is stable, so walk or distance order stays within a priority. */
    private static final Comparator<Endpoint> BY_PRIORITY = Comparator.comparingInt(e -> e.priority().ordinal());

    private final List<Endpoint> destinations = new ArrayList<>();
    /** Closest First order per Extract pipe, built on first use and kept until the network is rebuilt. */
    private final Map<BlockPos, List<Endpoint>> nearest = new HashMap<>();
    private final Set<BlockPos> members = new HashSet<>();
    /** Priority group ends of each sorted endpoint list handed out, so a pull never re-scans them. */
    private final Map<List<Endpoint>, int[]> groupEnds = new IdentityHashMap<>();
    private int pipes;
    private boolean valid = true;

    /** A route between two pipes: one direction per step (null: none), and the box it covers. */
    public record Route(long from, long to, byte[] steps, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    /** Ticks between two glows of the same Extract face to Insert face pair (the client's light lasts about 10). */
    static final int GLOW_GAP = 6;
    /** Glows one network sends per tick at most. */
    static final int GLOWS_PER_TICK = 8;
    /** Players this far from a route (blocks) see it. */
    static final int GLOW_RANGE = 48;
    private static final int MAX_ROUTES = 512;
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Route> routes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap lastGlow = new Long2LongOpenHashMap();
    private long glowTick;
    private int glowsThisTick;

    private PipeNetwork() {}

    public boolean isValid() {
        return valid;
    }

    public void invalidate() {
        valid = false;
    }

    /** Insert connections, highest priority first, then in walk order (nearest to the pipe that built the network first). */
    public List<Endpoint> destinations() {
        return destinations;
    }

    public int size() {
        return pipes;
    }

    /** Insert connections, highest priority first, then by how many pipes away they are from {@code from}, its own faces first. */
    public List<Endpoint> byDistance(ServerLevel level, ItemPipeBlockEntity from) {
        return nearest.computeIfAbsent(from.getBlockPos(), pos -> {
            List<Endpoint> order = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(pos);
            seen.add(pos);
            while (!queue.isEmpty()) {
                BlockPos at = queue.poll();
                if (!(level.getBlockEntity(at) instanceof ItemPipeBlockEntity pipe) || pipe.isRemoved()) continue;
                for (Direction dir : Direction.values()) {
                    PipeConnection link = pipe.getBlockState().getValue(ItemPipeBlock.prop(dir));
                    BlockPos next = at.relative(dir);
                    if (link == PipeConnection.INSERT) order.add(new Endpoint(pipe, dir, next));
                    else if (link == PipeConnection.PIPE && members.contains(next) && seen.add(next)) queue.add(next);
                }
            }
            order.sort(BY_PRIORITY);
            return order;
        });
    }

    /**
     * For a list from {@link #destinations()} or {@link #byDistance}: the exclusive end index of each priority group,
     * in order. Computed once per list.
     */
    public int[] groupEnds(List<Endpoint> sorted) {
        return groupEnds.computeIfAbsent(sorted, list -> {
            int[] ends = new int[list.size()];
            int groups = 0;
            for (int i = 1; i <= list.size(); i++) {
                if (i == list.size() || list.get(i).priority() != list.get(i - 1).priority()) ends[groups++] = i;
            }
            return Arrays.copyOf(ends, groups);
        });
    }

    // ---------------------------------------------------------------- glow

    /**
     * Items went from the Extract face {@code side} of {@code from} into {@code to}: lights the route for the players
     * near it, unless this pair was sent less than {@link #GLOW_GAP} ticks ago or the network sent its share this tick.
     */
    void glow(ServerLevel level, ItemPipeBlockEntity from, Direction side, Endpoint to) {
        long now = level.getGameTime();
        BlockPos a = from.getBlockPos(), b = to.pipe().getBlockPos();
        long pair = (a.asLong() * 31 + side.ordinal()) * 0x9E3779B97F4A7C15L ^ (b.asLong() * 31 + to.side().ordinal());
        long last = lastGlow.getOrDefault(pair, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < GLOW_GAP && now >= last) return;
        if (glowTick != now) {
            glowTick = now;
            glowsThisTick = 0;
        }
        if (glowsThisTick >= GLOWS_PER_TICK || !anyViewer(level, a, b)) return;
        Route route = route(level, a, b);
        if (route == null) return;
        glowsThisTick++;
        if (lastGlow.size() > 4096) lastGlow.clear();
        lastGlow.put(pair, now);
        PipeGlowPayload payload = null;
        for (ServerPlayer player : level.players()) {
            if (!PipeGlowPayload.canReceive(player) || !near(player, route.minX, route.minY, route.minZ, route.maxX, route.maxY, route.maxZ)) continue;
            if (payload == null) payload = new PipeGlowPayload(a.asLong(), side.get3DDataValue(), b.asLong(), to.side().get3DDataValue(), route.steps);
            payload.sendTo(player);
        }
    }

    /** A link was cut or joined: the two pipes flash for the players near them. */
    static void flash(ServerLevel level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        PipeGlowPayload payload = null;
        for (ServerPlayer player : level.players()) {
            if (!PipeGlowPayload.canReceive(player) || !near(player, pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ())) continue;
            if (payload == null) payload = new PipeGlowPayload(pos.asLong(), side.get3DDataValue(), other.asLong(), -1, new byte[]{(byte) side.get3DDataValue()});
            payload.sendTo(player);
        }
    }

    /** A player near either end (a long route is checked again by its whole box before it is sent). */
    private static boolean anyViewer(ServerLevel level, BlockPos a, BlockPos b) {
        for (ServerPlayer player : level.players()) {
            if (!PipeGlowPayload.canReceive(player)) continue;
            if (near(player, a.getX(), a.getY(), a.getZ(), a.getX(), a.getY(), a.getZ())
                    || near(player, b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ())) return true;
        }
        return false;
    }

    private static boolean near(ServerPlayer player, int x0, int y0, int z0, int x1, int y1, int z1) {
        double dx = Math.max(0, Math.max(x0 - player.getX(), player.getX() - x1 - 1));
        double dy = Math.max(0, Math.max(y0 - player.getY(), player.getY() - y1 - 1));
        double dz = Math.max(0, Math.max(z0 - player.getZ(), player.getZ() - z1 - 1));
        return dx * dx + dy * dy + dz * dz <= GLOW_RANGE * GLOW_RANGE;
    }

    /** The shortest route between two pipes of this network, found once (breadth first over its pipe links) and kept. */
    @org.jetbrains.annotations.Nullable
    public Route route(ServerLevel level, BlockPos from, BlockPos to) {
        long key = from.asLong() * 0x9E3779B97F4A7C15L ^ to.asLong();
        Route known = routes.get(key);
        if (known != null && known.from == from.asLong() && known.to == to.asLong()) return known.steps == null ? null : known;
        Route found = findRoute(level, from, to);
        if (routes.size() >= MAX_ROUTES) routes.clear();
        // no route (past the network's size limit): remembered too, so it is not searched again on every pull
        routes.put(key, found != null ? found : new Route(from.asLong(), to.asLong(), null, 0, 0, 0, 0, 0, 0));
        return found;
    }

    @org.jetbrains.annotations.Nullable
    private Route findRoute(ServerLevel level, BlockPos from, BlockPos to) {
        Map<BlockPos, Direction> cameBy = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(from);
        cameBy.put(from, Direction.DOWN);
        boolean reached = from.equals(to);
        while (!queue.isEmpty() && !reached) {
            BlockPos at = queue.poll();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof ItemPipeBlock)) continue;
            for (Direction dir : Direction.values()) {
                if (state.getValue(ItemPipeBlock.prop(dir)) != PipeConnection.PIPE) continue;
                BlockPos next = at.relative(dir);
                if (!members.contains(next) || cameBy.containsKey(next)) continue;
                cameBy.put(next, dir);
                if (next.equals(to)) {
                    reached = true;
                    break;
                }
                queue.add(next);
            }
        }
        if (!reached) return null;
        // walk back from the end, then reverse; the box covers every pipe on the way
        List<Direction> back = new ArrayList<>();
        int minX = to.getX(), minY = to.getY(), minZ = to.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        for (BlockPos at = to; !at.equals(from); ) {
            Direction dir = cameBy.get(at);
            back.add(dir);
            at = at.relative(dir.getOpposite());
            minX = Math.min(minX, at.getX());
            minY = Math.min(minY, at.getY());
            minZ = Math.min(minZ, at.getZ());
            maxX = Math.max(maxX, at.getX());
            maxY = Math.max(maxY, at.getY());
            maxZ = Math.max(maxZ, at.getZ());
        }
        int n = Math.min(back.size(), PipeGlowPayload.MAX_STEPS);
        byte[] steps = new byte[n];
        for (int i = 0; i < n; i++) steps[i] = (byte) back.get(back.size() - 1 - i).get3DDataValue();
        return new Route(from.asLong(), to.asLong(), steps, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Walks the loaded pipes linked to {@code start}, gives each of them the new network and collects the Insert faces. */
    static PipeNetwork build(ServerLevel level, ItemPipeBlockEntity start) {
        PipeNetwork net = new PipeNetwork();
        ArrayDeque<ItemPipeBlockEntity> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start.getBlockPos());
        int max = LogisticsConfig.maxPipes();
        while (!queue.isEmpty()) {
            ItemPipeBlockEntity pipe = queue.poll();
            pipe.joinNetwork(net);
            net.pipes++;
            net.members.add(pipe.getBlockPos());
            for (Direction dir : Direction.values()) {
                PipeConnection link = pipe.getBlockState().getValue(ItemPipeBlock.prop(dir));
                BlockPos at = pipe.getBlockPos().relative(dir);
                if (link == PipeConnection.INSERT) {
                    net.destinations.add(new Endpoint(pipe, dir, at));
                } else if (link == PipeConnection.PIPE && seen.size() < max && seen.add(at) && level.isLoaded(at)
                        && level.getBlockEntity(at) instanceof ItemPipeBlockEntity next && !next.isRemoved()) {
                    queue.add(next);
                }
            }
        }
        net.destinations.sort(BY_PRIORITY);
        return net;
    }
}
