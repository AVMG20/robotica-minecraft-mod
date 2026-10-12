package com.arno.robotica.boss.entity;

import com.arno.robotica.core.particle.CoreParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Null Pylon: a hovering crystal the Null Sentinel raises in phase 2. While one stands, the Sentinel takes a quarter of
 * the damage (a cyan tether shows the link). It does not move or attack; break it with any weapon. Falls apart when its
 * Sentinel is gone.
 */
public class NullPylon extends Monster {
    public static final float HEALTH = 30.0F;

    /** Entity id of the Sentinel it shields, for the client's tether (-1: none). */
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(NullPylon.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerId;

    public NullPylon(EntityType<? extends NullPylon> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, -1);
    }

    @Override
    protected void registerGoals() {
    }

    public void setOwner(NullSentinel owner) {
        this.ownerId = owner.getUUID();
        entityData.set(OWNER, owner.getId());
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    /** The Sentinel it shields, if loaded (both sides). */
    @Nullable
    public NullSentinel owner() {
        int id = entityData.get(OWNER);
        return id >= 0 && level().getEntity(id) instanceof NullSentinel s && s.isAlive() ? s : null;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide && random.nextInt(3) == 0) {
            level().addParticle(CoreParticles.mote(CoreParticles.WARP_CYAN, 1.0F), getX() + random.nextGaussian() * 0.25, getY() + 0.4 + random.nextDouble(),
                    getZ() + random.nextGaussian() * 0.25, 0.0, 0.02, 0.0);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 40 != 0 || !(level() instanceof ServerLevel server)) return;
        if (ownerId == null || !(server.getEntity(ownerId) instanceof NullSentinel boss && boss.isAlive())) {
            shatter();
            return;
        }
        if (entityData.get(OWNER) != boss.getId()) entityData.set(OWNER, boss.getId());
    }

    /** Breaks without loot: its Sentinel fell or is gone. */
    public void shatter() {
        if (level() instanceof ServerLevel server) burst(server);
        discard();
    }

    private void burst(ServerLevel server) {
        Vec3 c = getBoundingBox().getCenter();
        server.sendParticles(CoreParticles.shard(0x3FAFA0, 1.0F), c.x, c.y, c.z, 8, 0.2, 0.3, 0.2, 0.25);
        server.sendParticles(CoreParticles.spark(CoreParticles.WARP_CYAN, 1.0F), c.x, c.y, c.z, 8, 0.2, 0.3, 0.2, 0.2);
        playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0F, 0.7F);
        playSound(SoundEvents.GLASS_BREAK, 1.5F, 0.6F);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) burst(server);
    }

    /** Same rules as its Sentinel: only living attackers within reach break it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source) || BossRules.attackerTooFar(this, source)) return true;
        if (source.getEntity() instanceof NullSentinel) return true;
        return !(source.getEntity() instanceof LivingEntity) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
