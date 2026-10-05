package com.arno.robotica.boss;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Boss loot that only its killer may pick up, for a while. The lock ends after {@link #LOCK_TICKS} or as soon as the killer
 * is offline, so a full inventory or a disconnect never leaves the Servo Core lying there for nobody. The release time is
 * stored on the item entity (it survives chunk unloads and restarts); loaded locked drops are checked once a second.
 */
public final class BossLoot {
    private BossLoot() {}

    /** Two minutes for the killer alone. */
    public static final int LOCK_TICKS = 20 * 120;
    private static final String KEY = "robotica:loot_lock_until";

    /** Loaded, still locked drops. Server thread only. */
    private static final List<WeakReference<ItemEntity>> LOCKED = new ArrayList<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(BossLoot::onJoin);
        NeoForge.EVENT_BUS.addListener(BossLoot::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> LOCKED.clear());
    }

    /** Locks a drop to the killer until {@code now + LOCK_TICKS}. The drop is tracked once it joins the level. */
    public static void lock(ItemEntity drop, UUID killer, long now) {
        drop.setTarget(killer);
        drop.getPersistentData().putLong(KEY, now + LOCK_TICKS);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item
                && item.getTarget() != null && item.getPersistentData().contains(KEY)) {
            LOCKED.add(new WeakReference<>(item));
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (LOCKED.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        releaseDue(server);
    }

    /** Unlocks every tracked drop whose time is up or whose killer is offline. Returns how many were released. */
    public static int releaseDue(MinecraftServer server) {
        int released = 0;
        for (Iterator<WeakReference<ItemEntity>> it = LOCKED.iterator(); it.hasNext(); ) {
            ItemEntity item = it.next().get();
            if (item == null || item.isRemoved()) {
                it.remove();
                continue;
            }
            UUID owner = item.getTarget();
            if (owner == null || !item.getPersistentData().contains(KEY)) {
                it.remove();
                continue;
            }
            long until = item.getPersistentData().getLong(KEY);
            if (item.level().getGameTime() >= until || server.getPlayerList().getPlayer(owner) == null) {
                item.setTarget(null);
                item.getPersistentData().remove(KEY);
                it.remove();
                released++;
            }
        }
        return released;
    }
}
