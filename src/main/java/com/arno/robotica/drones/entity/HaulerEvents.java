package com.arno.robotica.drones.entity;

import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.item.DroneItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Game event hooks of the Hauler Drone: capture with the item (before the mob's own right-click, so villagers do not open
 * trades), right-clicks on a carried mob, no riding a carried mob, AI back on every dismount and no suffocation or fall
 * damage while hanging. The capture listener runs at LOW priority and skips cancelled events, so claim mods that veto the
 * interaction keep the mob.
 */
public final class HaulerEvents {
    private HaulerEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, PlayerInteractEvent.EntityInteract.class, HaulerEvents::onInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityMountEvent.class, HaulerEvents::onMountAttempt);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityMountEvent.class, HaulerEvents::onDismounted);
        NeoForge.EVENT_BUS.addListener(EntityInvulnerabilityCheckEvent.class, HaulerEvents::onInvulnerabilityCheck);
    }

    private static boolean isHauler(ItemStack stack) {
        return stack.getItem() instanceof DroneItem item && item.kind() == DronesRegistry.Kind.HAULER;
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (target.getVehicle() instanceof HaulerDrone drone) {
            InteractionResult result = player.level().isClientSide ? InteractionResult.SUCCESS : drone.interactFrom(player, event.getHand());
            event.setCanceled(true);
            event.setCancellationResult(result);
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!isHauler(stack) || !(target instanceof Mob mob) || target instanceof DroneBase) return;
        if (player instanceof ServerPlayer sp) HaulerDrone.capture(sp, stack, mob);
        event.setCanceled(true);
        event.setCancellationResult(player.level().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    /** Nothing mounts a carried mob (no riding a horse away from the drone). */
    private static void onMountAttempt(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityBeingMounted().getVehicle() instanceof HaulerDrone) event.setCanceled(true);
    }

    /** Any dismount from a hauler gives the mob its AI back, except when both are only being unloaded with the chunk. */
    private static void onDismounted(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide) return;
        if (!(event.getEntityBeingMounted() instanceof HaulerDrone drone) || !(event.getEntityMounting() instanceof Mob mob)) return;
        Entity.RemovalReason reason = drone.getRemovalReason();
        if (reason != null && !reason.shouldDestroy()) return;
        HaulerDrone.restoreAi(mob);
    }

    private static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.isInvulnerable() || !(event.getEntity().getVehicle() instanceof HaulerDrone)) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.IS_DROWNING)) {
            event.setInvulnerable(true);
        }
    }
}
