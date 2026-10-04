package com.arno.robotica.gear.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;

/** Age 1 melee weapon: 7 damage, Slowness II for 2 s, 250 FE per hit. */
public class ShockBatonItem extends EnergyWeaponItem {
    public ShockBatonItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 1);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (pay(stack, attacker)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), attacker);
            if (attacker.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
                level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3F, 2.0F);
            }
        }
        return true;
    }
}
