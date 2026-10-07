package com.arno.robotica.gear.entity;

import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearEntities;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * hook. Rendered by {@code gear.client.RivetRenderer}.
 */
public class RivetEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(RivetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions TRACER = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.35F), 0.7F);

    private float damage = 8.0F;
    private float pierce;
    private int bounces;
    private int bouncesDone;
    private int stuckTicks;
    private boolean clientStuck;
    /** Server: a ricochet found during this tick's hit; applied after the tick's move (see {@link #tick}). */
    @Nullable
    private Entity bounceFrom;
    @Nullable
    private LivingEntity bounceTo;
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

    public boolean isStuck() {
        return entityData.get(STUCK) || clientStuck;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STUCK, false);
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
                if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0.02, 0);
            } else if (stuckTicks >= GearConfig.rivetStickTicks()) {
                shatter();
            }
            return;
        }
        Vec3 before = position();
        super.tick();
        if (bounceTo != null && level() instanceof ServerLevel server) {
            // The projectile tick already moved on along the old line; restart from the monster that was hit.
            bounce(server, bounceFrom, bounceTo);
            bounceFrom = null;
            bounceTo = null;
        }
        if (level().isClientSide) {
            trail(before, position());
        } else if (tickCount > GearConfig.rivetFlightTicks()) {
            discard();
        }
    }

    /** Client: a bright dotted tracer between last and this tick's position, a spark now and then. */
    private void trail(Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int steps = Math.min(12, Math.max(1, (int) (d.length() / 0.35)));
        for (int i = 0; i < steps; i++) {
            Vec3 p = a.add(d.scale(i / (double) steps));
            level().addParticle(TRACER, p.x, p.y, p.z, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 0, 0, 0);
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
        Vec3 at = result.getLocation();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.3);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
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
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, start.x, start.y, start.z, 14, 0.2, 0.2, 0.2, 0.35);
        level.sendParticles(ParticleTypes.FLASH, start.x, start.y, start.z, 1, 0, 0, 0, 0);
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
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.25);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.7F, 1.4F + random.nextFloat() * 0.3F);
    }

    /** Stuck too long: bursts into sparks and steel shards and is gone (never an item). */
    private void shatter() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_NUGGET)), getX(), getY(), getZ(),
                    6, 0.05, 0.05, 0.05, 0.08);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 5, 0.05, 0.05, 0.05, 0.15);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        pierce = tag.getFloat("pierce");
        bounces = tag.getInt("bounces");
        bouncesDone = tag.getInt("bouncesDone");
        entityData.set(STUCK, tag.getBoolean("stuck"));
    }
}
