package com.arno.robotica.drones.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.item.DroneItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared part of the Mining Drone and the Sentry Drone: a small flying robot with an owner (UUID), an internal FE
 * buffer that a battery slot refills, a tier (1 to 3; only the Mining Drone has a Mk3), hit points and the pick-up/deploy round trip through the item.
 * All logic runs on the server; the client only renders what the synced data says.
 */
public abstract class DroneBase extends PathfinderMob {
    protected static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(DroneBase.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(DroneBase.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(DroneBase.class, EntityDataSerializers.BOOLEAN);

    public static final TagKey<Item> IRON_PLATES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/iron"));

    @Nullable
    protected UUID owner;
    protected String ownerName = "";
    protected int energy;
    /** Ticks the drone keeps still because a player has its GUI open. */
    private int holdTicks;
    private int stuckTicks;
    private int pathCooldown;
    private int lineCooldown;
    private boolean lineClear;
    private float wantedYaw = Float.NaN;

    /** Any FE item (cell, Mainspring). Its charge refills the internal buffer. */
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return EnergyUtil.isEnergyItem(stack) && !(stack.getItem() instanceof DroneItem);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    protected DroneBase(EntityType<? extends DroneBase> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createBaseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.2)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // ---------------------------------------------------------------- type info

    public abstract DronesRegistry.Kind kind();

    public abstract Item baseItem();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TIER, 1);
        builder.define(DATA_MODE, 0);
        builder.define(DATA_ACTIVE, false);
    }

    /** Highest tier of any drone (the Mining Drone Mk3). */
    public static final int MAX_TIER = 3;

    public int tier() {
        return entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        entityData.set(DATA_TIER, Mth.clamp(tier, 1, MAX_TIER));
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(maxHealthFor(tier()));
    }

    protected abstract int maxHealthFor(int tier);

    public abstract int energyCapacityFor(int tier);

    public int getEnergyCapacity() {
        return energyCapacityFor(tier());
    }

    public boolean isActive() {
        return entityData.get(DATA_ACTIVE);
    }

    protected void setActive(boolean active) {
        if (isActive() != active) entityData.set(DATA_ACTIVE, active);
    }

    // ---------------------------------------------------------------- owner

    @Nullable
    public UUID getOwnerUUID() {
        return owner;
    }

    public void setOwner(@Nullable Player player) {
        if (player == null) {
            owner = null;
            ownerName = "";
        } else {
            owner = player.getUUID();
            ownerName = player.getGameProfile().getName();
        }
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        owner = uuid;
        if (uuid == null) ownerName = "";
    }

    @Nullable
    public ServerPlayer ownerPlayer() {
        if (owner == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(owner);
    }

    /** The owner when online in this dimension, else null. */
    @Nullable
    public ServerPlayer ownerHere() {
        ServerPlayer p = ownerPlayer();
        return p != null && p.level() == level() && p.isAlive() ? p : null;
    }

    /** Owner, same team as the owner, or an operator. A drone without owner (spawned by commands) obeys everyone. */
    public boolean canInteract(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        Team mine = player.getTeam();
        if (mine == null) return false;
        Team theirs = level().getScoreboard().getPlayersTeam(currentOwnerName());
        return theirs != null && mine.isAlliedTo(theirs);
    }

    public String currentOwnerName() {
        if (owner == null || !(level() instanceof ServerLevel sl)) return ownerName;
        MinecraftServer server = sl.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(owner);
            if (profile.isPresent()) return profile.get().getName();
        }
        return ownerName;
    }

    // ---------------------------------------------------------------- energy

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int value) {
        energy = Mth.clamp(value, 0, getEnergyCapacity());
    }

    /** Adds FE up to capacity, returns what was accepted. */
    public int receiveEnergy(int amount) {
        int accepted = Math.max(0, Math.min(amount, getEnergyCapacity() - energy));
        energy += accepted;
        return accepted;
    }

    /** Takes the FE if the buffer (after pulling from the battery slot) holds it. Returns false and takes nothing otherwise. */
    public boolean consume(int fe) {
        if (fe <= 0) return true;
        if (energy < fe) pullFromBattery(fe - energy);
        if (energy < fe) return false;
        energy -= fe;
        return true;
    }

    /** Moves up to {@code want} FE from the battery slot into the buffer. */
    protected int pullFromBattery(int want) {
        ItemStack cell = battery.getStackInSlot(0);
        if (cell.isEmpty()) return 0;
        IEnergyStorage storage = cell.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canExtract()) return 0;
        int moved = 0;
        for (int i = 0; i < 24 && moved < want && energy < getEnergyCapacity(); i++) {
            int got = storage.extractEnergy(Math.min(want - moved, getEnergyCapacity() - energy), false);
            if (got <= 0) break;
            energy += got;
            moved += got;
        }
        return moved;
    }

    /** FE capability of the drone itself (receive only), for chargers and cables of other mods. */
    public IEnergyStorage energyStorage() {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                int accepted = Math.max(0, Math.min(Math.min(toReceive, 4000), getEnergyCapacity() - energy));
                if (!simulate) energy += accepted;
                return accepted;
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return energy;
            }

            @Override
            public int getMaxEnergyStored() {
                return getEnergyCapacity();
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    public boolean isEnergyLow() {
        return (long) energy * 100L < (long) getEnergyCapacity() * DronesConfig.lowEnergyPercent();
    }

    /** Scaled base cost helper so every module reads the global multiplier the same way. */
    protected static int scaled(int base) {
        return CoreConfig.scaleEnergy(base);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG) || held.is(Items.LEAD)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!canInteract(player)) {
            sp.displayClientMessage(Component.translatable("message.robotica.drone.not_yours", currentOwnerName()), true);
            return InteractionResult.CONSUME;
        }
        holdTicks = 40;
        if (player.isShiftKeyDown()) {
            pickUp(sp);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty() && (held.is(Tags.Items.INGOTS_IRON) || held.is(IRON_PLATES)) && getHealth() < getMaxHealth()) {
            heal(DronesConfig.repairAmount());
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            playSound(SoundEvents.IRON_GOLEM_REPAIR, 0.8F, 1.0F + random.nextFloat() * 0.2F);
            sp.displayClientMessage(Component.translatable("message.robotica.drone.repaired", Math.round(getHealth()), Math.round(getMaxHealth())), true);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty() && EnergyUtil.isEnergyItem(held) && !(held.getItem() instanceof DroneItem)) {
            if (chargeFrom(sp, held)) return InteractionResult.CONSUME;
        }
        openGui(sp);
        return InteractionResult.CONSUME;
    }

    protected abstract void openGui(ServerPlayer player);

    /** Right-click with an FE item: an empty battery slot takes it, else the drone drains it directly. */
    private boolean chargeFrom(ServerPlayer player, ItemStack held) {
        if (battery.getStackInSlot(0).isEmpty() && battery.isItemValid(0, held)) {
            battery.setStackInSlot(0, held.copyWithCount(1));
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            CoreSounds.play(this, CoreSounds.UPGRADE_INSTALL, SoundSource.NEUTRAL, 0.6F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.drone.battery_in"), true);
            return true;
        }
        IEnergyStorage storage = held.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canExtract()) return false;
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
        return total > 0;
    }

    /** Keeps the drone still while a GUI is open; called by the menus every tick. */
    public void holdForGui() {
        holdTicks = Math.max(holdTicks, 4);
    }

    protected boolean isHeld() {
        return holdTicks > 0;
    }

    // ---------------------------------------------------------------- state <-> item

    /** Settings and contents that survive a pick-up (inventory, battery, options). Mode and job state are not part of it. */
    protected abstract void writeSettings(CompoundTag tag, HolderLookup.Provider registries);

    protected abstract void readSettings(CompoundTag tag, HolderLookup.Provider registries);

    /** Runtime state (mode, running job): saved with the entity, dropped when picked up. */
    protected void writeRuntime(CompoundTag tag) {
    }

    protected void readRuntime(CompoundTag tag) {
    }

    /** The item form: energy in the energy component, everything else in the drone state component. */
    public ItemStack toItemStack(boolean destroyed) {
        ItemStack stack = new ItemStack(baseItem());
        CompoundTag tag = new CompoundTag();
        writeSettings(tag, registryAccess());
        tag.putFloat("Health", destroyed ? Math.max(getHealth(), getMaxHealth() * 0.25F) : getHealth());
        stack.set(DronesRegistry.DRONE_STATE.get(), tag);
        ItemEnergy.set(stack, energy);
        if (hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, getCustomName());
        return stack;
    }

    /** Applies a drone item to this fresh entity (tier comes from the item class, the rest from its components). */
    public void initFromStack(ItemStack stack, @Nullable Player placer) {
        if (stack.getItem() instanceof DroneItem item) setTier(item.tier());
        CompoundTag tag = stack.getOrDefault(DronesRegistry.DRONE_STATE.get(), new CompoundTag());
        readSettings(tag, registryAccess());
        setEnergy(ItemEnergy.get(stack));
        setHealth(tag.contains("Health") ? Math.max(1.0F, Math.min(tag.getFloat("Health"), getMaxHealth())) : getMaxHealth());
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setCustomName(name);
        if (placer != null) setOwner(placer);
    }

    /** Turns the drone back into its item (energy, contents and settings kept) and hands it to the player. */
    public void pickUp(ServerPlayer player) {
        onPickedUp();
        ItemStack stack = toItemStack(false);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.7F, 1.2F);
        discard();
    }

    protected void onPickedUp() {
    }

    /** Called once after a drone item was deployed at {@code pos}. */
    public void onDeployed(@Nullable Player placer, BlockPos pos) {
    }

    /** A destroyed drone is never lost: it drops as an item with everything it carried. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        ItemStack stack = toItemStack(true);
        ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.2, getZ(), stack);
        drop.setDefaultPickUpDelay();
        drop.setDeltaMovement(random.nextGaussian() * 0.05, 0.2, random.nextGaussian() * 0.05);
        level.addFreshEntity(drop);
        ServerPlayer p = ownerPlayer();
        if (p != null) p.displayClientMessage(Component.translatable("message.robotica.drone.destroyed", getDisplayName()), true);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(baseItem());
    }

    // ---------------------------------------------------------------- messages

    /** Says something to the owner: always on the action bar, optionally in chat as well. */
    public void say(Component text, boolean chat) {
        ServerPlayer p = ownerHere();
        if (p == null) return;
        Component line = Component.translatable("message.robotica.drone.say", getDisplayName(), text);
        p.displayClientMessage(line, true);
        if (chat) p.sendSystemMessage(line);
    }

    // ---------------------------------------------------------------- flying

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance()) {
            if (isInWater()) {
                moveRelative(0.02F, travelVector);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8F));
            } else if (isInLava()) {
                moveRelative(0.02F, travelVector);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.5));
            } else {
                moveRelative(getSpeed(), travelVector);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91F));
            }
        }
        calculateEntityAnimation(false);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        // Players cannot knock a drone out of the world (nobody steals a drone by hitting it); mobs and explosions can hurt it.
        if (source.getEntity() instanceof Player || source.is(DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** The point the drone should face, or NaN (see {@link #faceYaw}). Server only. */
    protected void faceYaw(float yaw) {
        wantedYaw = yaw;
    }

    protected void faceFree() {
        wantedYaw = Float.NaN;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!Float.isNaN(wantedYaw)) {
            float cur = getYRot();
            float diff = Mth.wrapDegrees(wantedYaw - cur);
            float next = cur + Mth.clamp(diff, -25.0F, 25.0F);
            setYRot(next);
            yBodyRot = next;
            yHeadRot = next;
        }
        if (holdTicks > 0) holdTicks--;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 5 == 0 && energy < getEnergyCapacity()) pullFromBattery(getEnergyCapacity() - energy);
        if (level() instanceof ServerLevel sl) droneTick(sl);
    }

    /** Per tick behaviour on the server. */
    protected abstract void droneTick(ServerLevel level);

    /** Moves toward a point: straight when the way is clear (checked every few ticks), along a flying path otherwise. */
    protected void flyTo(Vec3 target, double speed) {
        if (position().distanceToSqr(target) < 0.04) return;
        if (--lineCooldown <= 0) {
            lineCooldown = 5;
            lineClear = clearLine(target);
        }
        if (lineClear) {
            navigation.stop();
            moveControl.setWantedPosition(target.x, target.y, target.z, speed);
        } else if (--pathCooldown <= 0 || navigation.isDone()) {
            pathCooldown = 10;
            navigation.moveTo(target.x, target.y, target.z, speed);
        }
    }

    /** A hover point beside and behind the player (relative to where they look), {@code up} blocks above their feet. */
    protected Vec3 followPoint(ServerPlayer o, double back, double side, double up) {
        Vec3 look = o.getLookAngle();
        Vec3 b = new Vec3(-look.x, 0, -look.z);
        b = b.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : b.normalize();
        Vec3 s = new Vec3(-b.z, 0, b.x);
        return o.position().add(b.scale(back)).add(s.scale(side)).add(0, up, 0);
    }

    /** Horizontal distance band in which a following drone stays put instead of repositioning. */
    private static final double FOLLOW_MIN = 1.5, FOLLOW_MAX = 4.5, FOLLOW_KEEP = 2.5;

    /**
     * Relaxed follow: the drone keeps whatever side of the owner it is on and only moves when it drifts out of a
     * comfortable ring (1.5 to 4.5 blocks) or the owner walks away. Turning around never sends it behind the player,
     * and while the owner looks at it, it holds still so it is easy to right-click.
     */
    protected void followOwner(ServerPlayer o, double up, double speed) {
        Vec3 anchor = o.position().add(0, up, 0);
        Vec3 rel = position().subtract(anchor);
        double horiz = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
        boolean comfortable = horiz >= FOLLOW_MIN && horiz <= FOLLOW_MAX && Math.abs(rel.y) < 2.5;
        if (comfortable || (isLookedAtBy(o) && horiz <= FOLLOW_MAX + 2.0)) {
            navigation.stop();
            setDeltaMovement(getDeltaMovement().scale(0.5));
            stuckTicks = 0;
            return;
        }
        Vec3 dir = horiz < 1.0E-3 ? followPoint(o, 1.0, 0.0, 0.0).subtract(o.position()) : new Vec3(rel.x, 0, rel.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        chase(anchor.add(dir.scale(FOLLOW_KEEP)), speed, 32.0);
    }

    /** True when the player's crosshair points roughly at this drone (within about 10 degrees, 8 blocks). */
    protected boolean isLookedAtBy(ServerPlayer o) {
        Vec3 to = getBoundingBox().getCenter().subtract(o.getEyePosition());
        double d = to.length();
        if (d > 8.0 || d < 1.0E-3) return d < 1.0E-3;
        return o.getLookAngle().dot(to.scale(1.0 / d)) > 0.985;
    }

    /** Straight flight at a point inside a tunnel (no path finding). */
    protected void flyDirect(Vec3 target, double speed) {
        if (position().distanceToSqr(target) < 0.01) return;
        navigation.stop();
        moveControl.setWantedPosition(target.x, target.y, target.z, speed);
    }

    protected boolean clearLine(Vec3 target) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.add(0, getBbHeight() * 0.5, 0);
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /**
     * Keeps a follower close: flies to the point, and when it cannot get there (stuck, or too far away) teleports next to
     * it like a pet would. Returns true when it has arrived.
     */
    protected boolean chase(Vec3 target, double speed, double teleportDistance) {
        double dist = position().distanceTo(target);
        if (dist < 1.2) {
            stuckTicks = 0;
            return true;
        }
        if (dist > teleportDistance) {
            teleportNear(target);
            stuckTicks = 0;
            return false;
        }
        Vec3 before = position();
        flyTo(target, speed);
        if (before.distanceToSqr(position()) < 0.0004) {
            if (++stuckTicks > 100) {
                teleportNear(target);
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        return false;
    }

    protected void teleportNear(Vec3 target) {
        for (int i = 0; i < 12; i++) {
            double x = target.x + (i == 0 ? 0 : random.nextInt(5) - 2);
            double y = target.y + (i == 0 ? 0 : random.nextInt(3) - 1);
            double z = target.z + (i == 0 ? 0 : random.nextInt(5) - 2);
            if (level().noCollision(this, getBoundingBox().move(x - getX(), y - getY(), z - getZ()))) {
                navigation.stop();
                setPos(x, y, z);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- sound

    /**
     * True once every {@code period} ticks, at a phase of its own per drone (from its UUID), so a fleet that spawned or
     * loaded together does not beep, grind or whir in unison.
     */
    protected boolean every(int period) {
        return Math.floorMod(tickCount + getUUID().hashCode(), Math.max(1, period)) == 0;
    }

    /** Flying, following a path or busy with a job; a parked drone keeps its hum to itself. */
    protected boolean busy() {
        Vec3 v = getDeltaMovement();
        return isActive() || v.x * v.x + v.z * v.z + v.y * v.y > 0.0025 || !getNavigation().isDone();
    }

    @Override
    public void playAmbientSound() {
        if (busy()) super.playAmbientSound();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return CoreSounds.DRONE_BUZZ.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Tier", tier());
        tag.putInt("Energy", energy);
        writeSettings(tag, registryAccess());
        writeRuntime(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        setTier(tag.getInt("Tier"));
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        readSettings(tag, registryAccess());
        energy = Mth.clamp(tag.getInt("Energy"), 0, getEnergyCapacity());
        readRuntime(tag);
    }

    /** Message helper for subclasses: translation key under message.robotica. */
    protected static Component msg(String key, Object... args) {
        return Component.translatable("message.robotica." + key, args);
    }

    public static ResourceLocation id(String path) {
        return Robotica.id(path);
    }
}
