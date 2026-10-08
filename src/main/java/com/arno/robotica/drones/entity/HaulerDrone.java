package com.arno.robotica.drones.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.RoboticaBoss;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.item.DroneItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoublePredicate;

/**
 * Hauler Drone: carries one mob hanging under it and follows its owner. Right-click a mob with the drone item to deploy it
 * onto that mob; right-click the drone (or the mob) with an empty hand to set the mob down. The mob stays the real entity,
 * riding the drone as a passenger (saved with it), with its AI switched off while carried. Its previous no-AI flag lives in
 * the mob's own persistent data, so any dismount restores it (see {@link HaulerEvents}).
 */
public class HaulerDrone extends DroneBase {
    public static final TagKey<EntityType<?>> BLACKLIST = TagKey.create(Registries.ENTITY_TYPE, Robotica.id("hauler_blacklist"));
    /** Key in the carried mob's persistent data: its no-AI flag from before the capture. */
    public static final String PREV_NO_AI = "robotica_hauler_prev_noai";
    /** Largest mob (bounding box) per tier: Mk1 up to a horse or villager, Mk2 up to an Iron Golem or Ravager. */
    public static final float MK1_WIDTH = 1.5F, MK1_HEIGHT = 2.0F, MK2_WIDTH = 2.0F, MK2_HEIGHT = 3.0F;
    /** Beyond this distance (or in another dimension) the drone hovers in place until the owner comes back. */
    public static final double LEASH_RANGE = 64.0;
    /** Cable length: gap between the drone's feet and the top of the carried mob. Shorter where there is no room, then let out. */
    public static final float HANG_GAP = 0.85F, MIN_GAP = 0.05F;
    private static final float GAP_STEP = 0.03F;
    private static final EntityDataAccessor<Float> DATA_GAP = SynchedEntityData.defineId(HaulerDrone.class, EntityDataSerializers.FLOAT);

    private boolean lowNotified;
    /** Ticks spent carrying with an empty buffer; the drone hovers and looks for a safe spot every second. */
    private int emptyTicks;
    /** Ticks spent carrying since the drone was created or loaded (tests, stats). */
    public int ticksCarried;

    // Client side animation state, eased every tick and lerped with the partial tick by the model.
    /** Claw: 0 closed, 1 open. Winch: 0 reeled in, 1 lowered onto the mob. */
    public float claw = 1.0F, clawO = 1.0F, winch, winchO;
    /** Body tilt from velocity (radians, pitch and roll). */
    public float tiltX, tiltXO, tiltZ, tiltZO;
    /** Rotor angle and its spin speed. */
    public float rotor, rotorO, rotorSpeed = 1.6F;

    public HaulerDrone(EntityType<? extends HaulerDrone> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GAP, HANG_GAP);
    }

    /** Current cable length in blocks. */
    public float gap() {
        return entityData.get(DATA_GAP);
    }

    private void setGap(float gap) {
        entityData.set(DATA_GAP, Mth.clamp(gap, MIN_GAP, HANG_GAP));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().expandTowards(0.0, -gap() - 0.5, 0.0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    @Override
    public DronesRegistry.Kind kind() {
        return DronesRegistry.Kind.HAULER;
    }

    @Override
    public Item baseItem() {
        return tier() >= 2 ? DronesRegistry.HAULER_DRONE_MK2.get() : DronesRegistry.HAULER_DRONE.get();
    }

    @Override
    protected int maxHealthFor(int tier) {
        return DronesConfig.haulerHealth(tier);
    }

    @Override
    public int energyCapacityFor(int tier) {
        return DronesConfig.haulerBuffer(tier);
    }

    /** FE per tick while a mob hangs under the drone (Mk2 doubles it). */
    public int fePerTick() {
        return scaled(DronesConfig.haulerFePerTick() * (tier() >= 2 ? 2 : 1));
    }

    public static int fePerTick(int tier) {
        return scaled(DronesConfig.haulerFePerTick() * (tier >= 2 ? 2 : 1));
    }

    private double flySpeed() {
        return tier() >= 2 ? 1.2 * DronesConfig.mk2Speed() : 1.2;
    }

    @Nullable
    public Mob cargo() {
        return getFirstPassenger() instanceof Mob m ? m : null;
    }

    public boolean isCarrying() {
        return cargo() != null;
    }

    // ---------------------------------------------------------------- what may be carried

    /** Mobs no hauler takes: players, bosses, blacklisted types, drones, other players' pets and Ranchers, and mobs that ride or are ridden. */
    public static boolean isForbidden(Entity target, @Nullable Player player) {
        if (!(target instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) return true;
        EntityType<?> type = mob.getType();
        if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.ELDER_GUARDIAN || type == EntityType.WARDEN
                || type.is(Tags.EntityTypes.BOSSES) || type.is(BLACKLIST) || mob instanceof RoboticaBoss || mob instanceof DroneBase) {
            return true;
        }
        if (mob.isPassenger() || mob.isVehicle()) return true;
        if (mob instanceof OwnableEntity pet && pet.getOwnerUUID() != null && (player == null || !pet.getOwnerUUID().equals(player.getUUID()))) {
            return true;
        }
        if (mob instanceof com.arno.robotica.automation.rancher.Rancher rancher && (player == null || !rancher.canInteract(player))) return true;
        return false;
    }

    /** Why a hauler of this tier may not take the mob, or null when it may. */
    @Nullable
    public static Component refusal(Entity target, @Nullable Player player, int tier) {
        if (isForbidden(target, player)) return msg("hauler.cant");
        boolean mk2 = tier >= 2;
        if (target.getBbWidth() > (mk2 ? MK2_WIDTH : MK1_WIDTH) + 1.0E-3 || target.getBbHeight() > (mk2 ? MK2_HEIGHT : MK1_HEIGHT) + 1.0E-3) {
            return msg(mk2 ? "hauler.too_big" : "hauler.too_big_mk1");
        }
        if (!mk2 && target instanceof Enemy) return msg("hauler.hostile");
        return null;
    }

    /**
     * Deploys the drone item onto the mob: checks everything first, then spawns the drone above it, hangs the mob under it and
     * takes the item. Returns true when the mob was taken; otherwise the player gets the reason and keeps the item.
     */
    public static boolean capture(ServerPlayer player, ItemStack stack, Mob target) {
        if (!(stack.getItem() instanceof DroneItem item) || item.kind() != DronesRegistry.Kind.HAULER) return false;
        Component no = refusal(target, player, item.tier());
        if (no == null && ItemEnergy.get(stack) < fePerTick(item.tier()) * 20) no = msg("hauler.no_energy");
        ServerLevel level = player.serverLevel();
        HaulerDrone drone = no == null ? DronesRegistry.HAULER_DRONE_ENTITY.get().create(level) : null;
        if (no == null && drone == null) no = msg("hauler.cant");
        if (drone != null) {
            // longest cable that fits above the mob; it is let out later where there is room
            float gap = longestGap(g -> {
                drone.moveTo(target.getX(), target.getY() + target.getBbHeight() + g, target.getZ(), player.getYRot(), 0.0F);
                return level.noCollision(drone, drone.getBoundingBox());
            });
            if (gap < 0.0F) no = msg("hauler.no_room");
        }
        if (no != null) {
            refuse(player, target, no);
            return false;
        }
        drone.initFromStack(stack, player);
        level.addFreshEntity(drone);
        if (!drone.grab(target)) {
            drone.discard();
            refuse(player, target, msg("hauler.cant"));
            return false;
        }
        CoreSounds.play(drone, CoreSounds.HAULER_GRAB, SoundSource.NEUTRAL, 0.9F, 1.0F);
        CoreSounds.play(drone, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.5F, 1.2F);
        grabParticles(level, drone, target);
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return true;
    }

    private static void refuse(ServerPlayer player, Mob target, Component why) {
        player.displayClientMessage(why, true);
        CoreSounds.play(target.level(), target.getX(), target.getY() + target.getBbHeight(), target.getZ(), CoreSounds.ROBOT_ERROR,
                SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    /** Sparks around the mob and the claw, a puff at the claw. */
    private static void grabParticles(ServerLevel level, HaulerDrone drone, Mob mob) {
        double w = mob.getBbWidth() * 0.5, h = mob.getBbHeight();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + h * 0.5, mob.getZ(), 14, w, h * 0.4, w, 0.08);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, drone.getX(), drone.getY(), drone.getZ(), 8, 0.2, 0.05, 0.2, 0.05);
        level.sendParticles(ParticleTypes.CLOUD, drone.getX(), drone.getY() - 0.05, drone.getZ(), 4, 0.15, 0.02, 0.15, 0.01);
    }

    /** Longest cable from HANG_GAP down to MIN_GAP that fits, or -1 when none does. */
    private static float longestGap(DoublePredicate fits) {
        for (float gap = HANG_GAP; ; gap -= 0.15F) {
            float g = Math.max(MIN_GAP, gap);
            if (fits.test(g)) return g;
            if (g <= MIN_GAP) return -1.0F;
        }
    }

    /**
     * Hangs the mob under this drone on the longest cable that fits and switches its AI off. Returns false when there is no
     * room below or the mob could not be mounted.
     */
    public boolean grab(Mob mob) {
        if (isCarrying() || level().isClientSide) return false;
        if (mob.isSleeping()) mob.stopSleeping();
        float gap = longestGap(g -> level().noCollision(mob,
                mob.getDimensions(mob.getPose()).makeBoundingBox(new Vec3(getX(), getY() - mob.getBbHeight() - g, getZ()))));
        if (gap < 0.0F) return false;
        setGap(gap);
        if (mob.isLeashed()) mob.dropLeash(true, true);
        boolean prevNoAi = mob.isNoAi();
        if (!mob.startRiding(this, true) || mob.getVehicle() != this) return false;
        mob.getPersistentData().putBoolean(PREV_NO_AI, prevNoAi);
        mob.setNoAi(true);
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.fallDistance = 0.0F;
        mob.setPersistenceRequired();
        setActive(true);
        return true;
    }

    /** Sets the carried mob down on safe ground below and gives its AI back. Returns false when nothing was released. */
    public boolean releaseCargo() {
        Mob mob = cargo();
        if (mob == null) return false;
        mob.stopRiding();
        if (mob.getVehicle() == this) return false;
        restoreAi(mob);
        setActive(false);
        setGap(MIN_GAP);
        CoreSounds.play(this, CoreSounds.HAULER_RELEASE, SoundSource.NEUTRAL, 0.8F, 1.0F);
        if (level() instanceof ServerLevel sl) {
            double w = mob.getBbWidth() * 0.5;
            sl.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.1, mob.getZ(), 10, w, 0.05, w, 0.02);
            sl.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.05, mob.getZ(), 4, w * 0.6, 0.02, w * 0.6, 0.01);
        }
        return true;
    }

    /** Puts back the mob's no-AI flag from before the capture (once) and clears its fall. */
    public static void restoreAi(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(PREV_NO_AI)) {
            mob.setNoAi(data.getBoolean(PREV_NO_AI));
            data.remove(PREV_NO_AI);
        }
        mob.fallDistance = 0.0F;
    }

    /**
     * Highest safe standing spot for the mob below the drone: the column under it first, then the columns around it, up to 64
     * blocks down. Falls back to the owner's feet, then to where the mob hangs.
     */
    public Vec3 dropSpot(LivingEntity mob) {
        Vec3 safe = safeSpot(mob);
        if (safe != null) return safe;
        ServerPlayer o = ownerHere();
        if (o != null && o.distanceToSqr(this) < LEASH_RANGE * LEASH_RANGE) return o.position();
        return mob.position();
    }

    /** Highest safe standing spot in the columns under the drone (5x5, up to 64 down), or null when there is none. */
    @Nullable
    public Vec3 safeSpot(LivingEntity mob) {
        Level lvl = level();
        int top = (int) Math.floor(getY());
        for (int r = 0; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Vec3 spot = scanColumn(lvl, mob, BlockPos.containing(getX() + dx, top, getZ() + dz));
                    if (spot != null) return spot;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 scanColumn(Level lvl, LivingEntity mob, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < 64 && pos.getY() > lvl.getMinBuildHeight(); i++, pos.move(0, -1, 0)) {
            if (!lvl.isLoaded(pos)) return null;
            if (lvl.getFluidState(pos).is(FluidTags.LAVA)) return null;
            if (lvl.getFluidState(pos.below()).is(FluidTags.WATER) && lvl.getBlockState(pos).getCollisionShape(lvl, pos).isEmpty()) {
                Vec3 at = Vec3.atBottomCenterOf(pos).subtract(0, 0.4, 0);
                return fits(lvl, mob, at) ? at : null;
            }
            if (mob.getType().isBlockDangerous(lvl.getBlockState(pos))) continue;
            double floor = lvl.getBlockFloorHeight(DismountHelper.nonClimbableShape(lvl, pos), () -> DismountHelper.nonClimbableShape(lvl, pos.below()));
            if (!DismountHelper.isBlockFloorValid(floor)) continue;
            if (floor <= 0.0 && mob.getType().isBlockDangerous(lvl.getBlockState(pos.below()))) return null;
            Vec3 at = Vec3.upFromBottomCenterOf(pos, floor);
            if (fits(lvl, mob, at)) return at;
        }
        return null;
    }

    private static boolean fits(Level lvl, LivingEntity mob, Vec3 at) {
        AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(at);
        return DismountHelper.canDismountTo(lvl, mob, box) && lvl.getBlockStates(box).noneMatch(s -> s.getFluidState().is(FluidTags.LAVA));
    }

    // ---------------------------------------------------------------- riding

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        callback.accept(passenger, getX(), getY() - passenger.getBbHeight() - gap(), getZ());
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(getX(), getY() - passenger.getBbHeight() - gap(), getZ());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return dropSpot(passenger);
    }

    /** Only {@link #grab} (forced) and loading put a mob on the drone. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean dismountsUnderwater() {
        return false;
    }

    // ---------------------------------------------------------------- removal: the mob is always set down first

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) releaseCargo();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) releaseCargo();
        super.remove(reason);
    }

    @Override
    protected void onPickedUp() {
        releaseCargo();
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG) || held.is(Items.LEAD)) return InteractionResult.PASS;
        boolean energyItem = !held.isEmpty() && EnergyUtil.isEnergyItem(held) && !(held.getItem() instanceof DroneItem);
        boolean repair = !held.isEmpty() && (held.is(Tags.Items.INGOTS_IRON) || held.is(IRON_PLATES)) && getHealth() < getMaxHealth();
        if (!player.isShiftKeyDown() && !held.isEmpty() && !energyItem && !repair) return InteractionResult.PASS;
        return interact(player, hand, held, energyItem, repair);
    }

    /** Right-click on the drone or on the carried mob. */
    public InteractionResult interactFrom(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean energyItem = !held.isEmpty() && EnergyUtil.isEnergyItem(held) && !(held.getItem() instanceof DroneItem);
        return interact(player, hand, held, energyItem, false);
    }

    private InteractionResult interact(Player player, InteractionHand hand, ItemStack held, boolean energyItem, boolean repair) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!canInteract(player)) {
            sp.displayClientMessage(Component.translatable("message.robotica.drone.not_yours", currentOwnerName()), true);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            pickUp(sp);
            return InteractionResult.CONSUME;
        }
        if (repair) return super.mobInteract(player, hand);
        if (energyItem) {
            chargeFromItem(sp, held);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty()) return InteractionResult.CONSUME;
        if (!releaseCargo()) {
            sp.displayClientMessage(msg("hauler.status", energy, getEnergyCapacity()), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Drains a held FE item into the buffer (the hauler has no battery slot). */
    private void chargeFromItem(ServerPlayer player, ItemStack held) {
        IEnergyStorage storage = held.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canExtract()) return;
        int total = 0;
        for (int i = 0; i < 64 && energy < getEnergyCapacity(); i++) {
            int got = storage.extractEnergy(getEnergyCapacity() - energy, false);
            if (got <= 0) break;
            energy += got;
            total += got;
        }
        if (total > 0) {
            CoreSounds.play(this, CoreSounds.CHARGE_COMPLETE, SoundSource.NEUTRAL, 0.5F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.drone.charged", total), true);
        }
    }

    @Override
    protected void openGui(ServerPlayer player) {
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) animTick();
    }

    private void animTick() {
        boolean carrying = isVehicle();
        clawO = claw;
        winchO = winch;
        tiltXO = tiltX;
        tiltZO = tiltZ;
        rotorO = rotor;
        // lower the claw first, then close it; on release open first, then reel in
        winch = Mth.approach(winch, carrying ? 1.0F : (claw > 0.9F ? 0.0F : winch), 0.2F);
        claw = Mth.approach(claw, carrying && winch > 0.6F ? 0.0F : carrying ? claw : 1.0F, 0.25F);
        double dx = getX() - xo, dz = getZ() - zo;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double forward = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
        double side = dx * Mth.cos(yaw) + dz * Mth.sin(yaw);
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        float gain = carrying ? 1.6F : 1.0F;
        float ease = carrying ? 0.12F : 0.3F;
        float wantX = Mth.clamp((float) forward * 1.4F * gain, -0.35F, 0.35F);
        float wantZ = Mth.clamp((float) side * 1.2F * gain, -0.3F, 0.3F);
        tiltX += (wantX - tiltX) * ease;
        tiltZ += (wantZ - tiltZ) * ease;
        float wantSpin = 1.6F + (carrying ? 0.9F : 0.0F) + Math.min(speed * 4.0F, 1.0F);
        rotorSpeed += (wantSpin - rotorSpeed) * 0.15F;
        rotor += rotorSpeed;
        if (rotor > Mth.TWO_PI * 64.0F) {
            rotor -= Mth.TWO_PI * 64.0F;
            rotorO -= Mth.TWO_PI * 64.0F;
        }
    }

    @Override
    protected void droneTick(ServerLevel sl) {
        Mob mob = cargo();
        setActive(mob != null);
        if (mob != null) {
            if (!consume(fePerTick())) {
                // Out of FE: hover in place and set the mob down only where it can stand safely.
                if (++emptyTicks == 1) {
                    say(msg("hauler.empty"), true);
                    CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.8F, 1.0F);
                }
                if (emptyTicks % 20 == 1 && safeSpot(mob) != null) {
                    releaseCargo();
                    emptyTicks = 0;
                    mob = null;
                } else {
                    navigation.stop();
                    setDeltaMovement(getDeltaMovement().scale(0.5));
                    return;
                }
            } else {
                emptyTicks = 0;
                ticksCarried++;
                if (gap() < HANG_GAP) {
                    float next = Math.min(HANG_GAP, gap() + GAP_STEP);
                    AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(new Vec3(getX(), getY() - mob.getBbHeight() - next, getZ()));
                    if (sl.noCollision(mob, box)) setGap(next);
                }
                if ((tickCount + getId()) % 40 == 0) CoreSounds.play(this, CoreSounds.HAULER_WINCH, SoundSource.NEUTRAL, 0.6F, 1.0F);
                if (tickCount % 8 == 0) {
                    sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() - 0.05, getZ(), 1, 0.08, 0.02, 0.08, 0.0);
                }
                if (!mob.isNoAi()) {
                    if (!mob.getPersistentData().contains(PREV_NO_AI)) mob.getPersistentData().putBoolean(PREV_NO_AI, false);
                    mob.setNoAi(true);
                }
                boolean low = isEnergyLow();
                if (low && !lowNotified) {
                    lowNotified = true;
                    say(msg("hauler.low"), true);
                } else if (!low) {
                    lowNotified = false;
                }
            }
        }
        faceFree();
        ServerPlayer o = ownerHere();
        if (o == null || o.distanceToSqr(this) > LEASH_RANGE * LEASH_RANGE || isHeld()) {
            navigation.stop();
            setDeltaMovement(getDeltaMovement().scale(0.5));
            return;
        }
        double up = Math.max(1.9, (mob != null ? mob.getBbHeight() + gap() - MIN_GAP : 0.0) + 1.0);
        followOwner(o, up, flySpeed());
    }

    /** Teleports only where the carried mob fits too. */
    @Override
    protected void teleportNear(Vec3 target) {
        Mob mob = cargo();
        if (mob == null) {
            super.teleportNear(target);
            return;
        }
        for (int i = 0; i < 12; i++) {
            double x = target.x + (i == 0 ? 0 : random.nextInt(5) - 2);
            double y = target.y + (i == 0 ? 0 : random.nextInt(3) - 1);
            double z = target.z + (i == 0 ? 0 : random.nextInt(5) - 2);
            AABB self = getBoundingBox().move(x - getX(), y - getY(), z - getZ());
            AABB hang = mob.getDimensions(mob.getPose()).makeBoundingBox(new Vec3(x, y - mob.getBbHeight() - gap(), z));
            if (level().noCollision(this, self) && level().noCollision(mob, hang)) {
                navigation.stop();
                setPos(x, y, z);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Gap", gap());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setGap(tag.contains("Gap") ? tag.getFloat("Gap") : MIN_GAP);
    }

    @Override
    protected void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
    }
}
