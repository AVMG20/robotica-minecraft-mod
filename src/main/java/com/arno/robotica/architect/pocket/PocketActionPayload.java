package com.arno.robotica.architect.pocket;

import com.arno.robotica.Robotica;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Pocket Architect GUI request (take blocks, pick the block to place, toggle junk eating). The server only acts when the
 * player has a Pocket Architect menu open with this container id; indices are validated by the menu.
 */
public record PocketActionPayload(int containerId, int action, int a, int b) implements CustomPacketPayload {
    public static final Type<PocketActionPayload> TYPE = new Type<>(Robotica.id("architect_pocket"));
    public static final StreamCodec<ByteBuf, PocketActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PocketActionPayload::containerId,
            ByteBufCodecs.VAR_INT, PocketActionPayload::action,
            ByteBufCodecs.VAR_INT, PocketActionPayload::a,
            ByteBufCodecs.VAR_INT, PocketActionPayload::b,
            PocketActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PocketActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) process(player, payload);
        });
    }

    /** Validates the request and applies it. Returns false when it was refused before reaching the menu. */
    public static boolean process(ServerPlayer player, PocketActionPayload payload) {
        if (!(player.containerMenu instanceof PocketMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) return false;
        Component feedback = menu.handleAction(payload.action(), payload.a(), payload.b());
        if (feedback != null) player.displayClientMessage(feedback, true);
        menu.broadcastChanges();
        return true;
    }
}
