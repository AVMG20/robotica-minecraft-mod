package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: change mode, silk/fortune swap or a toggle of the tool in the main hand. See GearActions. */
public record GearActionPayload(int action, int arg) implements CustomPacketPayload {
    public static final Type<GearActionPayload> TYPE = new Type<>(Robotica.id("gear_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GearActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GearActionPayload::action,
            ByteBufCodecs.VAR_INT, GearActionPayload::arg,
            GearActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TYPE, CODEC, GearActionPayload::handle);
        registrar.playToClient(GearFxPayload.TYPE, GearFxPayload.CODEC, GearFxPayload::handle);
    }

    private static void handle(GearActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            com.arno.robotica.gear.tool.GearActions.apply(player, payload.action(), payload.arg());
        }
    }
}
