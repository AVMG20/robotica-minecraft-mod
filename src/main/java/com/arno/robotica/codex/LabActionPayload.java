package com.arno.robotica.codex;

import com.arno.robotica.Robotica;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Creative Lab request from the codex screen. The server re-checks operator permission on every action.
 * action: "give" (arg = item id, count), or one of {@link LabActions#ACTIONS} (arg/count unused or age).
 */
public record LabActionPayload(String action, String arg, int count) implements CustomPacketPayload {
    public static final Type<LabActionPayload> TYPE = new Type<>(Robotica.id("codex_lab_action"));
    public static final StreamCodec<ByteBuf, LabActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), LabActionPayload::action,
            ByteBufCodecs.stringUtf8(256), LabActionPayload::arg,
            ByteBufCodecs.VAR_INT, LabActionPayload::count,
            LabActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LabActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!CodexModule.canUseLab(player)) {
                Robotica.LOGGER.warn("{} sent a Creative Lab action without operator permission", player.getGameProfile().getName());
                return;
            }
            LabActions.run(player, payload.action(), payload.arg(), payload.count());
        });
    }
}
