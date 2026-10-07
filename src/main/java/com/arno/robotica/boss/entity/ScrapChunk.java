package com.arno.robotica.boss.entity;

import com.arno.robotica.boss.BossRegistry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A tumbling chunk of rusted copper thrown by the Scrap Colossus. Flies in a slow, readable arc, hurts what it hits and
 * breaks apart. Never breaks blocks. Rendered as a small spinning weathered copper block.
 */
public class ScrapChunk extends ThrowableProjectile {
    public static final BlockState LOOK = Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
    private static final double GRAVITY = 0.05;
    private static final double SPEED = 1.0;
    /** Longest flight in ticks: far throws fly faster instead of higher. */
    private static final int MAX_FLIGHT = 40;

    private float damage = ScrapColossus.SCRAP_DAMAGE;

    public ScrapChunk(EntityType<? extends ScrapChunk> type, Level level) {
        super(type, level);
    }

    public ScrapChunk(Level level, LivingEntity owner) {
        super(BossRegistry.SCRAP_CHUNK.get(), owner, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** Ballistic lob that lands on the aim point. */
    public void lobAt(Vec3 aim) {
        setDeltaMovement(BossRules.lob(position(), aim, SPEED, GRAVITY, MAX_FLIGHT));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isAlive()) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0.0, 0.01, 0.0);
            if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.2, getZ(), 0.0, 0.0, 0.0);
        }
        if (!level().isClientSide && tickCount > 200) discard();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof ScrapColossus) && !(target instanceof ScrapDrone)
                && BossRules.isFoe(getOwner() instanceof Mob mob ? mob : null, target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        if (target.hurt(damageSources().mobProjectile(this, owner), damage) && target instanceof LivingEntity living) {
            Vec3 push = getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize();
            living.knockback(0.8, -push.x, -push.z);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, LOOK), getX(), getY() + 0.2, getZ(), 24, 0.3, 0.3, 0.3, 0.15);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 1.4F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.COPPER_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
            discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Damage")) damage = tag.getFloat("Damage");
    }
}
