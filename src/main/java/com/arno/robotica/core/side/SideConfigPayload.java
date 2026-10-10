package com.arno.robotica.core.side;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: a click in a machine's side config tab. Only applied to the menu the player has open (same
 * container id), when that menu is still valid (block there, player in reach), has a side config and the player may
 * change it ({@link MachineMenu#mayConfigure}).
 */
public record SideConfigPayload(int containerId, int action) implements CustomPacketPayload {
    public static final Type<SideConfigPayload> TYPE = new Type<>(Robotica.id("core_side_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SideConfigPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SideConfigPayload::containerId,
            ByteBufCodecs.VAR_INT, SideConfigPayload::action,
            SideConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, SideConfigPayload::handle);
    }

    private static void handle(SideConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return;
        if (!(player.containerMenu instanceof MachineMenu menu) || menu.containerId != payload.containerId()) return;
        SideConfig sides = menu.sides();
        if (sides == null || !sides.isServer() || !menu.stillValid(player) || !menu.mayConfigure(player)) return;
        sides.handleAction(payload.action());
    }
}
