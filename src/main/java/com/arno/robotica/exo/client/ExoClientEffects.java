package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.ExoTicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client-side touch for the local player: the Magma Core set bonus lets the wearer move through lava at about walking
 * speed (lava movement is decided by the client), and Step Assist drops the moment the wearer sneaks so they stop at
 * edges without waiting for the server. Thruster sounds and other effects live in {@link ExoFx}.
 */
final class ExoClientEffects {
    private ExoClientEffects() {}

    static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        if (!ExoSuit.wearingAny(player)) return;
        lava(player);
        sneakStep(player);
    }

    /** The server removes the Step Assist bonus while sneaking too; this only spares the round trip. */
    private static void sneakStep(LocalPlayer player) {
        if (!player.isShiftKeyDown()) return;
        AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && step.getModifier(ExoTicker.STEP_ID) != null) step.removeModifier(ExoTicker.STEP_ID);
    }

    /** Lava damps motion by half every tick; a push in the input direction gives back roughly walking speed. */
    private static void lava(LocalPlayer player) {
        if (!player.isInLava() || player.getAbilities().flying || ExoSuit.setBonus(player) != ExoData.Core.MAGMA) return;
        if (player.xxa == 0 && player.zza == 0) return;
        player.moveRelative(0.16F, new Vec3(player.xxa, 0, player.zza));
    }
}
