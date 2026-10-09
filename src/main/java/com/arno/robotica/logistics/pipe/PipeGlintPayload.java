package com.arno.robotica.logistics.pipe;

import com.arno.robotica.Robotica;
import com.arno.robotica.logistics.LogisticsClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * Server to client: an item pipe pulled items. A short streak of light runs in from the extract face, and a glint
 * pops where they went in ({@code to}, the insert pipe and face; {@code toSide} -1 when unknown). Sent at most a few
 * times a second per pipe to the players close by; the client draws it only when its toggle is on. Only common
 * classes: the particles go through the client's {@link Level}.
 */
public record PipeGlintPayload(long from, int fromSide, long to, int toSide) implements CustomPacketPayload {
    public static final Type<PipeGlintPayload> TYPE = new Type<>(Robotica.id("logistics_pipe_glint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeGlintPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PipeGlintPayload::from, ByteBufCodecs.BYTE.map(b -> (int) b, i -> (byte) (int) i), PipeGlintPayload::fromSide,
            ByteBufCodecs.VAR_LONG, PipeGlintPayload::to, ByteBufCodecs.BYTE.map(b -> (int) b, i -> (byte) (int) i), PipeGlintPayload::toSide,
            PipeGlintPayload::new);

    /** Players this close see the glints. */
    private static final double RANGE = 24;
    private static final DustParticleOptions CORE = new DustParticleOptions(new Vector3f(0.85F, 0.97F, 1.0F), 0.45F);
    private static final DustParticleOptions HALO = new DustParticleOptions(new Vector3f(0.45F, 0.8F, 1.0F), 0.7F);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, PipeGlintPayload::handle);
    }

    static void send(ServerLevel level, BlockPos pipe, Direction side, BlockPos insertPipe, Direction insertSide) {
        PipeGlintPayload payload = null;
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            // mock and fake players (game tests, other mods) have no Robotica channel
            if (player.distanceToSqr(pipe.getX() + 0.5, pipe.getY() + 0.5, pipe.getZ() + 0.5) > RANGE * RANGE
                    || player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null
                    || !player.connection.hasChannel(TYPE)) continue;
            if (payload == null) payload = new PipeGlintPayload(pipe.asLong(), side.get3DDataValue(), insertPipe == null ? 0 : insertPipe.asLong(),
                    insertSide == null ? -1 : insertSide.get3DDataValue());
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    static void handle(PipeGlintPayload p, IPayloadContext context) {
        Player viewer = context.player();
        Level level = viewer.level();
        if (!level.isClientSide || !LogisticsClientConfig.pipeParticles() || p.fromSide < 0 || p.fromSide > 5) return;
        // the pipe's arm hides anything inside it, so the glints sit just outside, on the side facing the player
        Direction side = Direction.from3DDataValue(p.fromSide);
        BlockPos pos = BlockPos.of(p.from);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        lift(viewer, cx, cy, cz, side);
        // a short streak from the inventory's face into the pipe, brightest at the pipe end, and a glint at the face
        for (int i = 0; i < 3; i++) {
            double d = 0.5 - i * 0.16;
            level.addParticle(i == 2 ? CORE : HALO, cx + side.getStepX() * d + lx, cy + side.getStepY() * d + ly, cz + side.getStepZ() * d + lz, 0, 0, 0);
        }
        level.addParticle(ParticleTypes.WAX_OFF, cx + side.getStepX() * 0.45 + lx, cy + side.getStepY() * 0.45 + ly, cz + side.getStepZ() * 0.45 + lz, 0, 0, 0);
        if (p.toSide >= 0 && p.toSide <= 5) {
            Direction in = Direction.from3DDataValue(p.toSide);
            BlockPos to = BlockPos.of(p.to);
            double tx = to.getX() + 0.5, ty = to.getY() + 0.5, tz = to.getZ() + 0.5;
            lift(viewer, tx, ty, tz, in);
            level.addParticle(ParticleTypes.WAX_OFF, tx + in.getStepX() * 0.4 + lx, ty + in.getStepY() * 0.4 + ly, tz + in.getStepZ() * 0.4 + lz, 0, 0, 0);
            level.addParticle(CORE, tx + in.getStepX() * 0.3 + lx, ty + in.getStepY() * 0.3 + ly, tz + in.getStepZ() * 0.3 + lz, 0, 0, 0);
        }
    }

    /** Offset of the glints: out of the pipe, square to its arm, toward the viewer. Main thread only. */
    private static double lx, ly, lz;

    private static void lift(Player viewer, double x, double y, double z, Direction arm) {
        double dx = viewer.getX() - x, dy = viewer.getEyeY() - y, dz = viewer.getZ() - z;
        double along = dx * arm.getStepX() + dy * arm.getStepY() + dz * arm.getStepZ();
        dx -= along * arm.getStepX();
        dy -= along * arm.getStepY();
        dz -= along * arm.getStepZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-3) {
            dx = 0;
            dy = arm.getAxis() == Direction.Axis.Y ? 0 : 1;
            dz = arm.getAxis() == Direction.Axis.Y ? 1 : 0;
            len = 1;
        }
        lx = dx / len * LIFT;
        ly = dy / len * LIFT;
        lz = dz / len * LIFT;
    }

    private static final double LIFT = 0.36;
}
