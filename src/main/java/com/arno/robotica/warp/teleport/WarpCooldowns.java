package com.arno.robotica.warp.teleport;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Short per-player cooldown for pad trips, keyed by UUID. Cleared on logout and when the server stops. */
public final class WarpCooldowns {
    private WarpCooldowns() {}

    private static final Map<UUID, Long> PAD_READY_AT = new HashMap<>();

    /** Ticks left before the player may use a pad again (0 when ready). */
    public static int padRemaining(UUID player, long gameTime) {
        Long readyAt = PAD_READY_AT.get(player);
        if (readyAt == null) return 0;
        if (readyAt <= gameTime) {
            PAD_READY_AT.remove(player);
            return 0;
        }
        return (int) (readyAt - gameTime);
    }

    public static void startPad(UUID player, long gameTime, int ticks) {
        if (ticks > 0) PAD_READY_AT.put(player, gameTime + ticks);
    }

    /** Drops entries that have expired (called now and then so abandoned entries do not pile up). */
    public static void prune(long gameTime) {
        Iterator<Map.Entry<UUID, Long>> it = PAD_READY_AT.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= gameTime) it.remove();
        }
    }

    public static void clear(UUID player) {
        PAD_READY_AT.remove(player);
    }

    public static void clearAll() {
        PAD_READY_AT.clear();
    }
}
