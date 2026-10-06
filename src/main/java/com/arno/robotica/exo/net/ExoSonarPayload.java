package com.arno.robotica.exo.net;

import com.arno.robotica.Robotica;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the server charged a Sonar Pulse; the client scans its loaded blocks within {@code radius} and
 * outlines ores and mobs for {@code duration} ticks. The handler only stores the request (plain data, no client
 * classes), the client module picks it up on its next tick.
 */
public record ExoSonarPayload(int radius, int duration) implements CustomPacketPayload {
    public static final Type<ExoSonarPayload> TYPE = new Type<>(Robotica.id("exo_sonar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExoSonarPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExoSonarPayload::radius, ByteBufCodecs.VAR_INT, ExoSonarPayload::duration, ExoSonarPayload::new);

    private static volatile ExoSonarPayload pending;

    /** Takes the last unhandled ping, or null. Client side. */
    public static ExoSonarPayload take() {
        ExoSonarPayload p = pending;
        pending = null;
        return p;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ExoSonarPayload payload, IPayloadContext context) {
        pending = new ExoSonarPayload(Math.max(1, Math.min(payload.radius(), 64)), Math.max(1, Math.min(payload.duration(), 1_200)));
    }
}
