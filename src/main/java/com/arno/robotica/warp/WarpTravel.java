package com.arno.robotica.warp;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import com.arno.robotica.warp.item.RemoteItem;
import com.arno.robotica.warp.item.RemoteTargets;
import com.arno.robotica.warp.menu.DestinationEntry;
import com.arno.robotica.warp.menu.DestinationMenu;
import com.arno.robotica.warp.menu.PadMenu;
import com.arno.robotica.warp.menu.RemoteEntry;
import com.arno.robotica.warp.menu.RemoteMenu;
import com.arno.robotica.warp.pad.PadRecord;
import com.arno.robotica.warp.pad.WarpCosts;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.Teleporter;
import com.arno.robotica.warp.teleport.WarpCharges;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side logic of pad trips, remote trips, storing pads and projector linking. Every entry point validates
 * everything again (permissions, standing on the pad, energy, destination block, safe spot); the GUIs and payloads are
 * only requests.
 */
public final class WarpTravel {
    private WarpTravel() {}

    public static final int MAX_LIST = 100;

    public static void message(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    /** True when the player's feet are on top of the pad (pad blocks are half height, the surface is at y + 0.5). */
    public static boolean standingOn(Player player, BlockPos pad) {
        return Math.abs(player.getX() - (pad.getX() + 0.5)) <= 0.8 && Math.abs(player.getZ() - (pad.getZ() + 0.5)) <= 0.8
                && player.getY() >= pad.getY() + 0.3 && player.getY() <= pad.getY() + 1.3;
    }

    // ---- Names ----

    /** Trimmed name when it is valid: 1 to 24 characters, all of them printable chat characters. */
    public static Optional<String> sanitizeName(String raw) {
        if (raw == null) return Optional.empty();
        String name = raw.strip();
        if (name.isEmpty() || name.length() > WarpPadBlockEntity.MAX_NAME) return Optional.empty();
        for (int i = 0; i < name.length(); i++) {
            if (!StringUtil.isAllowedChatCharacter(name.charAt(i))) return Optional.empty();
        }
        return Optional.of(name);
    }

    // ---- Opening the GUIs ----

    public static void openPadSettings(ServerPlayer player, WarpPadBlockEntity pad) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PadMenu(id, inv, pad), pad.getBlockState().getBlock().getName()),
                buf -> PadMenu.writeOpenData(buf, pad));
    }

    public static void openDestinations(ServerPlayer player, WarpPadBlockEntity pad) {
        MinecraftServer server = player.server;
        PadRecord departure = pad.ensureRegistered();
        if (departure == null) return;
        if (!WarpPads.canUse(server, departure, player)) {
            message(player, Component.translatable("message.robotica.warp.private_pad", departure.ownerName()));
            return;
        }
        boolean editor = pad.canEdit(player);
        if (!standingOn(player, pad.getBlockPos())) {
            // owners get the settings (name, visibility) when they click the pad from the side
            if (editor) openPadSettings(player, pad);
            else message(player, Component.translatable("message.robotica.warp.stand_on_pad"));
            return;
        }
        List<DestinationEntry> entries = buildEntries(server, player, pad, departure);
        if (entries.isEmpty() && !editor) {
            message(player, Component.translatable("message.robotica.warp.no_destinations"));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DestinationMenu(id, inv, pad, entries),
                        Component.translatable("gui.robotica.warp.destinations")),
                buf -> DestinationMenu.writeOpenData(buf, pad, entries, editor));
    }

    /** Pads the player may travel to from the departure pad, nearest first, other dimensions last. */
    public static List<DestinationEntry> buildEntries(MinecraftServer server, ServerPlayer player, WarpPadBlockEntity pad, PadRecord departure) {
        WarpPads pads = WarpPads.get(server);
        List<DestinationEntry> list = new ArrayList<>();
        for (PadRecord rec : pads.usableBy(server, player)) {
            if (rec.id().equals(departure.id())) continue;
            if (pads.validated(server, rec.id()) == null) continue;
            boolean cross = !rec.dimension().equals(departure.dimension());
            int distance = cross ? -1 : (int) Math.round(Math.sqrt(rec.pos().distSqr(departure.pos())));
            int cost = WarpCosts.padTrip(Math.max(0, distance), cross);
            list.add(new DestinationEntry(rec.id(), rec.name(), rec.ownerName().isEmpty() ? "-" : rec.ownerName(), rec.dimension(),
                    distance, cost, !cross || pad.isMk2()));
        }
        list.sort(Comparator.<DestinationEntry>comparingInt(e -> e.distance() < 0 ? Integer.MAX_VALUE : e.distance())
                .thenComparing(DestinationEntry::name, String.CASE_INSENSITIVE_ORDER));
        return list.size() > MAX_LIST ? new ArrayList<>(list.subList(0, MAX_LIST)) : list;
    }

    // ---- Pad trip ----

    /** The player clicked a destination in the list: check the trip now, then wind up for a second ({@link WarpCharges}). */
    public static boolean requestPadTrip(ServerPlayer player, WarpPadBlockEntity pad, UUID destinationId) {
        if (!padTrip(player, pad, destinationId, true)) return false;
        WarpCharges.startPad(player, pad.getBlockPos(), destinationId);
        CoreSounds.play(player, CoreSounds.WARP_START, SoundSource.PLAYERS, 0.8F, 1.2F);
        return true;
    }

    /** Pad trip right now (the end of the wind-up). Returns true when the trip happened. */
    public static boolean travelFromPad(ServerPlayer player, WarpPadBlockEntity pad, UUID destinationId) {
        return padTrip(player, pad, destinationId, false);
    }

    /** Every check, the cost and the cooldown of a pad trip. {@code dryRun} stops after the checks. */
    private static boolean padTrip(ServerPlayer player, WarpPadBlockEntity pad, UUID destinationId, boolean dryRun) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        PadRecord departure = pad.ensureRegistered();
        if (departure == null) return false;
        WarpPads pads = WarpPads.get(server);

        int wait = WarpCooldowns.get(server).padRemaining(player.getUUID(), level.getGameTime());
        if (wait > 0) {
            message(player, Component.translatable("message.robotica.warp.cooldown", (wait + 19) / 20));
            return false;
        }
        if (!WarpPads.canUse(server, departure, player)) {
            message(player, Component.translatable("message.robotica.warp.private_pad", departure.ownerName()));
            return false;
        }
        if (!standingOn(player, pad.getBlockPos())) {
            message(player, Component.translatable("message.robotica.warp.stand_on_pad"));
            return false;
        }
        PadRecord dest = pads.validated(server, destinationId);
        if (dest == null || !WarpPads.canUse(server, dest, player) || dest.id().equals(departure.id())) {
            message(player, Component.translatable("message.robotica.warp.destination_gone"));
            return false;
        }
        boolean cross = !dest.dimension().equals(level.dimension());
        if (cross && !pad.isMk2()) {
            message(player, Component.translatable("message.robotica.warp.needs_mk2"));
            return false;
        }
        ServerLevel destLevel = server.getLevel(dest.dimension());
        if (destLevel == null) {
            message(player, Component.translatable("message.robotica.warp.destination_gone"));
            return false;
        }
        int cost = WarpCosts.padTrip(cross ? 0 : Math.sqrt(dest.pos().distSqr(departure.pos())), cross);
        if (pad.energy.getEnergyStored() < cost) {
            message(player, Component.translatable("message.robotica.warp.pad_no_energy", Fmt.energy(cost), Fmt.energy(pad.energy.getEnergyStored())));
            return false;
        }
        if (dryRun) return true;

        destLevel.getChunk(dest.pos());
        if (!WarpPads.isPad(destLevel.getBlockState(dest.pos()))) {
            pads.remove(dest.id());
            message(player, Component.translatable("message.robotica.warp.destination_gone"));
            return false;
        }
        Optional<Vec3> spot = arrivalSpot(destLevel, dest.pos());
        if (spot.isEmpty()) {
            message(player, Component.translatable("message.robotica.warp.no_safe_spot"));
            return false;
        }

        pad.energy.consume(cost);
        Teleporter.departEffects(level, player.position(), false);
        if (Teleporter.teleport(player, destLevel, spot.get(), player.getYRot(), player.getXRot()) == null) {
            pad.energy.generate(cost); // refund: the move was cancelled
            message(player, Component.translatable("message.robotica.warp.cancelled"));
            return false;
        }
        Teleporter.arriveEffects(destLevel, spot.get(), false);
        WarpCooldowns.get(server).startPad(player.getUUID(), destLevel.getGameTime(), WarpConfig.padTravelCooldown());
        message(player, Component.translatable("message.robotica.warp.arrived", dest.name(), Fmt.energy(cost)));
        com.arno.robotica.core.progress.Milestones.award(player, com.arno.robotica.core.progress.Milestones.WARP);
        return true;
    }

    /** Safe spot above a destination pad. Empty when that would generate terrain (never on the main thread for a trip). */
    private static Optional<Vec3> arrivalSpot(ServerLevel destLevel, BlockPos padPos) {
        BlockPos center = padPos.above();
        if (!Teleporter.aroundGenerated(destLevel, center)) return Optional.empty();
        return Teleporter.prepareArrival(destLevel, center);
    }

    // ---- Remote ----

    /** Sneak-right-click of a Warp Remote on a pad: stores it (the first pad becomes home). */
    public static void bindRemote(ServerPlayer player, ItemStack remote, WarpPadBlockEntity pad) {
        PadRecord rec = pad.ensureRegistered();
        if (rec == null) return;
        if (!WarpPads.canUse(player.server, rec, player)) {
            message(player, Component.translatable("message.robotica.warp.private_pad", rec.ownerName()));
            return;
        }
        switch (RemoteTargets.add(remote, rec)) {
            case FULL -> {
                message(player, Component.translatable("message.robotica.warp.remote_full", WarpComponents.MAX_PADS));
                return;
            }
            case ALREADY_STORED -> {
                message(player, Component.translatable("message.robotica.warp.remote_known", rec.name()));
                return;
            }
            case ADDED -> {
                int size = RemoteTargets.list(remote).size();
                message(player, size == 1 ? Component.translatable("message.robotica.warp.remote_home", rec.name())
                        : Component.translatable("message.robotica.warp.remote_added", rec.name(), size, WarpComponents.MAX_PADS));
            }
        }
        CoreSounds.play(player, CoreSounds.WARP_BIND, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** The 3 second hold finished: travel to the home pad. */
    public static boolean goHome(ServerPlayer player, ItemStack stack) {
        WarpComponents.BoundPad home = RemoteTargets.home(stack);
        if (home == null) {
            message(player, Component.translatable("message.robotica.warp.remote_unbound"));
            return false;
        }
        return remoteTrip(player, stack, home.id(), home.name(), false);
    }

    /** Opens the pad list of the Warp Remote in {@code hand}, with current names from the registry. */
    public static void openRemote(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RemoteItem)) return;
        MinecraftServer server = player.server;
        WarpPads pads = WarpPads.get(server);
        RemoteTargets.refresh(stack, pads);
        List<WarpComponents.BoundPad> stored = RemoteTargets.list(stack);
        if (stored.isEmpty()) {
            message(player, Component.translatable("message.robotica.warp.remote_unbound"));
            return;
        }
        List<RemoteEntry> entries = new ArrayList<>();
        for (WarpComponents.BoundPad b : stored) {
            PadRecord rec = pads.validated(server, b.id());
            if (rec == null) {
                entries.add(new RemoteEntry(b.id(), b.name(), b.pos().map(GlobalPos::dimension).orElse(null),
                        b.pos().map(GlobalPos::pos).orElse(null), 0, RemoteEntry.GONE));
                continue;
            }
            boolean cross = !rec.dimension().equals(player.level().dimension());
            int cost = cross ? RemoteItem.crossDimensionCost() : RemoteItem.sameDimensionCost();
            int status = !WarpPads.canUse(server, rec, player) ? RemoteEntry.PRIVATE
                    : cross && !rec.mk2() ? RemoteEntry.NEEDS_MK2 : RemoteEntry.OK;
            entries.add(new RemoteEntry(rec.id(), rec.name(), rec.dimension(), rec.pos(), cost, status));
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RemoteMenu(id, inv, hand, entries), stack.getHoverName()),
                buf -> RemoteMenu.writeOpenData(buf, hand, entries));
    }

    /** A row of the remote list was clicked: check the trip now, then charge for 3 seconds ({@link WarpCharges}). */
    public static boolean requestRemoteTrip(ServerPlayer player, InteractionHand hand, UUID padId) {
        ItemStack stack = player.getItemInHand(hand);
        if (!remoteListTrip(player, stack, padId, true)) return false;
        WarpCharges.startRemote(player, hand, padId);
        CoreSounds.play(player, CoreSounds.WARP_START, SoundSource.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    /** Trip to one of the remote's stored pads. With {@code dryRun} it only checks (and explains) without travelling. */
    public static boolean remoteListTrip(ServerPlayer player, ItemStack stack, UUID padId, boolean dryRun) {
        if (!(stack.getItem() instanceof RemoteItem)) return false;
        WarpComponents.BoundPad bound = RemoteTargets.find(RemoteTargets.list(stack), padId);
        if (bound == null) {
            message(player, Component.translatable("message.robotica.warp.remote_unbound"));
            return false;
        }
        return remoteTrip(player, stack, bound.id(), bound.name(), dryRun);
    }

    /** Remote trip to a pad: every check, the cost and the cooldown. {@code dryRun} stops after the checks. */
    private static boolean remoteTrip(ServerPlayer player, ItemStack stack, UUID padId, String storedName, boolean dryRun) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        if (RemoteItem.cooldownLeft(player) > 0) {
            message(player, Component.translatable("message.robotica.warp.remote_cooldown"));
            return false;
        }
        WarpPads pads = WarpPads.get(server);
        PadRecord dest = pads.validated(server, padId);
        if (dest == null) {
            message(player, Component.translatable("message.robotica.warp.remote_pad_gone", storedName));
            return false;
        }
        if (!WarpPads.canUse(server, dest, player)) {
            message(player, Component.translatable("message.robotica.warp.private_pad", dest.ownerName()));
            return false;
        }
        boolean cross = !dest.dimension().equals(level.dimension());
        if (cross && !dest.mk2()) {
            message(player, Component.translatable("message.robotica.warp.remote_needs_mk2"));
            return false;
        }
        ServerLevel destLevel = server.getLevel(dest.dimension());
        if (destLevel == null) {
            message(player, Component.translatable("message.robotica.warp.destination_gone"));
            return false;
        }
        boolean free = player.getAbilities().instabuild;
        int cost = cross ? RemoteItem.crossDimensionCost() : RemoteItem.sameDimensionCost();
        if (!free && !ItemEnergy.has(stack, cost)) {
            message(player, Component.translatable("message.robotica.warp.remote_no_energy", Fmt.energy(cost), Fmt.energy(ItemEnergy.get(stack))));
            return false;
        }
        if (dryRun) return true;

        destLevel.getChunk(dest.pos());
        if (!WarpPads.isPad(destLevel.getBlockState(dest.pos()))) {
            pads.remove(dest.id());
            message(player, Component.translatable("message.robotica.warp.remote_pad_gone", dest.name()));
            return false;
        }
        Optional<Vec3> spot = arrivalSpot(destLevel, dest.pos());
        if (spot.isEmpty()) {
            message(player, Component.translatable("message.robotica.warp.no_safe_spot"));
            return false;
        }

        Teleporter.departEffects(level, player.position(), false);
        if (Teleporter.teleport(player, destLevel, spot.get(), player.getYRot(), player.getXRot()) == null) {
            message(player, Component.translatable("message.robotica.warp.cancelled"));
            return false;
        }
        if (!free) ItemEnergy.tryUse(stack, cost);
        RemoteItem.startCooldown(player);
        Teleporter.arriveEffects(destLevel, spot.get(), false);
        message(player, Component.translatable("message.robotica.warp.arrived", dest.name(), Fmt.energy(free ? 0 : cost)));
        com.arno.robotica.core.progress.Milestones.award(player, com.arno.robotica.core.progress.Milestones.WARP);
        return true;
    }

    // ---- Projector linking ----

    /** Furthest the player may stand from the clicked projector when linking. */
    public static final double LINK_REACH = 8.0;

    /**
     * Sneak-right-click of a Warp Remote on a Portal Projector: the first click stores it, a click on a second one links
     * both, a second click on the same one cancels. Both projectors must be usable by the player (owner, op level 2 or
     * owner's team); an unowned projector is claimed by the player who links it.
     */
    public static void linkProjector(ServerPlayer player, ItemStack remote, GlobalPos clicked) {
        MinecraftServer server = player.server;
        ServerLevel clickedLevel = server.getLevel(clicked.dimension());
        if (clickedLevel == null || player.level() != clickedLevel
                || player.distanceToSqr(Vec3.atCenterOf(clicked.pos())) > LINK_REACH * LINK_REACH) {
            message(player, Component.translatable("message.robotica.warp.link_too_far"));
            return;
        }
        if (!(clickedLevel.getBlockEntity(clicked.pos()) instanceof PortalProjectorBlockEntity second)) {
            remote.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.link_lost"));
            return;
        }
        if (!second.canUse(player)) {
            message(player, Component.translatable("message.robotica.warp.link_not_owner", second.ownerName()));
            return;
        }
        GlobalPos source = remote.get(WarpComponents.LINK_SOURCE.get());
        if (source == null) {
            remote.set(WarpComponents.LINK_SOURCE.get(), clicked);
            CoreSounds.play(player, CoreSounds.WARP_BIND, SoundSource.PLAYERS, 0.8F, 0.85F);
            message(player, Component.translatable("message.robotica.warp.link_stored", clicked.pos().getX(), clicked.pos().getY(), clicked.pos().getZ()));
            return;
        }
        if (source.equals(clicked)) {
            remote.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.link_cleared"));
            return;
        }
        ServerLevel sourceLevel = server.getLevel(source.dimension());
        if (sourceLevel == null) {
            remote.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.link_lost"));
            return;
        }
        sourceLevel.getChunk(source.pos());
        if (!(sourceLevel.getBlockEntity(source.pos()) instanceof PortalProjectorBlockEntity first)) {
            remote.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.link_lost"));
            return;
        }
        if (!first.canUse(player)) {
            // the remote may have been filled by somebody else or the projector changed hands since
            remote.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.link_not_owner", first.ownerName()));
            return;
        }
        first.claimIfUnowned(player);
        second.claimIfUnowned(player);
        GateLinks.get(server).link(source, clicked);
        first.setLinked(clicked);
        second.setLinked(source);
        remote.remove(WarpComponents.LINK_SOURCE.get());
        CoreSounds.play(player, CoreSounds.WARP_LINK, SoundSource.PLAYERS, 0.8F, 1.0F);
        message(player, Component.translatable("message.robotica.warp.link_done"));
    }
}
