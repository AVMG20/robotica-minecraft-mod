package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Hooks that run the Exo-Frame (both sides where noted, otherwise server only). */
@EventBusSubscriber(modid = Robotica.MODID)
public final class ExoEvents {
    private ExoEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.tick(player);
    }

    /** Both sides: the client needs to agree that the player does not drown, or its bubble bar would drain. */
    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) ExoTicker.onBreathe(player, event);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onIncomingDamage(player, event);
        if (!event.isCanceled() && event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            ExoTicker.onAttack(attacker, event.getEntity(), event);
        }
    }

    /** After invulnerability frames and armor: the Kinetic Shield. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onDamage(player, event);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onKnockback(player, event);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onFall(player, event);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onJump(player);
    }

    /** Last, so a change another mod cancels (or one to the same mode, which vanilla skips) keeps the flight grant. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getNewGameMode() == event.getCurrentGameMode()) return;
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onGameModeChange(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onDeath(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onRespawn(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ExoTicker.onLogout(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ExoTicker.clearAll();
    }
}
