package com.arno.robotica.boss.entity;

import com.arno.robotica.boss.BossConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Scrap Drone: a small flying minion the Scrap Colossus calls in phase 2. Weak, fast, pecks at players. Falls apart after
 * 90 seconds or when its Colossus is gone, so the arena never fills up.
 */
public class ScrapDrone extends Monster {
    public static final float BASE_DAMAGE = 3.0F;
    private static final int LIFETIME = 20 * 90;

    @Nullable
    private UUID ownerId;

    public ScrapDrone(EntityType<? extends ScrapDrone> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 3;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ScrapColossus.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(BASE_DAMAGE * BossConfig.damageMultiplier());
        return result;
    }

    public void setOwner(ScrapColossus owner) {
        this.ownerId = owner.getUUID();
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boolean orphan = ownerId != null && tickCount % 40 == 0 && level() instanceof ServerLevel server
                && !(server.getEntity(ownerId) instanceof ScrapColossus boss && boss.isAlive());
        if (tickCount > LIFETIME || orphan) fallApart();
    }

    private void fallApart() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            playSound(SoundEvents.CHAIN_BREAK, 1.0F, 1.2F);
        }
        discard();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEEHIVE_WORK;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHAIN_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + (random.nextFloat() - 0.5F) * 0.2F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putInt("Age", tickCount);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        tickCount = tag.getInt("Age");
    }
}
