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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
        if (!energy.consume(scaledDrain(baseFePerTick()))) return Status.NO_ENERGY;
        progress++;
        if (progress >= actionInterval()) {
            progress = 0;
            BlockPos t = target;
            target = null;
            if (!act(sl, t)) scanWait = 0;
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

    private void pickupDrops(ServerLevel sl) {
        if (hasPendingOutput()) return;
        AABB box = areaBox().inflate(0, 3, 0);
        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay());
        int taken = 0;
        for (ItemEntity entity : items) {
            if (taken >= MAX_PICKUP) break;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !canAcceptFully(stack)) continue;
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
