package com.arno.robotica.boss.entity;

import com.arno.robotica.boss.BossRegistry;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A glob of magma lobbed by the Forge Tyrant. Flies in a slow, readable arc and splashes where it lands: hurts and burns
 * players and their pets within {@value #SPLASH} blocks. Never breaks blocks or lights fires. Rendered as a small spinning magma block.
 */
public class MagmaGlob extends ThrowableProjectile {
    public static final BlockState LOOK = Blocks.MAGMA_BLOCK.defaultBlockState();
    public static final double SPLASH = 1.6;
    private static final double GRAVITY = 0.05;
    /** Longest flight in ticks: far lobs fly faster instead of higher, so they fit under a Nether cave roof. */
    private static final int MAX_FLIGHT = 40;

    private float damage = 9.0F;

    public MagmaGlob(EntityType<? extends MagmaGlob> type, Level level) {
        super(type, level);
    }

    public MagmaGlob(Level level, LivingEntity owner) {
        super(BossRegistry.MAGMA_GLOB.get(), owner, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** Ballistic lob that lands on the aim point; {@code speed} sets the flight time (blocks per tick). */
    public void lobAt(Vec3 aim, double speed) {
        setDeltaMovement(BossRules.lob(position(), aim, speed, GRAVITY, MAX_FLIGHT));
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
            level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 0.0, 0.01, 0.0);
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0.0, 0.02, 0.0);
            if (tickCount % 4 == 0) level().addParticle(ParticleTypes.DRIPPING_LAVA, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
        if (!level().isClientSide && tickCount > 200) discard();
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof ForgeTyrant) && !(target instanceof MagmaGlob)
                && BossRules.isFoe(ownerMob(), target);
    }

    @Nullable
    private Mob ownerMob() {
        return getOwner() instanceof Mob mob ? mob : null;
    }

    /** Splash on any hit, block or entity: everything close takes the damage once. */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        Vec3 at = result.getLocation();
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 10, 0.4, 0.2, 0.4, 0.0);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 20, 0.5, 0.3, 0.5, 0.04);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 4, 0.3, 0.2, 0.3, 0.02);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.MAGMA_CUBE_SQUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 1.2F);
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        AABB area = new AABB(at, at).inflate(SPLASH, SPLASH, SPLASH);
        Mob boss = ownerMob();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof ForgeTyrant) && BossRules.isFoe(boss, e))) {
            if (e.getBoundingBox().distanceToSqr(at) > SPLASH * SPLASH) continue;
            if (e.hurt(damageSources().mobProjectile(this, owner), damage)) e.igniteForSeconds(3.0F);
        }
        discard();
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
