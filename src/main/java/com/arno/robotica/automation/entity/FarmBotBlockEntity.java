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
    private double growthCarry;

    protected FarmBotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, KINDS, 4);
    }

    // ---- subclass hooks ----

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
        int radius = AutomationConfig.farmRadius(tier()) + 2 * upgrades.level(UpgradeKind.RANGE);
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
        if (target == null) scan(sl);
        if (target == null) return energy.getEnergyStored() <= 0 ? Status.NO_ENERGY : Status.IDLE;
        if (!energy.consume(scaledDrain(baseFePerTick(), effectiveSpeedMultiplier()))) return Status.NO_ENERGY;
        progress++;
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
