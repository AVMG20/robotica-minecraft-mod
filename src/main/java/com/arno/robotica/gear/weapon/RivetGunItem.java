package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.entity.RivetEntity;
import com.arno.robotica.gear.module.GearModuleKind;
import com.arno.robotica.gear.module.GearModules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntSupplier;

/**
 * Age 2 ranged weapon. Right-click fires a glowing steel rivet ({@link RivetEntity}) for {@link GearConfig#rivetDamage}
 * damage, 4 shots per second, no ammo. Muzzle flash, a punchy layered shot sound. Armor Pierce and Ricochet Rivets ride
 * along on the rivet.
 */
public class RivetGunItem extends EnergyWeaponItem {
    public static final int COOLDOWN_TICKS = 5;

    public RivetGunItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!hasCharge(gun) && !player.getAbilities().instabuild) {
            if (!level.isClientSide) {
                CoreSounds.play(level, player.blockPosition(), CoreSounds.RIVET_EMPTY, SoundSource.PLAYERS, 0.6F, 1.0F);
                player.getCooldowns().addCooldown(this, 8);
            }
            return InteractionResultHolder.fail(gun);
        }
        if (level instanceof ServerLevel server) {
            pay(gun, player);
            fire(server, player, gun);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
    }

    /** Spawns one rivet from the player's eyes along the look direction, with the gun's modules. Returns it. */
    public static RivetEntity fire(ServerLevel level, Player player, ItemStack gun) {
        float pierce = (float) GearConfig.pierceShare(GearModules.active(gun, GearModuleKind.ARMOR_PIERCE));
        int bounces = GearConfig.ricochetBounces(GearModules.active(gun, GearModuleKind.RICOCHET));
        RivetEntity rivet = new RivetEntity(level, player, GearConfig.rivetDamage(), pierce, bounces);
        rivet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, GearConfig.rivetSpeed(), 0.5F);
        level.addFreshEntity(rivet);
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(0.9)).add(0, -0.15, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.04, 0.04, 0.04, 0.02);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 5, 0.05, 0.05, 0.05, 0.12);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);
        float jitter = 0.92F + level.random.nextFloat() * 0.16F;
        CoreSounds.play(level, player.blockPosition(), CoreSounds.RIVET_SHOT, SoundSource.PLAYERS, 0.8F, jitter);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.35F, 1.7F * jitter);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.4F, 1.5F * jitter);
        return rivet;
    }
}
