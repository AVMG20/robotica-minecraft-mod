package com.arno.robotica.warp.teleport;

import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.item.RemoteItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side 3 second charge of a Rift Remote trip picked in its GUI, keyed by player UUID. It runs like the held
 * charge of the Recall Remote: damage cancels it, so does putting the remote away. Cleared on logout and server stop.
 */
public final class RiftCharges {
    private RiftCharges() {}

    private static final class Charge {
        final UUID pad;
        final InteractionHand hand;
        int elapsed;

        Charge(UUID pad, InteractionHand hand) {
            this.pad = pad;
            this.hand = hand;
        }
    }

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    public static void start(ServerPlayer player, InteractionHand hand, UUID pad) {
        CHARGES.put(player.getUUID(), new Charge(pad, hand));
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
        Map<ServerPlayer, Charge> done = new HashMap<>();
        Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Charge charge = e.getValue();
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            ItemStack stack = player.getItemInHand(charge.hand);
            if (!stack.is(WarpRegistry.RIFT_REMOTE.get())) {
                it.remove();
                WarpTravel.message(player, Component.translatable("message.robotica.warp.interrupted"));
                continue;
            }
            RemoteItem.chargeEffects(player.serverLevel(), player, charge.elapsed, true);
            if (++charge.elapsed >= RemoteItem.CHARGE_TICKS) {
                it.remove();
                done.put(player, charge);
            }
        }
        for (Map.Entry<ServerPlayer, Charge> e : done.entrySet()) {
            ServerPlayer player = e.getKey();
            ItemStack stack = player.getItemInHand(e.getValue().hand);
            if (stack.getItem() instanceof RemoteItem remote) WarpTravel.riftTravel(player, stack, remote, e.getValue().pad, false);
        }
    }
}
