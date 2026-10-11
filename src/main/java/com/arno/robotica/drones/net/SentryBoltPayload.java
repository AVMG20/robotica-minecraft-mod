package com.arno.robotica.drones.net;

import com.arno.robotica.Robotica;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a Buddy Bot fired from {@code from} to {@code to}, in {@code color} (its stance) and {@code tier}
 * (thicker beam per Mk). One small packet per shot to the players tracking the bot; the drones client
 * ({@code BoltBeams}) draws the beam and the impact. The handler only queues the data (no client classes).
 */
public record SentryBoltPayload(float fx, float fy, float fz, float tx, float ty, float tz, int color, int tier) implements CustomPacketPayload {
    public static final Type<SentryBoltPayload> TYPE = new Type<>(Robotica.id("drones_sentry_bolt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SentryBoltPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.fx);
                buf.writeFloat(p.fy);
                buf.writeFloat(p.fz);
                buf.writeFloat(p.tx);
                buf.writeFloat(p.ty);
                buf.writeFloat(p.tz);
                buf.writeMedium(p.color);
                buf.writeByte(p.tier);
            },
            buf -> new SentryBoltPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readUnsignedMedium(), buf.readByte()));

    /** Longest shot drawn; anything longer is ignored by the client. */
    public static final double MAX_LENGTH = 64.0;

    private static final List<SentryBoltPayload> PENDING = new ArrayList<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(Entity drone, Vec3 from, Vec3 to, int color, int tier) {
        if (!(drone.level() instanceof ServerLevel level)) return;
        SentryBoltPayload payload = null;
        for (net.minecraft.server.level.ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(drone.chunkPosition(), false)) {
            // mock and fake players (game tests, other mods) have no Robotica channel
            if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null || !player.connection.hasChannel(TYPE)) continue;
            if (payload == null) payload = new SentryBoltPayload((float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z, color, tier);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Client side: hands the queued shots to {@code sink} and clears the queue. */
    public static void drain(java.util.function.Consumer<SentryBoltPayload> sink) {
        for (int i = 0; i < PENDING.size(); i++) sink.accept(PENDING.get(i));
        PENDING.clear();
    }

    public double length() {
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static void handle(SentryBoltPayload p, IPayloadContext context) {
        double len = p.length();
        if (!(len <= MAX_LENGTH) || len < 1.0E-3) return;
        if (PENDING.size() < 64) PENDING.add(p);
    }
}
