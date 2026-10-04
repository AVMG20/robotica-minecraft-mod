package com.arno.robotica.gear.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.arno.robotica.core.CoreSounds;
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

    public NullLanceItem(Properties props, int capacity, IntSupplier cost) {
        super(props, capacity, cost, 4);
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
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("gear.robotica.lance.no_energy", cost()), true);
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
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        int hits = 0;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.5),
                e -> e != shooter && e.isAlive() && !e.isSpectator());
        for (LivingEntity e : candidates) {
            AABB box = e.getBoundingBox().inflate(0.3);
            if (box.contains(start) || box.clip(start, end).isPresent()) {
                if (e.hurt(level.damageSources().playerAttack(shooter), DAMAGE)) hits++;
            }
        }
        double length = start.distanceTo(end);
        Vec3 muzzle = start.add(0, -0.2, 0);
        for (double d = 1.0; d <= length; d += 0.5) {
            Vec3 p = muzzle.add(look.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (((int) (d * 2)) % 4 == 0) level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.2);
        }
        CoreSounds.play(level, shooter.blockPosition(), CoreSounds.LANCE_FIRE, SoundSource.PLAYERS, 0.9F, 1.0F);
        return hits;
    }
}
