package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoSuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client-side touch for the local player: the Magma Core set bonus lets the wearer move through lava at about walking
 * speed (lava movement is decided by the client). Thruster sounds and other effects live in {@link ExoFx}.
 */
final class ExoClientEffects {
    private ExoClientEffects() {}

    static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        if (!ExoSuit.wearingAny(player)) return;
        lava(player);
    }

    /** Lava damps motion by half every tick; a push in the input direction gives back roughly walking speed. */
    private static void lava(LocalPlayer player) {
        if (!player.isInLava() || player.getAbilities().flying || ExoSuit.setBonus(player) != ExoData.Core.MAGMA) return;
        if (player.xxa == 0 && player.zza == 0) return;
        player.moveRelative(0.16F, new Vec3(player.xxa, 0, player.zza));
    }
}
