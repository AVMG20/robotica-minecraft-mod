package com.arno.robotica.gear.entity;

import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearEntities;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Comparator;

/**
 * The Rivet Gun's projectile: a small glowing steel rivet with a bright tracer. Hits for {@link GearConfig#rivetDamage}
 * (arrow damage type, so it counts as a projectile), never drops as an item. In a block it sticks for a moment and then
 * shatters into sparks. With Ricochet Rivets it bounces off a hit monster to the next one (up to its bounce count),
 * each bounce dealing a share of the hit before it. Armor Pierce is carried as {@link #pierce()} and read by the damage
 * hook; Thermal Edge sets hit targets on fire. The Null Lance fires the same entity as a violet null bolt
 * ({@link #isNullBolt()}). Rendered by {@code gear.client.RivetRenderer}.
 */
public class RivetEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(RivetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> NULL_BOLT = SynchedEntityData.defineId(RivetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions TRACER = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.35F), 0.7F);
    private static final DustParticleOptions NULL_TRACER = new DustParticleOptions(new Vector3f(0.69F, 0.49F, 1.0F), 0.8F);
    private static final double TRAIL_GAP = 1.5;
    /** Client: ticks over which the drawn rivet closes from the gun's muzzle onto its true flight line. */
    public static final float MUZZLE_TICKS = 2.0F;

    private float damage = 8.0F;
    private float pierce;
    private int bounces;
    private int fireSeconds;
    private int bouncesDone;
    private int stuckTicks;
    private boolean clientStuck;
    /** Server: a ricochet found during this tick's hit; applied after the tick's move (see {@link #tick}). */
    @Nullable
    private Entity bounceFrom;
    @Nullable
    private LivingEntity bounceTo;
    /** Client: where the tracer started (see {@link #trail}). */
    @Nullable
    private Vec3 trailStart;
    /**
     * Client: from where the rivet appeared (the shooter's eyes, where the server fires it) to the gun's muzzle. The
     * rivet and its tracer are drawn this much off at first, closing onto the true flight line over
     * {@link #MUZZLE_TICKS}, so a shot leaves the gun while it still flies (and hits) along the crosshair.
     */
    private float muzzleX, muzzleY, muzzleZ;
    private boolean fromMuzzle;
    /** Entity ids already hit by this rivet (a ricochet never comes back). */
    private final IntList hit = new IntArrayList();

    public RivetEntity(EntityType<? extends RivetEntity> type, Level level) {
        super(type, level);
    }

    public RivetEntity(Level level, LivingEntity shooter, float damage, float pierce, int bounces) {
        super(GearEntities.RIVET.get(), shooter, level);
        this.damage = damage;
        this.pierce = pierce;
        this.bounces = bounces;
    }

    public float damage() {
        return damage;
    }

    /** Thermal Edge: seconds a hit sets the target on fire (0 = none). */
    public void setFireSeconds(int seconds) {
        this.fireSeconds = seconds;
    }

    /** Share of the armor reduction this rivet ignores (Armor Pierce). */
    public float pierce() {
        return pierce;
    }

    public int bounces() {
        return bounces;
    }

    public int bouncesDone() {
        return bouncesDone;
    }

    /** Fired by the Null Lance: drawn and traced violet. */
    public boolean isNullBolt() {
        return entityData.get(NULL_BOLT);
    }

    public void setNullBolt() {
        entityData.set(NULL_BOLT, true);
    }

    /** Spark color of this projectile: ember for a rivet, violet for a null bolt. */
    private int sparkColor() {
        return isNullBolt() ? com.arno.robotica.core.particle.CoreParticles.WARP_VIOLET : com.arno.robotica.core.particle.CoreParticles.EMBER;
    }

    public boolean isStuck() {
        return entityData.get(STUCK) || clientStuck;
    }

    /** Client: draw this rivet from the gun's muzzle, {@code (x, y, z)} away from where it appeared. */
    public void setMuzzleOffset(double x, double y, double z) {
        muzzleX = (float) x;
        muzzleY = (float) y;
        muzzleZ = (float) z;
        fromMuzzle = true;
    }

    public boolean fromMuzzle() {
        return fromMuzzle;
    }

    /** Client: share of the muzzle offset still drawn {@code age} ticks after the rivet appeared (0 once stuck). */
    public float muzzleShare(float age) {
        if (!fromMuzzle || isStuck()) return 0.0F;
        return Math.max(0.0F, 1.0F - age / MUZZLE_TICKS);
    }

    public float muzzleX() {
        return muzzleX;
    }

    public float muzzleY() {
        return muzzleY;
    }

    public float muzzleZ() {
        return muzzleZ;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STUCK, false);
        builder.define(NULL_BOLT, false);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.01;
    }

    @Override
    public void tick() {
        if (isStuck()) {
            baseTick();
            setDeltaMovement(Vec3.ZERO);
            stuckTicks++;
            if (level().isClientSide) {
                if (random.nextInt(8) == 0) level().addParticle(com.arno.robotica.core.particle.CoreParticles.zap(com.arno.robotica.core.particle.CoreParticles.ELECTRIC, 0.6F), getX(), getY(), getZ(), 0, 0, 0);
            } else if (stuckTicks >= GearConfig.rivetStickTicks()) {
                shatter();
            }
            return;
        }
        double bx = getX(), by = getY(), bz = getZ();
        super.tick();
        if (bounceTo != null && level() instanceof ServerLevel server) {
            // The projectile tick already moved on along the old line; restart from the monster that was hit.
            bounce(server, bounceFrom, bounceTo);
            bounceFrom = null;
            bounceTo = null;
        }
        if (level().isClientSide) {
            trail(bx, by, bz);
        } else if (tickCount > GearConfig.rivetFlightTicks()) {
            discard();
        }
    }

    /**
     * Client: a bright dotted tracer from last tick's position (ax, ay, az) to this one, a spark now and then. The first
     * {@link #TRAIL_GAP} blocks from where the rivet appeared stay clear, so the tracer never starts in the shooter's
     * face; while the rivet closes from the muzzle onto its flight line the tracer follows the drawn path.
     */
    private void trail(double ax, double ay, double az) {
        if (trailStart == null) trailStart = new Vec3(ax, ay, az);
        double dx = getX() - ax, dy = getY() - ay, dz = getZ() - az;
        int steps = Math.min(12, Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.35)));
        DustParticleOptions tracer = isNullBolt() ? NULL_TRACER : TRACER;
        for (int i = 0; i < steps; i++) {
            double f = i / (double) steps;
            double x = ax + dx * f, y = ay + dy * f, z = az + dz * f;
            if (trailStart.distanceToSqr(x, y, z) < TRAIL_GAP * TRAIL_GAP) continue;
            float share = muzzleShare(tickCount - 1 + (float) f);
            level().addParticle(tracer, x + muzzleX * share, y + muzzleY * share, z + muzzleZ * share, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) {
            float share = muzzleShare(tickCount);
            level().addParticle(com.arno.robotica.core.particle.CoreParticles.spark(sparkColor(), 0.7F),
                    getX() + muzzleX * share, getY() + muzzleY * share, getZ() + muzzleZ * share, 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !hit.contains(target.getId()) && target != getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel level)) return;
        Entity target = result.getEntity();
        hit.add(target.getId());
        boolean hurt = target.hurt(damageSource(), damage);
        if (hurt && fireSeconds > 0) target.igniteForSeconds(fireSeconds);
        Vec3 at = result.getLocation();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.3);
        level.sendParticles(com.arno.robotica.core.particle.CoreParticles.spark(sparkColor(), 1.0F), at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
        level.sendParticles(com.arno.robotica.core.particle.CoreParticles.shard(com.arno.robotica.core.particle.CoreParticles.STEEL, 1.0F), at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.6F, 1.5F + random.nextFloat() * 0.2F);
        if (hurt && bouncesDone < bounces) {
            LivingEntity next = ricochetTarget(level, target);
            if (next != null) {
                bounceFrom = target;
                bounceTo = next;
                return;
            }
        }
        discard();
    }

    /** Sends the rivet from the monster it just hit towards {@code next}: the same rivet flies on, weaker. */
    private void bounce(ServerLevel level, Entity from, LivingEntity next) {
        bouncesDone++;
        damage *= GearConfig.ricochetDamage();
        Vec3 start = from.getBoundingBox().getCenter();
        Vec3 dir = next.getBoundingBox().getCenter().subtract(start).normalize();
        setPos(start.x, start.y, start.z);
        shoot(dir.x, dir.y, dir.z, GearConfig.rivetSpeed(), 0.0F);
        hasImpulse = true;
        level.sendParticles(com.arno.robotica.core.particle.CoreParticles.spark(com.arno.robotica.core.particle.CoreParticles.ELECTRIC, 1.0F), start.x, start.y, start.z, 8, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(com.arno.robotica.core.particle.CoreParticles.flash(com.arno.robotica.core.particle.CoreParticles.ELECTRIC, 1.0F), start.x, start.y, start.z, 1, 0, 0, 0, 0);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.9F + random.nextFloat() * 0.1F);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.6F);
    }

    /** Nearest living monster in ricochet range that this rivet has not hit and can see, or null. */
    @Nullable
    public LivingEntity ricochetTarget(ServerLevel level, Entity from) {
        double range = GearConfig.ricochetRange();
        Vec3 c = from.getBoundingBox().getCenter();
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                        e -> e != from && e != getOwner() && e.isAlive() && e instanceof Enemy && !hit.contains(e.getId())
                                && e.distanceToSqr(c) <= range * range && canSee(level, c, e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(c))).orElse(null);
    }

    private boolean canSee(ServerLevel level, Vec3 from, Entity e) {
        Vec3 to = e.getBoundingBox().getCenter();
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Vec3 dir = getDeltaMovement().normalize();
        Vec3 at = result.getLocation().subtract(dir.scale(0.05));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel level)) {
            clientStuck = true;
            return;
        }
        entityData.set(STUCK, true);
        level.sendParticles(com.arno.robotica.core.particle.CoreParticles.spark(sparkColor(), 1.0F), at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.25);
        level.sendParticles(com.arno.robotica.core.particle.CoreParticles.shard(com.arno.robotica.core.particle.CoreParticles.STEEL, 0.8F), at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.7F, 1.4F + random.nextFloat() * 0.3F);
    }

    /** Stuck too long: bursts into sparks and steel shards and is gone (never an item). */
    private void shatter() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(com.arno.robotica.core.particle.CoreParticles.shard(com.arno.robotica.core.particle.CoreParticles.STEEL, 1.0F), getX(), getY(), getZ(), 6, 0.05, 0.05, 0.05, 0.1);
            level.sendParticles(com.arno.robotica.core.particle.CoreParticles.spark(sparkColor(), 0.8F), getX(), getY(), getZ(), 4, 0.05, 0.05, 0.05, 0.15);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.4F, 1.7F);
        }
        discard();
    }

    private DamageSource damageSource() {
        var type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW);
        Entity owner = getOwner();
        return new DamageSource(type, this, owner == null ? this : owner);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putFloat("pierce", pierce);
        tag.putInt("bounces", bounces);
        tag.putInt("bouncesDone", bouncesDone);
        tag.putBoolean("stuck", entityData.get(STUCK));
        tag.putBoolean("nullBolt", isNullBolt());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        pierce = tag.getFloat("pierce");
        bounces = tag.getInt("bounces");
        bouncesDone = tag.getInt("bouncesDone");
        entityData.set(STUCK, tag.getBoolean("stuck"));
        entityData.set(NULL_BOLT, tag.getBoolean("nullBolt"));
    }
}
