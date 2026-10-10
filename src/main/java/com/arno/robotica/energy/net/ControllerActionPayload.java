package com.arno.robotica.energy.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.energy.block.ColliderBlockEntity;
import com.arno.robotica.energy.block.StructureControllerBlockEntity;
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
 * Client to server: a button in a controller GUI (the Ring Collider's on/off switch, the Tesla Spire's mute). Only accepted when the player's open menu belongs to that
 * position and is still valid (in reach), so it can not be used remotely, and only from the owner, the owner's team or
 * an operator ({@link StructureControllerBlockEntity#canControl}). Other players may watch.
 */
public record ControllerActionPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SET_ENABLED = 2;
    public static final int SET_MUTED = 3;

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
        registrar.playToClient(StructureFxPayload.TYPE, StructureFxPayload.CODEC, StructureFxPayload::handle);
    }

    private static void handle(ControllerActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) process(player, payload);
    }

    /** Applies the action when the player may; returns whether it reached the controller. Public for game tests. */
    public static boolean process(ServerPlayer player, ControllerActionPayload payload) {
        if (!(player.containerMenu instanceof ControllerMenu menu) || !menu.pos().equals(payload.pos()) || !menu.stillValid(player)) return false;
        var be = player.level().getBlockEntity(payload.pos());
        if (!(be instanceof StructureControllerBlockEntity controller) || !controller.canControl(player)) return false;
        switch (payload.action()) {
            case SET_ENABLED -> {
                if (be instanceof ColliderBlockEntity collider) collider.setEnabled(payload.value() != 0);
                else return false;
            }
            case SET_MUTED -> {
                if (be instanceof com.arno.robotica.energy.block.SpireBlockEntity spire) spire.setMuted(payload.value() != 0);
                else return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
