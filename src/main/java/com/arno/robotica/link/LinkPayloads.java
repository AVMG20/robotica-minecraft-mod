package com.arno.robotica.link;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.NetworkVersion;
import com.arno.robotica.link.block.LinkPriority;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.block.TransferMode;
import com.arno.robotica.link.menu.LinkMenu;
import com.arno.robotica.link.menu.NetworkEntry;
import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.LinkNetworks;
import com.arno.robotica.link.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Requests of the Quantum Link screen (client to server) and the network list (server to client). Every request is
 * only taken for the Link whose screen the player has open, in reach, that the player may open; network rights are
 * checked again here.
 */
public final class LinkPayloads {
    private LinkPayloads() {}

    public static final int SET_ENERGY = 0, SET_ITEMS = 1, SET_PRIORITY = 2, SET_WHITELIST = 3;

    /** Join a network (empty: leave it). */
    public record Select(BlockPos pos, Optional<UUID> network) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(Robotica.id("link_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Select::pos,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Select::network,
                Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Make a network and join it. */
    public record Create(BlockPos pos, String name, int color, int visibility) implements CustomPacketPayload {
        public static final Type<Create> TYPE = new Type<>(Robotica.id("link_create"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Create> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Create::pos,
                ByteBufCodecs.stringUtf8(64), Create::name,
                ByteBufCodecs.VAR_INT, Create::color,
                ByteBufCodecs.VAR_INT, Create::visibility,
                Create::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Rename, recolour or change who sees a network (owner only). */
    public record Edit(BlockPos pos, UUID network, String name, int color, int visibility) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(Robotica.id("link_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Edit::pos,
                UUIDUtil.STREAM_CODEC, Edit::network,
                ByteBufCodecs.stringUtf8(64), Edit::name,
                ByteBufCodecs.VAR_INT, Edit::color,
                ByteBufCodecs.VAR_INT, Edit::visibility,
                Edit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Delete a network (owner only). */
    public record Delete(BlockPos pos, UUID network) implements CustomPacketPayload {
        public static final Type<Delete> TYPE = new Type<>(Robotica.id("link_delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Delete::pos,
                UUIDUtil.STREAM_CODEC, Delete::network,
                Delete::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One switch of the Link: energy mode, item mode, priority or whitelist ({@code setting} is one of SET_*). */
    public record Config(BlockPos pos, int setting, int value) implements CustomPacketPayload {
        public static final Type<Config> TYPE = new Type<>(Robotica.id("link_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Config> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Config::pos,
                ByteBufCodecs.VAR_INT, Config::setting,
                ByteBufCodecs.VAR_INT, Config::value,
                Config::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the networks the player may join and the one the open Link is on. */
    public record Networks(int containerId, Optional<UUID> current, List<NetworkEntry> entries) implements CustomPacketPayload {
        public static final Type<Networks> TYPE = new Type<>(Robotica.id("link_networks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Networks> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Networks::containerId,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Networks::current,
                NetworkEntry.STREAM_CODEC.apply(ByteBufCodecs.list(LinkMenu.MAX_ENTRIES)), Networks::entries,
                Networks::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Networks payload, IPayloadContext context) {
            if (context.player().containerMenu instanceof LinkMenu menu && menu.containerId == payload.containerId()) {
                menu.setNetworks(payload.current().orElse(null), payload.entries());
            }
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NetworkVersion.get());
        registrar.playToServer(Select.TYPE, Select.CODEC, LinkPayloads::onSelect);
        registrar.playToServer(Create.TYPE, Create.CODEC, LinkPayloads::onCreate);
        registrar.playToServer(Edit.TYPE, Edit.CODEC, LinkPayloads::onEdit);
        registrar.playToServer(Delete.TYPE, Delete.CODEC, LinkPayloads::onDelete);
        registrar.playToServer(Config.TYPE, Config.CODEC, LinkPayloads::onConfig);
        registrar.playToClient(Networks.TYPE, Networks.CODEC, Networks::handle);
    }

    // ---------------------------------------------------------------- server

    /**
     * The Link of the screen the player has open at {@code pos}, when it is still in reach and the player may open and
     * change it (every caller changes something, so adventure mode and spectators get null, like the menu's edits).
     */
    @Nullable
    private static QuantumLinkBlockEntity openLink(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || !player.mayBuild()) return null;
        if (!(player.containerMenu instanceof LinkMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) return null;
        if (!(player.level().getBlockEntity(pos) instanceof QuantumLinkBlockEntity be) || !be.canOpen(player)) return null;
        return be;
    }

    private static void message(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    /** Trimmed, control characters removed, 1 to {@link LinkNetwork#MAX_NAME} characters; empty when unusable. */
    public static Optional<String> sanitizeName(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127 && c != '§') out.append(c);
        }
        String name = out.toString().trim();
        if (name.isEmpty() || name.length() > LinkNetwork.MAX_NAME) return Optional.empty();
        return Optional.of(name);
    }

    private static void onSelect(Select payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        QuantumLinkBlockEntity be = openLink(player, payload.pos());
        if (be == null) return;
        if (payload.network().isEmpty()) {
            be.joinNetwork(null);
            sendList(player);
            return;
        }
        LinkNetwork net = LinkNetworks.get(player.server).get(payload.network().get());
        if (net == null) {
            message(player, Component.translatable("message.robotica.link.gone"));
        } else if (!join(player.server, be, net, player)) {
            message(player, Component.translatable("message.robotica.link.refused", net.name()));
        }
        sendList(player);
    }

    /**
     * Puts the Link on the network when both the player and the Link's owner may use it. Returns false (and changes
     * nothing) otherwise: a private network refuses everyone but its owner.
     */
    public static boolean join(MinecraftServer server, QuantumLinkBlockEntity be, LinkNetwork net, Player player) {
        if (!LinkNetworks.canUse(server, net, player) || !LinkNetworks.canUse(server, net, be.owner(), be.ownerName())) return false;
        be.joinNetwork(net);
        return true;
    }

    private static void onCreate(Create payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        QuantumLinkBlockEntity be = openLink(player, payload.pos());
        if (be == null) return;
        Optional<String> name = sanitizeName(payload.name());
        if (name.isEmpty()) {
            message(player, Component.translatable("message.robotica.link.bad_name", LinkNetwork.MAX_NAME));
            return;
        }
        LinkNetworks data = LinkNetworks.get(player.server);
        int max = LinkConfig.maxNetworksPerPlayer();
        if (data.ownedBy(player.getUUID()) >= max) {
            message(player, Component.translatable("message.robotica.link.too_many", max));
            return;
        }
        LinkNetwork net = data.create(player.getUUID(), player.getGameProfile().getName(), name.get(),
                Math.floorMod(payload.color(), LinkNetwork.COLORS), Visibility.byId(payload.visibility()));
        be.joinNetwork(net);
        message(player, Component.translatable("message.robotica.link.created", net.name()));
        broadcastLists(player.server);
    }

    private static void onEdit(Edit payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (openLink(player, payload.pos()) == null) return;
        LinkNetworks data = LinkNetworks.get(player.server);
        LinkNetwork net = data.get(payload.network());
        if (net == null || !LinkNetworks.canManage(net, player)) {
            message(player, Component.translatable("message.robotica.link.not_owner"));
            return;
        }
        Optional<String> name = sanitizeName(payload.name());
        if (name.isEmpty()) {
            message(player, Component.translatable("message.robotica.link.bad_name", LinkNetwork.MAX_NAME));
            return;
        }
        data.edit(net, name.get(), payload.color(), Visibility.byId(payload.visibility()));
        broadcastLists(player.server);
    }

    private static void onDelete(Delete payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (openLink(player, payload.pos()) == null) return;
        LinkNetworks data = LinkNetworks.get(player.server);
        LinkNetwork net = data.get(payload.network());
        if (net == null || !LinkNetworks.canManage(net, player)) {
            message(player, Component.translatable("message.robotica.link.not_owner"));
            return;
        }
        // what was still in the buffer goes to the owner, nothing is lost
        for (ItemStack stack : data.delete(net)) player.getInventory().placeItemBackInInventory(stack);
        message(player, Component.translatable("message.robotica.link.deleted", net.name()));
        broadcastLists(player.server);
    }

    private static void onConfig(Config payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        QuantumLinkBlockEntity be = openLink(player, payload.pos());
        if (be == null) return;
        switch (payload.setting()) {
            case SET_ENERGY -> be.setEnergyMode(TransferMode.byId(payload.value()));
            case SET_ITEMS -> be.setItemMode(TransferMode.byId(payload.value()));
            case SET_PRIORITY -> be.setPriority(LinkPriority.byId(payload.value()));
            case SET_WHITELIST -> be.setWhitelist(payload.value() != 0);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- lists

    /** Sends the open Link screen of this player a fresh network list. */
    public static void sendList(ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkMenu menu) || menu.link() == null) return;
        List<NetworkEntry> entries = LinkMenu.cap(LinkNetworks.get(player.server).entriesFor(player.server, player));
        PacketDistributor.sendToPlayer(player, new Networks(menu.containerId, Optional.ofNullable(menu.link().networkId()), entries));
    }

    /** Every player with a Link screen open gets a fresh list (a network was made, changed or deleted). */
    public static void broadcastLists(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendList(player);
    }
}
