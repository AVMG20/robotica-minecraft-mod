package com.arno.robotica.core.chunk;

import com.arno.robotica.Robotica;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Per-player chunk state: anchors pause and resume with their owner (offline loading off), on-demand chunks end on logout. */
@EventBusSubscriber(modid = Robotica.MODID)
public final class ChunkEvents {
    private ChunkEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkClaims.get(player.server).ownerJoined(player.server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkTickets.releasePlayer(player.server, player.getUUID());
            ChunkClaims.get(player.server).ownerLeft(player.server, player.getUUID(), ChunkClaims.whileOffline());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ChunkTickets.clear();
    }
}
