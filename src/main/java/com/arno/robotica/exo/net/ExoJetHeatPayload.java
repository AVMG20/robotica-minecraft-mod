package com.arno.robotica.exo.net;

import com.arno.robotica.Robotica;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to the wearer: the Jet Assist heat (in ticks of thrust, see {@link com.arno.robotica.exo.ExoJet}) and the
 * overheat, sent when the server's pack starts on a new player object or a fresh state (suit on, login, respawn,
 * dimension change), so the client's prediction does not start cold. The handler only keeps the latest values (no
 * client classes); the client module takes them on its next tick.
 */
public record ExoJetHeatPayload(float heat, boolean overheated) implements CustomPacketPayload {
    public static final Type<ExoJetHeatPayload> TYPE = new Type<>(Robotica.id("exo_jet_heat"));
    public static final StreamCodec<ByteBuf, ExoJetHeatPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ExoJetHeatPayload::heat, ByteBufCodecs.BOOL, ExoJetHeatPayload::overheated, ExoJetHeatPayload::new);

    private static boolean pending;
    private static float latestHeat;
    private static boolean latestOverheated;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ExoJetHeatPayload payload, IPayloadContext context) {
        if (!Float.isFinite(payload.heat())) return;
        latestHeat = Math.max(0, payload.heat());
        latestOverheated = payload.overheated();
        pending = true;
    }

    /** Client side: true once per received update; then read {@link #heatValue} and {@link #overheatedValue}. */
    public static boolean take() {
        boolean was = pending;
        pending = false;
        return was;
    }

    public static float heatValue() {
        return latestHeat;
    }

    public static boolean overheatedValue() {
        return latestOverheated;
    }
}
