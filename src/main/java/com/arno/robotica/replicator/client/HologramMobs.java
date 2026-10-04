package com.arno.robotica.replicator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One client side entity instance per mob type, used only to draw the hologram. These entities are never added to the
 * level, never tick and are dropped when the player leaves the world.
 */
final class HologramMobs {
    private HologramMobs() {}

    private static final Map<EntityType<?>, Entity> CACHE = new HashMap<>();
    private static final Set<EntityType<?>> FAILED = new HashSet<>();

    @Nullable
    static Entity get(EntityType<?> type) {
        Entity cached = CACHE.get(type);
        if (cached != null) return cached;
        if (FAILED.contains(type)) return null;
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            Entity entity = type.create(level);
            if (entity != null) {
                CACHE.put(type, entity);
                return entity;
            }
        } catch (RuntimeException ignored) {
            // A modded mob that can not be built on the client just gets no hologram.
        }
        FAILED.add(type);
        return null;
    }

    static void clear() {
        CACHE.clear();
        FAILED.clear();
    }
}
