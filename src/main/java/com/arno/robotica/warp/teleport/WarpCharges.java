package com.arno.robotica.warp.teleport;

import com.arno.robotica.warp.WarpFxPayload;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side wind-up of a trip picked in a GUI, keyed by player UUID: a pad trip (1 second, the player stays on the
 * pad) or a remote trip from the pad list (3 seconds, the remote stays in hand). Damage cancels it (WarpEvents).
 * Cleared on logout and server stop. The held charge of "go home" is the vanilla item use instead.
 */
public final class WarpCharges {
    private WarpCharges() {}

    public static final int PAD_TICKS = 20;

    private static final class Charge {
        final UUID destination;
        @Nullable
        final InteractionHand hand;
        @Nullable
        final BlockPos pad;
        final int total;
        int elapsed;

        Charge(UUID destination, @Nullable InteractionHand hand, @Nullable BlockPos pad, int total) {
            this.destination = destination;
            this.hand = hand;
            this.pad = pad;
            this.total = total;
        }
    }

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final List<ServerPlayer> DONE_PLAYERS = new ArrayList<>();
    private static final List<Charge> DONE_CHARGES = new ArrayList<>();

    /** Remote list trip: 3 seconds with the remote in {@code hand}. */
    public static void startRemote(ServerPlayer player, InteractionHand hand, UUID pad) {
        CHARGES.put(player.getUUID(), new Charge(pad, hand, null, com.arno.robotica.warp.item.RemoteItem.CHARGE_TICKS));
    }

    /** Pad trip: 1 second standing on the pad at {@code pad}. */
    public static void startPad(ServerPlayer player, BlockPos pad, UUID destination) {
        CHARGES.put(player.getUUID(), new Charge(destination, null, pad.immutable(), PAD_TICKS));
    }

    public static boolean isCharging(UUID player) {
        return CHARGES.containsKey(player);
    }

    /** Returns true when a charge was running. */
    public static boolean cancel(UUID player) {
        return CHARGES.remove(player) != null;
    }

    public static void clear() {
        CHARGES.clear();
    }

    public static void tick(MinecraftServer server) {
        if (CHARGES.isEmpty()) return;
        // collect finished trips first: travelling can fire events that touch the map
        Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Charge charge = e.getValue();
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            if (!stillValid(player, charge)) {
                it.remove();
                WarpTravel.message(player, Component.translatable("message.robotica.warp.interrupted"));
                continue;
            }
            WarpFxPayload.charge(player.serverLevel(), player, charge.elapsed, charge.total);
            if (++charge.elapsed >= charge.total) {
                it.remove();
                DONE_PLAYERS.add(player);
                DONE_CHARGES.add(charge);
            }
        }
        try {
            for (int i = 0; i < DONE_PLAYERS.size(); i++) {
                ServerPlayer player = DONE_PLAYERS.get(i);
                Charge charge = DONE_CHARGES.get(i);
                if (charge.pad != null) {
                    if (player.level().getBlockEntity(charge.pad) instanceof WarpPadBlockEntity pad) WarpTravel.travelFromPad(player, pad, charge.destination);
                } else if (charge.hand != null) {
                    WarpTravel.remoteListTrip(player, player.getItemInHand(charge.hand), charge.destination, false);
                }
            }
        } finally {
            DONE_PLAYERS.clear();
            DONE_CHARGES.clear();
        }
    }

    private static boolean stillValid(ServerPlayer player, Charge charge) {
        if (charge.pad != null) {
            return player.level().getBlockEntity(charge.pad) instanceof WarpPadBlockEntity && WarpTravel.standingOn(player, charge.pad);
        }
        ItemStack stack = charge.hand == null ? ItemStack.EMPTY : player.getItemInHand(charge.hand);
        return stack.is(WarpRegistry.WARP_REMOTE.get());
    }
}
