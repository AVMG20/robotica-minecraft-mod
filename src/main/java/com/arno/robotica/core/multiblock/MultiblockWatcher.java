package com.arno.robotica.core.multiblock;

import com.arno.robotica.Robotica;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Cheap change detection for multiblocks: a controller registers the box it cares about (its structure, or the
 * search area while unformed) and gets a callback whenever a block inside changes with a neighbour update
 * (players, pistons, flowing water, other mods' machines). The callback should only set a "dirty" flag; the
 * controller re-scans on its own tick, throttled. Changes without neighbour updates are caught by the controller's
 * slow periodic re-scan. Server side only; a few controllers per level, so the per-update cost is a short loop.
 *
 * <p>The callbacks hold their block entities and those their level, so a weak key alone would never let a closed
 * world go: watches are dropped explicitly when a level unloads and when the server stops.
 */
@EventBusSubscriber(modid = Robotica.MODID)
public final class MultiblockWatcher {
    private MultiblockWatcher() {}

    private record Watch(BoundingBox box, Predicate<BlockPos> filter, Runnable onChange) {}

    private static final Map<Level, Map<BlockPos, Watch>> WATCHES = new WeakHashMap<>();

    /** Watches {@code box} grown by one block on every side, replacing an earlier watch of the same owner. */
    public static void watch(Level level, BlockPos owner, BoundingBox box, Runnable onChange) {
        watch(level, owner, box, pos -> true, onChange);
    }

    /** As {@link #watch(Level, BlockPos, BoundingBox, Runnable)}, but only changes {@code filter} accepts (checked after the box). */
    public static void watch(Level level, BlockPos owner, BoundingBox box, Predicate<BlockPos> filter, Runnable onChange) {
        if (level.isClientSide) return;
        WATCHES.computeIfAbsent(level, l -> new HashMap<>()).put(owner.immutable(), new Watch(box.inflatedBy(1), filter, onChange));
    }

    public static void unwatch(Level level, BlockPos owner) {
        Map<BlockPos, Watch> map = WATCHES.get(level);
        if (map != null) map.remove(owner);
    }

    public static boolean isWatched(Level level, BlockPos owner) {
        Map<BlockPos, Watch> map = WATCHES.get(level);
        return map != null && map.containsKey(owner);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        WATCHES.remove(event.getLevel());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WATCHES.clear();
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        Map<BlockPos, Watch> map = WATCHES.get(level);
        if (map == null || map.isEmpty()) return;
        BlockPos pos = event.getPos();
        for (Map.Entry<BlockPos, Watch> e : map.entrySet()) {
            Watch w = e.getValue();
            if (w.box.isInside(pos) && !e.getKey().equals(pos) && w.filter.test(pos)) w.onChange.run();
        }
    }
}
