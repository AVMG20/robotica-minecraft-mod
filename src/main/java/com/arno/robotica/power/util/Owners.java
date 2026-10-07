package com.arno.robotica.power.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Owner and team checks for power blocks (server side). */
public final class Owners {
    private Owners() {}

    /**
     * True for the owner and for players on a team allied with the owner's (vanilla scoreboard teams). A block without
     * an owner (placed by commands) counts everyone as allied. {@code ownerName} is the fallback while the owner is offline.
     */
    public static boolean allied(@Nullable Level level, @Nullable UUID owner, String ownerName, Player player) {
        if (owner == null || owner.equals(player.getUUID())) return true;
        Team mine = player.getTeam();
        if (mine == null || !(level instanceof ServerLevel serverLevel)) return false;
        String name = ownerName;
        MinecraftServer server = serverLevel.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) name = online.getGameProfile().getName();
        if (name == null || name.isEmpty()) return false;
        Team theirs = serverLevel.getScoreboard().getPlayersTeam(name);
        return theirs != null && mine.isAlliedTo(theirs);
    }
}
