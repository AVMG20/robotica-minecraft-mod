package com.arno.robotica.drones.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.DronesClientConfig;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * Server to client: a Sentry Drone fired from {@code from} to {@code to}. One small packet per shot to the players
 * tracking the drone; each client draws the trail and the impact sparks itself (vanilla particles, so the particle
 * setting applies). Only common classes: the particles go through the client's {@link Level}.
 */
public record SentryBoltPayload(float fx, float fy, float fz, float tx, float ty, float tz) implements CustomPacketPayload {
    public static final Type<SentryBoltPayload> TYPE = new Type<>(Robotica.id("drones_sentry_bolt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SentryBoltPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SentryBoltPayload::fx, ByteBufCodecs.FLOAT, SentryBoltPayload::fy, ByteBufCodecs.FLOAT, SentryBoltPayload::fz,
            ByteBufCodecs.FLOAT, SentryBoltPayload::tx, ByteBufCodecs.FLOAT, SentryBoltPayload::ty, ByteBufCodecs.FLOAT, SentryBoltPayload::tz,
            SentryBoltPayload::new);

    /** Bright core dust and a fainter, larger glow dust, the drone's cyan. */
    private static final DustParticleOptions CORE = new DustParticleOptions(new Vector3f(0.75F, 0.97F, 1.0F), 0.6F);
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.37F, 0.89F, 0.94F), 1.0F);
    /** Trail points per block, and at most this many in all. */
    private static final double DENSITY = 2.2;
    private static final int MAX_POINTS = 28;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(Entity drone, Vec3 from, Vec3 to) {
        if (!(drone.level() instanceof ServerLevel)) return;
        PacketDistributor.sendToPlayersTrackingEntity(drone, new SentryBoltPayload((float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z));
    }

    static void handle(SentryBoltPayload p, IPayloadContext context) {
        Level level = context.player().level();
        if (!level.isClientSide) return;
        double dx = p.tx - p.fx, dy = p.ty - p.fy, dz = p.tz - p.fz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 64 || len < 1.0E-3) return;
        RandomSource random = level.random;
        if (DronesClientConfig.boltTrails()) {
            int n = Math.min(MAX_POINTS, Math.max(2, (int) (len * DENSITY)));
            for (int i = 1; i <= n; i++) {
                double t = (double) i / n;
                double x = p.fx + dx * t, y = p.fy + dy * t, z = p.fz + dz * t;
                level.addParticle(CORE, x, y, z, 0, 0, 0);
                if ((i & 1) == 0) level.addParticle(GLOW, x, y, z, 0, 0, 0);
            }
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.tx + random.nextGaussian() * 0.15, p.ty + random.nextGaussian() * 0.15,
                    p.tz + random.nextGaussian() * 0.15, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1);
        }
    }
}
