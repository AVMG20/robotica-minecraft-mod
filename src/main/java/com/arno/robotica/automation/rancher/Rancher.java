package com.arno.robotica.automation.rancher;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.progress.Milestones;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Rancher: a walking robot that runs a passive-mob farm around its home. It keeps every species of {@link Animal} in its
 * area at an even share of one population target (breeding with feed from storage, culling extra adults), shears sheep
 * and milks cows, and puts the loot into an inventory near home. Server side, throttled: one entity scan every
 * {@link #scanInterval()} ticks, one action at a time.
 */
public class Rancher extends PathfinderMob {
    public enum Status {
        WORKING, IDLE, NO_ENERGY, STORAGE_FULL, NO_FEED, NO_STORAGE;

        public String key() {
            return "gui.robotica.rancher.status." + name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Status byId(int id) {
            Status[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }

        boolean stalled() {
            return this != WORKING && this != IDLE;
        }
    }

    enum Kind { FEED, SHEAR, MILK, CULL }

    /** One walk-and-act job: the animal, an optional second animal to feed right after, and the item shown in hand. */
    static final class Job {
        final Kind kind;
        final Animal target;
        @Nullable
        final Animal partner;
        final ItemStack shown;
        int ticks;

        Job(Kind kind, Animal target, @Nullable Animal partner, ItemStack shown) {
            this.kind = kind;
            this.target = target;
            this.partner = partner;
            this.shown = shown;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(Rancher.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATUS = SynchedEntityData.defineId(Rancher.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(Rancher.class, EntityDataSerializers.BLOCK_POS);

    public static final int MIN_TARGET = 2;
    public static final int MAX_TARGET = 64;
    public static final int DEFAULT_TARGET = 8;
    /** Storage is any item handler block within this many blocks of home. */
    public static final int STORAGE_RANGE = 3;
    public static final int AREA_DOWN = 2;
    public static final int AREA_UP = 3;
    static final int MILK_COOLDOWN = 1200;
    static final int JOB_TIMEOUT = 240;
    static final int SKIP_TICKS = 600;
    private static final UUID NO_OWNER = UUID.fromString("7d3c1a52-4b0e-4c1f-9a77-5c2e8f61b0d3");

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private int energy;
    private int target = DEFAULT_TARGET;
    private boolean shear = true;
    private boolean milk = true;
    private boolean homeSet;

    @Nullable
    private BlockPos storagePos;
    @Nullable
    private Job job;
    private int cooldown;
    private int scanIn = 10;
    private int holdTicks;
    private int lastAdults;
    private long lastWorking = -1000;
    @Nullable
    private Status lastStall;
    private long lastStallTick;
    private final Map<UUID, Long> milkedAt = new HashMap<>();
    private final Map<UUID, Long> skipUntil = new HashMap<>();

    /** Any FE item (cell, Mainspring). Its charge refills the internal buffer. */
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return EnergyUtil.isEnergyItem(stack) && !(stack.getItem() instanceof RancherItem);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public Rancher(EntityType<? extends Rancher> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TIER, 1);
        builder.define(DATA_STATUS, Status.IDLE.ordinal());
        builder.define(DATA_HOME, BlockPos.ZERO);
    }

    // ---------------------------------------------------------------- tier, numbers

    public int tier() {
        return entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        entityData.set(DATA_TIER, Mth.clamp(tier, 1, 2));
    }

    /** Area radius: Mk1 4 (9x9), Mk2 6 (13x13). */
    public int radius() {
        return tier() >= 2 ? 6 : 4;
    }

    public static int energyCapacityFor(int tier) {
        return tier >= 2 ? 1_600_000 : 400_000;
    }

    public int getEnergyCapacity() {
        return energyCapacityFor(tier());
    }

    int scanInterval() {
        return tier() >= 2 ? 20 : 30;
    }

    int actionCooldown() {
        return tier() >= 2 ? 20 : 40;
    }

    double speed() {
        return tier() >= 2 ? 1.3 : 1.0;
    }

    public static int actionFe() {
        return com.arno.robotica.core.CoreConfig.scaleEnergy(AutomationConfig.rancherFePerAction());
    }

    public static int cullFe() {
        return com.arno.robotica.core.CoreConfig.scaleEnergy(AutomationConfig.rancherFePerCull());
    }

    public Status status() {
        return Status.byId(entityData.get(DATA_STATUS));
    }

    private void setStatus(ServerLevel sl, Status next) {
        Status from = status();
        if (from == next) return;
        entityData.set(DATA_STATUS, next.ordinal());
        if (next == Status.WORKING) {
            Milestones.awardOwner(sl, blockPosition(), owner, Milestones.ROBOT_WORKING);
            if (tickCount - lastWorking > 600) CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.6F, 1.1F);
        } else if (next.stalled() && (next != lastStall || tickCount - lastStallTick > 1200)) {
            // once per kind of stall (again after a minute), so a robot that flips between work and a stall stays quiet
            lastStall = next;
            lastStallTick = tickCount;
            CoreSounds.play(this, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.6F, 1.0F);
            ServerPlayer p = Milestones.nearbyOwner(sl, blockPosition(), owner, 32);
            if (p != null) p.displayClientMessage(Component.translatable("message.robotica.rancher.stalled", getDisplayName(),
                    Component.translatable(next.key())), true);
        }
    }

    // ---------------------------------------------------------------- home and area

    public BlockPos home() {
        return entityData.get(DATA_HOME);
    }

    public void setHome(BlockPos pos) {
        entityData.set(DATA_HOME, pos.immutable());
        homeSet = true;
        storagePos = null;
    }

    /** The work area: (2r+1) square around home, from 2 below to 3 above it. */
    public AABB areaBox() {
        BlockPos h = home();
        int r = radius();
        return new AABB(h.getX() - r, h.getY() - AREA_DOWN, h.getZ() - r, h.getX() + r + 1, h.getY() + AREA_UP + 1, h.getZ() + r + 1);
    }

    // ---------------------------------------------------------------- settings

    public int target() {
        return target;
    }

    public void setTarget(int value) {
        target = Mth.clamp(value, MIN_TARGET, MAX_TARGET);
        scanIn = Math.min(scanIn, 5);
    }

    public boolean shearOn() {
        return shear;
    }

    public void setShear(boolean on) {
        shear = on;
        scanIn = Math.min(scanIn, 5);
    }

    public boolean milkOn() {
        return milk;
    }

    public void setMilk(boolean on) {
        milk = on;
        scanIn = Math.min(scanIn, 5);
    }

    /** Adults of every species in the area at the last scan. */
    public int lastAdults() {
        return lastAdults;
    }

    // ---------------------------------------------------------------- owner

    @Nullable
    public UUID getOwnerUUID() {
        return owner;
    }

    public void setOwner(@Nullable Player player) {
        owner = player == null ? null : player.getUUID();
        ownerName = player == null ? "" : player.getGameProfile().getName();
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        owner = uuid;
        if (uuid == null) ownerName = "";
    }

    /** Owner, same team as the owner, or an operator. A Rancher without owner obeys everyone. */
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
        var cache = server.getProfileCache();
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

    /** Takes the FE if the buffer (after pulling from the battery slot) holds it; takes nothing otherwise. */
    public boolean consume(int fe) {
        if (fe <= 0) return true;
        if (energy < fe) pullFromBattery(fe - energy);
        if (energy < fe) return false;
        energy -= fe;
        return true;
    }

    private boolean hasEnergy(int fe) {
        if (energy < fe) pullFromBattery(fe - energy);
        return energy >= fe;
    }

    private void pullFromBattery(int want) {
        ItemStack cell = battery.getStackInSlot(0);
        if (cell.isEmpty()) return;
        IEnergyStorage storage = cell.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canExtract()) return;
        int moved = 0;
        for (int i = 0; i < 24 && moved < want && energy < getEnergyCapacity(); i++) {
            int got = storage.extractEnergy(Math.min(want - moved, getEnergyCapacity() - energy), false);
            if (got <= 0) break;
            energy += got;
            moved += got;
        }
    }

    /** FE capability of the robot (receive only), for chargers and cables of other mods. */
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

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!canInteract(player)) {
            sp.displayClientMessage(Component.translatable("message.robotica.rancher.not_yours", currentOwnerName()), true);
            return InteractionResult.CONSUME;
        }
        holdTicks = 40;
        if (player.isShiftKeyDown()) {
            pickUp(sp);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty() && EnergyUtil.isEnergyItem(held) && !(held.getItem() instanceof RancherItem) && chargeFrom(sp, held)) {
            return InteractionResult.CONSUME;
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new RancherMenu(id, inv, this), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
        return InteractionResult.CONSUME;
    }

    /** Right-click with an FE item: an empty battery slot takes it, else the robot drains it directly. */
    private boolean chargeFrom(ServerPlayer player, ItemStack held) {
        if (battery.getStackInSlot(0).isEmpty() && battery.isItemValid(0, held)) {
            battery.setStackInSlot(0, held.copyWithCount(1));
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            CoreSounds.play(this, CoreSounds.UPGRADE_INSTALL, SoundSource.NEUTRAL, 0.6F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.rancher.battery_in"), true);
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
            player.displayClientMessage(Component.translatable("message.robotica.rancher.charged", total), true);
        }
        return total > 0;
    }

    /** Keeps the robot still while a GUI is open; called by the menu every tick. */
    public void holdForGui() {
        holdTicks = Math.max(holdTicks, 4);
    }

    // ---------------------------------------------------------------- item form

    /** Settings and battery: kept by pick-up and smithing. */
    void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Target", target);
        tag.putBoolean("Shear", shear);
        tag.putBoolean("Milk", milk);
        tag.put("Battery", battery.serializeNBT(registries));
    }

    void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        target = tag.contains("Target") ? Mth.clamp(tag.getInt("Target"), MIN_TARGET, MAX_TARGET) : DEFAULT_TARGET;
        shear = !tag.contains("Shear") || tag.getBoolean("Shear");
        milk = !tag.contains("Milk") || tag.getBoolean("Milk");
        if (tag.contains("Battery")) battery.deserializeNBT(registries, tag.getCompound("Battery"));
    }

    public ItemStack toItemStack(boolean destroyed) {
        ItemStack stack = new ItemStack(tier() >= 2 ? RancherContent.RANCHER_MK2.get() : RancherContent.RANCHER.get());
        CompoundTag tag = new CompoundTag();
        writeSettings(tag, registryAccess());
        tag.putFloat("Health", destroyed ? Math.max(getHealth(), getMaxHealth() * 0.25F) : getHealth());
        stack.set(RancherContent.STATE.get(), tag);
        ItemEnergy.set(stack, energy);
        if (hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, getCustomName());
        return stack;
    }

    public void initFromStack(ItemStack stack, @Nullable Player placer) {
        if (stack.getItem() instanceof RancherItem item) setTier(item.tier());
        CompoundTag tag = stack.getOrDefault(RancherContent.STATE.get(), new CompoundTag());
        readSettings(tag, registryAccess());
        setEnergy(ItemEnergy.get(stack));
        setHealth(tag.contains("Health") ? Math.max(1.0F, Math.min(tag.getFloat("Health"), getMaxHealth())) : getMaxHealth());
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setCustomName(name);
        if (placer != null) setOwner(placer);
    }

    public void pickUp(ServerPlayer player) {
        ItemStack stack = toItemStack(false);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.7F, 1.2F);
        discard();
    }

    /** A destroyed Rancher drops as its item with energy and settings; never its held tool. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.3, getZ(), toItemStack(true));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (p != null) p.displayClientMessage(Component.translatable("message.robotica.rancher.destroyed", getDisplayName()), true);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(tier() >= 2 ? RancherContent.RANCHER_MK2.get() : RancherContent.RANCHER.get());
    }

    // ---------------------------------------------------------------- body

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() instanceof Player || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)) {
            return false;
        }
        return super.hurt(source, amount);
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
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
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
    protected float getSoundVolume() {
        return 0.5F;
    }

    private void hold(ItemStack stack) {
        if (!ItemStack.isSameItemSameComponents(getMainHandItem(), stack)) setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(Math.min(1, stack.getCount())));
    }

    // ---------------------------------------------------------------- brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (!homeSet) setHome(blockPosition());
        if (tickCount % 5 == 0) {
            if (energy < getEnergyCapacity()) pullFromBattery(getEnergyCapacity() - energy);
            if (energy > 0) energy--;
        }
        if (status().stalled() && tickCount % 40 == 0) stallParticles(sl);
        if (holdTicks > 0) {
            holdTicks--;
            navigation.stop();
            return;
        }
        if (cooldown > 0) cooldown--;
        if (job != null) {
            runJob(sl);
            return;
        }
        if (cooldown > 0) return;
        if (--scanIn > 0) {
            if (tickCount % 20 == 0) walkHome();
            return;
        }
        scanIn = scanInterval();
        plan(sl);
        if (job == null) walkHome();
    }

    private void stallParticles(ServerLevel sl) {
        if (status() == Status.NO_ENERGY) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() + 0.1, getZ(), 6, 0.2, 0.1, 0.2, 0.01);
        } else {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    private void walkHome() {
        BlockPos h = home();
        Vec3 spot = new Vec3(h.getX() + 0.5, h.getY(), h.getZ() + 0.5);
        if (position().distanceToSqr(spot) > 2.25) {
            if (navigation.isDone()) navigation.moveTo(spot.x, spot.y, spot.z, speed());
        } else if (!getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private void startJob(Job next) {
        job = next;
        hold(next.shown);
        navigation.moveTo(next.target, speed());
    }

    private void endJob(int rest) {
        job = null;
        cooldown = rest;
        scanIn = 1;
    }

    private void skip(Animal a) {
        skipUntil.put(a.getUUID(), level().getGameTime() + SKIP_TICKS);
    }

    private boolean skipped(Animal a, long now) {
        Long until = skipUntil.get(a.getUUID());
        return until != null && until > now;
    }

    private void runJob(ServerLevel sl) {
        Job j = job;
        Animal a = j.target;
        if (!a.isAlive() || a.isRemoved() || !areaBox().inflate(2.0).contains(a.position())) {
            endJob(5);
            return;
        }
        if (++j.ticks > JOB_TIMEOUT) {
            skip(a);
            endJob(5);
            return;
        }
        getLookControl().setLookAt(a, 30.0F, 30.0F);
        if (closeEnough(a)) {
            navigation.stop();
            perform(sl, j);
        } else if (j.ticks % 10 == 1 || navigation.isDone()) {
            navigation.moveTo(a, speed());
        }
    }

    private boolean closeEnough(Animal a) {
        double r = 1.5 + (getBbWidth() + a.getBbWidth()) * 0.5;
        return distanceToSqr(a) <= r * r;
    }

    private void perform(ServerLevel sl, Job j) {
        IItemHandler store = storage(sl);
        Animal a = j.target;
        if (store == null) {
            setStatus(sl, Status.NO_STORAGE);
            endJob(20);
            return;
        }
        int cost = j.kind == Kind.CULL ? cullFe() : actionFe();
        if (!hasEnergy(cost)) {
            setStatus(sl, Status.NO_ENERGY);
            endJob(actionCooldown());
            return;
        }
        boolean done = switch (j.kind) {
            case FEED -> feed(sl, store, a);
            case SHEAR -> shear(sl, store, a);
            case MILK -> milk(sl, store, a);
            case CULL -> cull(sl, store, a);
        };
        if (!done) {
            skip(a);
            endJob(5);
            return;
        }
        consume(cost);
        swing(InteractionHand.MAIN_HAND);
        lastWorking = tickCount;
        setStatus(sl, Status.WORKING);
        if (j.kind == Kind.FEED && j.partner != null && j.partner.isAlive() && canBreedNow(j.partner)) {
            Job next = new Job(Kind.FEED, j.partner, null, j.shown);
            job = null;
            cooldown = 0;
            startJob(next);
            return;
        }
        endJob(actionCooldown());
    }

    private boolean feed(ServerLevel sl, IItemHandler store, Animal a) {
        if (!canBreedNow(a)) return false;
        for (int i = 0; i < store.getSlots(); i++) {
            ItemStack s = store.getStackInSlot(i);
            if (s.isEmpty() || !a.isFood(s)) continue;
            ItemStack taken = store.extractItem(i, 1, false);
            if (taken.isEmpty()) continue;
            hold(taken);
            a.setInLove(null);
            sl.playSound(null, a, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
            return true;
        }
        setStatus(sl, Status.NO_FEED);
        return false;
    }

    private boolean shear(ServerLevel sl, IItemHandler store, Animal a) {
        ItemStack shears = new ItemStack(Items.SHEARS);
        if (!(a instanceof IShearable s) || a.isBaby() || !s.isShearable(null, shears, sl, a.blockPosition())) return false;
        List<ItemStack> drops = s.onSheared(null, shears, sl, a.blockPosition());
        for (ItemStack drop : drops) store(sl, store, drop, a);
        return true;
    }

    /** Swaps one empty bucket for a milk bucket. Without a free slot the bucket comes from a single-bucket slot; milk that does not fit puts the bucket back. */
    private boolean milk(ServerLevel sl, IItemHandler store, Animal a) {
        if (a.isBaby() || !isMilkSpecies(a)) return false;
        boolean free = hasEmptySlot(store);
        for (int i = 0; i < store.getSlots(); i++) {
            ItemStack s = store.getStackInSlot(i);
            if (!s.is(Items.BUCKET) || (!free && s.getCount() != 1)) continue;
            ItemStack bucket = store.extractItem(i, 1, false);
            if (bucket.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(store, new ItemStack(Items.MILK_BUCKET), false);
            if (!rest.isEmpty()) {
                ItemStack back = store.insertItem(i, bucket, false);
                if (!back.isEmpty()) back = ItemHandlerHelper.insertItemStacked(store, back, false);
                if (!back.isEmpty()) a.spawnAtLocation(back, 1.0F);
                return false;
            }
            milkedAt.put(a.getUUID(), sl.getGameTime());
            sl.playSound(null, a, a instanceof Goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
            hold(new ItemStack(Items.MILK_BUCKET));
            return true;
        }
        return false;
    }

    private boolean cull(ServerLevel sl, IItemHandler store, Animal a) {
        if (!cullable(a) || (shear && isShearSpecies(a)) || (milk && isMilkSpecies(a))) return false;
        // Claim mods veto attacks through this event; the fake player carries the owner's UUID.
        FakePlayer fake = FakePlayerFactory.get(sl, new GameProfile(owner != null ? owner : NO_OWNER, "[Rancher]"));
        fake.setPos(getX(), getY(), getZ());
        if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(fake, a)).isCanceled()) return false;
        float damage = Math.max(a.getMaxHealth(), a.getHealth()) * 4.0F + 20.0F;
        a.hurt(damageSources().mobAttack(this), damage);
        sl.playSound(null, a, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.7F, 1.0F);
        if (a.isAlive()) return false;
        collectDrops(sl, store);
        return true;
    }

    /** Into storage; what does not fit drops at the animal and is picked up once there is room. */
    private static void store(ServerLevel sl, IItemHandler store, ItemStack stack, Animal at) {
        ItemStack rest = ItemHandlerHelper.insertItemStacked(store, stack.copy(), false);
        if (!rest.isEmpty()) at.spawnAtLocation(rest, 1.0F);
    }

    /** Puts item entities in the area into storage, except display items and items players threw. Returns false when something did not fit. */
    private boolean collectDrops(ServerLevel sl, IItemHandler store) {
        boolean allFit = true;
        for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, areaBox().inflate(1.0), e -> e.isAlive() && !e.getItem().isEmpty() && collectable(sl, e))) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(store, item.getItem().copy(), false);
            if (rest.isEmpty()) {
                item.discard();
            } else {
                if (rest.getCount() != item.getItem().getCount()) item.setItem(rest);
                allFit = false;
            }
        }
        return allFit;
    }

    @Nullable
    private static java.lang.reflect.Field pickupDelayField, throwerField;
    private static boolean fieldsLooked;

    @Nullable
    private static Object read(@Nullable java.lang.reflect.Field f, ItemEntity e) {
        try {
            return f == null ? null : f.get(e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** False for never-pickup items (pickup delay 32767) and for items a player threw. */
    static boolean collectable(ServerLevel sl, ItemEntity e) {
        if (!fieldsLooked) {
            fieldsLooked = true;
            try {
                pickupDelayField = net.neoforged.fml.util.ObfuscationReflectionHelper.findField(ItemEntity.class, "pickupDelay");
                throwerField = net.neoforged.fml.util.ObfuscationReflectionHelper.findField(ItemEntity.class, "thrower");
            } catch (RuntimeException ex) {
                pickupDelayField = null;
                throwerField = null;
            }
        }
        if (read(pickupDelayField, e) instanceof Integer delay && delay == 32767) return false;
        if (!(read(throwerField, e) instanceof UUID thrower)) return true;
        if (e.getOwner() instanceof Player) return false;
        if (sl.getServer().getPlayerList().getPlayer(thrower) != null) return false;
        var cache = sl.getServer().getProfileCache();
        return cache == null || cache.get(thrower).isEmpty();
    }

    // ---------------------------------------------------------------- storage

    /** The item handler block within {@link #STORAGE_RANGE} of home with the most slots (ties: nearest); cached while it stays there. */
    @Nullable
    IItemHandler storage(ServerLevel sl) {
        if (storagePos != null) {
            IItemHandler cap = sl.isLoaded(storagePos) ? sl.getCapability(Capabilities.ItemHandler.BLOCK, storagePos, null) : null;
            if (cap != null) return cap;
            storagePos = null;
        }
        BlockPos h = home();
        BlockPos best = null;
        IItemHandler found = null;
        double bestDist = Double.MAX_VALUE;
        int bestSlots = 0;
        for (BlockPos p : BlockPos.betweenClosed(h.offset(-STORAGE_RANGE, -STORAGE_RANGE, -STORAGE_RANGE), h.offset(STORAGE_RANGE, STORAGE_RANGE, STORAGE_RANGE))) {
            if (!sl.isLoaded(p) || !sl.getBlockState(p).hasBlockEntity()) continue;
            IItemHandler cap = sl.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (cap == null) continue;
            int slots = cap.getSlots();
            double d = p.distSqr(h);
            if (slots > bestSlots || (slots == bestSlots && d < bestDist)) {
                best = p.immutable();
                found = cap;
                bestDist = d;
                bestSlots = slots;
            }
        }
        storagePos = best;
        return found;
    }

    private static boolean hasEmptySlot(IItemHandler store) {
        for (int i = 0; i < store.getSlots(); i++) {
            if (store.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasBucket(IItemHandler store) {
        for (int i = 0; i < store.getSlots(); i++) {
            if (store.getStackInSlot(i).is(Items.BUCKET)) return true;
        }
        return false;
    }

    /** Room for a milk bucket: an empty slot, or a slot holding a single empty bucket that the milk replaces. */
    private static boolean milkRoom(IItemHandler store) {
        if (hasEmptySlot(store)) return true;
        for (int i = 0; i < store.getSlots(); i++) {
            ItemStack s = store.getStackInSlot(i);
            if (s.is(Items.BUCKET) && s.getCount() == 1) return true;
        }
        return false;
    }

    /** One feed item for this animal when the storage holds at least {@code need}, else empty. */
    private static ItemStack findFeed(IItemHandler store, Animal a, int need) {
        int total = 0;
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < store.getSlots(); i++) {
            ItemStack s = store.getStackInSlot(i);
            if (s.isEmpty() || !a.isFood(s)) continue;
            if (first.isEmpty()) first = s.copyWithCount(1);
            total += s.getCount();
            if (total >= need) return first;
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- animal rules

    static boolean isShearSpecies(Animal a) {
        return a instanceof IShearable && !(a instanceof MushroomCow);
    }

    static boolean isMilkSpecies(Animal a) {
        return a instanceof Cow || a instanceof Goat;
    }

    static boolean owned(Animal a) {
        return a instanceof OwnableEntity o && o.getOwnerUUID() != null;
    }

    static boolean canBreedNow(Animal a) {
        return !a.isBaby() && a.getAge() == 0 && a.canFallInLove() && !a.isInLove();
    }

    /** Never babies, named, leashed, tamed, ridden or riding animals. */
    static boolean cullable(Animal a) {
        return a.isAlive() && !a.isBaby() && !a.hasCustomName() && !a.isLeashed() && !owned(a) && !a.isVehicle() && !a.isPassenger()
                && !a.isInvulnerable();
    }

    /** Bees, parrots and other flying animals: never counted, bred or culled. */
    static boolean flying(Animal a) {
        return a instanceof net.minecraft.world.entity.animal.FlyingAnimal;
    }

    static boolean isHerd(List<Animal> species) {
        int adults = 0;
        for (Animal a : species) {
            if (!a.isBaby() && ++adults >= 2) return true;
        }
        return false;
    }

    /** Even split of the target over {@code n} species, the remainder to the first ones, at least 2 each (a breeding pair). */
    public static int[] shares(int target, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = Math.max(2, target / n + (i < target % n ? 1 : 0));
        return out;
    }

    private void plan(ServerLevel sl) {
        IItemHandler store = storage(sl);
        if (store == null) {
            setStatus(sl, Status.NO_STORAGE);
            return;
        }
        boolean dropsFit = collectDrops(sl, store);
        long now = sl.getGameTime();
        skipUntil.values().removeIf(t -> t <= now);
        milkedAt.values().removeIf(t -> now - t >= MILK_COOLDOWN);

        Map<String, List<Animal>> bySpecies = new TreeMap<>();
        for (Animal a : sl.getEntitiesOfClass(Animal.class, areaBox(), x -> x.isAlive() && !owned(x) && !flying(x))) {
            bySpecies.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).toString(), k -> new ArrayList<>()).add(a);
        }
        Iterator<List<Animal>> it = bySpecies.values().iterator();
        while (it.hasNext()) {
            if (it.next().stream().allMatch(Animal::isBaby)) it.remove();
        }
        // only herds (2+ adults) split the target; a lone animal is still sheared or milked but never culled or bred
        int herds = 0;
        for (List<Animal> list : bySpecies.values()) {
            if (isHerd(list)) herds++;
        }
        int[] herdShares = shares(target, Math.max(1, herds));
        boolean room = hasEmptySlot(store);
        boolean wantOutput = !dropsFit;
        boolean missingFeed = false;
        Job cullJob = null, shearJob = null, milkJob = null, feedJob = null;
        int adultsTotal = 0;
        int index = 0;
        for (List<Animal> list : bySpecies.values()) {
            int share = isHerd(list) ? herdShares[index++] : MIN_TARGET;
            List<Animal> adults = new ArrayList<>();
            int babies = 0;
            for (Animal a : list) {
                if (a.isBaby()) babies++;
                else adults.add(a);
            }
            adultsTotal += adults.size();
            Animal first = adults.get(0);
            boolean shearKind = shear && isShearSpecies(first);
            boolean milkKind = milk && isMilkSpecies(first);
            if (shearKind && shearJob == null) {
                ItemStack shears = new ItemStack(Items.SHEARS);
                for (Animal a : adults) {
                    if (skipped(a, now) || !((IShearable) a).isShearable(null, shears, sl, a.blockPosition())) continue;
                    if (room) shearJob = new Job(Kind.SHEAR, a, null, shears);
                    else wantOutput = true;
                    break;
                }
            }
            if (milkKind && milkJob == null && hasBucket(store)) {
                for (Animal a : adults) {
                    if (skipped(a, now) || milkedAt.containsKey(a.getUUID())) continue;
                    if (milkRoom(store)) milkJob = new Job(Kind.MILK, a, null, new ItemStack(Items.BUCKET));
                    else wantOutput = true;
                    break;
                }
            }
            if (!shearKind && !milkKind && cullJob == null && adults.size() > share) {
                Animal pick = null;
                for (Animal a : adults) {
                    if (skipped(a, now) || !cullable(a)) continue;
                    if (pick == null || (pick.isInLove() && !a.isInLove())) pick = a;
                }
                if (pick != null) {
                    if (room) cullJob = new Job(Kind.CULL, pick, null, new ItemStack(Items.IRON_SWORD));
                    else wantOutput = true;
                }
            }
            if (feedJob == null && adults.size() + babies < share) {
                List<Animal> ready = new ArrayList<>();
                for (Animal a : adults) {
                    if (!skipped(a, now) && canBreedNow(a)) ready.add(a);
                }
                if (ready.size() >= 2) {
                    ItemStack food = findFeed(store, ready.get(0), 2);
                    if (food.isEmpty()) missingFeed = true;
                    else feedJob = new Job(Kind.FEED, ready.get(0), ready.get(1), food);
                }
            }
        }
        lastAdults = adultsTotal;
        Job next = cullJob != null ? cullJob : shearJob != null ? shearJob : milkJob != null ? milkJob : feedJob;
        if (next != null && !hasEnergy(next.kind == Kind.CULL ? cullFe() : actionFe())) {
            setStatus(sl, Status.NO_ENERGY);
            return;
        }
        if (next != null) {
            startJob(next);
            setStatus(sl, Status.WORKING);
        } else if (wantOutput) {
            setStatus(sl, Status.STORAGE_FULL);
        } else if (missingFeed) {
            setStatus(sl, Status.NO_FEED);
        } else {
            setStatus(sl, Status.IDLE);
        }
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Tier", tier());
        tag.putInt("Energy", energy);
        if (homeSet) tag.put("Home", NbtUtils.writeBlockPos(home()));
        writeSettings(tag, registryAccess());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        setTier(tag.getInt("Tier"));
        readSettings(tag, registryAccess());
        energy = Mth.clamp(tag.getInt("Energy"), 0, getEnergyCapacity());
        NbtUtils.readBlockPos(tag, "Home").ifPresent(this::setHome);
    }
}
