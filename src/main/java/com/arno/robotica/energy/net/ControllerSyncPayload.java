package com.arno.robotica.energy.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.energy.menu.ControllerMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the numbers of the controller GUI the player has open (see {@link ControllerMenu}). */
public record ControllerSyncPayload(int containerId, CompoundTag data) implements CustomPacketPayload {
    public static final Type<ControllerSyncPayload> TYPE = new Type<>(Robotica.id("energy_controller_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ControllerSyncPayload::containerId,
            ByteBufCodecs.COMPOUND_TAG, ControllerSyncPayload::data,
            ControllerSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ControllerSyncPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ControllerMenu menu && menu.containerId == payload.containerId()) {
            menu.receive(payload.data());
        }
    }
}
