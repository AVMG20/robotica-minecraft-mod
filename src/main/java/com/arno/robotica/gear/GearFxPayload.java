package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;

/**
 * Server to client: one weapon or tool effect, drawn by each client itself (GearFx), so a swing or shot costs one packet per
 * nearby player instead of one per particle.
 * <ul>
 *   <li>{@link #ARC_CHAIN}: {@code points} is the chain of struck centres (x, y, z each), first the main target;</li>
 *   <li>{@link #LANCE}: {@code points} is eye, look direction, beam end, then each hit centre; {@code ints[0]} is 1
 *   when the beam hit a block;</li>
 *   <li>{@link #DRAIN}: {@code points} is the Lifesteal stream from the target to the player;</li>
 *   <li>{@link #STATIC}: {@code points} are the centres of a few blocks of an area break that get a static pop.</li>
 * </ul>
 * The handler only queues plain data (no client classes); the client takes it on its next tick.
 */
public record GearFxPayload(byte kind, float[] points, int[] ints) implements CustomPacketPayload {
    public static final byte ARC_CHAIN = 0, LANCE = 1, DRAIN = 2, STATIC = 3;
    private static final int MAX_FLOATS = 3 * 64, MAX_INTS = 8, MAX_QUEUED = 64;
    /** Players further away than this get no effect. */
    public static final double RANGE = 64;

    public static final Type<GearFxPayload> TYPE = new Type<>(Robotica.id("gear_fx"));
    public static final StreamCodec<ByteBuf, GearFxPayload> CODEC = StreamCodec.of(GearFxPayload::write, GearFxPayload::read);

    private static void write(ByteBuf buf, GearFxPayload p) {
        FriendlyByteBuf b = new FriendlyByteBuf(buf);
        b.writeByte(p.kind);
        b.writeVarInt(p.points.length);
        for (float f : p.points) b.writeFloat(f);
        b.writeVarInt(p.ints.length);
        for (int i : p.ints) b.writeVarInt(i);
    }

    private static GearFxPayload read(ByteBuf buf) {
        FriendlyByteBuf b = new FriendlyByteBuf(buf);
        byte kind = b.readByte();
        int n = b.readVarInt();
        if (n < 0 || n > MAX_FLOATS) throw new IllegalArgumentException("gear_fx: too many points");
        float[] points = new float[n];
        for (int i = 0; i < n; i++) points[i] = b.readFloat();
        int m = b.readVarInt();
        if (m < 0 || m > MAX_INTS) throw new IllegalArgumentException("gear_fx: too many ints");
        int[] ints = new int[m];
        for (int i = 0; i < m; i++) ints[i] = b.readVarInt();
        return new GearFxPayload(kind, points, ints);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final ArrayDeque<GearFxPayload> QUEUE = new ArrayDeque<>();

    static void handle(GearFxPayload payload, IPayloadContext context) {
        if (QUEUE.size() < MAX_QUEUED) QUEUE.addLast(payload);
    }

    /** Client side: the next queued effect, or null. */
    public static GearFxPayload poll() {
        return QUEUE.pollFirst();
    }

    public static void clearQueue() {
        QUEUE.clear();
    }

    /** Sends the effect to every player within {@link #RANGE} of (x, y, z) who has the channel. */
    public static void send(ServerLevel level, double x, double y, double z, GearFxPayload payload) {
        for (ServerPlayer p : level.players()) {
            if (p instanceof FakePlayer || p.connection == null || p.distanceToSqr(x, y, z) > RANGE * RANGE) continue;
            if (p.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(p, payload);
        }
    }
}
