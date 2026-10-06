package com.arno.robotica.energy.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.energy.block.FusionControllerBlockEntity;
import com.arno.robotica.energy.block.ReactorControllerBlockEntity;
import com.arno.robotica.energy.menu.ControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: a button or slider in a controller GUI. Only accepted when the player's open menu belongs to that
 * position and is still valid (in reach), so it can not be used remotely.
 */
public record ControllerActionPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SET_RODS = 0, RESET_SCRAM = 1, SET_ENABLED = 2;

    public static final Type<ControllerActionPayload> TYPE = new Type<>(Robotica.id("energy_controller_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerActionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControllerActionPayload::pos,
            ByteBufCodecs.VAR_INT, ControllerActionPayload::action,
            ByteBufCodecs.VAR_INT, ControllerActionPayload::value,
            ControllerActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TYPE, CODEC, ControllerActionPayload::handle);
        registrar.playToClient(ControllerSyncPayload.TYPE, ControllerSyncPayload.CODEC, ControllerSyncPayload::handle);
    }

    private static void handle(ControllerActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ControllerMenu menu) || !menu.pos().equals(payload.pos()) || !menu.stillValid(player)) return;
        var be = player.level().getBlockEntity(payload.pos());
        switch (payload.action()) {
            case SET_RODS -> {
                if (be instanceof ReactorControllerBlockEntity reactor) reactor.setRodInsertion(payload.value());
            }
            case RESET_SCRAM -> {
                if (be instanceof ReactorControllerBlockEntity reactor) reactor.resetScram();
            }
            case SET_ENABLED -> {
                if (be instanceof FusionControllerBlockEntity fusion) fusion.setEnabled(payload.value() != 0);
            }
            default -> {
            }
        }
    }
}
