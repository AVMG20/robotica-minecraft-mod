package com.arno.robotica.boss.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.arno.robotica.core.particle.CoreParticles;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Null Sentinel, the Age 4 Robotica boss: a hovering relay robot of null steel and purpur with an antigrav core, woken at a
 * Null Altar on the outer End islands. Drops the Antigrav Core.
 *
 * <p>It wakes slowly ({@value #INTRO_TICKS} ticks, untouchable, the boss bar fills) and fights in three phases.
 * Big attacks, one every few seconds, each with a tell:
 * <ul>
 *   <li>Null beam: the lens charges and a thin aim line follows the target, then freezes; the beam fires along it and turns
 *   after the target slower than a sprinting player can circle it. Blocks stop it.</li>
 *   <li>Gravity well: arms spread and a ring marks {@value #BURST_RADIUS} blocks around it; then it drags everyone within
 *   {@value #WELL_RANGE} blocks in and bursts: whoever is inside the ring gets hurt and thrown up. Sprint away.</li>
 *   <li>Gravity inversion: an arm goes up and rings appear under every player near it; 1.5 s later each ring throws
 *   whoever stands in it high into the air. The fall hurts too.</li>
 * </ul>
 * Claw swipes in between. After every {@value #ATTACKS_PER_RECHARGE} big attacks it sinks and opens its core to recharge
 * and takes double damage. At 60 % health it rises (phase 2) and raises Null Pylons around the arena: while one stands it
 * takes a quarter of the damage. At 30 % (phase 3) the sky darkens, it attacks faster, its beam turns faster and null rifts
 * open on the floor under players (slow and hurt while you stand in them).
 *
 * <p>Fairness as with the other bosses: only damage with a living attacker within {@link BossConfig#maxAttackerDistance()}
 * blocks hurts it, its attacks only hit players, their pets and whatever fights it, it never breaks blocks, its throws are
 * mostly straight up (no void deaths from knockback), and a target it cannot reach gets the beam and the rings out to its
 * follow range. It never leaves its arena for the void: falling far below its altar takes it back. Left alone it heals
 * ({@link BossRules#regen}). Numbers: {@link BossConfig}.
 */
public class NullSentinel extends Monster implements RoboticaBoss {
    public static final int INTRO_TICKS = 80;
    public static final int SHIFT_TICKS = 50;
    /** Tick of the shift when the pylons rise or the rifts begin. */
    public static final int SHIFT_CLIMAX = 26;
    public static final int BEAM_WINDUP = 30;
    /** Last ticks of the wind-up with the aim frozen: move now. */
    public static final int BEAM_LOCK = 10;
    public static final int BEAM_TICKS = 50;
    public static final int BEAM_PULSE = 10;
    public static final double BEAM_RANGE = 28.0;
    /** Hit radius around the beam's axis. */
    public static final double BEAM_RADIUS = 0.6;
    /** Degrees per tick the beam turns after its target (phase 3: {@link #BEAM_TURN_FAST}). */
    public static final float BEAM_TURN = 1.4F;
    public static final float BEAM_TURN_FAST = 2.0F;
    public static final int WELL_WINDUP = 30;
    public static final int WELL_TICKS = 24;
    public static final double WELL_RANGE = 16.0;
    public static final double BURST_RADIUS = 5.0;
    public static final int LAUNCH_WINDUP = 16;
    public static final int LAUNCH_RECOVER = 14;
    /** Ticks a gravity ring glows before it throws. */
    public static final int LAUNCH_DELAY = 30;
    public static final double LAUNCH_RADIUS = 1.6;
    public static final double LAUNCH_RANGE = 28.0;
    public static final int RECHARGE_TICKS = 80;
    public static final int ATTACKS_PER_RECHARGE = 3;
    public static final float RECHARGE_MULTIPLIER = 2.0F;
    /** Share of the damage it takes while a Null Pylon stands. */
    public static final float SHIELD_MULTIPLIER = 0.25F;
    public static final double PYLON_RADIUS = 9.0;
    public static final int RIFT_INTERVAL = 110;
    /** Ticks a rift shows as a thin ring before it opens. */
    public static final int RIFT_ARM = 30;
    public static final int RIFT_LIFE = 160;
    public static final double RIFT_RADIUS = 2.0;
    public static final float RIFT_DAMAGE = 3.0F;
    public static final int MAX_RIFTS = 6;
    public static final int OUT_OF_REACH_TICKS = 60;
    public static final int DEATH_TICKS = 60;
    public static final float PHASE_TWO_AT = 0.6F;
    public static final float PHASE_THREE_AT = 0.3F;

    public static final byte SPOT_LAUNCH = 0;
    public static final byte SPOT_RIFT = 1;

    /** Its own damage type: armor counts, no knockback (it throws on its own terms). */
    public static final ResourceKey<DamageType> NULL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Robotica.id("null_sentinel"));

    private static final byte EV_INTRO = 101;
    private static final byte EV_SHIFT = 102;
    private static final byte EV_BURST = 103;
    private static final byte EV_BEAM = 104;
    private static final byte EV_RECHARGE = 105;
    private static final byte EV_FINALE = 106;
    private static final byte EV_SHIELD = 107;

    /** What the Sentinel is doing, synced to clients for the animation and the telegraphs. */
    public enum Action {
        IDLE, INTRO, BEAM_WINDUP, BEAM, WELL_WINDUP, WELL, LAUNCH_WINDUP, LAUNCH, SHIFT, RECHARGE;

        static Action of(int id) {
            return id >= 0 && id < values().length ? values()[id] : IDLE;
        }

        /** Untouchable: waking up or changing phase. */
        public boolean shielded() {
            return this == INTRO || this == SHIFT;
        }
    }

    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(NullSentinel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(NullSentinel.class, EntityDataSerializers.BYTE);
    /** Direction of the aim line and the beam (from the lens). */
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(NullSentinel.class, EntityDataSerializers.VECTOR3);
    /** Gravity rings and rifts (list "S" of k, x, y, z, at, until). Changes only when spots come or go. */
    private static final EntityDataAccessor<CompoundTag> SPOTS = SynchedEntityData.defineId(NullSentinel.class, EntityDataSerializers.COMPOUND_TAG);
    /** Pylons standing (the client draws the shield). */
    private static final EntityDataAccessor<Byte> PYLONS = SynchedEntityData.defineId(NullSentinel.class, EntityDataSerializers.BYTE);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setPlayBossMusic(false);

    /**
     * A gravity ring ({@link #SPOT_LAUNCH}: throws at {@code at}) or a rift ({@link #SPOT_RIFT}: open from {@code at} to
     * {@code until}). Not saved; synced to clients through {@link #SPOTS}.
     */
    public record Spot(byte kind, Vec3 pos, long at, long until) {}

    private final List<Spot> spots = new ArrayList<>();
    private List<Spot> clientSpots = List.of();
    private final List<UUID> pylons = new ArrayList<>();
    private int actionTicks;
    private int dyingTicks;
    private float ringAngle;
    private float ringAngleO;
    private int aloneTicks;
    private int attackCooldown = 40;
    private int attacksSinceRecharge;
    private int outOfReachTicks;
    private int riftCooldown;
    private Action lastAttack = Action.IDLE;
    private Vec3 aim = new Vec3(0.0, 0.0, 1.0);
    @Nullable
    private BlockPos altarPos;

    public NullSentinel(EntityType<? extends NullSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, (byte) 0);
        builder.define(PHASE, (byte) 1);
        builder.define(AIM, new Vector3f(0.0F, 0.0F, 1.0F));
        builder.define(SPOTS, new CompoundTag());
        builder.define(PYLONS, (byte) 0);
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
        targetSelector.addGoal(1, new BossHurtByTargetGoal(this, NullPylon.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Health, armor and claw from the server config, then the wake-up; also runs for /summon. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        applyConfig();
        startIntro();
        return result;
    }

    public void applyConfig() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(BossConfig.sentinelHealth());
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(BossConfig.sentinelArmor());
        AttributeInstance melee = getAttribute(Attributes.ATTACK_DAMAGE);
        if (melee != null) melee.setBaseValue(BossConfig.sentinelMelee());
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

    /** Ticks since it died (0 while alive). */
    public int dyingTicks() {
        return dyingTicks;
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public Vec3 aim() {
        Vector3f v = entityData.get(AIM);
        return new Vec3(v.x, v.y, v.z);
    }

    public boolean shieldUp() {
        return entityData.get(PYLONS) > 0;
    }

    public boolean isRecharging() {
        return action() == Action.RECHARGE;
    }

    /** Spots the client draws: gravity rings and rifts. */
    public List<Spot> clientSpots() {
        return clientSpots;
    }

    public int pendingSpots(byte kind) {
        int n = 0;
        for (Spot s : spots) if (s.kind() == kind) n++;
        return n;
    }

    /** Ticks until the next big attack (tests use it to hold the attacks). */
    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
    }

    /** Ticks it has been without a player nearby (tests use it to skip the wait before it heals). */
    public void setAloneTicks(int ticks) {
        this.aloneTicks = ticks;
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
        if (SPOTS.equals(key) && level().isClientSide) clientSpots = readSpots(entityData.get(SPOTS));
    }

    private void syncSpots() {
        ListTag list = new ListTag();
        for (Spot s : spots) {
            CompoundTag t = new CompoundTag();
            t.putByte("k", s.kind());
            t.putDouble("x", s.pos().x);
            t.putDouble("y", s.pos().y);
            t.putDouble("z", s.pos().z);
            t.putLong("at", s.at());
            t.putLong("until", s.until());
            list.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("S", list);
        entityData.set(SPOTS, tag);
    }

    private static List<Spot> readSpots(CompoundTag tag) {
        List<Spot> out = new ArrayList<>();
        for (Tag t : tag.getList("S", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            out.add(new Spot(c.getByte("k"), new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")), c.getLong("at"), c.getLong("until")));
        }
        return out;
    }

    /** Holds an action and an aim (the showcase: a mob without AI keeps it). */
    public void pose(Action action, Vec3 dir) {
        setAim(dir);
        setAction(action);
        faceAim();
    }

    private void setAim(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        aim = dir.normalize();
        entityData.set(AIM, aim.toVector3f());
    }

    // ------------------------------------------------------------------ geometry

    private Vec3 forward() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    /** The lens on its head: where the beam comes from. */
    public Vec3 lensPos() {
        Vec3 f = forward();
        return new Vec3(getX() + f.x * 0.45, getY() + 3.1, getZ() + f.z * 0.45);
    }

    /** The antigrav core in its chest. */
    public Vec3 corePos() {
        Vec3 f = forward();
        return new Vec3(getX() + f.x * 0.6, getY() + 2.15, getZ() + f.z * 0.6);
    }

    /** Where the beam ends: the first block on its way or its full range. */
    public Vec3 beamEnd(Vec3 from, Vec3 dir) {
        Vec3 to = from.add(dir.scale(BEAM_RANGE));
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
    }

    /** Drops a point onto the floor below it (at most 6 blocks), so a jump or a launch does not lift the mark into the air. */
    private Vec3 ground(Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        for (int i = 0; i < 6 && level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty(); i++) p = p.below();
        return new Vec3(pos.x, Math.min(pos.y, p.getY()), pos.z);
    }

    private boolean canHurt(LivingEntity e) {
        return !(e instanceof NullPylon) && BossRules.isFoe(this, e);
    }

    private DamageSource nullDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NULL_DAMAGE), this);
    }

    /** Sets a throw on whoever it hits. Players get their velocity from the server only through hurtMarked. */
    private static void fling(LivingEntity e, double x, double y, double z) {
        e.setDeltaMovement(x, y, z);
        e.hurtMarked = true;
        e.resetFallDistance();
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        actionTicks++;
        if (level().isClientSide) {
            ringAngleO = ringAngle;
            ringAngle += ringSpin();
            clientEffects();
        }
    }

    /** Angle of the gyro ring on its back (client). */
    public float ringAngle(float partialTick) {
        return Mth.lerp(partialTick, ringAngleO, ringAngle);
    }

    /** How fast the gyro ring turns, in radians per tick: it races before big attacks and stalls while it recharges. */
    private float ringSpin() {
        if (isDeadOrDying()) return 0.02F;
        return switch (action()) {
            case INTRO -> 0.05F + 0.4F * Mth.clamp((actionTicks - 24) / 48.0F, 0.0F, 1.0F);
            case BEAM_WINDUP -> 0.25F;
            case BEAM -> 0.35F;
            case WELL_WINDUP -> 0.45F;
            case WELL -> 0.8F;
            case LAUNCH_WINDUP -> 0.3F;
            case SHIFT -> 0.9F;
            case RECHARGE -> 0.01F;
            default -> phase() >= 3 ? 0.1F : 0.05F;
        };
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) return;
        Action a = action();
        if (attackCooldown > 0 && !a.shielded()) attackCooldown--;
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && !isWithinMeleeAttackRange(target)) outOfReachTicks++;
        else outOfReachTicks = 0;
        if (a == Action.INTRO) tickIntro(server);
        else if (a == Action.SHIFT) tickShift(server);
        else if (phase() == 1 && getHealth() <= getMaxHealth() * PHASE_TWO_AT) startShift(2);
        else if (phase() == 2 && getHealth() <= getMaxHealth() * PHASE_THREE_AT) startShift(3);
        else if (a == Action.RECHARGE) tickRecharge(server);
        tickSpots(server);
        if (phase() >= 3 && !action().shielded() && action() != Action.RECHARGE && --riftCooldown <= 0) openRifts(server);
        if (tickCount % 10 == 0) countPylons(server);
        aloneTicks = BossRules.regen(this, aloneTicks);
        if (altarPos != null && getY() < altarPos.getY() - 12) returnToAltar();
        if (action() != Action.INTRO) bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    /** Falling off its island (or pushed into the void) brings it back onto its altar. */
    private void returnToAltar() {
        if (altarPos == null) return;
        teleportTo(altarPos.getX() + 0.5, altarPos.getY() + 1.0, altarPos.getZ() + 0.5);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.5F);
    }

    private void freeze(boolean frozen) {
        for (Goal.Flag flag : new Goal.Flag[]{Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP}) {
            if (frozen) goalSelector.disableControlFlag(flag);
            else goalSelector.enableControlFlag(flag);
        }
        if (frozen) getNavigation().stop();
    }

    // ------------------------------------------------------------------ intro, phases, recharge

    /** The wake-up: it rises out of the altar, untouchable, while the boss bar fills. */
    public void startIntro() {
        setAction(Action.INTRO);
        freeze(true);
        bossEvent.setProgress(0.0F);
        playSound(SoundEvents.PORTAL_TRIGGER, 2.0F, 0.6F);
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
    }

    /** Skips the wake-up (tests). */
    public void skipIntro() {
        if (action() != Action.INTRO) return;
        setAction(Action.IDLE);
        freeze(false);
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    private void tickIntro(ServerLevel server) {
        bossEvent.setProgress(Mth.clamp(actionTicks / (float) INTRO_TICKS, 0.0F, 1.0F));
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (actionTicks == 30) playSound(SoundEvents.BEACON_ACTIVATE, 2.5F, 0.5F);
        if (actionTicks == 50) playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.5F, 0.5F);
        if (actionTicks == INTRO_TICKS - 12) {
            server.broadcastEntityEvent(this, EV_INTRO);
            playSound(SoundEvents.END_PORTAL_SPAWN, 2.0F, 1.2F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
            shove(server, 6.0, 0.6, 0.5);
        }
        if (actionTicks >= INTRO_TICKS) {
            setAction(Action.IDLE);
            freeze(false);
            attackCooldown = 30;
        }
    }

    /** Pushes foes nearby back and up a little (no damage): the wake-up and phase shockwaves. */
    private void shove(ServerLevel server, double radius, double out, double up) {
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::canHurt)) {
            Vec3 d = e.position().subtract(position());
            double h = d.horizontalDistance();
            if (h > radius) continue;
            Vec3 dir = h < 0.1 ? forward() : new Vec3(d.x / h, 0.0, d.z / h);
            fling(e, dir.x * out, up, dir.z * out);
        }
    }

    /** Phase 2 at 60 % health, phase 3 at 30 %: it rises, untouchable for a moment, and comes back harder. */
    public void startShift(int phase) {
        if (phase <= phase()) return;
        entityData.set(PHASE, (byte) phase);
        spots.removeIf(s -> s.kind() == SPOT_LAUNCH);
        syncSpots();
        setAction(Action.SHIFT);
        freeze(true);
        bossEvent.setColor(phase >= 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
        if (phase >= 3) bossEvent.setDarkenScreen(true);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.6F);
        playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.5F, 0.6F);
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.6F);
    }

    private void tickShift(ServerLevel server) {
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (actionTicks == SHIFT_CLIMAX) {
            server.broadcastEntityEvent(this, EV_SHIFT);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.5F);
            shove(server, 7.0, 0.8, 0.6);
            if (phase() == 2) raisePylons(server);
            else riftCooldown = 20;
        }
        if (actionTicks >= SHIFT_TICKS) {
            setAction(Action.IDLE);
            freeze(false);
            attackCooldown = 20;
        }
    }

    public void startRecharge() {
        spots.removeIf(s -> s.kind() == SPOT_LAUNCH);
        syncSpots();
        setAction(Action.RECHARGE);
        attacksSinceRecharge = 0;
        freeze(true);
        if (level() instanceof ServerLevel server) server.broadcastEntityEvent(this, EV_RECHARGE);
        playSound(SoundEvents.BEACON_DEACTIVATE, 2.5F, 0.6F);
        playSound(SoundEvents.IRON_DOOR_OPEN, 2.0F, 0.5F);
    }

    private void tickRecharge(ServerLevel server) {
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (actionTicks % 20 == 0) playSound(SoundEvents.BEACON_AMBIENT, 2.0F, 1.4F);
        if (actionTicks >= RECHARGE_TICKS) {
            setAction(Action.IDLE);
            freeze(false);
            attackCooldown = BossConfig.sentinelCooldown(phase());
            playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.2F);
            playSound(SoundEvents.IRON_DOOR_CLOSE, 2.0F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ pylons

    /** Three Null Pylons around its altar (or itself), one more for each extra player nearby, five at most. */
    public int raisePylons(ServerLevel server) {
        int players = server.getPlayers(p -> !p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) < 32.0 * 32.0).size();
        int count = Mth.clamp(2 + players, 3, 5);
        Vec3 centre = altarPos != null ? Vec3.atBottomCenterOf(altarPos) : position();
        double offset = random.nextDouble() * Math.PI * 2.0;
        int raised = 0;
        for (int i = 0; i < count; i++) {
            double a = offset + i * Math.PI * 2.0 / count;
            Vec3 at = ground(new Vec3(centre.x + Math.cos(a) * PYLON_RADIUS, centre.y + 2.0, centre.z + Math.sin(a) * PYLON_RADIUS));
            NullPylon pylon = BossRegistry.NULL_PYLON.get().create(server);
            if (pylon == null) continue;
            pylon.moveTo(at.x, at.y + 1.2, at.z, random.nextFloat() * 360.0F, 0.0F);
            pylon.setOwner(this);
            server.addFreshEntity(pylon);
            pylons.add(pylon.getUUID());
            server.sendParticles(CoreParticles.flash(CoreParticles.WARP_CYAN, 2.5F), at.x, at.y + 1.8, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 16, 0.2, 0.8, 0.2, 0.05);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.6F);
            raised++;
        }
        entityData.set(PYLONS, (byte) pylons.size());
        return raised;
    }

    /** Living pylons of this Sentinel nearby. */
    public List<NullPylon> livingPylons() {
        return level().getEntitiesOfClass(NullPylon.class, getBoundingBox().inflate(48.0), p -> p.isAlive() && getUUID().equals(p.ownerId()));
    }

    private void countPylons(ServerLevel server) {
        if (pylons.isEmpty()) {
            if (entityData.get(PYLONS) != 0) entityData.set(PYLONS, (byte) 0);
            return;
        }
        pylons.removeIf(id -> !(server.getEntity(id) instanceof NullPylon p && p.isAlive()));
        byte n = (byte) pylons.size();
        if (entityData.get(PYLONS) != n) {
            entityData.set(PYLONS, n);
            if (n == 0) {
                playSound(SoundEvents.BEACON_DEACTIVATE, 2.5F, 0.8F);
                playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
            }
        }
    }

    // ------------------------------------------------------------------ attacks

    /**
     * Which big attacks fit this target right now. A target it has not reached in melee for {@value #OUT_OF_REACH_TICKS}
     * ticks gets the beam and the gravity rings out to the full follow range; the rings and the well need no line of sight.
     */
    public List<Action> attackOptions(LivingEntity target) {
        List<Action> options = new ArrayList<>();
        double d = distanceTo(target);
        boolean sight = hasLineOfSight(target);
        boolean stuck = outOfReachTicks >= OUT_OF_REACH_TICKS;
        double reach = getAttributeValue(Attributes.FOLLOW_RANGE);
        double beamRange = stuck ? Math.max(BEAM_RANGE, reach) : BEAM_RANGE;
        double launchRange = stuck ? Math.max(LAUNCH_RANGE, reach) : LAUNCH_RANGE;
        if (sight && d <= beamRange - 1.0 && (d > 4.0 || stuck)) options.add(Action.BEAM_WINDUP);
        if (d <= 12.0 || stuck && d <= WELL_RANGE) options.add(Action.WELL_WINDUP);
        if (d <= launchRange && (d > 3.0 || stuck)) options.add(Action.LAUNCH_WINDUP);
        return options;
    }

    @Nullable
    private Action pickAttack(LivingEntity target) {
        List<Action> options = attackOptions(target);
        if (options.size() > 1) options.remove(lastAttack);
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    /** Turns {@code from} towards {@code to} by at most {@code degrees}. */
    private static Vec3 turn(Vec3 from, Vec3 to, float degrees) {
        double dot = Mth.clamp(from.dot(to), -1.0, 1.0);
        double angle = Math.acos(dot);
        double max = Math.toRadians(degrees);
        if (angle <= max || angle < 1.0E-4) return to;
        double t = max / angle;
        return from.scale(1.0 - t).add(to.scale(t)).normalize();
    }

    private Vec3 aimAt(LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(lensPos());
        return d.lengthSqr() < 1.0E-4 ? forward() : d.normalize();
    }

    private void faceAim() {
        float yaw = (float) (Mth.atan2(aim.z, aim.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** One tick of the beam wind-up: the aim line follows the target fast, then freezes for the last ticks. */
    public void beamWindupTick(@Nullable LivingEntity target) {
        if (target != null && actionTicks < BEAM_WINDUP - BEAM_LOCK) setAim(turn(aim, aimAt(target), 6.0F));
        faceAim();
        if (actionTicks == BEAM_WINDUP - BEAM_LOCK) playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 1.6F);
    }

    /** One tick of the beam: it turns after the target, slowly, and hurts every {@value #BEAM_PULSE} ticks. Returns how many it hit. */
    public int beamTick(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return 0;
        if (target != null && target.isAlive() && hasLineOfSight(target)) setAim(turn(aim, aimAt(target), phase() >= 3 ? BEAM_TURN_FAST : BEAM_TURN));
        faceAim();
        if (actionTicks % 25 == 0) playSound(SoundEvents.GUARDIAN_ATTACK, 2.0F, 0.6F);
        if (actionTicks % BEAM_PULSE != 0) return 0;
        Vec3 from = lensPos();
        Vec3 to = beamEnd(from, aim);
        float damage = BossConfig.sentinelBeam();
        int hit = 0;
        AABB box = new AABB(from, to).inflate(BEAM_RADIUS + 1.0);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, this::canHurt)) {
            Vec3 c = e.getBoundingBox().getCenter();
            if (distanceToSegment(c, from, to) > BEAM_RADIUS + e.getBbWidth() * 0.5) continue;
            if (e.hurt(nullDamage(), damage)) {
                hit++;
                server.sendParticles(CoreParticles.zap(CoreParticles.WARP_VIOLET, 1.4F), c.x, c.y, c.z, 4, 0.2, 0.3, 0.2, 0.0);
            }
        }
        return hit;
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0E-6 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / len, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Fires the beam along the frozen aim line. */
    public void startBeam() {
        setAction(Action.BEAM);
        faceAim();
        if (level() instanceof ServerLevel server) server.broadcastEntityEvent(this, EV_BEAM);
        playSound(SoundEvents.BEACON_ACTIVATE, 2.5F, 1.6F);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 1.4F);
    }

    /** The pull of the gravity well: everyone around is dragged towards it. Returns how many it pulled. */
    public int wellTick() {
        if (!(level() instanceof ServerLevel server)) return 0;
        if (actionTicks % 6 == 0) playSound(SoundEvents.PORTAL_AMBIENT, 2.0F, 1.6F);
        double strength = phase() >= 3 ? 0.42 : 0.36;
        int pulled = 0;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(WELL_RANGE), this::canHurt)) {
            Vec3 to = position().subtract(e.position());
            double h = to.horizontalDistance();
            if (h > WELL_RANGE || h < 0.8) continue;
            double dx = to.x / h;
            double dz = to.z / h;
            boolean above = e.getY() > getY() + 2.0;
            if (e instanceof Player) {
                // a player's velocity is set (not added: the server's copy of it is not kept up), in steps, so they can still fight it
                if (actionTicks % 4 == 0) fling(e, dx * strength, above ? -0.4 : 0.0, dz * strength);
            } else {
                e.setDeltaMovement(e.getDeltaMovement().add(dx * 0.1, above ? -0.08 : 0.0, dz * 0.1));
            }
            pulled++;
        }
        return pulled;
    }

    /** The end of the gravity well: hurts and throws up everyone inside the ring. Returns how many it hit. */
    public int burst() {
        if (!(level() instanceof ServerLevel server)) return 0;
        server.broadcastEntityEvent(this, EV_BURST);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.8F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
        float damage = BossConfig.sentinelBurst();
        int hit = 0;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BURST_RADIUS + 1.0, 4.0, BURST_RADIUS + 1.0), this::canHurt)) {
            Vec3 d = e.position().subtract(position());
            double h = d.horizontalDistance();
            if (h > BURST_RADIUS + e.getBbWidth() * 0.5 || d.y < -2.0 || d.y > 4.0) continue;
            if (e.hurt(nullDamage(), damage)) {
                Vec3 dir = h < 0.1 ? forward() : new Vec3(d.x / h, 0.0, d.z / h);
                fling(e, dir.x * 0.35, 0.95, dir.z * 0.35);
                hit++;
            }
        }
        return hit;
    }

    /**
     * Marks the gravity rings: one under every foe player within range (four at most) and under the target, plus two around
     * the target from phase 2. Returns how many.
     */
    public int markLaunches(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return 0;
        long at = server.getGameTime() + LAUNCH_DELAY;
        List<Vec3> marks = new ArrayList<>();
        marks.add(ground(target.position()));
        for (ServerPlayer p : server.getPlayers(p -> p != target && canHurt(p) && p.distanceToSqr(this) < LAUNCH_RANGE * LAUNCH_RANGE)) {
            if (marks.size() >= 4) break;
            marks.add(ground(p.position()));
        }
        if (phase() >= 2) {
            double offset = random.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < 2; i++) {
                double a = offset + i * Math.PI;
                double r = 2.8 + random.nextDouble();
                marks.add(ground(target.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r)));
            }
        }
        for (Vec3 m : marks) {
            spots.add(new Spot(SPOT_LAUNCH, m, at, at));
            server.playSound(null, m.x, m.y, m.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.HOSTILE, 1.5F, 0.7F);
        }
        syncSpots();
        playSound(SoundEvents.SHULKER_SHOOT, 2.0F, 0.5F);
        return marks.size();
    }

    /** Throws whoever stands in a gravity ring: hurt, then high up, nearly straight. Returns how many it hit. */
    private int launchAt(ServerLevel server, Vec3 pos) {
        server.sendParticles(CoreParticles.flash(CoreParticles.WARP_VIOLET, 2.0F), pos.x, pos.y + 0.3, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 24, 0.4, 0.2, 0.4, 0.25);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0, pos.z, 30, 0.5, 1.2, 0.5, 0.1);
        server.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SHULKER_BULLET_HIT, SoundSource.HOSTILE, 2.0F, 0.6F);
        server.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2.0F, 0.6F);
        AABB box = new AABB(pos.x - LAUNCH_RADIUS, pos.y - 1.0, pos.z - LAUNCH_RADIUS, pos.x + LAUNCH_RADIUS, pos.y + 3.0, pos.z + LAUNCH_RADIUS);
        float damage = BossConfig.sentinelLaunch();
        int hit = 0;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, this::canHurt)) {
            double dx = e.getX() - pos.x;
            double dz = e.getZ() - pos.z;
            if (Math.sqrt(dx * dx + dz * dz) > LAUNCH_RADIUS + e.getBbWidth() * 0.5) continue;
            if (e.hurt(nullDamage(), damage)) {
                fling(e, dx * 0.05, 1.25, dz * 0.05);
                hit++;
            }
        }
        return hit;
    }

    /** Phase 3: rifts open under up to four foe players and one near itself, at most {@value #MAX_RIFTS} at once. */
    private void openRifts(ServerLevel server) {
        riftCooldown = RIFT_INTERVAL;
        List<Vec3> at = new ArrayList<>();
        for (ServerPlayer p : server.getPlayers(p -> canHurt(p) && p.distanceToSqr(this) < 24.0 * 24.0)) {
            if (at.size() >= 4) break;
            at.add(ground(p.position()));
        }
        double a = random.nextDouble() * Math.PI * 2.0;
        at.add(ground(position().add(Math.cos(a) * 5.0, 0.5, Math.sin(a) * 5.0)));
        for (Vec3 p : at) {
            if (pendingSpots(SPOT_RIFT) >= MAX_RIFTS) break;
            markRift(p);
        }
    }

    /** Marks a rift on the floor at {@code pos}: it opens {@value #RIFT_ARM} ticks later for {@value #RIFT_LIFE} ticks. */
    public void markRift(Vec3 pos) {
        long now = level().getGameTime();
        Vec3 p = ground(pos);
        spots.add(new Spot(SPOT_RIFT, p, now + RIFT_ARM, now + RIFT_ARM + RIFT_LIFE));
        syncSpots();
        level().playSound(null, p.x, p.y, p.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    /** Throws the due gravity rings, hurts whoever stands in an open rift, closes old rifts. */
    private void tickSpots(ServerLevel server) {
        if (spots.isEmpty()) return;
        long now = server.getGameTime();
        List<Spot> done = new ArrayList<>();
        for (Spot s : spots) {
            if (s.kind() == SPOT_LAUNCH && now >= s.at()) {
                launchAt(server, s.pos());
                done.add(s);
            } else if (s.kind() == SPOT_RIFT) {
                if (now >= s.until()) done.add(s);
                else if (now >= s.at() && now % 10 == 0) riftTick(server, s.pos());
            }
        }
        if (!done.isEmpty()) {
            spots.removeAll(done);
            syncSpots();
        }
    }

    private void riftTick(ServerLevel server, Vec3 pos) {
        AABB box = new AABB(pos.x - RIFT_RADIUS, pos.y - 1.0, pos.z - RIFT_RADIUS, pos.x + RIFT_RADIUS, pos.y + 2.5, pos.z + RIFT_RADIUS);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, this::canHurt)) {
            double dx = e.getX() - pos.x;
            double dz = e.getZ() - pos.z;
            if (Math.sqrt(dx * dx + dz * dz) > RIFT_RADIUS) continue;
            if (e.hurt(nullDamage(), RIFT_DAMAGE)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            outOfReachTicks = 0;
            playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.5F, 0.6F);
            if (level() instanceof ServerLevel server) {
                Vec3 c = target.getBoundingBox().getCenter();
                server.sendParticles(CoreParticles.zap(CoreParticles.WARP_VIOLET, 1.5F), c.x, c.y, c.z, 5, 0.3, 0.3, 0.3, 0.0);
            }
        }
        return hit;
    }

    // ------------------------------------------------------------------ damage

    /**
     * Only attacks hurt it: damage without a living attacker (lava, fire, suffocation, cactus, unowned TNT or arrows,
     * lightning, poison) does nothing, and neither do hits from attackers further away than the config allows. /kill and
     * the void still work.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source) || BossRules.attackerTooFar(this, source)) return true;
        return !(source.getEntity() instanceof LivingEntity) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Untouchable while it wakes or shifts, a quarter of the damage behind its pylons, double while it recharges. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && action().shielded()) {
            if (!level().isClientSide && !isInvulnerableTo(source)) shieldPing();
            return false;
        }
        if (!bypass && shieldUp()) {
            amount *= SHIELD_MULTIPLIER;
            if (!level().isClientSide && !isInvulnerableTo(source)) shieldPing();
        }
        if (!bypass && isRecharging()) amount *= RECHARGE_MULTIPLIER;
        boolean hurt = super.hurt(source, amount);
        if (hurt && isRecharging() && level() instanceof ServerLevel server) {
            Vec3 c = corePos();
            server.sendParticles(CoreParticles.spark(CoreParticles.WARP_CYAN, 1.2F), c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.25);
        }
        return hurt;
    }

    private void shieldPing() {
        if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
            server.broadcastEntityEvent(this, EV_SHIELD);
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 2.0F, 0.5F + random.nextFloat() * 0.2F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        spots.clear();
        syncSpots();
        bossEvent.setProgress(0.0F);
        if (!(level() instanceof ServerLevel server)) return;
        for (NullPylon p : livingPylons()) p.shatter();
        playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
        // everyone who fought it (had the boss bar and stayed close) gets the guide step, not only the killer
        AdvancementHolder step = server.getServer().getAdvancements().get(Robotica.id("guide/sentinel"));
        if (step == null) return;
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (player.level() == server && player.distanceToSqr(this) < 64.0 * 64.0) player.getAdvancements().award(step, "done");
        }
    }

    /** A slow collapse instead of the usual tip-over: it shakes, sheds its plates, and its core blows {@value #DEATH_TICKS} ticks later. */
    @Override
    protected void tickDeath() {
        dyingTicks++;
        if (level().isClientSide) {
            dyingEffects();
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        if (dyingTicks % 8 == 0 && dyingTicks < DEATH_TICKS - 10) {
            Vec3 c = corePos();
            server.sendParticles(CoreParticles.flash(CoreParticles.WARP_VIOLET, 1.5F), c.x + random.nextGaussian() * 0.6, c.y + random.nextGaussian() * 0.6,
                    c.z + random.nextGaussian() * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.2F + random.nextFloat() * 0.4F);
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5F, 0.6F);
        }
        if (dyingTicks == DEATH_TICKS - 10) {
            server.broadcastEntityEvent(this, EV_FINALE);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
            playSound(SoundEvents.END_PORTAL_SPAWN, 2.0F, 1.5F);
        }
        if (dyingTicks >= DEATH_TICKS && !isRemoved()) {
            server.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ client effects

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EV_INTRO -> burstFx(1.0F);
            case EV_SHIFT -> burstFx(1.3F);
            case EV_BURST -> ringFx(BURST_RADIUS, 48);
            case EV_BEAM -> {
                Vec3 l = lensPos();
                level().addParticle(CoreParticles.flash(0xE8D8FF, 2.0F), l.x, l.y, l.z, 0.0, 0.0, 0.0);
            }
            case EV_RECHARGE -> {
                Vec3 c = corePos();
                for (int i = 0; i < 16; i++) {
                    level().addParticle(CoreParticles.puff(0x5A3C88, 1.4F), c.x, c.y, c.z, random.nextGaussian() * 0.08, 0.05, random.nextGaussian() * 0.08);
                }
            }
            case EV_SHIELD -> {
                Vec3 c = getBoundingBox().getCenter();
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    double y = (random.nextDouble() - 0.5) * 2.4;
                    level().addParticle(CoreParticles.zap(CoreParticles.WARP_CYAN, 1.6F), c.x + Math.cos(a) * 1.6, c.y + y, c.z + Math.sin(a) * 1.6, 0.0, 0.0, 0.0);
                }
            }
            case EV_FINALE -> {
                Vec3 c = corePos();
                level().addParticle(CoreParticles.flash(0xF2E8FF, 9.0F), c.x, c.y, c.z, 0.0, 0.0, 0.0);
                level().addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 0.0, 0.0, 0.0);
                for (int i = 0; i < 40; i++) {
                    level().addParticle(CoreParticles.shard(0x2A2238, 1.8F), c.x, c.y, c.z, random.nextGaussian() * 0.35, random.nextDouble() * 0.5, random.nextGaussian() * 0.35);
                    level().addParticle(CoreParticles.spark(i % 2 == 0 ? CoreParticles.WARP_VIOLET : CoreParticles.WARP_CYAN, 1.5F), c.x, c.y, c.z,
                            random.nextGaussian() * 0.45, random.nextDouble() * 0.6, random.nextGaussian() * 0.45);
                }
                for (int i = 0; i < 30; i++) level().addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, random.nextGaussian() * 0.3, random.nextGaussian() * 0.3, random.nextGaussian() * 0.3);
                ringFx(6.0, 64);
            }
            default -> super.handleEntityEvent(id);
        }
    }

    /** A flash at the core, a ring rolling out on the floor and sparks: wake-up and phase shifts. */
    private void burstFx(float scale) {
        Vec3 c = corePos();
        level().addParticle(CoreParticles.flash(0xF2E8FF, 6.0F * scale), c.x, c.y, c.z, 0.0, 0.0, 0.0);
        for (int i = 0; i < 30; i++) {
            level().addParticle(CoreParticles.spark(i % 2 == 0 ? CoreParticles.WARP_VIOLET : CoreParticles.WARP_CYAN, 1.4F), c.x, c.y, c.z,
                    random.nextGaussian() * 0.4, random.nextDouble() * 0.5, random.nextGaussian() * 0.4);
        }
        for (int i = 0; i < 20; i++) level().addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, random.nextGaussian() * 0.25, random.nextGaussian() * 0.25, random.nextGaussian() * 0.25);
        ringFx(7.0 * scale, 56);
    }

    /** Motes rolling out on the floor to {@code radius}. */
    private void ringFx(double radius, int count) {
        for (int i = 0; i < count; i++) {
            level().addParticle(CoreParticles.warp(i % 3 == 0 ? CoreParticles.WARP_CYAN : CoreParticles.WARP_VIOLET, 2.2F),
                    getX(), getY() + 0.2 + random.nextDouble() * 0.3, getZ(), -radius, 0.01, 0.05);
        }
    }

    /** Client-only particles that follow the animation and the telegraphs (the renderer draws the beam and the rings). */
    private void clientEffects() {
        if (isDeadOrDying()) return;
        Action a = action();
        Vec3 core = corePos();
        // thrusters under the keel and the hum of the core
        if (random.nextInt(3) == 0) {
            level().addParticle(CoreParticles.mote(CoreParticles.WARP_VIOLET, 1.0F), getX() + random.nextGaussian() * 0.2, getY() + 0.5,
                    getZ() + random.nextGaussian() * 0.2, 0.0, -0.04, 0.0);
        }
        if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.PORTAL, getX(), getY() + 0.4, getZ(), random.nextGaussian(), 0.2, random.nextGaussian());
        if (phase() >= 3 && random.nextInt(4) == 0) {
            level().addParticle(CoreParticles.zap(random.nextBoolean() ? CoreParticles.WARP_VIOLET : CoreParticles.WARP_CYAN, 1.2F),
                    core.x + random.nextGaussian() * 0.7, core.y + random.nextGaussian() * 0.8, core.z + random.nextGaussian() * 0.7, 0.0, 0.0, 0.0);
        }
        switch (a) {
            case INTRO -> {
                float t = actionTicks / (float) INTRO_TICKS;
                for (int i = 0; i < 3; i++) {
                    level().addParticle(CoreParticles.warp(i == 0 ? CoreParticles.WARP_CYAN : CoreParticles.WARP_VIOLET, 1.8F),
                            getX(), getY() + 0.3 + random.nextDouble() * 3.0 * t, getZ(), 2.5 + random.nextDouble() * 3.0, 0.03, 0.0);
                }
                if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + random.nextGaussian(), getY() + 0.2,
                        getZ() + random.nextGaussian(), 0.0, 0.05, 0.0);
            }
            case BEAM_WINDUP -> {
                Vec3 l = lensPos();
                level().addParticle(CoreParticles.warp(0xE8D8FF, 1.0F), l.x, l.y - 0.2, l.z, 0.9, 0.012, 0.0);
            }
            case BEAM -> {
                if (actionTicks % 2 == 0) {
                    Vec3 l = lensPos();
                    Vec3 end = beamEnd(l, aim());
                    level().addParticle(CoreParticles.flash(CoreParticles.WARP_VIOLET, 1.2F), end.x, end.y, end.z, 0.0, 0.0, 0.0);
                    for (int i = 0; i < 2; i++) {
                        level().addParticle(CoreParticles.spark(CoreParticles.WARP_VIOLET, 1.0F), end.x, end.y, end.z,
                                random.nextGaussian() * 0.15, random.nextDouble() * 0.2, random.nextGaussian() * 0.15);
                    }
                    double t = random.nextDouble();
                    Vec3 p = l.add(end.subtract(l).scale(t));
                    level().addParticle(CoreParticles.zap(CoreParticles.WARP_CYAN, 1.0F), p.x, p.y, p.z, 0.0, 0.0, 0.0);
                }
            }
            case WELL_WINDUP, WELL -> {
                int n = a == Action.WELL ? 4 : 2;
                for (int i = 0; i < n; i++) {
                    level().addParticle(CoreParticles.warp(i % 2 == 0 ? CoreParticles.WARP_VIOLET : 0x4A2E7A, 2.0F),
                            getX(), getY() + 0.2 + random.nextDouble() * 2.5, getZ(), 6.0 + random.nextDouble() * (WELL_RANGE - 6.0), 0.0, 0.09);
                }
            }
            case RECHARGE -> {
                if (random.nextInt(2) == 0) {
                    level().addParticle(CoreParticles.spark(CoreParticles.WARP_CYAN, 1.0F), core.x, core.y, core.z,
                            random.nextGaussian() * 0.12, 0.1 + random.nextDouble() * 0.1, random.nextGaussian() * 0.12);
                }
                if (random.nextInt(3) == 0) level().addParticle(CoreParticles.puff(0x3A2A55, 1.2F), core.x, core.y + 0.3, core.z, 0.0, 0.03, 0.0);
            }
            default -> {
            }
        }
        long now = level().getGameTime();
        for (Spot s : clientSpots) {
            if (s.kind() == SPOT_LAUNCH) {
                long left = s.at() - now;
                if (left < 0) continue;
                int n = left < 10 ? 3 : 1;
                for (int i = 0; i < n; i++) {
                    double ang = random.nextDouble() * Math.PI * 2.0;
                    double r = random.nextDouble() * LAUNCH_RADIUS;
                    level().addParticle(CoreParticles.mote(CoreParticles.WARP_VIOLET, 1.2F), s.pos().x + Math.cos(ang) * r, s.pos().y + 0.1,
                            s.pos().z + Math.sin(ang) * r, 0.0, left < 10 ? 0.25 : 0.08, 0.0);
                }
            } else if (now >= s.at() && random.nextInt(2) == 0) {
                double ang = random.nextDouble() * Math.PI * 2.0;
                double r = random.nextDouble() * RIFT_RADIUS;
                level().addParticle(ParticleTypes.REVERSE_PORTAL, s.pos().x + Math.cos(ang) * r, s.pos().y + 0.1, s.pos().z + Math.sin(ang) * r, 0.0, 0.02, 0.0);
            }
        }
    }

    private void dyingEffects() {
        if (dyingTicks > 6) hurtTime = 0;                          // a short red flash for the killing blow, not a red collapse
        Vec3 c = corePos();
        float t = dyingTicks / (float) DEATH_TICKS;
        int n = 1 + (int) (t * 4);
        for (int i = 0; i < n; i++) {
            level().addParticle(CoreParticles.warp(i % 2 == 0 ? CoreParticles.WARP_VIOLET : CoreParticles.WARP_CYAN, 1.8F),
                    c.x, c.y - 1.0 + random.nextDouble() * 2.0, c.z, 1.5 + random.nextDouble() * 3.0, 0.0, 0.0);
        }
        if (random.nextInt(2) == 0) {
            level().addParticle(CoreParticles.zap(CoreParticles.WARP_CYAN, 1.5F), c.x + random.nextGaussian() * 0.8, c.y + random.nextGaussian() * 0.9,
                    c.z + random.nextGaussian() * 0.8, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(CoreParticles.shard(0x2A2238, 1.4F), c.x, c.y, c.z, random.nextGaussian() * 0.2, 0.2, random.nextGaussian() * 0.2);
        }
    }

    // ------------------------------------------------------------------ misc

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

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
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
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.NETHERITE_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + (random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 80;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", (byte) phase());
        tag.putInt("AttacksSinceRecharge", attacksSinceRecharge);
        ListTag ids = new ListTag();
        for (UUID id : pylons) ids.add(NbtUtils.createUUID(id));
        tag.put("Pylons", ids);
        if (altarPos != null) tag.put("Altar", NbtUtils.writeBlockPos(altarPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int phase = Mth.clamp(tag.getByte("Phase"), 1, 3);
        entityData.set(PHASE, (byte) phase);
        if (phase == 2) bossEvent.setColor(BossEvent.BossBarColor.BLUE);
        if (phase >= 3) {
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            bossEvent.setDarkenScreen(true);
        }
        attacksSinceRecharge = tag.getInt("AttacksSinceRecharge");
        pylons.clear();
        for (Tag t : tag.getList("Pylons", Tag.TAG_INT_ARRAY)) pylons.add(NbtUtils.loadUUID(t));
        entityData.set(PYLONS, (byte) pylons.size());
        Optional<BlockPos> altar = NbtUtils.readBlockPos(tag, "Altar");
        altar.ifPresent(this::setAltarPos);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ goal

    /** Runs one big attack from wind-up to recovery, then counts it towards the next recharge if it went off. */
    class AttackGoal extends Goal {
        @Nullable
        private Action chosen;
        private boolean fired;

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
            return a != Action.IDLE && a != Action.RECHARGE && !a.shielded();
        }

        @Override
        public void start() {
            if (chosen == null) return;
            lastAttack = chosen;
            fired = false;
            setAction(chosen);
            getNavigation().stop();
            switch (chosen) {
                case BEAM_WINDUP -> {
                    LivingEntity target = getTarget();
                    if (target != null) setAim(aimAt(target));
                    playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.5F);
                    playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5F, 1.2F);
                }
                case WELL_WINDUP -> {
                    playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.6F);
                    playSound(SoundEvents.PORTAL_TRIGGER, 1.5F, 1.4F);
                }
                case LAUNCH_WINDUP -> playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.0F);
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
            if (a != Action.BEAM && a != Action.BEAM_WINDUP && target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            switch (a) {
                case BEAM_WINDUP -> {
                    beamWindupTick(target);
                    if (actionTicks >= BEAM_WINDUP) {
                        startBeam();
                        fired = true;
                    }
                }
                case BEAM -> {
                    beamTick(target);
                    if (actionTicks >= BEAM_TICKS) setAction(Action.IDLE);
                }
                case WELL_WINDUP -> {
                    if (actionTicks >= WELL_WINDUP) {
                        setAction(Action.WELL);
                        fired = true;
                    }
                }
                case WELL -> {
                    wellTick();
                    if (actionTicks >= WELL_TICKS) {
                        burst();
                        setAction(Action.IDLE);
                    }
                }
                case LAUNCH_WINDUP -> {
                    if (actionTicks >= LAUNCH_WINDUP) {
                        if (target != null && target.isAlive()) {
                            markLaunches(target);
                            fired = true;
                        }
                        setAction(Action.LAUNCH);
                    }
                }
                case LAUNCH -> {
                    if (actionTicks >= LAUNCH_RECOVER) setAction(Action.IDLE);
                }
                default -> {
                }
            }
        }

        @Override
        public void stop() {
            Action a = action();
            if (a != Action.RECHARGE && a != Action.IDLE && !a.shielded()) setAction(Action.IDLE);
            if (!action().shielded()) attackCooldown = BossConfig.sentinelCooldown(phase());
            if (fired && !action().shielded() && ++attacksSinceRecharge >= ATTACKS_PER_RECHARGE && isAlive()) startRecharge();
            fired = false;
        }
    }
}
