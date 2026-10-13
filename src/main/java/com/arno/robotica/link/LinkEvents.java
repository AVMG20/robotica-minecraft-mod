package com.arno.robotica.link;

import com.arno.robotica.Robotica;
import com.arno.robotica.link.network.LinkNetworks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server side hooks of the link module: the per-tick hand-out of every network's buffer, owner name refresh. */
@EventBusSubscriber(modid = Robotica.MODID)
public final class LinkEvents {
    private LinkEvents() {}

    /** After every level ticked (so every loaded Link registered): energy and items go to the receivers. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LinkNetworks.get(event.getServer()).tick(event.getServer());
    }

    /** Networks show their owner's current name; names change, UUIDs do not. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LinkNetworks.get(player.server).refreshOwnerName(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LinkNetworks.get(event.getServer()).clearRuntime();
    }
}
