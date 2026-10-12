package com.arno.robotica.replicator.logic;

import com.arno.robotica.Robotica;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The death sound of each mob type the Replicator harvests, read once from the mob it rolls the loot with
 * ({@code getDeathSound} is protected, so through reflection) and kept per type.
 */
public final class DeathSounds {
    private DeathSounds() {}

    private static final Map<EntityType<?>, Optional<SoundEvent>> CACHE = new ConcurrentHashMap<>();
    @Nullable
    private static Method getDeathSound;
    private static boolean looked;

    /** The death sound of {@code mob}'s type, or null when it has none. */
    @Nullable
    public static SoundEvent of(LivingEntity mob) {
        return CACHE.computeIfAbsent(mob.getType(), t -> Optional.ofNullable(read(mob))).orElse(null);
    }

    @Nullable
    private static SoundEvent read(LivingEntity mob) {
        try {
            if (!looked) {
                looked = true;
                getDeathSound = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "getDeathSound");
            }
            return getDeathSound == null ? null : (SoundEvent) getDeathSound.invoke(mob);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Robotica.LOGGER.debug("Replicator could not read the death sound of {}", mob.getType(), e);
            return null;
        }
    }
}
