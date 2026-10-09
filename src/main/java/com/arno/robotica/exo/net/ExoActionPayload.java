package com.arno.robotica.exo.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.exo.ExoActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: an Exo-Frame key press (open the module screen, toggle flight, double jump, dash, sonar, overclock). Validated in {@link ExoActions}. */
public record ExoActionPayload(int action) implements CustomPacketPayload {
    public static final Type<ExoActionPayload> TYPE = new Type<>(Robotica.id("exo_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExoActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ExoActionPayload::action, ExoActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TYPE, CODEC, ExoActionPayload::handle);
        registrar.playToClient(ExoSonarPayload.TYPE, ExoSonarPayload.CODEC, ExoSonarPayload::handle);
        registrar.playToClient(ExoFxPayload.TYPE, ExoFxPayload.CODEC, ExoFxPayload::handle);
    }

    private static void handle(ExoActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) ExoActions.apply(player, payload.action());
    }
}
