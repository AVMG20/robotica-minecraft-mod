package com.arno.robotica.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Owner UUID to current player name: online player first, then the profile cache. Null when unknown. */
public final class OwnerNames {
    private OwnerNames() {}

    @Nullable
    public static String name(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(owner);
            if (profile.isPresent() && !profile.get().getName().isEmpty()) return profile.get().getName();
        }
        return null;
    }
}
