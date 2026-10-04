package com.arno.robotica.gear.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import com.arno.robotica.core.CoreSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/** Age 3 melee weapon: 11 damage, arcs 50% of it to up to 3 other hostile mobs near the target, 800 FE per hit. */
public class ArcBladeItem extends EnergyWeaponItem {
    public static final float BASE_DAMAGE = 11.0F;
    public static final int CHAIN_TARGETS = 3;
    public static final double CHAIN_RANGE = 6.0;

    public ArcBladeItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 3);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!pay(stack, attacker) || !(attacker.level() instanceof ServerLevel level)) return true;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(CHAIN_RANGE),
                e -> e != target && e != attacker && e.isAlive() && e instanceof Enemy && e.distanceToSqr(target) <= CHAIN_RANGE * CHAIN_RANGE);
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));
        DamageSource source = attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
        Vec3 from = target.getBoundingBox().getCenter();
        int hit = 0;
        for (LivingEntity next : nearby) {
            if (hit++ >= CHAIN_TARGETS) break;
            next.hurt(source, BASE_DAMAGE * 0.5F);
            arc(level, from, next.getBoundingBox().getCenter());
            from = next.getBoundingBox().getCenter();
        }
        CoreSounds.play(level, target.blockPosition(), CoreSounds.ARC_STRIKE, SoundSource.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    private static void arc(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int steps = Math.max(2, (int) (d.length() / 0.35));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.add(d.scale(i / (double) steps));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.0);
        }
    }
}
