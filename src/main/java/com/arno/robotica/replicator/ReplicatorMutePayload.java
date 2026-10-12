package com.arno.robotica.replicator;

import com.arno.robotica.Robotica;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: mutes or unmutes the working sounds of the controller whose GUI the player has open. Accepted only
 * while the player's open menu belongs to that position and is still valid (in reach).
 */
public record ReplicatorMutePayload(BlockPos pos, boolean muted) implements CustomPacketPayload {
    public static final Type<ReplicatorMutePayload> TYPE = new Type<>(Robotica.id("replicator_mute"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplicatorMutePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ReplicatorMutePayload::pos,
            ByteBufCodecs.BOOL, ReplicatorMutePayload::muted,
            ReplicatorMutePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, ReplicatorMutePayload::handle);
    }

    private static void handle(ReplicatorMutePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ReplicatorMenu menu) || !menu.pos().equals(payload.pos()) || !menu.stillValid(player)) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof ReplicatorControllerBlockEntity be) be.setMuted(payload.muted());
    }
}
