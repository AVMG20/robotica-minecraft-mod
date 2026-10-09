package com.arno.robotica.codex.client;

import com.arno.robotica.codex.CodexClientConfig;
import com.arno.robotica.codex.GuideProgressPayload;
import com.arno.robotica.core.CoreSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Codex sounds and the guide unlock effect, all local to this client: the book opening, a page turn per action
 * (slightly random pitch, at most one every 70 ms however fast the wheel spins), and when a guide step is finished a
 * chime with a ring of sparkles rising round the player. Nothing here is heard or seen by other players.
 */
public final class CodexSounds {
    private CodexSounds() {}

    private static long lastPage;
    private static long lastChime = Long.MIN_VALUE / 2;

    static void open() {
        if (!CodexClientConfig.pageSounds()) return;
        ui(CoreSounds.CODEX_OPEN.get(), 0.95F + random().nextFloat() * 0.1F, 0.7F);
        lastPage = Util.getMillis();
    }

    static void page() {
        if (!CodexClientConfig.pageSounds()) return;
        long now = Util.getMillis();
        if (now - lastPage < 70) return;
        lastPage = now;
        ui(CoreSounds.CODEX_PAGE.get(), 0.9F + random().nextFloat() * 0.25F, 0.6F);
    }

    private static void ui(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static RandomSource random() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.random : RandomSource.create();
    }

    static void onClientTick(ClientTickEvent.Post event) {
        if (!GuideProgressPayload.takeEarned()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !CodexClientConfig.unlockEffects()) return;
        long now = mc.level.getGameTime();
        if (now - lastChime < 10 && now >= lastChime) return;
        lastChime = now;
        RandomSource random = mc.level.random;
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), CoreSounds.CODEX_UNLOCK.get(), SoundSource.PLAYERS,
                0.8F, 0.95F + random.nextFloat() * 0.1F, false);
        // a ring of soft motes rising round the chest, a little wider than the player so first person sees some
        int count = 14;
        double y = player.getY() + player.getBbHeight() * 0.55;
        for (int i = 0; i < count; i++) {
            float a = i * Mth.TWO_PI / count + random.nextFloat() * 0.3F;
            double r = 0.75 + random.nextDouble() * 0.15;
            mc.level.addParticle(ParticleTypes.END_ROD, player.getX() + Mth.cos(a) * r, y + random.nextDouble() * 0.3, player.getZ() + Mth.sin(a) * r,
                    -Mth.cos(a) * 0.012, 0.04 + random.nextDouble() * 0.03, -Mth.sin(a) * 0.012);
        }
        for (int i = 0; i < 4; i++) {
            mc.level.addParticle(ParticleTypes.WAX_ON, player.getX() + (random.nextDouble() - 0.5) * 1.2, y + 0.4 + random.nextDouble() * 0.6,
                    player.getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
        }
    }
}
