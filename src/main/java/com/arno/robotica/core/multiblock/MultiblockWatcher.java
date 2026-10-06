package com.arno.robotica.core.multiblock;

import com.arno.robotica.Robotica;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cheap change detection for multiblocks: a controller registers the box it cares about (its structure, or the
 * search area while unformed) and gets a callback whenever a block inside changes with a neighbour update
 * (players, pistons, flowing water, other mods' machines). The callback should only set a "dirty" flag; the
 * controller re-scans on its own tick, throttled. Changes without neighbour updates are caught by the controller's
 * slow periodic re-scan. Server side only; a few controllers per level, so the per-update cost is a short loop.
 */
@EventBusSubscriber(modid = Robotica.MODID)
public final class MultiblockWatcher {
    private MultiblockWatcher() {}

    private record Watch(BoundingBox box, Runnable onChange) {}

    private static final Map<Level, Map<BlockPos, Watch>> WATCHES = new WeakHashMap<>();

    /** Watches {@code box} grown by one block on every side, replacing an earlier watch of the same owner. */
    public static void watch(Level level, BlockPos owner, BoundingBox box, Runnable onChange) {
        if (level.isClientSide) return;
        WATCHES.computeIfAbsent(level, l -> new HashMap<>()).put(owner.immutable(), new Watch(box.inflatedBy(1), onChange));
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
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        Map<BlockPos, Watch> map = WATCHES.get(level);
        if (map == null || map.isEmpty()) return;
        BlockPos pos = event.getPos();
        for (Map.Entry<BlockPos, Watch> e : map.entrySet()) {
            if (e.getValue().box.isInside(pos) && !e.getKey().equals(pos)) e.getValue().onChange.run();
        }
    }
}
