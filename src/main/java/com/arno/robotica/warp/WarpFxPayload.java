package com.arno.robotica.warp;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

/**
 * Server to client: one step of the shared warp effect, drawn by each client itself (warp.client.WarpFx).
 * {@link #CHARGE}: the vortex gathers around the traveller, {@code value} is the charge progress 0..1;
 * {@link #DEPART} / {@link #ARRIVE}: the flash at either end, {@code value} 1 for a full trip, 0 for a quiet one (portals).
 * Positions are the traveller's feet. {@link #STOP} (to one player): the server stopped or refused the go-home hold the
 * client started, so the client ends its use animation too.
 */
public record WarpFxPayload(byte kind, float x, float y, float z, float value) implements CustomPacketPayload {
    public static final byte CHARGE = 0, DEPART = 1, ARRIVE = 2, STOP = 3;
    /** Players further away than this get no effect. */
    public static final double RANGE = 48;

    public static final Type<WarpFxPayload> TYPE = new Type<>(Robotica.id("warp_fx"));
    public static final StreamCodec<ByteBuf, WarpFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WarpFxPayload::kind,
            ByteBufCodecs.FLOAT, WarpFxPayload::x,
            ByteBufCodecs.FLOAT, WarpFxPayload::y,
            ByteBufCodecs.FLOAT, WarpFxPayload::z,
            ByteBufCodecs.FLOAT, WarpFxPayload::value,
            WarpFxPayload::new);

    /** Set by the client entry point; a no-op on a dedicated server. */
    public static Consumer<WarpFxPayload> clientHandler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void send(ServerLevel level, double x, double y, double z, WarpFxPayload payload) {
        for (ServerPlayer p : level.players()) {
            if (p instanceof FakePlayer || p.connection == null || p.distanceToSqr(x, y, z) > RANGE * RANGE) continue;
            if (p.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    /** Ends the Warp Remote hold on this player's client too (a server-side stopUsingItem does not reach it). */
    public static void stopUse(ServerPlayer player) {
        player.stopUsingItem();
        if (!(player instanceof FakePlayer) && player.connection != null && player.connection.hasChannel(TYPE)) {
            PacketDistributor.sendToPlayer(player, new WarpFxPayload(STOP, 0, 0, 0, 0));
        }
    }

    /** One tick of a charging trip, {@code elapsed} of {@code total} ticks in: vortex every other tick, a rising hum. */
    public static void charge(ServerLevel level, Entity traveller, int elapsed, int total) {
        if (elapsed % 2 == 0) {
            send(level, traveller.getX(), traveller.getY(), traveller.getZ(), new WarpFxPayload(CHARGE,
                    (float) traveller.getX(), (float) traveller.getY(), (float) traveller.getZ(), Math.min(1.0F, (float) elapsed / total)));
        }
        if (elapsed % 6 == 0) {
            float pitch = 0.6F + 1.2F * elapsed / total;
            CoreSounds.play(level, traveller.getX(), traveller.getY(), traveller.getZ(), CoreSounds.WARP_CHARGE, SoundSource.PLAYERS, 0.8F, pitch);
        }
    }

    public static void depart(ServerLevel level, double x, double y, double z, boolean quiet) {
        send(level, x, y, z, new WarpFxPayload(DEPART, (float) x, (float) y, (float) z, quiet ? 0 : 1));
        CoreSounds.play(level, x, y, z, quiet ? CoreSounds.TELEPORT_QUIET_DEPART : CoreSounds.TELEPORT_DEPART, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void arrive(ServerLevel level, double x, double y, double z, boolean quiet) {
        send(level, x, y, z, new WarpFxPayload(ARRIVE, (float) x, (float) y, (float) z, quiet ? 0 : 1));
        CoreSounds.play(level, x, y, z, quiet ? CoreSounds.TELEPORT_QUIET_ARRIVE : CoreSounds.TELEPORT_ARRIVE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
