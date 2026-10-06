package com.arno.robotica.storage.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.storage.menu.StorageMenu;
import com.arno.robotica.storage.menu.StorageView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: what the terminal GUI shows (scroll row, sort mode, search text). The server validates everything
 * and only changes the view of the sender's own open terminal; items are never touched by this packet.
 */
public record StorageViewPayload(int containerId, int scrollRow, int sort, String filter) implements CustomPacketPayload {
    public static final Type<StorageViewPayload> TYPE = new Type<>(Robotica.id("storage_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageViewPayload::containerId,
            ByteBufCodecs.VAR_INT, StorageViewPayload::scrollRow,
            ByteBufCodecs.VAR_INT, StorageViewPayload::sort,
            ByteBufCodecs.stringUtf8(StorageView.MAX_FILTER), StorageViewPayload::filter,
            StorageViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, StorageViewPayload::handle);
    }

    private static void handle(StorageViewPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof StorageMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.setView(payload.scrollRow(), StorageView.Sort.byOrdinal(payload.sort()), payload.filter());
        }
    }
}
