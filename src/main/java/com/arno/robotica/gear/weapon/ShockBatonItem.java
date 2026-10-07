package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.module.ModuleTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import com.arno.robotica.core.CoreSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;

/** Age 1 melee weapon: 7 damage, Slowness II for 2 s, 250 FE per hit. */
public class ShockBatonItem extends EnergyWeaponItem {
    public ShockBatonItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 1, ModuleTarget.SHOCK_BATON);
    }

    @Override
    public boolean paidMelee() {
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (pay(stack, attacker)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), attacker);
            if (attacker.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
                CoreSounds.play(level, target.blockPosition(), CoreSounds.SHOCK_ZAP, SoundSource.PLAYERS, 0.7F, 1.0F);
            }
        }
        return true;
    }
}
