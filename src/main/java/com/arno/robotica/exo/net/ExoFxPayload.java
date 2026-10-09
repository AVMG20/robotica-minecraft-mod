package com.arno.robotica.exo.net;

import com.arno.robotica.Robotica;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: an Exo-Frame moment of the player with this entity id (sent to everyone tracking the wearer and to
 * the wearer), or a change of its flying state. The client draws the effect itself, so it costs one tiny packet instead
 * of a stream of particle packets, and each player's client config decides what draws. The handler only queues plain
 * data (no client classes); the client module takes it on its next tick.
 */
public record ExoFxPayload(int entity, byte kind) implements CustomPacketPayload {
    public static final byte FLIGHT_ON = 0, FLIGHT_OFF = 1, LANDED = 2, DASH = 3, SPRING = 4, MED = 5, SHIELD_FULL = 6, SHIELD_PARTIAL = 7;
    private static final byte KINDS = 8;

    public static final Type<ExoFxPayload> TYPE = new Type<>(Robotica.id("exo_fx"));
    public static final StreamCodec<ByteBuf, ExoFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExoFxPayload::entity, ByteBufCodecs.BYTE, ExoFxPayload::kind, ExoFxPayload::new);

    /** Queued (entity, kind) pairs; handled on the main thread, read by the client tick. Overflow drops the newest. */
    private static final int[] QUEUE = new int[2 * 64];
    private static int queued;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ExoFxPayload payload, IPayloadContext context) {
        if (payload.kind() < 0 || payload.kind() >= KINDS || queued * 2 >= QUEUE.length) return;
        QUEUE[queued * 2] = payload.entity();
        QUEUE[queued * 2 + 1] = payload.kind();
        queued++;
    }

    /** Client side: how many entries wait. */
    public static int queued() {
        return queued;
    }

    public static int entity(int i) {
        return QUEUE[i * 2];
    }

    public static int kind(int i) {
        return QUEUE[i * 2 + 1];
    }

    public static void clearQueue() {
        queued = 0;
    }
}
