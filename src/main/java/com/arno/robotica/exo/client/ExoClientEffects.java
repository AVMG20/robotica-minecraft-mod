package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoData;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.ExoSuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client-side touches for the local player: the thruster loop while flying with the Flight module, and the Magma Core
 * set bonus that lets the wearer move through lava at about walking speed (lava movement is decided by the client).
 */
final class ExoClientEffects {
    private ExoClientEffects() {}

    private static FlightLoop loop;

    static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        if (!ExoSuit.wearingAny(player)) return;
        lava(player);
        flightSound(player);
    }

    /** Lava damps motion by half every tick; a push in the input direction gives back roughly walking speed. */
    private static void lava(LocalPlayer player) {
        if (!player.isInLava() || player.getAbilities().flying || ExoSuit.setBonus(player) != ExoData.Core.MAGMA) return;
        if (player.xxa == 0 && player.zza == 0) return;
        player.moveRelative(0.16F, new Vec3(player.xxa, 0, player.zza));
    }

    private static void flightSound(LocalPlayer player) {
        boolean flying = player.getAbilities().flying && !player.isCreative() && !player.isSpectator()
                && ExoSuit.isActive(player, ModuleKind.FLIGHT) && ExoClientConfig.flightSound();
        if (flying && (loop == null || loop.isStopped())) {
            loop = new FlightLoop(player);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
    }

    /** A soft jet hum that follows the player and fades out when they stop flying. */
    private static final class FlightLoop extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        private int fade = 0;

        FlightLoop(LocalPlayer player) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F;
            this.pitch = 1.3F;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            boolean on = !player.isRemoved() && player.getAbilities().flying && ExoSuit.isActive(player, ModuleKind.FLIGHT);
            fade = on ? Math.min(10, fade + 1) : fade - 1;
            if (fade <= 0 && !on) {
                stop();
                return;
            }
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            double speed = player.getDeltaMovement().length();
            this.volume = (0.12F + (float) Math.min(0.35, speed * 0.6)) * fade / 10.0F;
            this.pitch = 1.1F + (float) Math.min(0.5, speed * 0.8);
        }
    }
}
