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
 * Client to server: set the mode of the controller whose GUI the player has open. The server only accepts it when the
 * player's open menu belongs to that position and is still valid (in reach), so it can not be used remotely.
 */
public record ReplicatorModePayload(BlockPos pos, int mode) implements CustomPacketPayload {
    public static final Type<ReplicatorModePayload> TYPE = new Type<>(Robotica.id("replicator_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplicatorModePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ReplicatorModePayload::pos,
            ByteBufCodecs.VAR_INT, ReplicatorModePayload::mode,
            ReplicatorModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, ReplicatorModePayload::handle);
    }

    private static void handle(ReplicatorModePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ReplicatorMenu menu) || !menu.pos().equals(payload.pos()) || !menu.stillValid(player)) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof ReplicatorControllerBlockEntity be) {
            be.setMode(ReplicatorControllerBlockEntity.Mode.byId(payload.mode()));
        }
    }
}
