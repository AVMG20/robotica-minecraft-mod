package com.arno.robotica.warp;

import com.arno.robotica.Robotica;
import com.arno.robotica.warp.menu.DestinationMenu;
import com.arno.robotica.warp.item.RiftTargets;
import com.arno.robotica.warp.menu.PadMenu;
import com.arno.robotica.warp.menu.RiftRemoteMenu;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;
import java.util.UUID;

/** Client to server requests of the warp GUIs. Everything is validated on the server again. */
public final class WarpPayloads {
    private WarpPayloads() {}

    /** Rename the pad of the open Pad GUI. */
    public record Rename(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(Robotica.id("warp_rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Rename::pos,
                ByteBufCodecs.stringUtf8(64), Rename::name,
                Rename::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Switch the pad of the open Pad GUI between public and private. */
    public record SetPublic(BlockPos pos, boolean value) implements CustomPacketPayload {
        public static final Type<SetPublic> TYPE = new Type<>(Robotica.id("warp_public"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPublic> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetPublic::pos,
                ByteBufCodecs.BOOL, SetPublic::value,
                SetPublic::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Travel to a destination of the open destination GUI. */
    public record Travel(UUID destination) implements CustomPacketPayload {
        public static final Type<Travel> TYPE = new Type<>(Robotica.id("warp_travel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Travel> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Travel::destination,
                Travel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Travel to a pad of the open Rift Remote list (starts the 3 second charge). */
    public record RiftTravel(UUID pad) implements CustomPacketPayload {
        public static final Type<RiftTravel> TYPE = new Type<>(Robotica.id("warp_rift_travel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RiftTravel> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, RiftTravel::pad,
                RiftTravel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Remove a pad from the open Rift Remote list. */
    public record RiftForget(UUID pad) implements CustomPacketPayload {
        public static final Type<RiftForget> TYPE = new Type<>(Robotica.id("warp_rift_forget"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RiftForget> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, RiftForget::pad,
                RiftForget::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Rename.TYPE, Rename.CODEC, WarpPayloads::onRename);
        registrar.playToServer(SetPublic.TYPE, SetPublic.CODEC, WarpPayloads::onSetPublic);
        registrar.playToServer(Travel.TYPE, Travel.CODEC, WarpPayloads::onTravel);
        registrar.playToServer(RiftTravel.TYPE, RiftTravel.CODEC, WarpPayloads::onRiftTravel);
        registrar.playToServer(RiftForget.TYPE, RiftForget.CODEC, WarpPayloads::onRiftForget);
    }

    /** The pad of the open Pad or Destination GUI at {@code pos}, when the player may edit it. */
    private static WarpPadBlockEntity editablePad(ServerPlayer player, BlockPos pos) {
        boolean open = player.containerMenu instanceof PadMenu menu && menu.pos().equals(pos) && menu.stillValid(player)
                || player.containerMenu instanceof DestinationMenu dest && dest.pos().equals(pos) && dest.stillValid(player);
        if (!open) return null;
        if (!(player.level().getBlockEntity(pos) instanceof WarpPadBlockEntity pad)) return null;
        return pad.canEdit(player) ? pad : null;
    }

    private static void onRename(Rename payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WarpPadBlockEntity pad = editablePad(player, payload.pos());
        if (pad == null) return;
        Optional<String> name = WarpTravel.sanitizeName(payload.name());
        if (name.isEmpty()) {
            WarpTravel.message(player, Component.translatable("message.robotica.warp.bad_name", WarpPadBlockEntity.MAX_NAME));
            return;
        }
        pad.rename(name.get());
        WarpTravel.message(player, Component.translatable("message.robotica.warp.renamed", name.get()));
    }

    private static void onSetPublic(SetPublic payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WarpPadBlockEntity pad = editablePad(player, payload.pos());
        if (pad == null) return;
        pad.setPublic(payload.value());
    }

    private static void onTravel(Travel payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof DestinationMenu menu) || !menu.stillValid(player)) return;
        if (!(player.level().getBlockEntity(menu.pos()) instanceof WarpPadBlockEntity pad)) return;
        if (WarpTravel.travelFromPad(player, pad, payload.destination())) player.closeContainer();
    }

    private static void onRiftTravel(RiftTravel payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof RiftRemoteMenu menu) || !menu.stillValid(player)) return;
        if (WarpTravel.requestRiftTrip(player, menu.hand(), payload.pad())) player.closeContainer();
    }

    private static void onRiftForget(RiftForget payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof RiftRemoteMenu menu) || !menu.stillValid(player)) return;
        RiftTargets.remove(player.getItemInHand(menu.hand()), payload.pad());
    }
}
