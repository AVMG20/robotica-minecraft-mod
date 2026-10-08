package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.core.CoreSounds;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Lumber bot: fells whole trees (with their natural leaves) as one paid action. The tree comes down in a wave over about
 * a second, logs bottom-up and then leaves; then it replants saplings from its buffer and rests. Picks up drops.
 */
public class StumpyBlockEntity extends FarmBotBlockEntity {
    /** Log clusters without natural leaves (player builds). Transient. */
    private final LongOpenHashSet ignored = new LongOpenHashSet();
    private int wraps;

    /** Effect kind of a chop (the event's other kind is the plain sparkle). */
    private static final int CHOP = 0;
    /** Ticks the logs of a tree take to come down (more logs per tick for big trees), then the leaves. */
    private static final int WAVE_TICKS = 20;
    private static final int LEAF_TICKS = 6;
    private static final long[] NO_WAVE = new long[0];

    /** The felling wave: logs, then leaves (from {@link #waveLeafStart}), broken a few per tick. Saved. */
    private long[] wave = NO_WAVE;
    private int waveCursor;
    private int waveLeafStart;
    private int waveFelled;
    private float waveCarry;
    private long[] waveBases = NO_WAVE;
    private String wavePrefix = "";

    public StumpyBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.STUMPY_BE.get(), pos, state);
    }

    @Override
    public String blockKey() {
        return "block.robotica.stumpy";
    }

    /** One block less on each side than Sprout (Mk1 7x7): trees are worth more than a crop. */
    @Override
    protected int baseRadius() {
        return Math.max(2, super.baseRadius() - 1);
    }

    @Override
    protected int baseFePerTick() {
        return AutomationConfig.stumpyFe();
    }

    @Override
    protected int layerBase() {
        return worldPosition.getY() - 3;
    }

    @Override
    protected int layerCount() {
        return 8;
    }

    @Override
    protected boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(BlockTags.LOGS) && !ignored.contains(pos.asLong());
    }

    @Override
    protected boolean isGrowable(BlockState state) {
        return state.is(BlockTags.SAPLINGS);
    }

    @Override
    protected int keepAmount(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) ? 16 : 0;
    }

    @Override
    protected void onWorkTick(ServerLevel sl) {
        if (CoreSounds.due(sl, worldPosition, 50)) CoreSounds.play(sl, worldPosition, CoreSounds.SAW_WHIR, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    @Override
    protected void onScanWrapped() {
        if (++wraps % 8 == 0) ignored.clear();
    }

    @Override
    protected boolean act(ServerLevel sl, BlockPos start) {
        BlockState startState = sl.getBlockState(start);
        if (!startState.is(BlockTags.LOGS)) return false;
        TreeScan tree = TreeScan.scan(sl, start, AutomationConfig.maxLogs(), true);
        if (tree.naturalLeaves == 0) {
            for (BlockPos p : tree.logs) ignored.add(p.asLong());
            if (ignored.size() > 8192) ignored.clear();
            return false;
        }
        // Every log costs FE when the tree comes down. A huge tree is capped at half the buffer: the buffer drains a
        // little every working tick, so it is never exactly full and a full-buffer price could never be paid.
        // Not enough yet: skip this round, the battery tops the buffer up and the next scan finds the tree again.
        long cost = Math.min((long) scaledDrain(AutomationConfig.stumpyFePerLog(), 1) * tree.logs.size(), energy.getMaxEnergyStored() / 2);
        if (!energy.consume((int) cost)) return false;
        startWave(tree, start, BuiltInRegistries.BLOCK.getKey(startState.getBlock()).getPath());
        workSound(sl, start, CoreSounds.STUMPY_CHOP, 1.0F, 0.9F + sl.random.nextFloat() * 0.2F);
        workFx(sl, start, CHOP);
        return true;
    }

    // ---- felling wave ----

    /** Queues the paid-for tree: logs bottom-up (nearest the trunk first on a level), then the leaves bottom-up. */
    private void startWave(TreeScan tree, BlockPos start, String logPath) {
        List<BlockPos> logs = new ArrayList<>(tree.logs);
        logs.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(p -> (p.getX() - start.getX()) * (p.getX() - start.getX()) + (p.getZ() - start.getZ()) * (p.getZ() - start.getZ())));
        List<BlockPos> leaves = new ArrayList<>(tree.leaves);
        leaves.sort(Comparator.comparingInt(BlockPos::getY));
        wave = new long[logs.size() + leaves.size()];
        for (int i = 0; i < logs.size(); i++) wave[i] = logs.get(i).asLong();
        for (int i = 0; i < leaves.size(); i++) wave[logs.size() + i] = leaves.get(i).asLong();
        waveLeafStart = logs.size();
        waveCursor = 0;
        waveFelled = 0;
        waveCarry = 0;
        waveBases = trunkBases(logs);
        wavePrefix = logPath.replace("stripped_", "").replace("_log", "").replace("_wood", "")
                .replace("_stem", "").replace("_hyphae", "");
        setChanged();
    }

    /** The lowest logs on dirt inside the area: where saplings go after the wave. */
    private long[] trunkBases(List<BlockPos> sortedLogs) {
        Level level = getLevel();
        LongArrayList bases = new LongArrayList();
        int lowest = Integer.MAX_VALUE;
        for (BlockPos p : sortedLogs) {
            if (p.getY() > lowest) break;
            if (!inArea(p) || level == null || !level.getBlockState(p.below()).is(BlockTags.DIRT)) continue;
            lowest = p.getY();
            bases.add(p.asLong());
        }
        return bases.toLongArray();
    }

    public boolean waveActive() {
        return waveCursor < wave.length;
    }

    @Override
    protected boolean busyTick(ServerLevel sl) {
        if (!waveActive()) return false;
        stepWave(sl);
        return true;
    }

    /** Breaks the next few blocks of the wave. Logs that are gone or protected by now are skipped. */
    private void stepWave(ServerLevel sl) {
        int budget;
        if (waveCursor < waveLeafStart) {
            waveCarry += Math.max(waveLeafStart / (float) WAVE_TICKS, 1.0F / 3.0F);
            budget = (int) waveCarry;
            waveCarry -= budget;
        } else {
            budget = Math.max(1, Mth.ceil((wave.length - waveLeafStart) / (float) LEAF_TICKS));
        }
        ItemStack tool = new ItemStack(Items.IRON_AXE);
        List<ItemStack> drops = new ArrayList<>();
        int shown = 0;
        while (budget-- > 0 && waveCursor < wave.length) {
            boolean log = waveCursor < waveLeafStart;
            BlockPos p = BlockPos.of(wave[waveCursor++]);
            if (sl.isLoaded(p)) {
                BlockState state = sl.getBlockState(p);
                if (log && state.is(BlockTags.LOGS) && mayBreak(sl, p, state)) {
                    for (ItemStack drop : Block.getDrops(state, sl, p, sl.getBlockEntity(p), null, tool)) Drops.merge(drops, drop);
                    if (shown++ < 2) {
                        if (shown == 1) sl.levelEvent(2001, p, Block.getId(state));
                        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
                    }
                    sl.removeBlock(p, false);
                    waveFelled++;
                } else if (!log && TreeScan.isLeafLike(state) && TreeScan.isNatural(state) && mayBreak(sl, p, state)) {
                    for (ItemStack drop : Block.getDrops(state, sl, p, sl.getBlockEntity(p), null, tool)) Drops.merge(drops, drop);
                    sl.removeBlock(p, false);
                }
            }
            // The leaves start on the next tick.
            if (log && waveCursor == waveLeafStart) break;
        }
        for (ItemStack drop : drops) output(drop);
        if (!waveActive()) finishWave(sl);
        setChanged();
    }

    /** After the wave: replant at the trunk base and rest. */
    private void finishWave(ServerLevel sl) {
        rest = waveFelled * AutomationConfig.stumpyTicksPerLog() / Math.max(1, effectiveSpeedMultiplier());
        int planted = 0;
        for (long base : waveBases) {
            if (planted >= 4) break;
            BlockPos p = BlockPos.of(base);
            if (inArea(p) && sl.isLoaded(p) && sl.getBlockState(p).isAir() && sl.getBlockState(p.below()).is(BlockTags.DIRT)
                    && plantSapling(sl, p, wavePrefix)) planted++;
        }
        wave = NO_WAVE;
        waveCursor = 0;
        waveLeafStart = 0;
        waveBases = NO_WAVE;
    }

    private boolean plantSapling(ServerLevel sl, BlockPos pos, String prefix) {
        int bestSlot = -1;
        int bestScore = 0;
        BlockState bestState = null;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS) || !(stack.getItem() instanceof BlockItem bi)) continue;
            BlockState state = bi.getBlock().defaultBlockState();
            if (!state.canSurvive(sl, pos)) continue;
            String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int score = name.startsWith(prefix + "_") ? 2 : 1;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
                bestState = state;
            }
        }
        if (bestSlot < 0) return false;
        sl.setBlock(pos, bestState, Block.UPDATE_ALL);
        buffer.extractItem(bestSlot, 1, false);
        return true;
    }

    // ---- effects (client side, from the block event) ----

    @Override
    protected void playHitFx(Level level, int kind, @Nullable BlockPos target) {
        BotFx.whoosh(level, worldPosition, 0.2F);
        if (target == null) return;
        // At the trunk's face on the bot's side, not hidden inside the log.
        double[] face = BotFx.faceToward(worldPosition, target.getX() + 0.5, target.getZ() + 0.5, 0.6);
        double x = face[0], y = target.getY() + 0.4, z = face[1];
        BotFx.sparkLine(level, worldPosition, x, y, z);
        BotFx.slash(level, x, y, z, 0.7F);
        BotFx.burst(level, x, y, z, 3, BotFx.CYAN, 2);
        BotFx.sound(level, x, y, z, CoreSounds.SHOCK_ZAP.get(), 0.3F, 1.3F + level.random.nextFloat() * 0.3F);
    }

    // ---- persistence: an unfinished wave carries on after a reload ----

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveExtra(tag, registries);
        if (waveActive()) {
            CompoundTag w = new CompoundTag();
            w.putLongArray("left", Arrays.copyOfRange(wave, waveCursor, wave.length));
            w.putInt("leafStart", Math.max(0, waveLeafStart - waveCursor));
            w.putInt("felled", waveFelled);
            w.putFloat("carry", waveCarry);
            w.putLongArray("bases", waveBases);
            w.putString("prefix", wavePrefix);
            tag.put("wave", w);
        }
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadExtra(tag, registries);
        wave = NO_WAVE;
        waveCursor = 0;
        waveLeafStart = 0;
        waveBases = NO_WAVE;
        if (tag.contains("wave")) {
            CompoundTag w = tag.getCompound("wave");
            wave = w.getLongArray("left");
            waveLeafStart = Math.min(wave.length, w.getInt("leafStart"));
            waveFelled = w.getInt("felled");
            waveCarry = w.getFloat("carry");
            waveBases = w.getLongArray("bases");
            wavePrefix = w.getString("prefix");
        }
    }
}
