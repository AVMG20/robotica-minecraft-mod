package com.arno.robotica.warp;

import com.arno.robotica.Robotica;
import com.arno.robotica.warp.item.RemoteItem;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.RiftCharges;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server side hooks of the warp module: remote charge interruption, owner name refresh, cooldown pruning. */
@EventBusSubscriber(modid = Robotica.MODID)
public final class WarpEvents {
    private WarpEvents() {}

    /** Any damage that goes through cancels the 3 second remote charge. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getAmount() <= 0.0F || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof RemoteItem) {
            player.stopUsingItem();
            WarpTravel.message(player, Component.translatable("message.robotica.warp.interrupted"));
        }
        if (RiftCharges.cancel(player.getUUID())) WarpTravel.message(player, Component.translatable("message.robotica.warp.interrupted"));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RiftCharges.cancel(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RiftCharges.clear();
    }

    /** Pads show the owner's current name; names change, UUIDs do not. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WarpPads.get(player.server).refreshOwnerName(player.getUUID(), player.getGameProfile().getName());
            // the vanilla item cooldown does not survive a relog: restore it from the persisted ready-at time
            RemoteItem.restoreCooldown(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RemoteItem.restoreCooldown(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RiftCharges.tick(event.getServer());
        long time = event.getServer().overworld().getGameTime();
        if (time % 1200 == 0) WarpCooldowns.get(event.getServer()).prune(time);
    }
}
