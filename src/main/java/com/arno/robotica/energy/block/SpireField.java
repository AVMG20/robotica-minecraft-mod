package com.arno.robotica.energy.block;

import com.arno.robotica.Robotica;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The formed Tesla Spires of each level, so a spire can find its neighbours: spires closer than {@code spireSpacing}
 * weaken each other ({@link #interference}). Server side only; spires add themselves when they form and leave when
 * they break, unload or the level closes.
 */
@EventBusSubscriber(modid = Robotica.MODID)
public final class SpireField {
    private SpireField() {}

    private static final Map<Level, Set<BlockPos>> SPIRES = new WeakHashMap<>();

    public static void add(Level level, BlockPos base) {
        if (!level.isClientSide) SPIRES.computeIfAbsent(level, l -> new HashSet<>()).add(base.immutable());
    }

    public static void remove(Level level, BlockPos base) {
        Set<BlockPos> set = SPIRES.get(level);
        if (set != null) set.remove(base);
    }

    /**
     * Output share left after the neighbours: every other spire within {@code spacing} blocks (horizontal distance
     * between the bases) multiplies it by {@code 0.5 + 0.5 x distance / spacing}. 1 when alone or spacing is 0.
     */
    public static double interference(Level level, BlockPos base, int spacing) {
        Set<BlockPos> set = SPIRES.get(level);
        if (set == null || spacing <= 0) return 1.0;
        double factor = 1.0;
        for (BlockPos other : set) {
            if (other.equals(base)) continue;
            double dx = other.getX() - base.getX(), dz = other.getZ() - base.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < spacing) factor *= 0.5 + 0.5 * d / spacing;
        }
        return factor;
    }

    /** How many other spires are within {@code spacing} blocks. */
    public static int neighbours(Level level, BlockPos base, int spacing) {
        Set<BlockPos> set = SPIRES.get(level);
        if (set == null || spacing <= 0) return 0;
        int n = 0;
        for (BlockPos other : set) {
            if (other.equals(base)) continue;
            double dx = other.getX() - base.getX(), dz = other.getZ() - base.getZ();
            if (dx * dx + dz * dz < (double) spacing * spacing) n++;
        }
        return n;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        SPIRES.remove(event.getLevel());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SPIRES.clear();
    }
}
