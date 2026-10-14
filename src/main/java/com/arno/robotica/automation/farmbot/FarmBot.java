package com.arno.robotica.automation.farmbot;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.RobotArea;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.progress.Milestones;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared body of the walking farm robots (Stumpy and Sprout). Around its home (where it was placed) it sweeps a square
 * area a few hundred blocks per tick for work, walks to the nearest job and does it once in reach. Drops go into the
 * biggest inventory within {@link #STORAGE_RANGE} blocks of home; seeds and saplings it keeps in its 9-slot pouch.
 *
 * <p>Getting unstuck: a job it can not reach within {@link #JOB_TIMEOUT} ticks, or that it walks toward without getting
 * closer, is skipped for a while ({@link #clearWay} gets a try first: Stumpy fells the closest tree). After a few skips in
 * a row without moving, or when it ends up inside a block, far from home or under its area, it teleports home.
 */
public abstract class FarmBot extends PathfinderMob {
    public enum Status {
        IDLE, WORKING, NO_ENERGY, OUTPUT_FULL;

        public String key() {
            return "gui.robotica.status." + name().toLowerCase(Locale.ROOT);
        }

        public String hintKey() {
            return "gui.robotica.farm_bot.hint." + name().toLowerCase(Locale.ROOT);
        }

        public static Status byId(int id) {
            Status[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }

        public boolean stalled() {
            return this == NO_ENERGY || this == OUTPUT_FULL;
        }
    }

    public static final Set<UpgradeKind> KINDS = EnumSet.of(UpgradeKind.SPEED, UpgradeKind.RANGE, UpgradeKind.EFFICIENCY, UpgradeKind.GROWTH);
    public static final int POUCH_SLOTS = 9;
    /** Storage is the item handler block with the most slots within this many blocks of home. */
    public static final int STORAGE_RANGE = 3;
    private static final int MAX_PULL = 1000;
    private static final int SCAN_PER_TICK = 128;
    private static final int MAX_CANDIDATES = 4096;
    /** Ticks to reach a job before it is skipped. */
    static final int JOB_TIMEOUT = 300;
    /** Progress checks (one a second) without getting closer before a job counts as blocked. */
    private static final int STUCK_CHECKS = 3;
    static final int SKIP_TICKS = 600;
    /** Skipped jobs in a row without moving before the robot teleports home. */
    static final int FAILS_BEFORE_TELEPORT = 3;
    private static final int TELEPORT_COOLDOWN = 200;
    private static final int MAX_PICKUP = 16;
    private static final int MAX_ITEM_AGE = 200;
    private static final int MAX_NATURAL_AGE = 1200;
    private static final UUID FALLBACK_OWNER = new UUID(0L, 0xB07L);

    /** Ticks of a whole arm swing and the tick at which it hits. */
    public static final int SWING_TICKS = 7;
    public static final int HIT_TICK = 2;
    /** Effect kind of a plain sparkle (no swing), used when actions come faster than swings. */
    public static final int FX_SPARKLE = 3;
    private static final int SWING_GAP = 8;
    private static final int SPARKLE_GAP = 2;

    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATUS = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_AREA = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOW_AREA = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.BOOLEAN);
    /** Target of the last work effect; defined before {@link #DATA_ACTION} so it arrives first. */
    private static final EntityDataAccessor<BlockPos> DATA_FX_POS = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.BLOCK_POS);
    /** Last work effect: (count << 2) | kind, so a repeat of the same kind still changes the value. */
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(FarmBot.class, EntityDataSerializers.INT);

    public final MachineEnergyStorage energy = new MachineEnergyStorage(AutomationConfig.energyBuffer(), 1000, 0, () -> {}) {
        @Override
        public boolean consume(int amount) {
            boolean ok = super.consume(amount);
            if (ok) useWindow += amount;
            return ok;
        }
    };
    /** Any FE item that can extract (cells, Mainspring). Its charge refills the internal buffer. */
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canUseAsBattery(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    public final Upgrades upgrades = Upgrades.forMk(this::tier, KINDS, RobotArea.FIXED_CAPS, this::onUpgradesChanged);
    /** Seeds and saplings to plant, and whatever did not fit in the storage. */
    public final ItemStackHandler pouch = new ItemStackHandler(POUCH_SLOTS);

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private boolean homeSet;
    private boolean firstTick = true;
    private final List<ItemStack> pending = new ArrayList<>();
    @Nullable
    private BlockPos storagePos;
    private long storageSearchedAt = -1000;

    // sweep and jobs
    private LongArrayList candidates = new LongArrayList();
    private LongArrayList sweeping = new LongArrayList();
    private int scanCursor;
    private int scanWait;
    /** Extra scan budget an expensive {@link #isTarget} check spends (reset before every check). */
    protected int scanCost;
    private final Long2LongOpenHashMap skipUntil = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap strikes = new Long2IntOpenHashMap();
    @Nullable
    protected BlockPos job;
    private int jobTicks;
    private int progress;
    private int stuckChecks;
    private double bestDist;
    private int lastPathTick = -100;
    private int fails;
    @Nullable
    private Vec3 failAnchor;
    private int lastTeleport = -TELEPORT_COOLDOWN;
    /** Ticks of rest left after a big action (Stumpy after a tree). */
    protected int rest;
    private int holdTicks;
    private double growthCarry;

    // status and sounds
    private int lastWorking = -1000;
    private int lastStallTick = -2000;
    @Nullable
    private Status lastStall;
    private int lastWorkSound = -20;
    private int lastBreakSound = -20;
    private long useWindow;
    private int usePerTick;

    // work effects: server rate limits, client swing
    private int lastSwing = -100;
    private int lastSparkle = -100;
    private int actions;
    private int fxStart = Integer.MIN_VALUE;
    private int fxKind;
    private boolean fxHitPending;
    @Nullable
    private BlockPos fxTarget;

    protected FarmBot(EntityType<? extends FarmBot> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TIER, 1);
        builder.define(DATA_STATUS, Status.IDLE.ordinal());
        builder.define(DATA_HOME, BlockPos.ZERO);
        builder.define(DATA_AREA, 9);
        builder.define(DATA_SHOW_AREA, false);
        builder.define(DATA_FX_POS, BlockPos.ZERO);
        builder.define(DATA_ACTION, 0);
    }

    // ---------------------------------------------------------------- subclass hooks

    /** The Mk1 to Mk4 items of this robot. */
    protected abstract List<DeferredItem<FarmBotItem>> items();

    /** The item this robot turns into when picked up (its Mk). */
    public FarmBotItem item() {
        return FarmBotContent.item(items(), tier());
    }

    /** Energy per tick while walking to and working on a job, config value. */
    protected abstract int baseFePerTick();

    /** First Y layer of the area, relative to home, and the number of layers. */
    protected abstract int layerOffset();

    protected abstract int layerCount();

    /** How close (horizontally, blocks from the job's centre) the robot must stand to work on it. */
    protected abstract double reach();

    /** True if this block position is worth a job right now. Must be cheap. */
    protected abstract boolean isTarget(ServerLevel level, BlockPos pos, BlockState state);

    /** Does the job at {@code target}. Return false if there was nothing to do after all. */
    protected abstract boolean act(ServerLevel level, BlockPos target);

    /** Blocks that get the growth boost random ticks. */
    protected abstract boolean isGrowable(BlockState state);

    /** How many of this item the robot keeps in its pouch instead of passing it on. 0 = none. */
    protected int keepAmount(ItemStack stack) {
        return 0;
    }

    /** Runs a multi-tick action (Stumpy's felling wave) before anything else. True while it is busy. */
    protected boolean busyTick(ServerLevel sl) {
        return false;
    }

    /** Called every tick the robot spends working on a job in reach; used for the work sound. */
    protected void onWorkTick(ServerLevel sl) {
    }

    protected void onScanWrapped() {
    }

    /** Tries to free the way when a job is blocked (Stumpy fells the closest tree). True if it found something to do. */
    protected boolean clearWay(ServerLevel sl) {
        return false;
    }

    /** Client side: particles and sounds of an action as the swing lands. */
    protected abstract void playHitFx(Level level, int kind, BlockPos target);

    protected void saveExtra(CompoundTag tag) {
    }

    protected void loadExtra(CompoundTag tag) {
    }

    // ---------------------------------------------------------------- tier and numbers

    public int tier() {
        return entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        entityData.set(DATA_TIER, Mth.clamp(tier, 1, 4));
        recalc();
    }

    /** Side of the square work area (synced). */
    public int areaSize() {
        return entityData.get(DATA_AREA);
    }

    public int actionInterval() {
        int base = CoreConfig.scaleInterval(AutomationConfig.farmInterval(tier()));
        return Math.max(1, base / Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /** Speed multiplier that actually shortens the action interval (a floored interval must not be paid for). */
    public int effectiveSpeedMultiplier() {
        int base = Math.max(1, CoreConfig.scaleInterval(AutomationConfig.farmInterval(tier())));
        return Math.max(1, base / Math.max(1, actionInterval()));
    }

    public double growthMultiplier() {
        return AutomationConfig.farmGrowth(tier()) + Upgrades.growthBonus(upgrades.level(UpgradeKind.GROWTH));
    }

    /** Walking speed modifier: each Mk walks a bit faster. */
    double speed() {
        return 1.0 + 0.15 * (tier() - 1);
    }

    /** Energy per tick while working, before multipliers: base * speed multiplier * (speed/efficiency energy factor). */
    protected int scaledDrain(int baseFePerTick, int effectiveSpeedMultiplier) {
        int speed = upgrades.level(UpgradeKind.SPEED);
        int eff = upgrades.level(UpgradeKind.EFFICIENCY);
        double value = CoreConfig.scaleEnergy(baseFePerTick) * effectiveSpeedMultiplier * Upgrades.energyMultiplier(speed, eff);
        return (int) Math.max(baseFePerTick == 0 ? 0 : 1, Math.round(value));
    }

    public int usePerTick() {
        return usePerTick;
    }

    private void recalc() {
        if (level().isClientSide) return;
        entityData.set(DATA_AREA, RobotArea.size(upgrades.level(UpgradeKind.RANGE)));
        resetScan();
    }

    private void resetScan() {
        scanCursor = 0;
        sweeping.clear();
        candidates.clear();
        scanWait = 0;
    }

    private void onUpgradesChanged() {
        if (!level().isClientSide) recalc();
    }

    public Status status() {
        return Status.byId(entityData.get(DATA_STATUS));
    }

    // ---------------------------------------------------------------- home and area

    public BlockPos home() {
        return entityData.get(DATA_HOME);
    }

    public void setHome(BlockPos pos) {
        entityData.set(DATA_HOME, pos.immutable());
        homeSet = true;
        storagePos = null;
        storageSearchedAt = -1000;
        job = null;
        resetScan();
    }

    public int areaMinX() {
        return home().getX() - areaSize() / 2;
    }

    public int areaMinZ() {
        return home().getZ() - areaSize() / 2;
    }

    public int layerBase() {
        return home().getY() + layerOffset();
    }

    public AABB areaBox() {
        int size = areaSize();
        return new AABB(areaMinX(), layerBase(), areaMinZ(), areaMinX() + size, layerBase() + layerCount(), areaMinZ() + size);
    }

    public boolean inArea(BlockPos pos) {
        int minX = areaMinX(), minZ = areaMinZ(), size = areaSize();
        return pos.getX() >= minX && pos.getX() < minX + size && pos.getZ() >= minZ && pos.getZ() < minZ + size;
    }

    public boolean showArea() {
        return entityData.get(DATA_SHOW_AREA);
    }

    public void toggleShowArea() {
        entityData.set(DATA_SHOW_AREA, !showArea());
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

    /** Owner, same team as the owner, or an operator. A robot without owner obeys everyone. */
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

    protected GameProfile fakeProfile() {
        return new GameProfile(owner != null ? owner : FALLBACK_OWNER, "[Robotica]");
    }

    /** Fires a break event as the owner's fake player so claim and protection mods can veto. */
    protected boolean mayBreak(ServerLevel sl, BlockPos pos, BlockState state) {
        try {
            FakePlayer fake = FakePlayerFactory.get(sl, fakeProfile());
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(sl, pos, state, fake);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- energy

    public static boolean canUseAsBattery(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof FarmBotItem) return false;
        var cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap != null && cap.canExtract();
    }

    private void pullBattery() {
        ItemStack stack = battery.getStackInSlot(0);
        if (stack.isEmpty() || energy.getSpace() <= 0) return;
        if (energy.getEnergyStored() > energy.getMaxEnergyStored() / 4 && tickCount % 4 != 0) return;
        EnergyUtil.dischargeItem(stack, energy, MAX_PULL);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!canInteract(player)) {
            sp.displayClientMessage(Component.translatable("message.robotica.robot_not_yours", currentOwnerName()), true);
            return InteractionResult.CONSUME;
        }
        holdTicks = 40;
        if (player.isShiftKeyDown() && held.isEmpty()) {
            pickUp(sp);
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof FarmBotItem next && next.entityType() == getType()) {
            upgradeInPlace(sp, held, next);
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof UpgradeCardItem card) {
            insertCard(sp, held, card);
            return InteractionResult.CONSUME;
        }
        if (canUseAsBattery(held)) {
            ItemStack old = battery.getStackInSlot(0).copy();
            battery.setStackInSlot(0, held.copyWithCount(1));
            held.consume(1, player);
            if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
            CoreSounds.play(this, CoreSounds.SPRING_INSERT, SoundSource.NEUTRAL, 0.8F, 1.1F);
            sp.displayClientMessage(Component.translatable("message.robotica.battery_inserted", battery.getStackInSlot(0).getHoverName()), true);
            return InteractionResult.CONSUME;
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new FarmBotMenu(id, inv, this),
                hasCustomName() ? getDisplayName() : item().getDescription()), buf -> buf.writeVarInt(getId()));
        return InteractionResult.CONSUME;
    }

    /**
     * Right-click with a higher Mk of the same robot: this robot becomes that Mk and the player gets its old Mk back
     * (fresh), the same trade as the crafting recipe. Only a fresh item: a placed-before one carries its own cards.
     */
    public boolean upgradeInPlace(ServerPlayer player, ItemStack held, FarmBotItem next) {
        int tier = tier();
        if (next.tier() <= tier) {
            player.displayClientMessage(Component.translatable("message.robotica.farm_bot.already", tier), true);
            return false;
        }
        if (!FarmBotItem.isFresh(held)) {
            player.displayClientMessage(Component.translatable("message.robotica.farm_bot.not_fresh"), true);
            return false;
        }
        ItemStack old = new ItemStack(item());
        setTier(next.tier());
        long total = (long) energy.getEnergyStored() + ItemEnergy.get(held);
        energy.setEnergy((int) Math.min(energy.getMaxEnergyStored(), total));
        // What does not fit stays in the old Mk the player gets back.
        int overflow = (int) Math.min(Integer.MAX_VALUE, total - energy.getEnergyStored());
        if (overflow > 0) ItemEnergy.set(old, Math.min(overflow, ItemEnergy.capacity(old)));
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
            if (!player.getInventory().add(old)) player.drop(old, false);
        }
        CoreSounds.play(this, CoreSounds.UPGRADE_INSTALL, SoundSource.NEUTRAL, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable("message.robotica.machine_upgraded", item().getDescription()), true);
        return true;
    }

    private void insertCard(ServerPlayer player, ItemStack held, UpgradeCardItem card) {
        if (upgrades.insertOne(held)) {
            held.consume(1, player);
            CoreSounds.play(this, CoreSounds.UPGRADE_INSTALL, SoundSource.NEUTRAL, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.robotica.card_inserted", card.getDescription(),
                    upgrades.level(card.getKind()), upgrades.cap(card.getKind())), true);
            return;
        }
        int cap = upgrades.cap(card.getKind());
        player.displayClientMessage(cap <= 0 ? Component.translatable("message.robotica.card_refused")
                : Component.translatable("message.robotica.card_full", card.getDescription(), cap), true);
    }

    /** Keeps the robot still while a GUI is open; called by the menu every tick. */
    public void holdForGui() {
        holdTicks = Math.max(holdTicks, 4);
    }

    // ---------------------------------------------------------------- item form

    /** Battery, cards, pouch and health: kept by pick-up and when destroyed. The Mk is the item, energy the core energy component. */
    public ItemStack toItemStack(boolean destroyed) {
        ItemStack stack = new ItemStack(item());
        CompoundTag tag = new CompoundTag();
        tag.put("Battery", battery.serializeNBT(registryAccess()));
        tag.put("Upgrades", upgrades.serializeNBT(registryAccess()));
        tag.put("Pouch", pouch.serializeNBT(registryAccess()));
        tag.putFloat("Health", destroyed ? Math.max(getHealth(), getMaxHealth() * 0.25F) : getHealth());
        stack.set(FarmBotContent.STATE.get(), tag);
        ItemEnergy.set(stack, energy.getEnergyStored());
        if (hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, getCustomName());
        return stack;
    }

    public void initFromStack(ItemStack stack, @Nullable Player placer) {
        CompoundTag tag = stack.getOrDefault(FarmBotContent.STATE.get(), new CompoundTag());
        setTier(Math.max(1, FarmBotItem.tier(stack)));
        if (tag.contains("Battery")) battery.deserializeNBT(registryAccess(), tag.getCompound("Battery"));
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(registryAccess(), tag.getCompound("Upgrades"));
        if (tag.contains("Pouch")) pouch.deserializeNBT(registryAccess(), tag.getCompound("Pouch"));
        energy.setEnergy(ItemEnergy.get(stack));
        setHealth(tag.contains("Health") ? Math.max(1.0F, Math.min(tag.getFloat("Health"), getMaxHealth())) : getMaxHealth());
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setCustomName(name);
        if (placer != null) setOwner(placer);
        recalc();
    }

    public void pickUp(ServerPlayer player) {
        ItemStack stack = toItemStack(false);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        for (ItemStack p : pending) {
            if (!player.getInventory().add(p)) player.drop(p, false);
        }
        pending.clear();
        CoreSounds.play(this, CoreSounds.ROBOT_BEEP_LOW, SoundSource.NEUTRAL, 0.7F, 1.2F);
        discard();
    }

    /** A destroyed robot drops as its item with energy, cards and pouch; whatever waited for storage drops too. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.3, getZ(), toItemStack(true));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        for (ItemStack p : pending) spawnAtLocation(p);
        pending.clear();
        ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (p != null) p.displayClientMessage(Component.translatable("message.robotica.robot_destroyed", getDisplayName()), true);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(item());
    }

    // ---------------------------------------------------------------- body

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() instanceof Player || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Berry bushes do not slow it down: Sprout works in them. */
    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.SWEET_BERRY_BUSH)) super.makeStuckInBlock(state, motionMultiplier);
    }

    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
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
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return CoreSounds.RANCHER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CoreSounds.RANCHER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(CoreSounds.RANCHER_STEP.get(), 0.25F, 1.2F + random.nextFloat() * 0.1F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && fxHitPending && tickCount - fxStart >= HIT_TICK) {
            fxHitPending = false;
            if (fxTarget != null) playHitFx(level(), fxKind, fxTarget);
        }
    }

    // ---------------------------------------------------------------- brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (!homeSet) setHome(blockPosition());
        if (firstTick) {
            firstTick = false;
            recalc();
        }
        pullBattery();
        if (tickCount % 20 == 0) {
            usePerTick = (int) Math.min(Integer.MAX_VALUE, useWindow / 20);
            useWindow = 0;
        }
        if ((tickCount + getId()) % 10 == 0) flushPouch(sl);
        if (!pending.isEmpty()) flushPending(sl);
        if ((tickCount + getId()) % 20 == 0) watchdog(sl);
        Status next = pending.isEmpty() ? think(sl) : Status.OUTPUT_FULL;
        if (next != Status.WORKING && tickCount % 5 == 0) energy.consume(1);
        if (next.stalled()) navigation.stop();
        setStatus(sl, next);
        if (next.stalled() && (tickCount + getId()) % 40 == 0) stallParticles(sl);
    }

    private Status think(ServerLevel sl) {
        if (energy.getEnergyStored() > 0) {
            if (tickCount % 20 == 0) growthBoost(sl);
            if (tickCount % 60 == 30) pickupDrops(sl);
        }
        scanStep(sl);
        if (holdTicks > 0) {
            holdTicks--;
            navigation.stop();
            return status();
        }
        if (busyTick(sl)) return Status.WORKING;
        if (rest > 0) {
            rest--;
            return Status.WORKING;
        }
        if (job == null && !pickJob(sl)) {
            if (tickCount % 20 == 0) walkHome(sl);
            return energy.getEnergyStored() <= 0 ? Status.NO_ENERGY : Status.IDLE;
        }
        return runJob(sl);
    }

    /** Walks back home when there is nothing to do; a robot that cannot get there within half a minute teleports. */
    private void walkHome(ServerLevel sl) {
        BlockPos h = home();
        Vec3 spot = new Vec3(h.getX() + 0.5, h.getY(), h.getZ() + 0.5);
        double d = position().distanceToSqr(spot);
        awayTicks = d > 16.0 ? awayTicks + 20 : 0;
        if (awayTicks >= 600 && teleportHome(sl)) awayTicks = 0;
        else if (d > 2.25 && navigation.isDone()) navigation.moveTo(spot.x, spot.y, spot.z, speed());
    }

    private Status runJob(ServerLevel sl) {
        BlockPos t = job;
        if (energy.getEnergyStored() <= 0) return Status.NO_ENERGY;
        if (jobTicks % 10 == 0) {
            scanCost = 0;
            if (!sl.isLoaded(t) || !isTarget(sl, t, sl.getBlockState(t))) {
                job = null;
                return Status.WORKING;
            }
        }
        if (!energy.consume(scaledDrain(baseFePerTick(), effectiveSpeedMultiplier()))) return Status.NO_ENERGY;
        jobTicks++;
        getLookControl().setLookAt(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
        if (inReach(t)) {
            navigation.stop();
            onWorkTick(sl);
            if (progress + 1 >= actionInterval() && !canAfford(sl, t)) return Status.NO_ENERGY;
            if (++progress >= actionInterval()) {
                progress = 0;
                job = null;
                if (act(sl, t)) {
                    strikes.remove(t.asLong());
                    fails = 0;
                    failAnchor = null;
                    lastWorking = tickCount;
                } else {
                    skip(t);
                }
            }
            return Status.WORKING;
        }
        progress = 0;
        if (jobTicks > JOB_TIMEOUT || stuckChecks >= STUCK_CHECKS) {
            blocked(sl, t);
            return Status.WORKING;
        }
        // In the air (just placed or teleported) no path can be made yet: wait to land instead of calling the job blocked.
        boolean canPath = onGround() || isInLiquid() || isPassenger();
        if (canPath && (jobTicks == 1 || jobTicks - lastPathTick >= 20 || (navigation.isDone() && jobTicks - lastPathTick >= 10))) {
            lastPathTick = jobTicks;
            Path path = navigation.createPath(t, pathAccuracy());
            // No path while in the air (just placed, a jump, a step down) says nothing about the job: try again next tick.
            if (path == null && !onGround() && !isInLiquid()) {
                lastPathTick = -100;
                return Status.WORKING;
            }
            if (path == null) {
                blocked(sl, t);
                return Status.WORKING;
            }
            navigation.moveTo(path, speed());
        }
        if (jobTicks % 20 == 0) {
            double d = horizontalDistance(t);
            if (d < bestDist - 0.3) {
                bestDist = d;
                stuckChecks = 0;
            } else {
                stuckChecks++;
            }
        }
        return Status.WORKING;
    }

    /** Enough FE stored for the action at {@code t}; until then the robot waits at the job instead of skipping it. */
    protected boolean canAfford(ServerLevel sl, BlockPos t) {
        return true;
    }

    protected int pathAccuracy() {
        return 1;
    }

    protected double horizontalDistance(BlockPos t) {
        double dx = getX() - (t.getX() + 0.5), dz = getZ() - (t.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** In reach of a job: close enough horizontally and within a few blocks up or down. */
    protected boolean inReach(BlockPos t) {
        double dy = t.getY() + 0.5 - getY();
        return horizontalDistance(t) <= reach() && dy > -3.0 && dy < 4.5;
    }

    /** The job could not be reached: free the way, else skip the job; teleport home when nothing works from here. */
    private void blocked(ServerLevel sl, BlockPos t) {
        navigation.stop();
        job = null;
        if (clearWay(sl)) return;
        skip(t);
        if (failAnchor == null || position().distanceToSqr(failAnchor) > 4.0) {
            failAnchor = position();
            fails = 0;
        }
        if (++fails >= FAILS_BEFORE_TELEPORT) {
            fails = 0;
            failAnchor = null;
            if (position().distanceToSqr(Vec3.atBottomCenterOf(home())) > 9.0) teleportHome(sl);
        }
    }

    /** Starts a job right away (used by {@link #clearWay}). */
    protected void startJob(BlockPos pos) {
        job = pos.immutable();
        jobTicks = 0;
        progress = 0;
        stuckChecks = 0;
        bestDist = Double.MAX_VALUE;
        lastPathTick = -100;
    }

    /** Leaves a job alone for a while; each skip in a row doubles the wait, up to 8 times. */
    protected void skip(BlockPos pos) {
        long p = pos.asLong();
        int n = Math.min(strikes.get(p) + 1, 4);
        if (strikes.size() > 8192) strikes.clear();
        strikes.put(p, n);
        skipUntil.put(p, level().getGameTime() + ((long) SKIP_TICKS << (n - 1)));
    }

    private boolean skipped(long pos, long now) {
        long until = skipUntil.getOrDefault(pos, 0L);
        return until > now;
    }

    /** The job at this spot failed lately and waits. */
    protected boolean isSkipped(BlockPos pos) {
        return skipped(pos.asLong(), level().getGameTime());
    }

    /** The nearest candidate that is still a job, from the last full sweep or (before it ends) the running one. */
    private boolean pickJob(ServerLevel sl) {
        long now = sl.getGameTime();
        if (tickCount % 200 == 0) skipUntil.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
        return pickFrom(sl, candidates, now) || pickFrom(sl, sweeping, now);
    }

    private boolean pickFrom(ServerLevel sl, LongArrayList list, long now) {
        double x = getX(), y = getY(), z = getZ();
        for (int tries = 0; tries < 16 && !list.isEmpty(); tries++) {
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < list.size(); i++) {
                long p = list.getLong(i);
                if (skipped(p, now)) continue;
                double dx = BlockPos.getX(p) + 0.5 - x, dy = (BlockPos.getY(p) - y) * 2.0, dz = BlockPos.getZ(p) + 0.5 - z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best < 0) return false;
            long p = list.getLong(best);
            list.set(best, list.getLong(list.size() - 1));
            list.removeLong(list.size() - 1);
            BlockPos pos = BlockPos.of(p);
            if (!inArea(pos) || !sl.isLoaded(pos)) continue;
            scanCost = 0;
            if (isTarget(sl, pos, sl.getBlockState(pos))) {
                startJob(pos);
                return true;
            }
        }
        return false;
    }

    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    /** Idle ticks spent away from home (counted every 20). */
    private int awayTicks;

    /** Sweeps the area a few hundred blocks per tick; a finished sweep becomes the candidate list. */
    private void scanStep(ServerLevel sl) {
        if (scanWait > 0) {
            scanWait--;
            return;
        }
        int size = areaSize();
        int layerSize = size * size;
        int total = layerSize * layerCount();
        int minX = areaMinX(), minZ = areaMinZ(), baseY = layerBase();
        BlockPos.MutableBlockPos pos = scanPos;
        for (int n = 0; n < SCAN_PER_TICK; n++) {
            n += scanCost;
            scanCost = 0;
            if (scanCursor >= total) {
                LongArrayList done = sweeping;
                sweeping = candidates;
                sweeping.clear();
                candidates = done;
                scanCursor = 0;
                scanWait = candidates.isEmpty() ? 40 : 10;
                onScanWrapped();
                return;
            }
            int layer = scanCursor / layerSize;
            int idx = scanCursor % layerSize;
            scanCursor++;
            pos.set(minX + idx % size, baseY + layer, minZ + idx / size);
            if (!sl.isLoaded(pos)) continue;
            if (sweeping.size() < MAX_CANDIDATES && isTarget(sl, pos, sl.getBlockState(pos))) sweeping.add(pos.asLong());
        }
    }

    // ---------------------------------------------------------------- safety: lost, trapped, inside a block

    private void watchdog(ServerLevel sl) {
        BlockPos h = home();
        double dx = getX() - (h.getX() + 0.5), dz = getZ() - (h.getZ() + 0.5);
        double far = areaSize() / 2.0 + 12.0;
        boolean lost = dx * dx + dz * dz > far * far || getY() < h.getY() - 12 || getY() > h.getY() + 24;
        if (lost || isInWall() || isInLava()) teleportHome(sl);
    }

    /** Teleports to a free spot at home (or close to it). False when none is free or it teleported just before. */
    public boolean teleportHome(ServerLevel sl) {
        if (tickCount - lastTeleport < TELEPORT_COOLDOWN) return false;
        BlockPos spot = freeSpotNear(sl, home(), 3);
        if (spot == null) return false;
        lastTeleport = tickCount;
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        CoreSounds.play(this, CoreSounds.TELEPORT_QUIET_DEPART, SoundSource.NEUTRAL, 0.6F, 1.2F);
        navigation.stop();
        job = null;
        teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        resetFallDistance();
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        CoreSounds.play(this, CoreSounds.TELEPORT_QUIET_ARRIVE, SoundSource.NEUTRAL, 0.6F, 1.2F);
        return true;
    }

    /** A loaded spot near {@code center} with ground below, room for the robot and no lava; nearest rings first. */
    @Nullable
    BlockPos freeSpotNear(ServerLevel sl, BlockPos center, int radius) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (fits(sl, pos)) return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    private boolean fits(ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos) || !sl.isLoaded(pos.below())) return false;
        BlockPos below = pos.below();
        if (sl.getBlockState(below).getCollisionShape(sl, below).isEmpty()) return false;
        if (sl.getFluidState(pos).is(FluidTags.LAVA) || sl.getFluidState(pos).is(FluidTags.WATER)) return false;
        AABB box = getType().getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return sl.noCollision(this, box);
    }

    // ---------------------------------------------------------------- status

    private void setStatus(ServerLevel sl, Status next) {
        Status from = status();
        if (from == next) return;
        entityData.set(DATA_STATUS, next.ordinal());
        if (next == Status.WORKING) {
            Milestones.awardOwner(sl, blockPosition(), owner, Milestones.ROBOT_WORKING);
            if (tickCount - lastWorking > 200) CoreSounds.play(this, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.6F, 1.0F);
        } else if (next.stalled() && (next != lastStall || tickCount - lastStallTick > 1200)) {
            // once per kind of stall (again after a minute), so a robot that flips between work and a stall stays quiet
            lastStall = next;
            lastStallTick = tickCount;
            CoreSounds.play(this, CoreSounds.ROBOT_ERROR, SoundSource.NEUTRAL, 0.6F, 1.0F);
            ServerPlayer p = Milestones.nearbyOwner(sl, blockPosition(), owner, 32);
            if (p != null) {
                p.displayClientMessage(Component.translatable(next == Status.NO_ENERGY ? "message.robotica.robot_no_energy"
                        : "message.robotica.robot_full", getDisplayName()), true);
            }
        }
    }

    private void stallParticles(ServerLevel sl) {
        if (status() == Status.NO_ENERGY) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() + 0.1, getZ(), 6, 0.2, 0.1, 0.2, 0.01);
        } else {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    /** Plays a work sound at most once a second (several jobs can finish within a second at high Mk). */
    protected void workSound(ServerLevel sl, BlockPos at, Supplier<SoundEvent> sound, float volume, float pitch) {
        if (tickCount - lastWorkSound < 20) return;
        lastWorkSound = tickCount;
        CoreSounds.play(sl, at, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /** Block crumbs where a block was broken, with the break sound at most twice a second. */
    protected void breakFx(ServerLevel sl, BlockPos pos, BlockState state) {
        if (tickCount - lastBreakSound >= 10) {
            lastBreakSound = tickCount;
            sl.levelEvent(2001, pos, Block.getId(state));
            return;
        }
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return;
        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.25, 0.25, 0.25, 0.05);
    }

    // ---------------------------------------------------------------- work effects

    /** Fires the arm swing and sparks of an action at {@code at}. Server side; rate limited (a sparkle in between). */
    protected void workFx(BlockPos at, int kind) {
        if (tickCount - lastSwing >= SWING_GAP) {
            lastSwing = tickCount;
        } else if (tickCount - lastSparkle >= SPARKLE_GAP && tickCount - lastSwing >= SPARKLE_GAP) {
            kind = FX_SPARKLE;
        } else {
            return;
        }
        lastSparkle = tickCount;
        entityData.set(DATA_FX_POS, at.immutable());
        entityData.set(DATA_ACTION, ((++actions & 0xFFFFFF) << 2) | (kind & 3));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key) && level().isClientSide && entityData.get(DATA_ACTION) != 0) {
            int kind = entityData.get(DATA_ACTION) & 3;
            BlockPos at = entityData.get(DATA_FX_POS);
            if (kind == FX_SPARKLE) {
                com.arno.robotica.automation.entity.BotFx.sparkle(level(), at);
                return;
            }
            fxTarget = at;
            fxKind = kind;
            fxStart = tickCount;
            fxHitPending = true;
        }
    }

    /** Client side: ticks (with partial tick) since the current swing started, or -1 when the arms rest. */
    public float swingTime(float partialTick) {
        if (fxStart == Integer.MIN_VALUE) return -1;
        float t = tickCount - fxStart + partialTick;
        return t >= 0 && t < SWING_TICKS ? t : -1;
    }

    public int swingKind() {
        return fxKind;
    }

    // ---------------------------------------------------------------- growth boost

    private void growthBoost(ServerLevel sl) {
        double mult = growthMultiplier();
        if (mult <= 1.0) return;
        int size = areaSize();
        growthCarry += size * (double) size * AutomationConfig.growthTicksPerColumn() * (mult - 1.0);
        int picks = (int) growthCarry;
        growthCarry -= picks;
        picks = Math.min(picks, 256);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < picks; i++) {
            int x = areaMinX() + sl.random.nextInt(size);
            int z = areaMinZ() + sl.random.nextInt(size);
            for (int l = 0; l < layerCount(); l++) {
                pos.set(x, layerBase() + l, z);
                if (!sl.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(pos))) break;
                BlockState state = sl.getBlockState(pos);
                if (isGrowable(state)) {
                    state.randomTick(sl, pos.immutable(), sl.random);
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- item pickup

    /** Saplings, seeds, apples and sticks: what decaying leaves and plants drop on their own. */
    protected boolean isNaturalDecayDrop(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.APPLE) || stack.is(Items.STICK) || CropRules.isFarmSeed(stack);
    }

    /** Never something a player threw or that is reserved for a player, never old piles, natural drops a bit longer. */
    private boolean mayVacuum(ServerLevel sl, ItemEntity entity) {
        if (entity.getOwner() instanceof Player || entity.getTarget() != null) return false;
        int age = entity.getAge();
        if (age < 0 || age > MAX_NATURAL_AGE) return false;
        if (age > MAX_ITEM_AGE && !isNaturalDecayDrop(entity.getItem())) return false;
        try {
            FakePlayer fake = FakePlayerFactory.get(sl, fakeProfile());
            ItemEntityPickupEvent.Pre event = new ItemEntityPickupEvent.Pre(fake, entity);
            NeoForge.EVENT_BUS.post(event);
            return event.canPickup() != TriState.FALSE;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void pickupDrops(ServerLevel sl) {
        if (!pending.isEmpty()) return;
        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, areaBox().inflate(0, 3, 0), e -> e.isAlive() && !e.hasPickUpDelay());
        int taken = 0;
        for (ItemEntity entity : items) {
            if (taken >= MAX_PICKUP) break;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !mayVacuum(sl, entity) || !canAcceptFully(sl, stack)) continue;
            output(stack);
            entity.discard();
            taken++;
        }
    }

    // ---------------------------------------------------------------- storage and output

    /** The item handler block within {@link #STORAGE_RANGE} of home with the most slots (ties: nearest); cached. */
    @Nullable
    public IItemHandler storage(ServerLevel sl) {
        if (storagePos != null) {
            IItemHandler cap = sl.isLoaded(storagePos) ? sl.getCapability(Capabilities.ItemHandler.BLOCK, storagePos, null) : null;
            if (cap != null) return cap;
            storagePos = null;
        }
        long now = sl.getGameTime();
        if (now - storageSearchedAt < 100) return null;
        storageSearchedAt = now;
        BlockPos h = home();
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
                storagePos = p.immutable();
                found = cap;
                bestDist = d;
                bestSlots = slots;
            }
        }
        return found;
    }

    /** A storage block near home was found at the last look (server side; the menu syncs it). */
    public boolean hasStorage() {
        return storagePos != null;
    }

    /** Finds storage again on the next call (a chest was just placed). */
    public void forgetStorage() {
        storagePos = null;
        storageSearchedAt = -1000;
    }

    /** Keeps the reserved amount in the pouch, then storage, then the pouch; whatever does not fit waits in the pending queue. */
    public void output(ItemStack stack) {
        if (stack.isEmpty() || !(level() instanceof ServerLevel sl)) return;
        stack = stack.copy();
        int keep = keepAmount(stack);
        if (keep > 0) {
            int toKeep = Math.min(stack.getCount(), keep - countInPouch(stack));
            if (toKeep > 0) {
                ItemStack left = ItemHandlerHelper.insertItem(pouch, stack.copyWithCount(toKeep), false);
                stack.shrink(toKeep - left.getCount());
            }
        }
        if (stack.isEmpty()) return;
        IItemHandler store = storage(sl);
        if (store != null) stack = ItemHandlerHelper.insertItemStacked(store, stack, false);
        if (stack.isEmpty()) return;
        stack = ItemHandlerHelper.insertItem(pouch, stack, false);
        if (!stack.isEmpty()) pending.add(stack);
    }

    public boolean canAcceptFully(ServerLevel sl, ItemStack stack) {
        ItemStack left = stack.copy();
        IItemHandler store = storage(sl);
        if (store != null) left = ItemHandlerHelper.insertItemStacked(store, left, true);
        return left.isEmpty() || ItemHandlerHelper.insertItem(pouch, left, true).isEmpty();
    }

    public boolean hasPendingOutput() {
        return !pending.isEmpty();
    }

    protected int countInPouch(ItemStack stack) {
        int n = 0;
        for (int i = 0; i < pouch.getSlots(); i++) {
            ItemStack s = pouch.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(s, stack)) n += s.getCount();
        }
        return n;
    }

    /** Takes one of this item from the pouch. */
    protected boolean takeFromPouch(net.minecraft.world.item.Item item) {
        for (int i = 0; i < pouch.getSlots(); i++) {
            ItemStack stack = pouch.getStackInSlot(i);
            if (!stack.isEmpty() && stack.is(item)) {
                pouch.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    private void flushPending(ServerLevel sl) {
        IItemHandler store = storage(sl);
        for (int i = 0; i < pending.size(); i++) {
            ItemStack stack = pending.get(i);
            if (store != null) stack = ItemHandlerHelper.insertItemStacked(store, stack, false);
            if (!stack.isEmpty()) stack = ItemHandlerHelper.insertItem(pouch, stack, false);
            if (stack.isEmpty()) pending.remove(i--);
            else pending.set(i, stack);
        }
    }

    /** Moves pouch contents (except the reserved seeds and saplings) into the storage. */
    private void flushPouch(ServerLevel sl) {
        IItemHandler store = null;
        List<ItemStack> reserveItems = new ArrayList<>();
        List<Integer> reserveLeft = new ArrayList<>();
        for (int i = 0; i < pouch.getSlots(); i++) {
            ItemStack stack = pouch.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int reserved = 0;
            int keep = keepAmount(stack);
            if (keep > 0) {
                int idx = -1;
                for (int k = 0; k < reserveItems.size(); k++) {
                    if (ItemStack.isSameItemSameComponents(reserveItems.get(k), stack)) {
                        idx = k;
                        break;
                    }
                }
                if (idx < 0) {
                    reserveItems.add(stack.copy());
                    reserveLeft.add(keep);
                    idx = reserveItems.size() - 1;
                }
                reserved = Math.min(stack.getCount(), reserveLeft.get(idx));
                reserveLeft.set(idx, reserveLeft.get(idx) - reserved);
            }
            int pushable = stack.getCount() - reserved;
            if (pushable <= 0) continue;
            if (store == null) store = storage(sl);
            if (store == null) return;
            ItemStack left = ItemHandlerHelper.insertItemStacked(store, stack.copyWithCount(pushable), false);
            int moved = pushable - left.getCount();
            if (moved > 0) pouch.extractItem(i, moved, false);
        }
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Tier", tier());
        tag.putInt("Energy", energy.getEnergyStored());
        if (homeSet) tag.put("Home", NbtUtils.writeBlockPos(home()));
        tag.putBoolean("ShowArea", showArea());
        tag.put("Battery", battery.serializeNBT(registryAccess()));
        tag.put("Upgrades", upgrades.serializeNBT(registryAccess()));
        tag.put("Pouch", pouch.serializeNBT(registryAccess()));
        ListTag list = new ListTag();
        for (ItemStack stack : pending) list.add(stack.save(registryAccess()));
        tag.put("Pending", list);
        saveExtra(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        entityData.set(DATA_TIER, Mth.clamp(tag.getInt("Tier"), 1, 4));
        entityData.set(DATA_SHOW_AREA, tag.getBoolean("ShowArea"));
        if (tag.contains("Battery")) battery.deserializeNBT(registryAccess(), tag.getCompound("Battery"));
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(registryAccess(), tag.getCompound("Upgrades"));
        if (tag.contains("Pouch")) pouch.deserializeNBT(registryAccess(), tag.getCompound("Pouch"));
        energy.setEnergy(tag.getInt("Energy"));
        pending.clear();
        for (Tag t : tag.getList("Pending", Tag.TAG_COMPOUND)) ItemStack.parse(registryAccess(), t).ifPresent(pending::add);
        NbtUtils.readBlockPos(tag, "Home").ifPresent(this::setHome);
        loadExtra(tag);
        recalc();
    }
}
