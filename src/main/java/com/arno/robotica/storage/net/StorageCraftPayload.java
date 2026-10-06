package com.arno.robotica.storage.net;

import com.arno.robotica.Robotica;
import com.arno.robotica.storage.menu.StorageMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client to server: fill the terminal's crafting grid for a recipe (JEI's "+" button). One list of accepted items per
 * grid slot; the server takes them from the terminal first, then from the player's inventory, and validates everything.
 */
public record StorageCraftPayload(int containerId, List<List<ItemStack>> grid, boolean max) implements CustomPacketPayload {
    public static final Type<StorageCraftPayload> TYPE = new Type<>(Robotica.id("storage_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageCraftPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageCraftPayload::containerId,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(64)).apply(ByteBufCodecs.list(9)), StorageCraftPayload::grid,
            ByteBufCodecs.BOOL, StorageCraftPayload::max,
            StorageCraftPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, StorageCraftPayload::handle);
    }

    private static void handle(StorageCraftPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof StorageMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.fillGrid(payload.grid(), payload.max());
        }
    }
}
