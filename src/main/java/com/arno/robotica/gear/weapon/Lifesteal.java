package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.module.GearModuleKind;
import com.arno.robotica.gear.module.GearModules;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lifesteal module (Age 4 weapons only). Heals a share of the damage a paid hit or beam deals, never more than
 * {@link GearConfig#lifestealMaxPerSecond} health in any one-second window; reaching that cap starts a cooldown
 * ({@link GearConfig#lifestealCooldown}) without any healing. Every point healed costs FE from the weapon, so an empty
 * weapon heals nothing. The cooldown is a vanilla item cooldown on the Lifesteal module item, which syncs to the client
 * by itself for the HUD. Per-player windows are keyed by UUID and dropped on logout.
 */
public final class Lifesteal {
    private Lifesteal() {}

    private static final class Window {
        long start = Long.MIN_VALUE;
        float healed;
    }

    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    public static Item cooldownItem() {
        return GearItems.module(GearModuleKind.LIFESTEAL, 1).get();
    }

    /** Health healed in the player's current one-second window (tests, HUD). */
    public static float healedThisSecond(ServerPlayer player) {
        Window w = WINDOWS.get(player.getUUID());
        return w == null || player.level().getGameTime() - w.start >= 20 ? 0.0F : w.healed;
    }

    /**
     * Heals {@code player} for a hit of {@code dealt} damage on {@code target} made with {@code weapon}. Returns the
     * health healed (0 when the module is missing or off, on cooldown, at full health or the weapon is empty).
     */
    public static float onHit(ServerPlayer player, ItemStack weapon, LivingEntity target, float dealt) {
        if (dealt <= 0 || GearModules.active(weapon, GearModuleKind.LIFESTEAL) <= 0) return 0.0F;
        Item cd = cooldownItem();
        if (player.getCooldowns().isOnCooldown(cd)) return 0.0F;
        float missing = player.getMaxHealth() - player.getHealth();
        if (missing <= 0.0F || !player.isAlive()) return 0.0F;
        long now = player.level().getGameTime();
        Window w = WINDOWS.computeIfAbsent(player.getUUID(), k -> new Window());
        if (now - w.start >= 20 || now < w.start) {
            w.start = now;
            w.healed = 0.0F;
        }
        float cap = GearConfig.lifestealMaxPerSecond();
        float amount = Math.min(dealt * GearConfig.lifestealShare(), Math.min(cap - w.healed, missing));
        int costPer = GearConfig.lifestealCost();
        boolean creative = player.getAbilities().instabuild;
        if (!creative && costPer > 0) amount = Math.min(amount, ItemEnergy.get(weapon) / (float) costPer);
        if (amount < 0.01F) return 0.0F;
        if (!creative && costPer > 0) ItemEnergy.drain(weapon, (int) Math.ceil(amount * costPer));
        player.heal(amount);
        w.healed += amount;
        effects(player, target);
        if (w.healed >= cap - 0.001F) {
            player.getCooldowns().addCooldown(cd, GearConfig.lifestealCooldown());
            w.start = now;
            w.healed = 0.0F;
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        return amount;
    }

    /** A thin crimson stream from the target to the player and a soft heartbeat. */
    private static void effects(ServerPlayer player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 from = target.getBoundingBox().getCenter();
        Vec3 to = player.getBoundingBox().getCenter();
        Vec3 d = to.subtract(from);
        int steps = Math.min(24, Math.max(3, (int) (d.length() / 0.6)));
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.85F, 0.08F, 0.18F), 0.9F);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        level.sendParticles(ParticleTypes.HEART, to.x, to.y + 0.6, to.z, 1, 0.2, 0.1, 0.2, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    public static void forget(UUID player) {
        WINDOWS.remove(player);
    }

    public static void clearAll() {
        WINDOWS.clear();
    }
}
