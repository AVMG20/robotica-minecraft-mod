package com.arno.robotica.gear.weapon;

import com.arno.robotica.core.module.ModuleTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.arno.robotica.core.CoreSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.IntSupplier;

/** Age 4 weapon. Hold to charge for 1 s, release for a 32 block beam that pierces every mob for 30 damage (stops at blocks). */
public class NullLanceItem extends EnergyWeaponItem {
    public static final int CHARGE_TICKS = 20;
    public static final double RANGE = 32.0;
    public static final float DAMAGE = 30.0F;
    public static final int COOLDOWN_TICKS = 20;

    /** True while the beam deals its damage (server thread), so module hooks can tell the beam from a club hit. */
    private static boolean firing;

    public static boolean firing() {
        return firing;
    }

    public NullLanceItem(Properties props, IntSupplier capacity, IntSupplier cost) {
        super(props, capacity, cost, 4, ModuleTarget.NULL_LANCE);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack lance = player.getItemInHand(hand);
        if (!hasCharge(lance) && !player.getAbilities().instabuild) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("gear.robotica.lance.no_energy", cost(lance)), true);
            return InteractionResultHolder.fail(lance);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(lance);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide) return;
        int charged = getUseDuration(stack, user) - remaining;
        if (charged == 1) {
            CoreSounds.play(level, user.blockPosition(), CoreSounds.LANCE_CHARGE, SoundSource.PLAYERS, 0.7F, 1.0F);
        } else if (charged == CHARGE_TICKS) {
            CoreSounds.play(level, user.blockPosition(), CoreSounds.LANCE_READY, SoundSource.PLAYERS, 0.8F, 1.0F);
        } else if (charged > 0 && charged < CHARGE_TICKS && charged % 4 == 0 && level instanceof ServerLevel server) {
            Vec3 tip = user.getEyePosition().add(user.getLookAngle().scale(1.2));
            server.sendParticles(ParticleTypes.PORTAL, tip.x, tip.y - 0.2, tip.z, 4, 0.2, 0.2, 0.2, 0.1);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return;
        if (getUseDuration(stack, user) - timeLeft < CHARGE_TICKS) return;
        if (!pay(stack, player)) return;
        fire(server, player);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }

    /** Damages every living entity on the line from the eyes up to RANGE blocks or the first solid block. */
    public static int fire(ServerLevel level, Player shooter) {
        Vec3 start = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        Vec3 end = start.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        boolean hitBlock = block.getType() != HitResult.Type.MISS;
        if (hitBlock) end = block.getLocation();
        int hits = 0;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.5),
                e -> e != shooter && e.isAlive() && !e.isSpectator());
        firing = true;
        try {
            for (LivingEntity e : candidates) {
                AABB box = e.getBoundingBox().inflate(0.3);
                if (box.contains(start) || box.clip(start, end).isPresent()) {
                    if (e.hurt(level.damageSources().playerAttack(shooter), DAMAGE)) {
                        hits++;
                        Vec3 c = e.getBoundingBox().getCenter();
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 12, 0.25, 0.35, 0.25, 0.05);
                        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 4, 0.2, 0.3, 0.2, 0.08);
                    }
                }
            }
        } finally {
            firing = false;
        }
        beam(level, start, look, start.distanceTo(end));
        if (hitBlock) {
            Vec3 p = end;
            level.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 14, 0.1, 0.1, 0.1, 0.12);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        CoreSounds.play(level, shooter.blockPosition(), CoreSounds.LANCE_FIRE, SoundSource.PLAYERS, 0.9F, 1.0F);
        return hits;
    }

    /** Bright core with a slow purple spiral around it and a burst at the muzzle. */
    private static void beam(ServerLevel level, Vec3 start, Vec3 look, double length) {
        Vec3 muzzle = start.add(0, -0.2, 0).add(look.scale(0.8));
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(0.3);
        Vec3 up = side.cross(look).normalize().scale(0.3);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.08);
        for (double d = 0.5; d <= length; d += 0.5) {
            Vec3 p = muzzle.add(look.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
            double a = d * 1.4;
            Vec3 s = p.add(side.scale(Math.cos(a))).add(up.scale(Math.sin(a)));
            level.sendParticles(ParticleTypes.WITCH, s.x, s.y, s.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (((int) (d * 2)) % 4 == 0) level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.2);
        }
    }
}
