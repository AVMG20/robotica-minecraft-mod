package com.arno.robotica.boss.entity;

import com.arno.robotica.boss.BossConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Fight rules shared by the bosses: who their attacks hit, how far away an attacker may stand, healing when left alone. */
public final class BossRules {
    private BossRules() {}

    /** Drag of a thrown projectile per tick in air (ThrowableProjectile). */
    private static final double DRAG = 0.99;

    /**
     * Who a boss's attacks hit: players (not creative or spectator), their pets, its current target and any mob fighting it.
     * Other mobs standing around (piglins, ghasts, zombies) are left alone, so they never join the fight.
     */
    public static boolean isFoe(@Nullable Mob boss, Entity e) {
        if (e == boss || !(e instanceof LivingEntity living) || !living.isAlive()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (boss == null) return false;
        if (e == boss.getTarget()) return true;
        if (e instanceof OwnableEntity pet && pet.getOwner() instanceof Player) return true;
        return e instanceof Mob mob && mob.getTarget() == boss;
    }

    /** True when the attacker behind this damage stands further away than the config allows: the hit does nothing. */
    public static boolean attackerTooFar(Entity boss, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        double max = BossConfig.maxAttackerDistance();
        return source.getEntity() instanceof LivingEntity attacker && attacker != boss && attacker.distanceToSqr(boss) > max * max;
    }

    /** A survival or adventure player within {@code range} blocks. */
    public static boolean playerNear(Entity boss, double range) {
        return boss.level().getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), range, EntitySelector.NO_CREATIVE_OR_SPECTATOR) != null;
    }

    /**
     * Heals a boss no player has been near for a while. Call every server tick with the ticks it has been alone so far;
     * returns the new count.
     */
    public static int regen(Mob boss, int aloneTicks) {
        if (boss.tickCount % 20 != 0) return aloneTicks;
        if (playerNear(boss, BossConfig.regenRadius())) return 0;
        aloneTicks += 20;
        if (aloneTicks > BossConfig.regenDelayTicks() && boss.getHealth() < boss.getMaxHealth()) {
            boss.heal(boss.getMaxHealth() * BossConfig.regenPercent() / 100.0F);
        }
        return aloneTicks;
    }

    /**
     * Start velocity of a lobbed projectile (gravity {@code gravity}, air drag) that lands on {@code to}: flight time is
     * the distance over {@code speed}, at most {@code maxTicks} so long throws arc lower and fly faster.
     */
    public static Vec3 lob(Vec3 from, Vec3 to, double speed, double gravity, int maxTicks) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double dy = to.y - from.y;
        double horizontal = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
        double ticks = Math.max(4.0, Math.min(maxTicks, horizontal / speed));
        double reach = (1.0 - Math.pow(DRAG, ticks)) / (1.0 - DRAG);
        double vh = horizontal / reach;
        double terminal = gravity / (1.0 - DRAG);
        double vy = (dy + terminal * ticks) / reach - terminal;
        return new Vec3(dx / horizontal * vh, vy, dz / horizontal * vh);
    }
}
