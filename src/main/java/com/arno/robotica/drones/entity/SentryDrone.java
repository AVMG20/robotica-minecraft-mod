package com.arno.robotica.drones.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.drones.net.SentryBoltPayload;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.menu.SentryDroneMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Sentry Drone: shoots energy bolts at hostile mobs. Modes: Guard (patrols a circle around the spot it was set to guard),
 * Follow (stays next to the owner) and Stay (holds position). Stance Aggressive attacks every hostile mob in range,
 * Defensive only those that target or hurt the owner or a drone. It never targets players, tamed animals, villagers,
 * other drones or anything in the robotica:sentry_ignore tag, and needs a clear line of sight.
 */
public class SentryDrone extends DroneBase {
    public static final TagKey<EntityType<?>> IGNORE = TagKey.create(Registries.ENTITY_TYPE, Robotica.id("sentry_ignore"));
    public static final ResourceKey<DamageType> ENERGY_BOLT = ResourceKey.create(Registries.DAMAGE_TYPE, Robotica.id("energy_bolt"));
    public static final int[] RADII = {8, 16, 24};

    /** What the drone does when nothing is in range. Ids are stored and synced, never reorder. */
    public enum Mode {
        GUARD, FOLLOW, STAY;

        public static Mode byId(int id) {
            Mode[] all = values();
            return all[Math.floorMod(id, all.length)];
        }
    }


    /** Speed, range and efficiency cards. */
    public final Upgrades upgrades = Upgrades.fixed(com.arno.robotica.core.upgrade.UpgradeRules.Fixed.SENTRY_DRONE, () -> {});

    private boolean aggressive = true;
    private int radiusIdx;
    private BlockPos guardCenter = BlockPos.ZERO;
    private int cooldown;
    private static final int SHOT_SOUND_GAP = 8;
    private int lastShotSound = -SHOT_SOUND_GAP;
    private boolean lowNotified;
    @Nullable
    private LivingEntity target;
    @Nullable
    private Vec3 patrolPoint;
    private int patrolTicks;
    /** Bolts fired since the drone was created or loaded (tests, stats). */
    public int shots;

    public SentryDrone(EntityType<? extends SentryDrone> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    public DronesRegistry.Kind kind() {
        return DronesRegistry.Kind.SENTRY;
    }

    @Override
    public Item baseItem() {
        return tier() >= 2 ? DronesRegistry.SENTRY_DRONE_MK2.get() : DronesRegistry.SENTRY_DRONE.get();
    }

    @Override
    protected int maxHealthFor(int tier) {
        return DronesConfig.sentryHealth(tier);
    }

    @Override
    public int energyCapacityFor(int tier) {
        return DronesConfig.sentryBuffer(tier);
    }

    // ---------------------------------------------------------------- state access

    public Mode mode() {
        return Mode.byId(entityData.get(DATA_MODE));
    }

    public void setMode(Mode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
        patrolPoint = null;
        if (mode == Mode.GUARD) guardCenter = blockPosition();
    }

    public boolean aggressive() {
        return aggressive;
    }

    public void toggleStance() {
        aggressive = !aggressive;
    }

    public int radiusIndex() {
        return radiusIdx;
    }

    public void cycleRadius() {
        radiusIdx = (radiusIdx + 1) % RADII.length;
    }

    public BlockPos guardCenter() {
        return guardCenter;
    }

    public void setGuardCenter(BlockPos pos) {
        guardCenter = pos.immutable();
    }

    /** Patrol radius in blocks: the selected size plus 4 per range card level. */
    public int patrolRadius() {
        return RADII[radiusIdx] + 4 * upgrades.level(UpgradeKind.RANGE);
    }

    /** Firing range in blocks. */
    public int range() {
        return DronesConfig.sentryRange() + 4 * upgrades.level(UpgradeKind.RANGE) + (tier() >= 2 ? 4 : 0);
    }

    public int cooldownTicks() {
        double base = DronesConfig.sentryCooldown(tier());
        return Math.max(3, (int) Math.round(base / Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED))));
    }

    public int fePerShot() {
        double m = Upgrades.energyMultiplier(upgrades.level(UpgradeKind.SPEED), upgrades.level(UpgradeKind.EFFICIENCY));
        return (int) Math.round(scaled(DronesConfig.sentryFePerShot()) * m);
    }

    public float damage() {
        return DronesConfig.sentryDamage(tier());
    }

    @Nullable
    public LivingEntity currentTarget() {
        return target;
    }

    @Override
    public void onDeployed(@Nullable Player placer, BlockPos pos) {
        guardCenter = pos.immutable();
        entityData.set(DATA_MODE, Mode.GUARD.ordinal());
    }

    // ---------------------------------------------------------------- targeting

    /** True when this entity is a legal target for the current stance. Players, pets, villagers, drones and ignored types never are. */
    public boolean canTarget(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isRemoved() || e.isSpectator()) return false;
        if (e instanceof Player || e instanceof DroneBase) return false;
        if (e.getType().is(IGNORE)) return false;
        if (e instanceof TamableAnimal tame && tame.isTame()) return false;
        if (!(e instanceof Enemy)) return false;
        if (e instanceof NeutralMob neutral && !targetsUs(neutral instanceof Mob m ? m : null)) return false;
        if (aggressive) return true;
        return e instanceof Mob mob && isThreat(mob);
    }

    private boolean targetsUs(@Nullable Mob mob) {
        if (mob == null) return false;
        LivingEntity t = mob.getTarget();
        if (t == null) return false;
        if (t == this || t instanceof DroneBase d && sameOwner(d)) return true;
        return owner != null && t.getUUID().equals(owner);
    }

    private boolean sameOwner(DroneBase d) {
        return owner != null && owner.equals(d.getOwnerUUID());
    }

    /** Defensive stance: the mob targets the owner or a drone of the owner, or recently hurt one of them. */
    private boolean isThreat(Mob mob) {
        if (targetsUs(mob)) return true;
        if (getLastHurtByMob() == mob && tickCount - getLastHurtByMobTimestamp() < 200) return true;
        ServerPlayer o = ownerHere();
        return o != null && o.getLastHurtByMob() == mob && o.tickCount - o.getLastHurtByMobTimestamp() < 200;
    }

    private void updateTarget(ServerLevel sl) {
        double range = range();
        if (target != null && (!canTarget(target) || distanceToSqr(target) > range * range || !hasLineOfSight(target))) target = null;
        if (target != null && tickCount % 20 != 0) return;
        AABB box = getBoundingBox().inflate(range);
        List<LivingEntity> candidates = sl.getEntitiesOfClass(LivingEntity.class, box, this::canTarget);
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : candidates) {
            double d = distanceToSqr(e);
            if (d <= bestDist && hasLineOfSight(e)) {
                best = e;
                bestDist = d;
            }
        }
        target = best;
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void droneTick(ServerLevel sl) {
        if (cooldown > 0) cooldown--;
        boolean low = isEnergyLow();
        if (low && !lowNotified) {
            lowNotified = true;
            say(msg("sentry.low"), true);
            CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.8F, 1.0F);
        } else if (lowNotified && (long) energy * 100L > (long) getEnergyCapacity() * DronesConfig.lowEnergyPercent() * 2L) {
            lowNotified = false;
        }
        if (low) {
            target = null;
        } else if (tickCount % 5 == 0) {
            updateTarget(sl);
        }
        setActive(target != null);
        LivingEntity t = target;
        if (t != null) {
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
            faceYaw((float) (Mth.atan2(aim.z - getZ(), aim.x - getX()) * Mth.RAD_TO_DEG) - 90.0F);
            if (cooldown <= 0 && distanceToSqr(t) <= (double) range() * range()) fire(sl, t, aim);
        } else {
            faceFree();
        }
        moveForMode(sl, low, t != null);
    }

    private void moveForMode(ServerLevel sl, boolean low, boolean engaged) {
        if (isHeld()) {
            navigation.stop();
            return;
        }
        switch (mode()) {
            case FOLLOW -> {
                ServerPlayer o = ownerHere();
                if (o == null) return;
                followOwner(o, 1.9, 1.2);
            }
            case GUARD -> {
                if (low || engaged) {
                    navigation.stop();
                    return;
                }
                tickPatrol(sl);
            }
            case STAY -> navigation.stop();
        }
    }

    private void tickPatrol(ServerLevel sl) {
        Vec3 center = Vec3.atCenterOf(guardCenter);
        double radius = patrolRadius();
        if (position().distanceToSqr(center) > (radius + 6) * (radius + 6)) {
            chase(center.add(0, 1.0, 0), 1.0, 128.0);
            return;
        }
        if (patrolPoint == null || --patrolTicks <= 0 || position().distanceToSqr(patrolPoint) < 1.5) {
            patrolPoint = pickPatrolPoint(sl, center, radius);
            patrolTicks = 80 + random.nextInt(80);
        }
        flyTo(patrolPoint, 0.7);
    }

    private Vec3 pickPatrolPoint(ServerLevel sl, Vec3 center, double radius) {
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble()) * radius;
            Vec3 p = new Vec3(center.x + Math.cos(angle) * r, center.y + 1.0 + random.nextDouble() * 3.0, center.z + Math.sin(angle) * r);
            BlockPos bp = BlockPos.containing(p);
            if (sl.isLoaded(bp) && sl.getBlockState(bp).isAir() && sl.getBlockState(bp.above()).isAir()) return p;
        }
        return center.add(0, 1.5, 0);
    }

    // ---------------------------------------------------------------- shooting

    private DamageSource boltSource(ServerLevel sl) {
        ServerPlayer o = ownerHere();
        Optional<net.minecraft.core.Holder.Reference<DamageType>> holder = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ENERGY_BOLT);
        if (holder.isEmpty()) return o != null ? sl.damageSources().mobProjectile(this, o) : sl.damageSources().mobAttack(this);
        // The owner is the causing entity, so kills count as theirs (experience, player-only drops).
        return new DamageSource(holder.get(), this, o);
    }

    private void fire(ServerLevel sl, LivingEntity t, Vec3 aim) {
        if (!hasLineOfSight(t)) return;
        if (!consume(fePerShot())) return;
        cooldown = cooldownTicks();
        shots++;
        Vec3 from = position().add(0, getBbHeight() * 0.45, 0);
        Vec3 dir = aim.subtract(from);
        double len = dir.length();
        if (len > 1.0E-3) from = from.add(dir.scale(0.45 / len));
        // one small packet; the clients draw the trail and the sparks
        SentryBoltPayload.send(this, from, aim);
        // fast cards fire every 3 ticks: the shot sound at most every 8
        if (tickCount - lastShotSound >= SHOT_SOUND_GAP || tickCount < lastShotSound) {
            lastShotSound = tickCount;
            CoreSounds.play(this, CoreSounds.RIVET_SHOT, SoundSource.NEUTRAL, 0.6F, 1.3F + random.nextFloat() * 0.2F);
        }
        t.hurt(boltSource(sl), damage());
        if (target != null && !target.isAlive()) target = null;
    }

    // ---------------------------------------------------------------- gui

    @Override
    protected void openGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SentryDroneMenu(id, inv, this), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Upgrades", upgrades.serializeNBT(registries));
        tag.put("Battery", battery.serializeNBT(registries));
        tag.putBoolean("Aggressive", aggressive);
        tag.putInt("Radius", radiusIdx);
    }

    @Override
    protected void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
        if (tag.contains("Battery")) battery.deserializeNBT(registries, tag.getCompound("Battery"));
        aggressive = !tag.contains("Aggressive") || tag.getBoolean("Aggressive");
        radiusIdx = Mth.clamp(tag.getInt("Radius"), 0, RADII.length - 1);
    }

    @Override
    protected void writeRuntime(CompoundTag tag) {
        tag.putInt("Mode", mode().ordinal());
        tag.putLong("Guard", guardCenter.asLong());
    }

    @Override
    protected void readRuntime(CompoundTag tag) {
        entityData.set(DATA_MODE, Mode.byId(tag.getInt("Mode")).ordinal());
        guardCenter = BlockPos.of(tag.getLong("Guard"));
    }
}
