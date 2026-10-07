package com.arno.robotica.boss.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Forge Tyrant, the Age 3 Robotica boss: a walking blast furnace of blackstone and gold, woken at a Forge Altar in the
 * Nether. Drops the Magma Core.
 *
 * <p>Three telegraphed attacks, one every few seconds: flame breath (the furnace doors glow, then a 7 block cone of fire
 * in a fixed direction for 2 s; step aside), a magma mortar (the crucible arm swings back, then lobs globs of magma at
 * the target), and eruptions (the hammer goes up and slams down; glowing rings mark spots under and around the target,
 * which erupt 1.5 s later). Plain hammer punches in between. After every few big attacks it has to vent: it stands still
 * with its furnace doors open and takes extra damage. Below half health it marks a ring of eruptions around itself and
 * attacks faster.
 *
 * <p>Fairness: only damage with a living attacker hurts it (no traps, lava or suffocation), it never breaks blocks or sets
 * fires, walks on lava, and a target it cannot reach in melee gets mortar and eruptions. Numbers: {@link BossConfig}.
 */
public class ForgeTyrant extends Monster implements RoboticaBoss {
    public static final int BREATH_WINDUP = 20;
    public static final int BREATH_TICKS = 40;
    public static final int BREATH_PULSE = 10;
    public static final double BREATH_RANGE = 7.0;
    /** Cosine of the half angle of the flame cone (35 degrees). */
    private static final double BREATH_COS = Math.cos(Math.toRadians(35.0));
    public static final int MORTAR_WINDUP = 16;
    public static final int MORTAR_RECOVER = 12;
    public static final int ERUPT_WINDUP = 18;
    public static final int ERUPT_RECOVER = 14;
    /** Ticks a marked spot glows before it erupts. */
    public static final int ERUPT_DELAY = 30;
    public static final double ERUPT_RADIUS = 1.5;
    public static final double ERUPT_RANGE = 24.0;
    public static final double MORTAR_RANGE = 28.0;
    /** Ticks the target must stay out of melee reach before mortar and eruptions also go at close range. */
    public static final int OUT_OF_REACH_TICKS = 60;

    /** What the Tyrant is doing, synced to clients for the animation. */
    public enum Action {
        IDLE, BREATH_WINDUP, BREATH, MORTAR_WINDUP, MORTAR, ERUPT_WINDUP, ERUPT, VENT;

        static Action of(int id) {
            return id >= 0 && id < values().length ? values()[id] : IDLE;
        }
    }

    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(ForgeTyrant.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> PHASE_TWO = SynchedEntityData.defineId(ForgeTyrant.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10).setPlayBossMusic(false);

    /** A marked spot that erupts at {@code at} (game time). Server only, not saved. */
    private record Spot(Vec3 pos, long at) {}

    private final List<Spot> spots = new ArrayList<>();
    private int actionTicks;
    private int attackCooldown = 40;
    private int attacksSinceVent;
    private int outOfReachTicks;
    private Action lastAttack = Action.IDLE;
    private Vec3 breathDir = Vec3.ZERO;
    @Nullable
    private BlockPos altarPos;

    public ForgeTyrant(EntityType<? extends ForgeTyrant> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
        setPathfindingMalus(PathType.LAVA, 8.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
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
        goalSelector.addGoal(1, new AttackGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Health, armor and punch from the server config; also runs for /summon. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        applyConfig();
        return result;
    }

    public void applyConfig() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(BossConfig.tyrantHealth());
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(BossConfig.tyrantArmor());
        AttributeInstance melee = getAttribute(Attributes.ATTACK_DAMAGE);
        if (melee != null) melee.setBaseValue(BossConfig.tyrantMelee());
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

    public boolean isVenting() {
        return action() == Action.VENT;
    }

    /** Ticks until the next big attack (tests use it to hold the attacks). */
    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
    }

    public int pendingEruptions() {
        return spots.size();
    }

    @Override
    public void setAltarPos(@Nullable BlockPos pos) {
        this.altarPos = pos;
        if (pos != null) restrictTo(pos, 20);
    }

    @Nullable
    @Override
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

    /** Forward unit vector of the body. */
    private Vec3 forward() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    /** The furnace doors on the front of the body: where the flames come out. */
    public Vec3 mouthPos() {
        Vec3 f = forward();
        return new Vec3(getX() + f.x * 0.75, getY() + 1.5, getZ() + f.z * 0.75);
    }

    private Vec3 chimneyPos() {
        Vec3 f = forward();
        return new Vec3(getX() - f.x * 0.3, getY() + 2.85, getZ() - f.z * 0.3);
    }

    /** Client-only particles that follow the animation (the server sends the attack bursts itself). */
    private void clientEffects() {
        Vec3 top = chimneyPos();
        if (random.nextInt(isVenting() ? 1 : 3) == 0) {
            level().addParticle(isVenting() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, top.x + (random.nextDouble() - 0.5) * 0.3, top.y,
                    top.z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.08, 0.0);
        }
        if (random.nextInt(isPhaseTwo() ? 4 : 10) == 0) level().addParticle(ParticleTypes.FLAME, top.x, top.y, top.z, 0.0, 0.04, 0.0);
        Vec3 mouth = mouthPos();
        Action a = action();
        if (a == Action.BREATH_WINDUP && actionTicks > 4) {
            level().addParticle(ParticleTypes.SMALL_FLAME, mouth.x + (random.nextDouble() - 0.5) * 0.6, mouth.y + (random.nextDouble() - 0.5) * 0.5,
                    mouth.z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.02, 0.0);
        }
        if (a == Action.VENT && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.LAVA, mouth.x, mouth.y, mouth.z, 0.0, 0.0, 0.0);
        }
        if (isPhaseTwo() && random.nextInt(8) == 0) {
            level().addParticle(ParticleTypes.FALLING_LAVA, getX() + (random.nextDouble() - 0.5) * 2.0, getY() + 1.0 + random.nextDouble(),
                    getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (attackCooldown > 0) attackCooldown--;
        if (!isPhaseTwo() && getHealth() <= getMaxHealth() * 0.5F) enterPhaseTwo();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && !isWithinMeleeAttackRange(target)) outOfReachTicks++;
        else outOfReachTicks = 0;
        tickSpots();
        if (isVenting()) {
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (actionTicks % 4 == 0 && level() instanceof ServerLevel server) {
                Vec3 top = chimneyPos();
                Vec3 mouth = mouthPos();
                server.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 3, 0.2, 0.1, 0.2, 0.03);
                server.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 2, 0.3, 0.3, 0.3, 0.03);
            }
            if (actionTicks % 20 == 0) playSound(SoundEvents.LAVA_EXTINGUISH, 1.5F, 0.6F);
            if (actionTicks >= BossConfig.tyrantVentTicks()) endVent();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    /** Phase 2 starts once, below half health: a roar, a ring of eruptions around itself, faster attacks. */
    public void enterPhaseTwo() {
        if (isPhaseTwo()) return;
        entityData.set(PHASE_TWO, true);
        bossEvent.setColor(BossEvent.BossBarColor.WHITE);
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
        playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.4F);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            mark(new Vec3(getX() + Math.cos(a) * 4.0, getY(), getZ() + Math.sin(a) * 4.0));
        }
    }

    public void startVent() {
        spots.clear();
        setAction(Action.VENT);
        attacksSinceVent = 0;
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
        goalSelector.disableControlFlag(Goal.Flag.LOOK);
        goalSelector.disableControlFlag(Goal.Flag.JUMP);
        getNavigation().stop();
        playSound(SoundEvents.IRON_DOOR_OPEN, 2.0F, 0.5F);
        playSound(SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.5F);
    }

    private void endVent() {
        setAction(Action.IDLE);
        goalSelector.enableControlFlag(Goal.Flag.MOVE);
        goalSelector.enableControlFlag(Goal.Flag.LOOK);
        goalSelector.enableControlFlag(Goal.Flag.JUMP);
        attackCooldown = BossConfig.tyrantCooldown(isPhaseTwo());
        playSound(SoundEvents.IRON_DOOR_CLOSE, 2.0F, 0.5F);
    }

    // ------------------------------------------------------------------ attacks

    /** Which big attacks fit this target right now. Mortar and eruptions also go at a close target it cannot reach. */
    public List<Action> attackOptions(LivingEntity target) {
        List<Action> options = new ArrayList<>();
        double d = distanceTo(target);
        boolean sight = hasLineOfSight(target);
        boolean stuck = outOfReachTicks >= OUT_OF_REACH_TICKS;
        if (sight && d <= BREATH_RANGE - 1.0 && Math.abs(target.getY() - getY()) < 3.0) options.add(Action.BREATH_WINDUP);
        if (sight && d <= MORTAR_RANGE && (d > 8.0 || stuck)) options.add(Action.MORTAR_WINDUP);
        if (d <= ERUPT_RANGE && (d > 4.0 || stuck)) options.add(Action.ERUPT_WINDUP);
        return options;
    }

    @Nullable
    private Action pickAttack(LivingEntity target) {
        List<Action> options = attackOptions(target);
        if (options.size() > 1) options.remove(lastAttack);
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    /** Locks the flame direction at the target's current spot: the cone does not follow anyone. */
    private void startBreath(@Nullable LivingEntity target) {
        Vec3 mouth = mouthPos();
        Vec3 aim = target != null ? target.getBoundingBox().getCenter().subtract(mouth) : forward();
        if (aim.lengthSqr() < 1.0E-4) aim = forward();
        breathDir = aim.normalize();
        float yaw = (float) (Mth.atan2(breathDir.z, breathDir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setAction(Action.BREATH);
        playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
    }

    /** One tick of flame breath: particles every tick, damage every {@value #BREATH_PULSE} ticks. Returns how many it hit. */
    public int breathTick(int tick) {
        if (!(level() instanceof ServerLevel server)) return 0;
        setYRot(yBodyRot);
        yHeadRot = yBodyRot;
        Vec3 mouth = mouthPos();
        for (int i = 0; i < 8; i++) {
            Vec3 v = breathDir.add(random.nextGaussian() * 0.12, random.nextGaussian() * 0.08, random.nextGaussian() * 0.12);
            server.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 0, v.x, v.y, v.z, 0.45 + random.nextDouble() * 0.3);
        }
        if (tick % 3 == 0) server.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, 0, breathDir.x, breathDir.y + 0.1, breathDir.z, 0.3);
        if (tick % BREATH_PULSE != 0) return 0;
        playSound(SoundEvents.BLAZE_SHOOT, 1.5F, 0.6F + random.nextFloat() * 0.2F);
        float damage = BossConfig.tyrantBreath();
        int hit = 0;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(mouth, mouth).inflate(BREATH_RANGE + 1.0), this::canHurt)) {
            Vec3 centre = e.getBoundingBox().getCenter();
            Vec3 to = centre.subtract(mouth);
            double dist = to.length();
            if (dist > BREATH_RANGE + e.getBbWidth() * 0.5) continue;
            if (dist > 0.75 && to.scale(1.0 / dist).dot(breathDir) < BREATH_COS) continue;
            if (server.clip(new ClipContext(mouth, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.BLOCK) continue;
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                e.igniteForSeconds(3.0F);
                hit++;
            }
        }
        return hit;
    }

    private boolean canHurt(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Lobs globs of magma at and around the target: three, five in phase 2. */
    public void fireMortar(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        int shots = isPhaseTwo() ? 5 : 3;
        Vec3 f = forward();
        Vec3 aimBase = target.position().add(target.getDeltaMovement().scale(10.0));
        for (int i = 0; i < shots; i++) {
            MagmaGlob glob = new MagmaGlob(server, this);
            glob.setPos(getX() - f.z * 0.9 + f.x * 0.3, getY() + 2.6, getZ() + f.x * 0.9 + f.z * 0.3);
            glob.setDamage(BossConfig.tyrantMortar());
            Vec3 aim = aimBase;
            if (i > 0) aim = aim.add((random.nextDouble() - 0.5) * 6.0, 0.0, (random.nextDouble() - 0.5) * 6.0);
            glob.lobAt(aim.add(0.0, 0.2, 0.0), 0.75 + i * 0.08);
            server.addFreshEntity(glob);
        }
        playSound(SoundEvents.GHAST_SHOOT, 2.0F, 0.5F);
        playSound(SoundEvents.BUCKET_EMPTY_LAVA, 1.5F, 0.6F);
    }

    /** Marks the eruption spots: under the target and around it (two more, four in phase 2). Returns how many. */
    public int erupt(LivingEntity target) {
        if (!(level() instanceof ServerLevel)) return 0;
        Vec3 base = ground(target.position());
        mark(base);
        int extra = isPhaseTwo() ? 4 : 2;
        double offset = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < extra; i++) {
            double a = offset + i * Math.PI * 2.0 / extra;
            double r = 2.5 + random.nextDouble() * 1.5;
            mark(ground(base.add(Math.cos(a) * r, 0.0, Math.sin(a) * r)));
        }
        playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        return 1 + extra;
    }

    /** Drops a point onto the floor below it (at most 4 blocks), so a jump does not lift the mark into the air. */
    private Vec3 ground(Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        for (int i = 0; i < 4 && level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty(); i++) p = p.below();
        return new Vec3(pos.x, Math.min(pos.y, p.getY()), pos.z);
    }

    private void mark(Vec3 pos) {
        spots.add(new Spot(pos, level().getGameTime() + ERUPT_DELAY));
        level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    /** Glowing rings over marked spots, then the eruption. */
    private void tickSpots() {
        if (spots.isEmpty() || !(level() instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        List<Spot> due = new ArrayList<>();
        for (Spot s : spots) {
            if (now >= s.at()) {
                due.add(s);
                continue;
            }
            if (now % 2 != 0) continue;
            int points = 10;
            double spin = now * 0.15;
            for (int i = 0; i < points; i++) {
                double a = spin + i * Math.PI * 2.0 / points;
                server.sendParticles(ParticleTypes.FLAME, s.pos().x + Math.cos(a) * ERUPT_RADIUS, s.pos().y + 0.1,
                        s.pos().z + Math.sin(a) * ERUPT_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (s.at() - now < 12) server.sendParticles(ParticleTypes.LAVA, s.pos().x, s.pos().y + 0.1, s.pos().z, 2, 0.4, 0.0, 0.4, 0.0);
        }
        spots.removeAll(due);
        for (Spot s : due) eruptAt(server, s.pos());
    }

    /** A column of fire at one spot: hurts, burns and throws up everything standing in it. Returns how many it hit. */
    private int eruptAt(ServerLevel server, Vec3 pos) {
        server.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.2, pos.z, 40, 0.35, 1.2, 0.35, 0.04);
        server.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3, pos.z, 12, 0.4, 0.2, 0.4, 0.0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2.0, pos.z, 8, 0.3, 0.6, 0.3, 0.02);
        server.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5F, 0.6F);
        server.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
        AABB box = new AABB(pos.x - ERUPT_RADIUS, pos.y - 0.5, pos.z - ERUPT_RADIUS, pos.x + ERUPT_RADIUS, pos.y + 3.0, pos.z + ERUPT_RADIUS);
        float damage = BossConfig.tyrantEruption();
        int hit = 0;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, this::canHurt)) {
            double dx = e.getX() - pos.x;
            double dz = e.getZ() - pos.z;
            if (Math.sqrt(dx * dx + dz * dz) > ERUPT_RADIUS + e.getBbWidth() * 0.5) continue;
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                e.igniteForSeconds(4.0F);
                e.push(0.0, 0.8, 0.0);
                e.hurtMarked = true;
                hit++;
            }
        }
        return hit;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            outOfReachTicks = 0;
            target.igniteForSeconds(3.0F);
            target.push(0.0, 0.3, 0.0);
            playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
        }
        return hit;
    }

    /**
     * Only attacks hurt it: damage without a living attacker (lava, fire, magma floors, suffocation, cactus, unowned TNT or
     * dispenser arrows, lightning, poison) does nothing. /kill and the void still work.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) return true;
        return !(source.getEntity() instanceof LivingEntity) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Extra damage while it vents: the counterplay window. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isVenting() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) amount *= BossConfig.tyrantVentMultiplier();
        boolean hurt = super.hurt(source, amount);
        if (hurt && isVenting() && level() instanceof ServerLevel server) {
            Vec3 mouth = mouthPos();
            server.sendParticles(ParticleTypes.LAVA, mouth.x, mouth.y, mouth.z, 4, 0.3, 0.3, 0.3, 0.0);
        }
        return hurt;
    }

    /** Walks over lava like a strider, so a lava pit is no trap. */
    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    /** Everyone who fought it (had the boss bar and stayed close) gets the guide step, not only the killer. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        spots.clear();
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.5, getZ(), 30, 0.8, 1.0, 0.8, 0.0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 40, 0.8, 1.2, 0.8, 0.05);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.6F);
        AdvancementHolder step = server.getServer().getAdvancements().get(Robotica.id("guide/tyrant"));
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
        return SoundEvents.NETHERITE_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.NETHERITE_BLOCK_STEP, 1.5F, 0.5F);
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + (random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 100;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PhaseTwo", isPhaseTwo());
        tag.putInt("AttacksSinceVent", attacksSinceVent);
        if (altarPos != null) tag.put("Altar", NbtUtils.writeBlockPos(altarPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE_TWO, tag.getBoolean("PhaseTwo"));
        if (isPhaseTwo()) bossEvent.setColor(BossEvent.BossBarColor.WHITE);
        attacksSinceVent = tag.getInt("AttacksSinceVent");
        Optional<BlockPos> altar = NbtUtils.readBlockPos(tag, "Altar");
        altar.ifPresent(this::setAltarPos);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ goal

    /** Runs one big attack from wind-up to recovery, then counts it towards the next vent. */
    class AttackGoal extends Goal {
        @Nullable
        private Action chosen;

        AttackGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (attackCooldown > 0 || action() != Action.IDLE || target == null || !target.isAlive()) return false;
            chosen = pickAttack(target);
            return chosen != null;
        }

        @Override
        public boolean canContinueToUse() {
            Action a = action();
            return a != Action.IDLE && a != Action.VENT;
        }

        @Override
        public void start() {
            if (chosen == null) return;
            lastAttack = chosen;
            setAction(chosen);
            getNavigation().stop();
            switch (chosen) {
                case BREATH_WINDUP -> {
                    playSound(SoundEvents.IRON_DOOR_OPEN, 1.5F, 0.7F);
                    playSound(SoundEvents.BLAZE_AMBIENT, 2.0F, 0.5F);
                }
                case MORTAR_WINDUP -> playSound(SoundEvents.BUCKET_FILL_LAVA, 2.0F, 0.6F);
                case ERUPT_WINDUP -> playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.5F);
                default -> {
                }
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            Action a = action();
            if (a != Action.BREATH && target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            switch (a) {
                case BREATH_WINDUP -> {
                    if (actionTicks >= BREATH_WINDUP) startBreath(target);
                }
                case BREATH -> {
                    breathTick(actionTicks);
                    if (actionTicks >= BREATH_TICKS) setAction(Action.IDLE);
                }
                case MORTAR_WINDUP -> {
                    if (actionTicks >= MORTAR_WINDUP) {
                        if (target != null && target.isAlive()) fireMortar(target);
                        setAction(Action.MORTAR);
                    }
                }
                case ERUPT_WINDUP -> {
                    if (actionTicks >= ERUPT_WINDUP) {
                        if (target != null && target.isAlive()) erupt(target);
                        setAction(Action.ERUPT);
                    }
                }
                case MORTAR -> {
                    if (actionTicks >= MORTAR_RECOVER) setAction(Action.IDLE);
                }
                case ERUPT -> {
                    if (actionTicks >= ERUPT_RECOVER) setAction(Action.IDLE);
                }
                default -> {
                }
            }
        }

        @Override
        public void stop() {
            if (action() != Action.VENT && action() != Action.IDLE) setAction(Action.IDLE);
            attackCooldown = BossConfig.tyrantCooldown(isPhaseTwo());
            if (++attacksSinceVent >= BossConfig.tyrantAttacksPerVent() && isAlive()) startVent();
        }
    }
}
