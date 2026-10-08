package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.block.FarmBotBlock;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Shared logic of the farm bots (Stumpy and Sprout): tiered square area, a cursor scan that finds one target at a time,
 * an action timer, a growth boost that random-ticks a few plants per second, and drop pickup.
 */
public abstract class FarmBotBlockEntity extends AreaWorkerBlockEntity {
    public static final Set<UpgradeKind> KINDS = EnumSet.of(UpgradeKind.SPEED, UpgradeKind.RANGE, UpgradeKind.EFFICIENCY, UpgradeKind.GROWTH);
    private static final int SCAN_PER_TICK = 64;
    private static final int MAX_PICKUP = 16;

    @Nullable
    protected BlockPos target;
    protected int scanCursor;
    /** Extra scan budget an expensive {@link #isTarget} check spends (reset before every check). */
    protected int scanCost;
    private int scanWait;
    private int progress;
    /** Ticks of rest left after a big action (Stumpy after a tree). Transient. */
    protected int rest;
    private double growthCarry;

    protected FarmBotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, KINDS);
    }

    // ---- subclass hooks ----

    /** Work radius before range cards. */
    protected int baseRadius() {
        return AutomationConfig.farmRadius(tier());
    }

    /** Energy per tick while working, config value. */
    protected abstract int baseFePerTick();

    /** First Y layer scanned and number of layers. */
    protected abstract int layerBase();

    protected abstract int layerCount();

    /** True if this block position is worth an action right now. Must be cheap. */
    protected abstract boolean isTarget(ServerLevel level, BlockPos pos, BlockState state);

    /** Performs the action on {@link #target}. Return false if the target turned out invalid. */
    protected abstract boolean act(ServerLevel level, BlockPos target);

    /** Blocks that get the growth boost random ticks. */
    protected abstract boolean isGrowable(BlockState state);

    @Override
    protected int areaMinY() {
        return layerBase();
    }

    @Override
    protected int areaMaxY() {
        return layerBase() + layerCount();
    }

    @Override
    public int tier() {
        BlockState state = getBlockState();
        return state.hasProperty(FarmBotBlock.TIER) ? state.getValue(FarmBotBlock.TIER) : 1;
    }

    @Override
    protected void recalc() {
        int radius = baseRadius() + 2 * upgrades.level(UpgradeKind.RANGE);
        areaSize = 2 * radius + 1;
        scanCursor = 0;
        target = null;
    }

    /** Called by the block after a Mk kit was applied. */
    public void onTierChanged() {
        recalc();
        progress = 0;
        setChangedAndSync();
    }

    public int actionInterval() {
        int base = CoreConfig.scaleInterval(AutomationConfig.farmInterval(tier()));
        return Math.max(1, base / Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    /**
     * Speed multiplier that actually shortens the action interval. Once the interval is floored at 1 tick (Mk4 with
     * speed cards) a bigger card multiplier gains nothing, so it must not be paid for either.
     */
    public int effectiveSpeedMultiplier() {
        int base = Math.max(1, CoreConfig.scaleInterval(AutomationConfig.farmInterval(tier())));
        int actual = actionInterval();
        return Math.max(1, base / Math.max(1, actual));
    }

    public double growthMultiplier() {
        return AutomationConfig.farmGrowth(tier()) + Upgrades.growthBonus(upgrades.level(UpgradeKind.GROWTH));
    }

    // ---- work loop ----

    @Override
    protected Status work(ServerLevel sl) {
        if (energy.getEnergyStored() > 0) {
            if (age % 20 == 0) growthBoost(sl);
            if (age % 60 == 30) pickupDrops(sl);
        }
        if (busyTick(sl)) return Status.WORKING;
        if (rest > 0) {
            rest--;
            return Status.WORKING;
        }
        if (target == null) scan(sl);
        if (target == null) return energy.getEnergyStored() <= 0 ? Status.NO_ENERGY : Status.IDLE;
        if (!energy.consume(scaledDrain(baseFePerTick(), effectiveSpeedMultiplier()))) return Status.NO_ENERGY;
        progress++;
        onWorkTick(sl);
        if (progress >= actionInterval()) {
            progress = 0;
            BlockPos t = target;
            target = null;
            if (!sl.isLoaded(t) || !act(sl, t)) scanWait = 0;
        }
        return Status.WORKING;
    }

    private void scan(ServerLevel sl) {
        if (scanWait > 0) {
            scanWait--;
            return;
        }
        int size = areaSize;
        int layerSize = size * size;
        int total = layerSize * layerCount();
        int minX = areaMinX();
        int minZ = areaMinZ();
        int baseY = layerBase();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < SCAN_PER_TICK; n++) {
            n += scanCost;
            scanCost = 0;
            if (scanCursor >= total) {
                scanCursor = 0;
                scanWait = 40;
                onScanWrapped();
                return;
            }
            int layer = scanCursor / layerSize;
            int idx = scanCursor % layerSize;
            scanCursor++;
            pos.set(minX + idx % size, baseY + layer, minZ + idx / size);
            if (!sl.isLoaded(pos)) continue;
            BlockState state = sl.getBlockState(pos);
            if (isTarget(sl, pos, state)) {
                target = pos.immutable();
                return;
            }
        }
    }

    protected void onScanWrapped() {
    }

    /** Called every tick the bot spends on a target (before the action itself); used for the ambient work sound. */
    protected void onWorkTick(ServerLevel sl) {
    }

    /** Runs a multi-tick action (Stumpy's felling wave) before anything else. True while it is busy. */
    protected boolean busyTick(ServerLevel sl) {
        return false;
    }

    // ---- work effects ----
    // An action fires one block event; the client swings the arms and spawns the sparks (so they follow its particle
    // setting). The two event bytes pack the target: dx and dz (6 bits each, 0 = no target), the target layer of the
    // scan area, and the effect kind in the bits the layer leaves free. The top kind value is a small sparkle without a
    // swing, used when actions come faster than a swing.

    /** Ticks of a whole arm swing and the tick at which it hits. */
    public static final int SWING_TICKS = 7;
    public static final int HIT_TICK = 2;
    /** Minimum gap between swings, and between plain sparkles. */
    private static final int SWING_GAP = 8;
    private static final int SPARKLE_GAP = 2;

    private long lastSwing = -100;
    private long lastSparkle = -100;
    // client side animation state
    private long fxStart = Long.MIN_VALUE;
    private int fxKind;
    private boolean fxHitPending;
    @Nullable
    private BlockPos fxTarget;

    private int layerBits() {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(layerCount() - 1));
    }

    /** Kind value of the plain sparkle (the highest kind the event can carry). */
    public int sparkleKind() {
        return (1 << (4 - layerBits())) - 1;
    }

    /** Fires the work effect of an action at {@code at}. Server side; rate limited per bot. */
    public void workFx(ServerLevel sl, BlockPos at, int kind) {
        if (age - lastSwing >= SWING_GAP) {
            lastSwing = age;
        } else if (age - lastSparkle >= SPARKLE_GAP && age - lastSwing >= SPARKLE_GAP) {
            kind = sparkleKind();
        } else {
            return;
        }
        lastSparkle = age;
        int bits = layerBits();
        int dx = at.getX() - worldPosition.getX();
        int dz = at.getZ() - worldPosition.getZ();
        int layer = at.getY() - layerBase();
        int v = Math.min(kind, sparkleKind()) << (12 + bits);
        if (Math.abs(dx) <= 31 && Math.abs(dz) <= 31 && layer >= 0 && layer < 1 << bits) {
            v |= (dx + 32) | (dz + 32) << 6 | layer << 12;
        }
        sl.blockEvent(worldPosition, getBlockState().getBlock(), v >>> 8, v & 0xFF);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level == null) return false;
        if (!level.isClientSide) return true;
        int v = (id & 0xFF) << 8 | (param & 0xFF);
        int bits = layerBits();
        int kind = v >>> (12 + bits);
        int rawDx = v & 63;
        BlockPos target = rawDx == 0 ? null : new BlockPos(worldPosition.getX() + rawDx - 32, layerBase() + ((v >>> 12) & ((1 << bits) - 1)),
                worldPosition.getZ() + ((v >>> 6) & 63) - 32);
        if (kind == sparkleKind()) {
            if (target != null) BotFx.sparkle(level, target);
            return true;
        }
        fxTarget = target;
        fxKind = kind;
        fxStart = level.getGameTime();
        fxHitPending = true;
        return true;
    }

    /** Client tick: plays the hit effects when the swing lands. */
    public void clientTick() {
        if (fxHitPending && level != null && level.getGameTime() - fxStart >= HIT_TICK) {
            fxHitPending = false;
            playHitFx(level, fxKind, fxTarget);
        }
    }

    /** Client side: particles and sounds of an action as the swing lands. {@code target} is null when it did not fit the event. */
    protected abstract void playHitFx(Level level, int kind, @Nullable BlockPos target);

    /** Client side: ticks (with partial tick) since the current swing started, or -1 when the arms rest. */
    public float swingTime(float partialTick) {
        if (level == null || fxStart == Long.MIN_VALUE) return -1;
        float t = level.getGameTime() - fxStart + partialTick;
        return t >= 0 && t < SWING_TICKS ? t : -1;
    }

    /** Client side: kind of the current or last swing. */
    public int swingKind() {
        return fxKind;
    }

    // ---- growth boost ----

    private void growthBoost(ServerLevel sl) {
        double mult = growthMultiplier();
        if (mult <= 1.0) return;
        int size = areaSize;
        growthCarry += size * (double) size * AutomationConfig.growthTicksPerColumn() * (mult - 1.0);
        int picks = (int) growthCarry;
        growthCarry -= picks;
        picks = Math.min(picks, 256);
        int layers = layerCount();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < picks; i++) {
            int x = areaMinX() + sl.random.nextInt(size);
            int z = areaMinZ() + sl.random.nextInt(size);
            for (int l = 0; l < layers; l++) {
                pos.set(x, layerBase() + l, z);
                if (!sl.isLoaded(pos)) break;
                BlockState state = sl.getBlockState(pos);
                if (isGrowable(state)) {
                    state.randomTick(sl, pos.immutable(), sl.random);
                    break;
                }
            }
        }
    }

    // ---- item pickup ----

    /** Items older than this are only taken when they are natural leaf decay products. */
    private static final int MAX_ITEM_AGE = 200;
    private static final int MAX_NATURAL_AGE = 1200;

    /** Saplings, seeds, apples and sticks: what decaying leaves and plants drop on their own near the bot's trees. */
    protected boolean isNaturalDecayDrop(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.APPLE) || stack.is(Items.STICK) || stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)
                || stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD) || SproutBlockEntity.isFarmSeed(stack);
    }

    /**
     * Whether the vacuum may take this item entity: never something a player threw or that is reserved for a player,
     * never old piles (death piles, chest contents), and only natural decay drops past the first few seconds.
     */
    private boolean mayVacuum(ServerLevel sl, ItemEntity entity) {
        if (entity.getOwner() instanceof Player || entity.getTarget() != null) return false;
        ItemStack stack = entity.getItem();
        int age = entity.getAge();
        if (age < 0 || age > MAX_NATURAL_AGE) return false;
        if (age > MAX_ITEM_AGE && !isNaturalDecayDrop(stack)) return false;
        return pickupAllowed(sl, entity);
    }

    /** Posts the pickup event with the owner's fake player so protection mods can veto. */
    private boolean pickupAllowed(ServerLevel sl, ItemEntity entity) {
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
        if (hasPendingOutput()) return;
        AABB box = areaBox().inflate(0, 3, 0);
        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay());
        int taken = 0;
        for (ItemEntity entity : items) {
            if (taken >= MAX_PICKUP) break;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !mayVacuum(sl, entity) || !canAcceptFully(stack)) continue;
            output(stack);
            entity.discard();
            taken++;
        }
    }

    // ---- persistence ----

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        progress = tag.getInt("progress");
    }
}
