package com.arno.robotica.automation.entity;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Mines a real hole straight down: a square area centred on the machine, from the block below it to the world
 * bottom, layer by layer with a resumable cursor. Fluids are plugged with cobblestone, block entities and
 * unbreakable blocks are never touched.
 */
public class ExcavatorBlockEntity extends AreaWorkerBlockEntity {
    public static final Set<UpgradeKind> KINDS = EnumSet.of(UpgradeKind.SPEED, UpgradeKind.RANGE, UpgradeKind.EFFICIENCY,
            UpgradeKind.FORTUNE, UpgradeKind.SILK, UpgradeKind.VOID);
    /** Square side per number of range cards 0-4 (0 cards: the config size). */
    public static final int[] RANGE_SIZES = {8, 16, 32, 48, 64};
    public static final TagKey<Item> VOIDABLE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Robotica.MODID, "voidable"));
    private static final int SKIP_PER_TICK = 256;

    private int cursorLayer;
    private int cursorIdx;
    private int cursorSize = -1;
    private int progress;
    private boolean done;
    @Nullable
    private BlockPos target;
    private int targetKind;
    /** Game tests shrink the area through this. 0 = off. */
    private int sizeOverride;
    private int toolKey = -1;
    private ItemStack toolStack = ItemStack.EMPTY;

    public ExcavatorBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.EXCAVATOR_BE.get(), pos, state, KINDS, 5);
    }

    @Override
    public String blockKey() {
        return "block.robotica.excavator";
    }

    public void setSizeOverride(int size) {
        sizeOverride = size;
        recalc();
    }

    @Override
    protected void recalc() {
        int range = upgrades.level(UpgradeKind.RANGE);
        int size = range <= 0 ? AutomationConfig.excavatorSize() : RANGE_SIZES[Math.min(range, 4)];
        if (sizeOverride > 0) size = sizeOverride;
        if (cursorSize != -1 && size != cursorSize) done = false;
        areaSize = size;
        toolKey = -1;
    }

    @Override
    protected int areaMinY() {
        return level != null ? level.getMinBuildHeight() : worldPosition.getY() - 64;
    }

    @Override
    protected int areaMaxY() {
        return worldPosition.getY();
    }

    @Override
    public int guiExtra() {
        return done ? 0 : worldPosition.getY() - 1 - cursorLayer;
    }

    /** Share of the hole that is dug, 0-100, from the cursor (layers from the top down to the world bottom). */
    @Override
    public int guiProgress() {
        if (done) return 100;
        int layers = layerCount();
        int size = Math.max(1, cursorSize > 0 ? cursorSize : areaSize);
        long total = (long) layers * size * size;
        if (total <= 0) return 100;
        long dug = (long) cursorLayer * size * size + cursorIdx;
        return (int) Math.min(99, dug * 100 / total);
    }

    public boolean isDone() {
        return done;
    }

    private int layerCount() {
        return Math.max(0, worldPosition.getY() - areaMinY());
    }

    public int actionInterval() {
        int base = CoreConfig.scaleInterval(AutomationConfig.excavatorInterval());
        return Math.max(1, base / Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)));
    }

    public int energyPerBlock() {
        int speed = upgrades.level(UpgradeKind.SPEED);
        int eff = upgrades.level(UpgradeKind.EFFICIENCY);
        // Steep on purpose: the Excavator is the strongest machine, each speed card costs more FE per block than the last.
        return (int) Math.round(CoreConfig.scaleEnergy(AutomationConfig.excavatorFe()) * Upgrades.steepEnergyMultiplier(speed, eff));
    }

    // ---- work loop ----

    @Override
    protected Status work(ServerLevel sl) {
        if (done) return Status.IDLE;
        if (target == null) findTarget(sl);
        if (target == null) return Status.IDLE;
        int cost = energyPerBlock();
        if (progress < actionInterval()) {
            if (energy.getEnergyStored() < cost) return Status.NO_ENERGY;
            progress++;
            if (CoreSounds.due(sl, worldPosition, 60)) CoreSounds.play(sl, worldPosition, CoreSounds.DRILL_GRIND, SoundSource.NEUTRAL, 0.6F, 1.0F);
            if (progress < actionInterval()) return Status.WORKING;
        }
        if (!energy.consume(cost)) return Status.NO_ENERGY;
        progress = 0;
        BlockPos pos = target;
        int kind = targetKind;
        target = null;
        dig(sl, pos, kind);
        return Status.WORKING;
    }

    /** Advances the cursor over cheap skips (air, unbreakable, block entities) until a block needs an action. */
    private void findTarget(ServerLevel sl) {
        int size = areaSize;
        if (cursorSize != size) {
            if (cursorSize != -1) cursorLayer = 0; // area changed: sweep again from the top, mined space is skipped fast
            cursorSize = size;
            cursorIdx = 0;
        }
        int layerSize = size * size;
        int layers = layerCount();
        int minX = areaMinX();
        int minZ = areaMinZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < SKIP_PER_TICK; n++) {
            if (cursorLayer >= layers) {
                done = true;
                finishedSound(sl);
                setChangedAndSync();
                return;
            }
            if (cursorIdx >= layerSize) {
                cursorIdx = 0;
                cursorLayer++;
                continue;
            }
            pos.set(minX + cursorIdx % size, worldPosition.getY() - 1 - cursorLayer, minZ + cursorIdx / size);
            if (!sl.isLoaded(pos)) return; // wait for the chunk, keep the cursor
            BlockState state = sl.getBlockState(pos);
            int kind = classify(sl, pos, state);
            if (kind != 0) {
                target = pos.immutable();
                targetKind = kind;
                return;
            }
            if (!state.isAir()) n += 15; // unbreakable or protected: a little more costly than air
            cursorIdx++;
        }
    }

    /** 0 = skip, 1 = mine, 2 = plug fluid. */
    private int classify(ServerLevel sl, BlockPos pos, BlockState state) {
        if (state.isAir()) return 0;
        if (state.getBlock() instanceof LiquidBlock) return mayBreak(sl, pos, state) ? 2 : 0;
        if (state.getDestroySpeed(sl, pos) < 0 || state.hasBlockEntity() || sl.getBlockEntity(pos) != null) return 0;
        return mayBreak(sl, pos, state) ? 1 : 0;
    }

    private void dig(ServerLevel sl, BlockPos pos, int kind) {
        cursorIdx++;
        // The world can change while the machine waits: re-check loaded state and the protection event.
        if (!sl.isLoaded(pos)) return;
        BlockState state = sl.getBlockState(pos);
        if (!mayBreak(sl, pos, state)) return;
        if (kind == 2) {
            if (state.getBlock() instanceof LiquidBlock) {
                sl.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                workSound(sl, pos, () -> SoundEvents.LAVA_EXTINGUISH, 0.3F, 1.2F);
            }
            return;
        }
        if (state.isAir() || state.getDestroySpeed(sl, pos) < 0 || state.hasBlockEntity() || sl.getBlockEntity(pos) != null) return;
        boolean plug = !state.getFluidState().isEmpty();
        if (!plug) {
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (sl.isLoaded(n) && !sl.getFluidState(n).isEmpty()) {
                    plug = true;
                    break;
                }
            }
        }
        List<ItemStack> drops = Block.getDrops(state, sl, pos, null, null, tool(sl));
        sl.levelEvent(2001, pos, Block.getId(state));
        workSound(sl, pos, CoreSounds.EXCAVATOR_DIG, 0.7F, 0.9F + sl.random.nextFloat() * 0.2F);
        sl.setBlock(pos, plug ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        boolean voiding = upgrades.level(UpgradeKind.VOID) > 0;
        for (ItemStack drop : drops) {
            if (voiding && drop.is(VOIDABLE)) continue;
            output(drop);
        }
    }

    /** A pickaxe carrying the Fortune or Silk Touch the cards stand for; used only as the loot tool. */
    private ItemStack tool(ServerLevel sl) {
        int silk = upgrades.level(UpgradeKind.SILK);
        int fortune = Upgrades.fortuneEnchantLevel(upgrades.level(UpgradeKind.FORTUNE));
        int key = silk > 0 ? 100 : fortune;
        if (key != toolKey || toolStack.isEmpty()) {
            toolKey = key;
            toolStack = new ItemStack(Items.NETHERITE_PICKAXE);
            var enchants = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (silk > 0) {
                toolStack.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
            } else if (fortune > 0) {
                toolStack.enchant(enchants.getOrThrow(Enchantments.FORTUNE), fortune);
            }
        }
        return toolStack;
    }

    // ---- persistence ----

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("cursorLayer", cursorLayer);
        tag.putInt("cursorIdx", cursorIdx);
        tag.putInt("cursorSize", cursorSize);
        tag.putInt("progress", progress);
        tag.putBoolean("done", done);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        cursorLayer = tag.getInt("cursorLayer");
        cursorIdx = tag.getInt("cursorIdx");
        cursorSize = tag.contains("cursorSize") ? tag.getInt("cursorSize") : -1;
        progress = tag.getInt("progress");
        done = tag.getBoolean("done");
    }
}
