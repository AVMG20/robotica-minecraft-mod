package com.arno.robotica.boss.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Scrap Colossus, the first Robotica boss: a 3 block tall rusted copper robot woken at a Colossus Altar.
 *
 * <p>Attacks, all telegraphed: a ground slam (1 s with raised arms, then a shockwave that hurts and throws back everything on
 * the ground within 5 blocks; jump to dodge), a lobbed chunk of scrap at targets further away, and plain punches. Below
 * half health it enters phase 2 once: it calls up to three Scrap Drones and starts to overheat every 20 s. While it
 * overheats it stands still for 4 s venting steam with its furnace core open and takes double damage.
 *
 * <p>Never breaks blocks and only takes damage from attackers within {@link BossConfig#maxAttackerDistance()} blocks (no
 * trap farming or sniping from afar). Its attacks only hit players, their pets and whatever fights it. Left alone it heals
 * ({@link BossRules#regen}). Drops a Servo Core (loot table), and the killer gets the loot (see BossModule#onDrops and
 * BossLoot).
 */
public class ScrapColossus extends Monster implements RoboticaBoss {
    public static final float BASE_HEALTH = 300.0F;
    public static final float BASE_MELEE = 10.0F;
    public static final float SLAM_DAMAGE = 14.0F;
    public static final float SCRAP_DAMAGE = 8.0F;
    public static final double SLAM_RADIUS = 5.0;
    public static final int SLAM_WINDUP = 20;
    public static final int SLAM_RECOVER = 14;
    public static final int THROW_WINDUP = 16;
    public static final int THROW_RECOVER = 10;
    public static final int OVERHEAT_TICKS = 80;
    public static final int OVERHEAT_INTERVAL = 400;
    /** Ticks the target must stay out of melee reach before scrap also flies at close range (pillars, pits, walls). */
    public static final int OUT_OF_REACH_TICKS = 60;
    /** Normal scrap range; an unreachable target gets scrap out to the full follow range. */
    public static final double THROW_RANGE = 28.0;

    /** What the Colossus is doing, synced to clients for the animation. */
    public enum Action {
        IDLE, SLAM_WINDUP, SLAM, THROW_WINDUP, THROW, OVERHEAT;

        static Action of(int id) {
            return id >= 0 && id < values().length ? values()[id] : IDLE;
        }
    }

    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(ScrapColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> PHASE_TWO = SynchedEntityData.defineId(ScrapColossus.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(getDisplayName(),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10).setPlayBossMusic(false);

    /** Ticks since the current action started (both sides; the client resets it when the synced action changes). */
    private int actionTicks;
    private int slamCooldown = 60;
    private int throwCooldown = 40;
    private int overheatCooldown = OVERHEAT_INTERVAL;
    private boolean minionsCalled;
    /** Ticks the current target has been out of melee reach. Server only, not saved. */
    private int outOfReachTicks;
    private int aloneTicks;
    @Nullable
    private BlockPos altarPos;

    public ScrapColossus(EntityType<? extends ScrapColossus> type, Level level) {
        super(type, level);
        this.xpReward = 150;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, BASE_MELEE)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, (byte) 0);
        builder.define(PHASE_TWO, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SlamGoal());
        goalSelector.addGoal(2, new ThrowScrapGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new BossHurtByTargetGoal(this, ScrapDrone.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Scales health and melee damage by the server config; also runs for /summon and spawn eggs. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        applyConfigScaling();
        return result;
    }

    public void applyConfigScaling() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(BASE_HEALTH * BossConfig.healthMultiplier());
        AttributeInstance melee = getAttribute(Attributes.ATTACK_DAMAGE);
        if (melee != null) melee.setBaseValue(BASE_MELEE * BossConfig.damageMultiplier());
        setHealth(getMaxHealth());
    }

    // ------------------------------------------------------------------ state

    public Action action() {
        return Action.of(entityData.get(ACTION));
    }

    private void setAction(Action action) {
        entityData.set(ACTION, (byte) action.ordinal());
        actionTicks = 0;
    }

    public int actionTicks() {
        return actionTicks;
    }

    public boolean isPhaseTwo() {
        return entityData.get(PHASE_TWO);
    }

    public boolean isOverheating() {
        return action() == Action.OVERHEAT;
    }

    @Override
    public void setAltarPos(@Nullable BlockPos pos) {
        this.altarPos = pos;
        if (pos != null) restrictTo(pos, 20);
    }

    @Nullable
    public BlockPos altarPos() {
        return altarPos;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key)) actionTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        actionTicks++;
        if (level().isClientSide) clientEffects();
    }

    /** Client-only particles that follow the animation (the server sends the big bursts itself). */
    private void clientEffects() {
        if (isOverheating() || (isPhaseTwo() && random.nextInt(6) == 0)) {
            Vec3 chest = chestPos();
            level().addParticle(ParticleTypes.CLOUD, chest.x + (random.nextDouble() - 0.5), chest.y + 0.6, chest.z + (random.nextDouble() - 0.5),
                    (random.nextDouble() - 0.5) * 0.1, 0.15, (random.nextDouble() - 0.5) * 0.1);
        }
        if (random.nextInt(4) == 0) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            for (int side = -1; side <= 1; side += 2) {
                double sx = getX() + Mth.cos(yaw) * 0.55 * side + Mth.sin(yaw) * 0.3;
                double sz = getZ() + Mth.sin(yaw) * 0.55 * side - Mth.cos(yaw) * 0.3;
                level().addParticle(ParticleTypes.SMOKE, sx, getY() + 3.25, sz, 0.0, 0.05, 0.0);
            }
        }
        if (action() == Action.SLAM_WINDUP && actionTicks > SLAM_WINDUP / 2) {
            level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 2.0, getY() + 3.0, getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.02, 0.0);
        }
    }

    /** Front of the chest, where the furnace core sits. */
    public Vec3 chestPos() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.sin(yaw) * 0.6, getY() + 1.9, getZ() + Mth.cos(yaw) * 0.6);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (slamCooldown > 0) slamCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (!isPhaseTwo() && getHealth() <= getMaxHealth() * 0.5F) enterPhaseTwo();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && !isWithinMeleeAttackRange(target)) outOfReachTicks++;
        else outOfReachTicks = 0;
        aloneTicks = BossRules.regen(this, aloneTicks);
        if (isOverheating()) {
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (actionTicks % 3 == 0 && level() instanceof ServerLevel server) {
                Vec3 chest = chestPos();
                server.sendParticles(ParticleTypes.CLOUD, chest.x, chest.y + 0.3, chest.z, 4, 0.35, 0.3, 0.35, 0.04);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 3.1, getZ(), 2, 0.6, 0.1, 0.6, 0.02);
            }
            if (actionTicks % 20 == 0) playSound(SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.6F);
            if (actionTicks >= OVERHEAT_TICKS) endOverheat();
        } else if (isPhaseTwo() && action() == Action.IDLE && --overheatCooldown <= 0) {
            startOverheat();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    /** Phase 2 starts once, below half health: a roar, minions and the first overheat. */
    public void enterPhaseTwo() {
        if (isPhaseTwo()) return;
        entityData.set(PHASE_TWO, true);
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
        playSound(SoundEvents.ANVIL_DESTROY, 2.0F, 0.5F);
        if (!minionsCalled) {
            minionsCalled = true;
            summonMinions();
        }
        startOverheat();
    }

    public void startOverheat() {
        setAction(Action.OVERHEAT);
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
        goalSelector.disableControlFlag(Goal.Flag.LOOK);
        goalSelector.disableControlFlag(Goal.Flag.JUMP);
        getNavigation().stop();
        playSound(SoundEvents.LAVA_EXTINGUISH, 2.0F, 0.5F);
        playSound(SoundEvents.IRON_DOOR_OPEN, 2.0F, 0.5F);
    }

    private void endOverheat() {
        setAction(Action.IDLE);
        goalSelector.enableControlFlag(Goal.Flag.MOVE);
        goalSelector.enableControlFlag(Goal.Flag.LOOK);
        goalSelector.enableControlFlag(Goal.Flag.JUMP);
        overheatCooldown = OVERHEAT_INTERVAL;
        playSound(SoundEvents.IRON_DOOR_CLOSE, 2.0F, 0.5F);
    }

    /** Living Scrap Drones of this Colossus nearby. */
    public List<ScrapDrone> minions() {
        return level().getEntitiesOfClass(ScrapDrone.class, getBoundingBox().inflate(48.0), d -> d.isAlive() && getUUID().equals(d.ownerId()));
    }

    /** Calls two or three Scrap Drones, never more than the configured cap alive at once. Returns how many came. */
    public int summonMinions() {
        if (!(level() instanceof ServerLevel server)) return 0;
        int room = BossConfig.minionCap() - minions().size();
        int count = Math.min(room, 2 + random.nextInt(2));
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            ScrapDrone drone = BossRegistry.SCRAP_DRONE.get().create(server);
            if (drone == null) continue;
            double angle = (i / (double) Math.max(1, count)) * Math.PI * 2.0 + random.nextDouble();
            double x = getX() + Math.cos(angle) * 2.0;
            double z = getZ() + Math.sin(angle) * 2.0;
            drone.moveTo(x, getY() + 3.2, z, random.nextFloat() * 360.0F, 0.0F);
            drone.setOwner(this);
            drone.finalizeSpawn(server, server.getCurrentDifficultyAt(drone.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            if (getTarget() != null) drone.setTarget(getTarget());
            server.addFreshEntity(drone);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, x, getY() + 3.2, z, 8, 0.2, 0.2, 0.2, 0.02);
            spawned++;
        }
        if (spawned > 0) playSound(SoundEvents.PISTON_EXTEND, 1.5F, 1.4F);
        return spawned;
    }

    // ------------------------------------------------------------------ attacks

    /** The shockwave: hurts and throws back players and their pets on the ground within SLAM_RADIUS (jumping dodges it). */
    public int slam() {
        if (!(level() instanceof ServerLevel server)) return 0;
        BlockPos below = blockPosition().below();
        BlockState floor = server.getBlockState(below);
        if (floor.isAir()) floor = Blocks.STONE.defaultBlockState();
        for (int ring = 1; ring <= (int) SLAM_RADIUS; ring++) {
            int points = ring * 8;
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2.0 / points;
                double px = getX() + Math.cos(a) * ring;
                double pz = getZ() + Math.sin(a) * ring;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), px, getY() + 0.1, pz, 2, 0.1, 0.05, 0.1, 0.15);
                if (ring == (int) SLAM_RADIUS) server.sendParticles(ParticleTypes.CLOUD, px, getY() + 0.2, pz, 1, 0.0, 0.0, 0.0, 0.02);
            }
        }
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.4, 0.1, 0.4, 0.0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
        playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        float damage = SLAM_DAMAGE * BossConfig.damageMultiplier();
        AABB area = getBoundingBox().inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS);
        int hit = 0;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof ScrapDrone) && BossRules.isFoe(this, e))) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > SLAM_RADIUS + 1.0 || !target.onGround()) continue;
            float falloff = (float) Mth.clamp(1.0 - dist / (SLAM_RADIUS + 1.0) * 0.5, 0.5, 1.0);
            if (target.hurt(damageSources().mobAttack(this), damage * falloff)) hit++;
            double len = Math.max(0.3, dist);
            target.knockback(1.6, -dx / len, -dz / len);
            target.push(0.0, 0.45, 0.0);
            target.hurtMarked = true;
        }
        return hit;
    }

    /** Lobs a chunk of scrap at the target. Phase 2 throws two. */
    public void throwScrap(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        int shots = isPhaseTwo() ? 2 : 1;
        for (int i = 0; i < shots; i++) {
            ScrapChunk chunk = new ScrapChunk(server, this);
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            chunk.setPos(getX() + Mth.cos(yaw) * 0.9, getY() + 3.3, getZ() + Mth.sin(yaw) * 0.9);
            chunk.setDamage(SCRAP_DAMAGE * BossConfig.damageMultiplier());
            Vec3 aim = target.position().add(target.getDeltaMovement().scale(10.0));
            if (i > 0) aim = aim.add((random.nextDouble() - 0.5) * 4.0, 0.0, (random.nextDouble() - 0.5) * 4.0);
            chunk.lobAt(aim.add(0.0, target.getBbHeight() * 0.5, 0.0));
            server.addFreshEntity(chunk);
        }
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.0F, 0.6F);
        playSound(SoundEvents.WITCH_THROW, 1.5F, 0.5F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            outOfReachTicks = 0;
            target.push(0.0, 0.35, 0.0);
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.7F);
        }
        return hit;
    }

    /**
     * Only attacks hurt it: damage that no living attacker caused (suffocation, cactus, drowning, falling anvils, unowned
     * TNT or dispenser arrows, lightning, poison) does nothing, so it cannot be farmed in a trap. Hits from attackers further
     * away than the config allows do nothing either. /kill and the void still work.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source) || BossRules.attackerTooFar(this, source)) return true;
        return !(source.getEntity() instanceof LivingEntity) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * Scrap goes at targets in sight 6 to {@value #THROW_RANGE} blocks away. A target it has not been able to reach in melee
     * for {@value #OUT_OF_REACH_TICKS} ticks gets scrap from 3 blocks out to the full follow range, lobbed over cover too:
     * no safe spot on a pillar, across a pit, behind a wall or out past its leash.
     */
    public boolean canThrowAt(LivingEntity target) {
        double d = distanceToSqr(target);
        boolean stuck = outOfReachTicks >= OUT_OF_REACH_TICKS;
        double range = stuck ? Math.max(THROW_RANGE, getAttributeValue(Attributes.FOLLOW_RANGE)) : THROW_RANGE;
        if (d >= range * range || (!stuck && !hasLineOfSight(target))) return false;
        return d > 36.0 || (d > 9.0 && stuck);
    }

    /** Double damage while the core is exposed: the counterplay window. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isOverheating() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) amount *= 2.0F;
        boolean hurt = super.hurt(source, amount);
        if (hurt && isOverheating() && level() instanceof ServerLevel server) {
            Vec3 chest = chestPos();
            server.sendParticles(ParticleTypes.LAVA, chest.x, chest.y, chest.z, 3, 0.2, 0.2, 0.2, 0.0);
        }
        return hurt;
    }

    /** Everyone who fought it (had the boss bar and stayed close) gets the guide step, not only the killer. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 40, 0.8, 1.2, 0.8, 0.05);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.7F);
        AdvancementHolder step = server.getServer().getAdvancements().get(Robotica.id("guide/colossus"));
        if (step == null) return;
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (player.level() == server && player.distanceToSqr(this) < 64.0 * 64.0) player.getAdvancements().award(step, "done");
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.4F, 0.55F);
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + (random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PhaseTwo", isPhaseTwo());
        tag.putBoolean("MinionsCalled", minionsCalled);
        tag.putInt("OverheatCooldown", overheatCooldown);
        if (altarPos != null) tag.put("Altar", NbtUtils.writeBlockPos(altarPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE_TWO, tag.getBoolean("PhaseTwo"));
        if (isPhaseTwo()) bossEvent.setColor(BossEvent.BossBarColor.RED);
        minionsCalled = tag.getBoolean("MinionsCalled");
        overheatCooldown = tag.contains("OverheatCooldown") ? tag.getInt("OverheatCooldown") : OVERHEAT_INTERVAL;
        Optional<BlockPos> altar = NbtUtils.readBlockPos(tag, "Altar");
        altar.ifPresent(this::setAltarPos);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ goals

    /** Ground slam when the target is close: 1 s wind-up with raised arms, the shockwave, a short recovery. */
    class SlamGoal extends Goal {
        SlamGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return slamCooldown <= 0 && !isOverheating() && action() == Action.IDLE && target != null && target.isAlive()
                    && onGround() && distanceToSqr(target) < (SLAM_RADIUS - 1.0) * (SLAM_RADIUS - 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            Action a = action();
            return a == Action.SLAM_WINDUP || (a == Action.SLAM && actionTicks < SLAM_RECOVER);
        }

        @Override
        public void start() {
            setAction(Action.SLAM_WINDUP);
            getNavigation().stop();
            playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.5F);
            playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (action() == Action.SLAM_WINDUP && actionTicks >= SLAM_WINDUP) {
                slam();
                setAction(Action.SLAM);
            }
        }

        @Override
        public void stop() {
            if (action() == Action.SLAM_WINDUP || action() == Action.SLAM) setAction(Action.IDLE);
            slamCooldown = isPhaseTwo() ? 50 : 80;
        }
    }

    /** Throws scrap at a target that keeps its distance: the arm swings back, then lobs. */
    class ThrowScrapGoal extends Goal {
        ThrowScrapGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (throwCooldown > 0 || isOverheating() || action() != Action.IDLE || target == null || !target.isAlive()) return false;
            return canThrowAt(target);
        }

        @Override
        public boolean canContinueToUse() {
            Action a = action();
            return a == Action.THROW_WINDUP || (a == Action.THROW && actionTicks < THROW_RECOVER);
        }

        @Override
        public void start() {
            setAction(Action.THROW_WINDUP);
            getNavigation().stop();
            playSound(SoundEvents.PISTON_CONTRACT, 1.5F, 0.6F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (action() == Action.THROW_WINDUP && actionTicks >= THROW_WINDUP) {
                if (target != null && target.isAlive()) throwScrap(target);
                setAction(Action.THROW);
            }
        }

        @Override
        public void stop() {
            if (action() == Action.THROW_WINDUP || action() == Action.THROW) setAction(Action.IDLE);
            throwCooldown = isPhaseTwo() ? 45 : 70;
        }
    }
}
