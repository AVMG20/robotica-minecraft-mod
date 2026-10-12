package com.arno.robotica.logistics.net;

import com.arno.robotica.Robotica;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: items went from the Extract face {@code fromSide} of pipe {@code from} to the Insert face
 * {@code toSide} of pipe {@code to}. {@code steps}: one direction (3D data value) per pipe, two per byte on the wire,
 * cut at {@link #MAX_STEPS}. The handler only queues it for {@code ItemPipeGlow}.
 */
public record PipeGlowPayload(long from, int fromSide, long to, int toSide, byte[] steps) implements CustomPacketPayload {
    public static final Type<PipeGlowPayload> TYPE = new Type<>(Robotica.id("logistics_pipe_glow"));
    public static final int MAX_STEPS = 1024;
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeGlowPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.from);
                buf.writeByte(p.fromSide);
                buf.writeLong(p.to);
                buf.writeByte(p.toSide);
                int n = Math.min(p.steps.length, MAX_STEPS);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i += 2) buf.writeByte(p.steps[i] | (i + 1 < n ? p.steps[i + 1] << 4 : 0));
            },
            buf -> {
                long from = buf.readLong();
                int fromSide = buf.readByte();
                long to = buf.readLong();
                int toSide = buf.readByte();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_STEPS) throw new IllegalArgumentException("pipe route too long: " + n);
                byte[] steps = new byte[n];
                for (int i = 0; i < n; i += 2) {
                    int b = buf.readUnsignedByte();
                    steps[i] = (byte) (b & 7);
                    if (i + 1 < n) steps[i + 1] = (byte) (b >> 4 & 7);
                }
                return new PipeGlowPayload(from, fromSide, to, toSide, steps);
            });

    private static final List<PipeGlowPayload> PENDING = new ArrayList<>();
    private static final int MAX_PENDING = 64;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, PipeGlowPayload::handle);
    }

    /** False for fake and mock players, which have no Robotica channel. */
    public static boolean canReceive(ServerPlayer player) {
        return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && player.connection != null && player.connection.hasChannel(TYPE);
    }

    public void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, this);
    }

    /** Client side: hands the queued routes to {@code sink} and clears the queue. */
    public static void drain(java.util.function.Consumer<PipeGlowPayload> sink) {
        for (int i = 0; i < PENDING.size(); i++) sink.accept(PENDING.get(i));
        PENDING.clear();
    }

    static void handle(PipeGlowPayload payload, IPayloadContext context) {
        if (payload.fromSide < 0 || payload.fromSide > 5 || payload.toSide < -1 || payload.toSide > 5) return;
        for (byte step : payload.steps) if (step > 5) return;
        if (PENDING.size() < MAX_PENDING) PENDING.add(payload);
    }
}
