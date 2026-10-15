package com.arno.robotica.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Chat lines when a boss wakes or falls. With {@link BossConfig#announceToServer()} every player on the server gets one
 * line; without it only players near the altar hear about a summon and a kill is not announced.
 */
public final class BossNews {
    private BossNews() {}

    /** Players this close to the altar hear about a summon when server-wide news is off. */
    public static final double NEARBY = 64.0;

    /** Announces a summon. {@code altarLine} is the altar's own line, used without a (real) player and for the nearby-only mode. */
    public static void awakened(LivingEntity boss, @Nullable Entity summoner, Component altarLine) {
        awakened(boss, summoner, altarLine, BossConfig.announceToServer());
    }

    /** Returns the line that was sent. */
    public static Component awakened(LivingEntity boss, @Nullable Entity summoner, Component altarLine, boolean toServer) {
        if (!(boss.level() instanceof ServerLevel level)) return altarLine;
        if (!toServer) {
            Component line = altarLine.copy().withStyle(ChatFormatting.GOLD);
            for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(boss) < NEARBY * NEARBY)) near.sendSystemMessage(line);
            return line;
        }
        Component who = name(summoner);
        Component line = (who == null ? altarLine.copy()
                : Component.translatable("message.robotica.boss.awakened_by", who, boss.getType().getDescription()))
                .withStyle(ChatFormatting.GOLD);
        level.getServer().getPlayerList().broadcastSystemMessage(line, false);
        return line;
    }

    /** Announces a kill. Call once, from the boss's {@code die} after it really died. */
    public static void defeated(LivingEntity boss, DamageSource source) {
        defeated(boss, source, BossConfig.announceToServer());
    }

    /** Returns the line that was sent, or null when server-wide news is off. */
    @Nullable
    public static Component defeated(LivingEntity boss, DamageSource source, boolean toServer) {
        if (!toServer || !(boss.level() instanceof ServerLevel level)) return null;
        Component who = name(source.getEntity());
        if (who == null) who = name(boss.getKillCredit());
        Component what = boss.getType().getDescription();
        Component line = (who == null ? Component.translatable("message.robotica.boss.defeated", what)
                : Component.translatable("message.robotica.boss.defeated_by", what, who)).withStyle(ChatFormatting.GOLD);
        level.getServer().getPlayerList().broadcastSystemMessage(line, false);
        return line;
    }

    /** A real player's name; null for anything else, fake players (machines) included. */
    @Nullable
    private static Component name(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player && !(player instanceof FakePlayer) ? player.getDisplayName() : null;
    }
}
