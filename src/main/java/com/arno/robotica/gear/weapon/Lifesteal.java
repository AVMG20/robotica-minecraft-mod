package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
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
 * Lifesteal module (Age 4 weapons only). Heals a share of the damage a paid hit or beam deals out of a budget of
 * {@link GearConfig#lifestealMaxPerSecond} health that refills over {@link GearConfig#lifestealCooldown} ticks, and never
 * more than that cap in any rolling second. An empty budget starts the cooldown (a vanilla item cooldown on the Lifesteal
 * module item, which syncs to the client by itself for the HUD) while it refills. Over time that is at most cap per
 * cooldown (3 health per 5 s, 0.6 health per second). Every point healed costs FE from the weapon, so an empty weapon
 * heals nothing. Per-player budgets are keyed by UUID and dropped on logout.
 */
public final class Lifesteal {
    private Lifesteal() {}

    /** One player's healing budget: a token bucket plus the healing of the last 20 ticks, one slot per tick. */
    public static final class Budget {
        public static final int SECOND = 20;
        private float tokens = -1.0F;
        private long last;
        private final float[] healed = new float[SECOND];
        private final long[] tick = new long[SECOND];

        private void refill(long now, float cap, int refillTicks) {
            if (tokens < 0.0F || now < last) {
                tokens = cap;
                java.util.Arrays.fill(healed, 0.0F);
                java.util.Arrays.fill(tick, Long.MIN_VALUE);
            } else {
                tokens = Math.min(cap, tokens + (now - last) * cap / Math.max(SECOND, refillTicks));
            }
            last = now;
        }

        /** Health healed in the 20 ticks up to {@code now}. */
        public float lastSecond(long now) {
            float sum = 0.0F;
            for (int i = 0; i < SECOND; i++) if (tick[i] <= now && tick[i] > now - SECOND) sum += healed[i];
            return sum;
        }

        /** How much may be healed at {@code now}. */
        public float room(long now, float cap, int refillTicks) {
            refill(now, cap, refillTicks);
            return Math.max(0.0F, Math.min(tokens, cap - lastSecond(now)));
        }

        /** Books {@code amount} healed at {@code now} (after {@link #room}); true when the budget is now empty. */
        public boolean spend(long now, float amount) {
            tokens = Math.max(0.0F, tokens - amount);
            int i = (int) Math.floorMod(now, (long) SECOND);
            if (tick[i] != now) {
                tick[i] = now;
                healed[i] = 0.0F;
            }
            healed[i] += amount;
            return tokens < 0.01F;
        }
    }

    private static final Map<UUID, Budget> BUDGETS = new HashMap<>();

    public static Item cooldownItem() {
        return ModuleItems.get(ModuleKind.LIFESTEAL, 1).get();
    }

    /** Health healed in the player's last second (tests, HUD). */
    public static float healedThisSecond(ServerPlayer player) {
        Budget b = BUDGETS.get(player.getUUID());
        return b == null ? 0.0F : b.lastSecond(player.level().getGameTime());
    }

    /**
     * Heals {@code player} for a hit of {@code dealt} damage on {@code target} made with {@code weapon}. Returns the
     * health healed (0 when the module is missing or off, on cooldown, at full health or the weapon is empty).
     */
    public static float onHit(ServerPlayer player, ItemStack weapon, LivingEntity target, float dealt) {
        if (dealt <= 0 || Modules.active(weapon, ModuleKind.LIFESTEAL) <= 0) return 0.0F;
        Item cd = cooldownItem();
        if (player.getCooldowns().isOnCooldown(cd)) return 0.0F;
        float missing = player.getMaxHealth() - player.getHealth();
        if (missing <= 0.0F || !player.isAlive()) return 0.0F;
        long now = player.level().getGameTime();
        Budget budget = BUDGETS.computeIfAbsent(player.getUUID(), k -> new Budget());
        float cap = GearConfig.lifestealMaxPerSecond();
        int cooldown = GearConfig.lifestealCooldown();
        float amount = Math.min(dealt * GearConfig.lifestealShare(), Math.min(budget.room(now, cap, cooldown), missing));
        int costPer = Modules.regulated(weapon, GearConfig.lifestealCost());
        boolean creative = player.getAbilities().instabuild;
        if (!creative && costPer > 0) amount = Math.min(amount, ItemEnergy.get(weapon) / (float) costPer);
        if (amount < 0.01F) return 0.0F;
        if (!creative && costPer > 0) ItemEnergy.drain(weapon, (int) Math.ceil(amount * costPer));
        player.heal(amount);
        effects(player, target);
        if (budget.spend(now, amount) && cooldown > 0) {
            player.getCooldowns().addCooldown(cd, cooldown);
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
        BUDGETS.remove(player);
    }

    public static void clearAll() {
        BUDGETS.clear();
    }
}
