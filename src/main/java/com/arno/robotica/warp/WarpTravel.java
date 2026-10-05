package com.arno.robotica.warp;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.item.RemoteItem;
import com.arno.robotica.warp.menu.DestinationEntry;
import com.arno.robotica.warp.menu.DestinationMenu;
import com.arno.robotica.warp.menu.PadMenu;
import com.arno.robotica.warp.pad.PadRecord;
import com.arno.robotica.warp.pad.WarpCosts;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.Teleporter;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.util.StringUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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
 * Server side logic of pad trips, remote trips, binding and gate linking. Every entry point validates everything again
 * (permissions, standing on the pad, energy, destination block, safe spot); the GUIs and payloads are only requests.
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
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PadMenu(id, inv, pad), Component.translatable("block.robotica.warp_pad")),
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
        if (!standingOn(player, pad.getBlockPos())) {
            message(player, Component.translatable("message.robotica.warp.stand_on_pad"));
            return;
        }
        List<DestinationEntry> entries = buildEntries(server, player, pad, departure);
        if (entries.isEmpty()) {
            message(player, Component.translatable("message.robotica.warp.no_destinations"));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DestinationMenu(id, inv, pad, entries),
                        Component.translatable("gui.robotica.warp.destinations")),
                buf -> DestinationMenu.writeOpenData(buf, pad, entries));
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
                    distance, cost, !cross || pad.hasRift()));
        }
        list.sort(Comparator.<DestinationEntry>comparingInt(e -> e.distance() < 0 ? Integer.MAX_VALUE : e.distance())
                .thenComparing(DestinationEntry::name, String.CASE_INSENSITIVE_ORDER));
        return list.size() > MAX_LIST ? new ArrayList<>(list.subList(0, MAX_LIST)) : list;
    }

    // ---- Pad trip ----

    /** The player clicked a destination in the list. Returns true when the trip happened. */
    public static boolean travelFromPad(ServerPlayer player, WarpPadBlockEntity pad, UUID destinationId) {
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
        if (cross && !pad.hasRift()) {
            message(player, Component.translatable("message.robotica.warp.needs_rift"));
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

        destLevel.getChunk(dest.pos());
        if (!destLevel.getBlockState(dest.pos()).is(WarpRegistry.WARP_PAD.get())) {
            pads.remove(dest.id());
            message(player, Component.translatable("message.robotica.warp.destination_gone"));
            return false;
        }
        Optional<Vec3> spot = Teleporter.prepareArrival(destLevel, dest.pos().above());
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

    // ---- Remotes ----

    public static void bindRemote(ServerPlayer player, ItemStack remote, WarpPadBlockEntity pad) {
        PadRecord rec = pad.ensureRegistered();
        if (rec == null) return;
        if (!WarpPads.canUse(player.server, rec, player)) {
            message(player, Component.translatable("message.robotica.warp.private_pad", rec.ownerName()));
            return;
        }
        remote.set(WarpComponents.BOUND_PAD.get(), new WarpComponents.BoundPad(rec.id(), rec.name()));
        CoreSounds.play(player, CoreSounds.WARP_BIND, SoundSource.PLAYERS, 0.8F, 1.0F);
        message(player, Component.translatable("message.robotica.warp.remote_bound", rec.name()));
    }

    /** Tells the player why a remote does not start charging. */
    public static void explainRemote(ServerPlayer player, ItemStack stack, RemoteItem remote) {
        if (!stack.has(WarpComponents.BOUND_PAD.get())) {
            message(player, Component.translatable("message.robotica.warp.remote_unbound"));
        } else if (RemoteItem.cooldownLeft(player, remote) > 0) {
            message(player, Component.translatable("message.robotica.warp.remote_cooldown"));
        } else {
            message(player, Component.translatable("message.robotica.warp.remote_no_energy", Fmt.energy(remote.sameDimensionCost()),
                    Fmt.energy(ItemEnergy.get(stack))));
        }
    }

    /** The 3 second charge finished: travel to the bound pad. */
    public static boolean recall(ServerPlayer player, ItemStack stack, RemoteItem remote) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        WarpComponents.BoundPad bound = stack.get(WarpComponents.BOUND_PAD.get());
        if (bound == null) {
            message(player, Component.translatable("message.robotica.warp.remote_unbound"));
            return false;
        }
        if (RemoteItem.cooldownLeft(player, remote) > 0) {
            message(player, Component.translatable("message.robotica.warp.remote_cooldown"));
            return false;
        }
        WarpPads pads = WarpPads.get(server);
        PadRecord dest = pads.validated(server, bound.id());
        if (dest == null) {
            message(player, Component.translatable("message.robotica.warp.remote_pad_gone", bound.name()));
            return false;
        }
        if (!WarpPads.canUse(server, dest, player)) {
            message(player, Component.translatable("message.robotica.warp.private_pad", dest.ownerName()));
            return false;
        }
        boolean cross = !dest.dimension().equals(level.dimension());
        if (cross && !remote.isRift()) {
            message(player, Component.translatable("message.robotica.warp.remote_needs_rift"));
            return false;
        }
        ServerLevel destLevel = server.getLevel(dest.dimension());
        if (destLevel == null) {
            message(player, Component.translatable("message.robotica.warp.destination_gone"));
            return false;
        }
        boolean free = player.getAbilities().instabuild;
        int cost = cross ? remote.crossDimensionCost() : remote.sameDimensionCost();
        if (!free && !ItemEnergy.has(stack, cost)) {
            message(player, Component.translatable("message.robotica.warp.remote_no_energy", Fmt.energy(cost), Fmt.energy(ItemEnergy.get(stack))));
            return false;
        }

        destLevel.getChunk(dest.pos());
        if (!destLevel.getBlockState(dest.pos()).is(WarpRegistry.WARP_PAD.get())) {
            pads.remove(dest.id());
            message(player, Component.translatable("message.robotica.warp.remote_pad_gone", bound.name()));
            return false;
        }
        Optional<Vec3> spot = Teleporter.prepareArrival(destLevel, dest.pos().above());
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

    // ---- Gate linking ----

    /** Furthest the player may stand from the clicked controller when linking. */
    public static final double LINK_REACH = 8.0;

    /**
     * Sneak-right-click of a Linking Card on a Portal Projector: first click stores it, second click links both. Both
     * projectors must be usable by the player (owner, op level 2 or owner's team); an unowned gate is claimed by the
     * player who links it.
     */
    public static void linkingCardUsed(ServerPlayer player, ItemStack card, GlobalPos clicked) {
        MinecraftServer server = player.server;
        ServerLevel clickedLevel = server.getLevel(clicked.dimension());
        if (clickedLevel == null || player.level() != clickedLevel
                || player.distanceToSqr(Vec3.atCenterOf(clicked.pos())) > LINK_REACH * LINK_REACH) {
            message(player, Component.translatable("message.robotica.warp.card_too_far"));
            return;
        }
        if (!(clickedLevel.getBlockEntity(clicked.pos()) instanceof PortalProjectorBlockEntity second)) {
            card.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.card_lost"));
            return;
        }
        if (!second.canUse(player)) {
            message(player, Component.translatable("message.robotica.warp.card_not_owner", second.ownerName()));
            return;
        }
        GlobalPos source = card.get(WarpComponents.LINK_SOURCE.get());
        if (source == null) {
            card.set(WarpComponents.LINK_SOURCE.get(), clicked);
            CoreSounds.play(player, CoreSounds.WARP_BIND, SoundSource.PLAYERS, 0.8F, 0.85F);
            message(player, Component.translatable("message.robotica.warp.card_stored", clicked.pos().getX(), clicked.pos().getY(), clicked.pos().getZ()));
            return;
        }
        if (source.equals(clicked)) {
            message(player, Component.translatable("message.robotica.warp.card_same"));
            return;
        }
        ServerLevel sourceLevel = server.getLevel(source.dimension());
        if (sourceLevel == null) {
            card.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.card_lost"));
            return;
        }
        sourceLevel.getChunk(source.pos());
        if (!(sourceLevel.getBlockEntity(source.pos()) instanceof PortalProjectorBlockEntity first)) {
            card.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.card_lost"));
            return;
        }
        if (!first.canUse(player)) {
            // the card may have been filled by somebody else or the gate changed hands since
            card.remove(WarpComponents.LINK_SOURCE.get());
            message(player, Component.translatable("message.robotica.warp.card_not_owner", first.ownerName()));
            return;
        }
        first.claimIfUnowned(player);
        second.claimIfUnowned(player);
        GateLinks.get(server).link(source, clicked);
        first.setLinked(clicked);
        second.setLinked(source);
        card.remove(WarpComponents.LINK_SOURCE.get());
        CoreSounds.play(player, CoreSounds.WARP_LINK, SoundSource.PLAYERS, 0.8F, 1.0F);
        message(player, Component.translatable("message.robotica.warp.card_linked"));
    }
}
