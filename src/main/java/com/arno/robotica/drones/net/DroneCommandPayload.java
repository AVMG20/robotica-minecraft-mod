package com.arno.robotica.drones.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client to server: the drone command key. {@link #TUNNEL}: the nearest own Mining Drone digs along the direction the
 * player looks (or returns when it is already digging). {@link #RECALL}: all own drones in range come back (Mining Drones
 * return, Sentry Drones follow). The server checks ownership and distance itself and rate limits per player.
 */
public record DroneCommandPayload(int action) implements CustomPacketPayload {
    public static final int TUNNEL = 0;
    public static final int RECALL = 1;

    public static final Type<DroneCommandPayload> TYPE = new Type<>(Robotica.id("drones_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneCommandPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DroneCommandPayload::action,
            DroneCommandPayload::new);

    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, DroneCommandPayload::handle);
    }

    /** Drops the rate limit entry of a player who left. */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_USE.remove(event.getEntity().getUUID());
    }

    private static void handle(DroneCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) apply(player, payload.action());
        });
    }

    /** Server side entry point (also used by tests). */
    public static void apply(ServerPlayer player, int action) {
        long now = player.level().getGameTime();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < 10) return;
        LAST_USE.put(player.getUUID(), now);
        double range = DronesConfig.commandDistance();
        List<DroneBase> drones = player.level().getEntitiesOfClass(DroneBase.class, player.getBoundingBox().inflate(range),
                d -> d.isAlive() && d.getOwnerUUID() != null && d.getOwnerUUID().equals(player.getUUID()) && d.distanceToSqr(player) <= range * range);
        if (drones.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.robotica.drone.none_near"), true);
            return;
        }
        if (action == TUNNEL) {
            MiningDrone nearest = null;
            for (DroneBase d : drones) {
                if (d instanceof MiningDrone m && (nearest == null || m.distanceToSqr(player) < nearest.distanceToSqr(player))) nearest = m;
            }
            if (nearest == null) {
                player.displayClientMessage(Component.translatable("message.robotica.drone.no_miner"), true);
            } else if (nearest.mode() == MiningDrone.Mode.TUNNEL) {
                nearest.commandReturn();
            } else {
                nearest.commandTunnel(player, player.getDirection());
            }
        } else if (action == RECALL) {
            for (DroneBase d : drones) {
                if (d instanceof MiningDrone m) m.commandReturn();
                else if (d instanceof SentryDrone s) s.setMode(SentryDrone.Mode.FOLLOW);
            }
            player.displayClientMessage(Component.translatable("message.robotica.drone.recalled", drones.size()), true);
        }
    }
}
