package com.arno.robotica.energy.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only, one-shot outline of a multiblock, started from the controller GUI's "Show" button: the structure's box
 * (or the smallest legal shape behind the controller when nothing was found yet) and the first wrong block in red.
 * Drawn by {@link ControllerHighlightRenderer} for {@link #DURATION_MS}.
 */
public final class StructureHighlight {
    private StructureHighlight() {}

    public static final long DURATION_MS = 12_000;

    public record Entry(BoundingBox box, @Nullable BlockPos problem, boolean preview, long until) {}

    private static final Map<BlockPos, Entry> ACTIVE = new HashMap<>();

    public static void show(BlockPos controller, BoundingBox box, @Nullable BlockPos problem, boolean preview) {
        ACTIVE.put(controller.immutable(), new Entry(box, problem, preview, System.currentTimeMillis() + DURATION_MS));
    }

    @Nullable
    public static Entry get(BlockPos controller) {
        Entry e = ACTIVE.get(controller);
        if (e != null && e.until < System.currentTimeMillis()) {
            ACTIVE.remove(controller);
            return null;
        }
        return e;
    }

    public static boolean active(BlockPos controller) {
        return get(controller) != null;
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
