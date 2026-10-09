package com.arno.robotica.energy.net;

import com.arno.robotica.Robotica;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a one-off multiblock effect, drawn by the energy client ({@code StructureFx}). {@link #FORM}: light
 * runs from the controller along the frame edges of {@code box}; {@link #UNFORM}: the light drains back with sparks and
 * smoke; {@link #SPAWN}: a shimmer where the Replicator spawned a mob ({@code box} is the mob's space). Sent once per event
 * to the players tracking the controller's chunk. The handler only queues the data (no client classes).
 */
public record StructureFxPayload(int kind, BlockPos origin, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color)
        implements CustomPacketPayload {
    public static final int FORM = 0, UNFORM = 1, SPAWN = 2;

    public static final Type<StructureFxPayload> TYPE = new Type<>(Robotica.id("energy_structure_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructureFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeBlockPos(p.origin);
                buf.writeVarInt(p.minX);
                buf.writeVarInt(p.minY);
                buf.writeVarInt(p.minZ);
                buf.writeVarInt(p.maxX - p.minX);
                buf.writeVarInt(p.maxY - p.minY);
                buf.writeVarInt(p.maxZ - p.minZ);
                buf.writeInt(p.color);
            },
            buf -> {
                int kind = buf.readByte();
                BlockPos origin = buf.readBlockPos();
                int x = buf.readVarInt(), y = buf.readVarInt(), z = buf.readVarInt();
                int w = buf.readVarInt(), h = buf.readVarInt(), d = buf.readVarInt();
                return new StructureFxPayload(kind, origin, x, y, z, x + w, y + h, z + d, buf.readInt());
            });

    /** Largest box side drawn (bigger ones are ignored by the client). */
    public static final int MAX_SIZE = 64;

    private static final List<StructureFxPayload> PENDING = new ArrayList<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Sends the effect to everyone tracking the controller's chunk. */
    public static void send(ServerLevel level, int kind, BlockPos origin, BoundingBox box, int color) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(origin), new StructureFxPayload(kind, origin,
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), color));
    }

    /** Client side: hands the queued effects to {@code sink} and clears the queue. */
    public static void drain(java.util.function.Consumer<StructureFxPayload> sink) {
        for (int i = 0; i < PENDING.size(); i++) sink.accept(PENDING.get(i));
        PENDING.clear();
    }

    static void handle(StructureFxPayload payload, IPayloadContext context) {
        if (payload.kind < FORM || payload.kind > SPAWN) return;
        if (payload.maxX - payload.minX >= MAX_SIZE || payload.maxY - payload.minY >= MAX_SIZE || payload.maxZ - payload.minZ >= MAX_SIZE) return;
        if (payload.maxX < payload.minX || payload.maxY < payload.minY || payload.maxZ < payload.minZ) return;
        if (PENDING.size() < 32) PENDING.add(payload);
    }
}
