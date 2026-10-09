package com.arno.robotica.gear.tool;

import com.arno.robotica.gear.GearConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side execution of area breaks. Small breaks run immediately, big ones wait in a per-player queue that is
 * drained in the server tick. Every block goes through {@code ServerPlayerGameMode.destroyBlock}, so claim and
 * protection mods can cancel each one through the normal BreakEvent. State is keyed by player UUID and dropped on logout.
 */
public final class BreakQueue {
    private BreakQueue() {}

    /** Where to plant a sapling once the tree is gone. */
    public record ReplantSpot(BlockPos pos, Block log) {}

    private static final class Job {
        final ResourceKey<Level> dimension;
        final GearToolItem tool;
        final ArrayDeque<BlockPos> blocks;
        final List<ReplantSpot> replant;

        Job(ResourceKey<Level> dimension, GearToolItem tool, ArrayDeque<BlockPos> blocks, List<ReplantSpot> replant) {
            this.dimension = dimension;
            this.tool = tool;
            this.blocks = blocks;
            this.replant = replant;
        }
    }

    private static final int MAX_QUEUED_PER_PLAYER = 8192;
    private static final Map<UUID, Deque<Job>> JOBS = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    /** True while this player's area break is running; the BreakEvent handler uses it to avoid recursion. */
    public static boolean isBreaking(UUID player) {
        return ACTIVE.contains(player);
    }

    /** Breaks (or queues) the extra blocks of an area break and plans the replant. Called from the BreakEvent. */
    public static void start(ServerPlayer player, ServerLevel level, GearToolItem tool, ItemStack stack, BlockPos origin, List<BlockPos> targets) {
        List<ReplantSpot> replant = planReplant(level, player, tool, stack, origin, targets);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(targets);
        if (targets.size() <= AreaBreaker.QUEUE_THRESHOLD) {
            breakBlocks(player, level, tool, queue, Integer.MAX_VALUE);
            if (!replant.isEmpty()) enqueue(player, new Job(level.dimension(), tool, new ArrayDeque<>(), replant));
        } else {
            enqueue(player, new Job(level.dimension(), tool, queue, replant));
        }
    }

    private static void enqueue(ServerPlayer player, Job job) {
        Deque<Job> jobs = JOBS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        int queued = 0;
        for (Job j : jobs) queued += j.blocks.size();
        if (queued + job.blocks.size() > MAX_QUEUED_PER_PLAYER) job.blocks.clear();
        jobs.addLast(job);
    }

    private static List<ReplantSpot> planReplant(ServerLevel level, ServerPlayer player, GearToolItem tool, ItemStack stack,
                                                 BlockPos origin, List<BlockPos> targets) {
        if (!tool.spec.replants) return List.of();
        if (tool.activeMode(stack, player) != AreaMode.TREE) return List.of();
        List<ReplantSpot> spots = new ArrayList<>();
        List<BlockPos> all = new ArrayList<>(targets);
        all.add(origin);
        for (BlockPos p : all) {
            if (!level.isLoaded(p) || !level.isLoaded(p.below())) continue;
            BlockState s = level.getBlockState(p);
            if (s.is(net.minecraft.tags.BlockTags.LOGS) && level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
                spots.add(new ReplantSpot(p.immutable(), s.getBlock()));
            }
        }
        return spots;
    }

    /**
     * Breaks up to {@code budget} queued positions. Returns how many entries were consumed (broken or skipped).
     * Stops and clears the queue when the held tool changed or can not pay for the next block.
     */
    static int breakBlocks(ServerPlayer player, ServerLevel level, GearToolItem tool, ArrayDeque<BlockPos> queue, int budget) {
        UUID id = player.getUUID();
        boolean added = ACTIVE.add(id);
        int consumed = 0;
        try {
            while (consumed < budget && !queue.isEmpty()) {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || held.getItem() != tool) {
                    queue.clear();
                    break;
                }
                BlockPos pos = queue.peekFirst();
                // Check the chunk first: getBlockState on an unloaded position would force-load or generate it.
                if (!level.isLoaded(pos)) {
                    queue.pollFirst();
                    consumed++;
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!tool.canAfford(held, state)) {
                    queue.clear();
                    break;
                }
                queue.pollFirst();
                consumed++;
                if (state.isAir() || state.getBlock() instanceof LiquidBlock
                        || state.getDestroySpeed(level, pos) < 0 || !level.mayInteract(player, pos)
                        || pos.distSqr(player.blockPosition()) > 48 * 48) {
                    continue;
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            if (added) ACTIVE.remove(id);
        }
        return consumed;
    }

    /** Drains the queues. Call once per server tick. */
    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        int perTick = GearConfig.maxBlocksPerTick();
        var it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Deque<Job> jobs = entry.getValue();
            if (player == null || jobs.isEmpty()) {
                it.remove();
                continue;
            }
            int budget = perTick;
            while (budget > 0 && !jobs.isEmpty()) {
                Job job = jobs.peekFirst();
                ServerLevel level = player.serverLevel();
                if (level.dimension() != job.dimension) {
                    jobs.pollFirst();
                    continue;
                }
                if (!job.blocks.isEmpty()) {
                    BlockPos first = job.blocks.peekFirst();
                    budget -= Math.max(1, breakBlocks(player, level, job.tool, job.blocks, budget));
                    if (level.getGameTime() % 4 == 0 && level.isLoaded(first)) GearSounds.debris(level, first, job.blocks.size());
                }
                if (job.blocks.isEmpty()) {
                    replant(player, level, job.replant);
                    jobs.pollFirst();
                }
            }
            if (jobs.isEmpty()) it.remove();
        }
    }

    private static void replant(ServerPlayer player, ServerLevel level, List<ReplantSpot> spots) {
        for (ReplantSpot spot : spots) {
            if (!level.isLoaded(spot.pos())) continue;
            if (!level.getBlockState(spot.pos()).isAir()) continue;
            ItemStack sapling = findSapling(player, spot.log());
            if (sapling.isEmpty() || !(sapling.getItem() instanceof BlockItem blockItem)) continue;
            BlockState plant = blockItem.getBlock().defaultBlockState();
            if (!plant.canSurvive(level, spot.pos())) continue;
            if (level.setBlock(spot.pos(), plant, Block.UPDATE_ALL)) {
                if (!player.getAbilities().instabuild) sapling.shrink(1);
            }
        }
    }

    private static ItemStack findSapling(ServerPlayer player, Block log) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(log);
        String wood = key.getPath().replace("stripped_", "").replace("_log", "").replace("_stem", "").replace("_wood", "").replace("_hyphae", "");
        ResourceLocation wanted = key.withPath(wood + "_sapling");
        Inventory inv = player.getInventory();
        ItemStack fallback = ItemStack.EMPTY;
        for (ItemStack s : inv.items) {
            if (s.isEmpty() || !s.is(ItemTags.SAPLINGS)) continue;
            if (BuiltInRegistries.ITEM.getKey(s.getItem()).equals(wanted)) return s;
            if (fallback.isEmpty()) fallback = s;
        }
        return fallback;
    }

    /** Drops all state of a player (logout). */
    public static void clear(UUID player) {
        JOBS.remove(player);
        ACTIVE.remove(player);
        FACES.remove(player);
    }

    public static void clearAll() {
        JOBS.clear();
        ACTIVE.clear();
        FACES.clear();
    }

    /** Queued blocks of a player (tests, diagnostics). */
    public static int queued(UUID player) {
        Deque<Job> jobs = JOBS.get(player);
        if (jobs == null) return 0;
        int n = 0;
        for (Job j : jobs) n += j.blocks.size();
        return n;
    }

    /** Last block face the player started mining, remembered from LeftClickBlock. */
    private record FaceHit(ResourceKey<Level> dimension, BlockPos pos, Direction face) {}

    private static final Map<UUID, FaceHit> FACES = new HashMap<>();

    public static void rememberFace(ServerPlayer player, BlockPos pos, Direction face) {
        FACES.put(player.getUUID(), new FaceHit(player.level().dimension(), pos.immutable(), face));
    }

    /** The face being mined: the remembered click if it matches, else the player's crosshair, else geometry. */
    public static Direction faceFor(ServerPlayer player, BlockPos pos) {
        FaceHit hit = FACES.get(player.getUUID());
        if (hit != null && hit.pos.equals(pos) && hit.dimension == player.level().dimension()) return hit.face;
        if (player.pick(player.blockInteractionRange() + 1.0, 1.0F, false) instanceof net.minecraft.world.phys.BlockHitResult bhr
                && bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && bhr.getBlockPos().equals(pos)) {
            return bhr.getDirection();
        }
        return AreaBreaker.faceFromPosition(player, pos);
    }

    public static void forgetFace(UUID player) {
        FACES.remove(player);
    }
}
