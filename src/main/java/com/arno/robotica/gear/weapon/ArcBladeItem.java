package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.module.GearModuleKind;
import com.arno.robotica.gear.module.GearModules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Age 3 melee weapon: 11 damage, arcs 50% of it to up to 3 other hostile mobs, each arc jumping from the last target,
 * 800 FE per hit. The Chain Lightning module adds arcs (paid per extra arc that lands) and jump range.
 */
public class ArcBladeItem extends EnergyWeaponItem {
    public static final float BASE_DAMAGE = 11.0F;
    public static final int CHAIN_TARGETS = 3;
    public static final double CHAIN_RANGE = 6.0;

    public ArcBladeItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 3);
    }

    @Override
    public boolean paidMelee() {
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!pay(stack, attacker) || !(attacker.level() instanceof ServerLevel level)) return true;
        int chain = GearModules.active(stack, GearModuleKind.CHAIN_LIGHTNING);
        int arcs = CHAIN_TARGETS + GearConfig.chainExtraArcs(chain);
        double range = CHAIN_RANGE + GearConfig.chainRangeBonus(chain);
        DamageSource source = attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
        boolean creative = attacker instanceof Player p && p.getAbilities().instabuild;
        List<LivingEntity> done = new ArrayList<>();
        done.add(target);
        LivingEntity from = target;
        int hit = 0;
        while (hit < arcs) {
            LivingEntity next = nextTarget(level, from, attacker, done, range);
            if (next == null) break;
            // Arcs beyond the blade's own 3 come from Chain Lightning and cost extra.
            if (hit >= CHAIN_TARGETS && !creative && !ItemEnergy.tryUse(stack, GearConfig.chainCostPerArc())) break;
            done.add(next);
            next.hurt(source, BASE_DAMAGE * 0.5F);
            arc(level, from.getBoundingBox().getCenter(), next.getBoundingBox().getCenter());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY(0.6), next.getZ(), 10, 0.3, 0.4, 0.3, 0.15);
            CoreSounds.play(level, next.blockPosition(), CoreSounds.ARC_STRIKE, SoundSource.PLAYERS, 0.4F, 1.1F + hit * 0.08F);
            from = next;
            hit++;
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.4, 0.3, 0.2);
        CoreSounds.play(level, target.blockPosition(), CoreSounds.ARC_STRIKE, SoundSource.PLAYERS, 0.8F, 0.95F + level.random.nextFloat() * 0.1F);
        if (hit >= CHAIN_TARGETS) level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35F, 1.6F);
        return true;
    }

    /** The nearest living hostile within {@code range} of {@code from} that has not been hit yet, or null. */
    private static LivingEntity nextTarget(ServerLevel level, LivingEntity from, LivingEntity attacker, List<LivingEntity> done, double range) {
        double r2 = range * range;
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                        e -> e != attacker && !done.contains(e) && e.isAlive() && e instanceof Enemy && e.distanceToSqr(from) <= r2)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
    }

    /** A jagged bolt: a few kinked segments of sparks with a bright core. */
    static void arc(ServerLevel level, Vec3 a, Vec3 b) {
        RandomSource random = level.random;
        Vec3 d = b.subtract(a);
        int kinks = Math.max(2, (int) (d.length() / 1.5));
        Vec3 prev = a;
        for (int k = 1; k <= kinks; k++) {
            Vec3 point = a.add(d.scale(k / (double) kinks));
            if (k < kinks) point = point.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
            Vec3 seg = point.subtract(prev);
            int steps = Math.max(2, (int) (seg.length() / 0.25));
            for (int i = 0; i <= steps; i++) {
                Vec3 p = prev.add(seg.scale(i / (double) steps));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
                if (i % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            prev = point;
        }
    }
}
