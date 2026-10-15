package com.arno.robotica.automation.farmbot;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.entity.BotFx;
import com.arno.robotica.automation.entity.Drops;
import com.arno.robotica.automation.entity.TreeScan;
import com.arno.robotica.core.CoreSounds;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lumber robot: walks up to the nearest natural tree in its area and fells it whole (with its natural leaves) as one paid
 * action. The tree comes down bottom-up in a wave over about a second, leaves along with their level; then it replants
 * saplings from its pouch and rests. When a tree blocks its way (saplings grown up around it), it fells the closest one.
 * It remembers where trees stood (saplings it saw, trunks it felled) and replants such a spot whenever it is empty, also
 * when the tree was cut by someone else.
 */
public class Stumpy extends FarmBot {
    /** Effect kind of a chop. */
    static final int CHOP = 0;
    /** Ticks a tree takes to come down (more blocks per tick for big trees). */
    private static final int WAVE_TICKS = 30;
    /** How far (horizontally) the saw reaches from where Stumpy stands. */
    private static final double REACH = 3.0;
    private static final long[] NO_WAVE = new long[0];
    /** The tool the drops are rolled with (never changed). */
    private static final ItemStack TOOL = new ItemStack(Items.IRON_AXE);
    /** Most tree spots one Stumpy remembers. */
    private static final int MAX_PLOTS = 1024;
    /**
     * Logs of the trees being felled right now, per level, with the entity id of the Stumpy felling them, so two Stumpys
     * never fell (and pay for) the same tree twice. Ids, not entities, so a level can still be unloaded. Server thread
     * only; entries of a removed Stumpy count as free.
     */
    private static final Map<Level, Long2IntOpenHashMap> CLAIMED = new WeakHashMap<>();

    /** Log clusters without natural leaves (player builds). */
    private final LongOpenHashSet ignored = new LongOpenHashSet();
    /** Where trees stood, with the sapling kind that grew there ("" when unknown). Saved. */
    private final Long2ObjectOpenHashMap<String> plots = new Long2ObjectOpenHashMap<>();
    private int wraps;

    /** The felling wave: logs and leaves, each sorted bottom-up, broken a few per tick. Saved. */
    private long[] waveLogs = NO_WAVE;
    private long[] waveLeaves = NO_WAVE;
    private int logCursor;
    private int leafCursor;
    private int waveFelled;
    private float waveCarry;
    private long[] waveBases = NO_WAVE;
    private String wavePrefix = "";

    public Stumpy(EntityType<? extends Stumpy> type, Level level) {
        super(type, level);
    }

    @Override
    protected List<DeferredItem<FarmBotItem>> items() {
        return FarmBotContent.STUMPY_ITEMS;
    }

    @Override
    protected int baseFePerTick() {
        return AutomationConfig.stumpyFe();
    }

    @Override
    protected int layerOffset() {
        return -3;
    }

    @Override
    protected int layerCount() {
        return 8;
    }

    @Override
    protected double reach() {
        return REACH;
    }

    @Override
    protected int baseRadius() {
        return AutomationConfig.stumpyRadius();
    }

    @Override
    protected int pathAccuracy() {
        return 2;
    }

    @Override
    protected boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LOGS)) return isTree(level, pos);
        if (state.is(BlockTags.SAPLINGS)) {
            remember(pos, saplingKind(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()));
            return false;
        }
        if (plots.isEmpty() || !plots.containsKey(pos.asLong())) return false;
        if (state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty())) return canReplant(level, pos);
        // built over: no tree spot any more
        if (!state.is(BlockTags.LEAVES)) plots.remove(pos.asLong());
        return false;
    }

    private boolean isTree(ServerLevel level, BlockPos pos) {
        return !ignored.contains(pos.asLong()) && !isClaimed(level, pos);
    }

    /** "oak" for oak_sapling, so a spot gets its own kind back. */
    private static String saplingKind(String path) {
        return path.endsWith("_sapling") ? path.substring(0, path.length() - "_sapling".length()) : path;
    }

    private void remember(BlockPos pos, String kind) {
        if (!inArea(pos)) return;
        long key = pos.asLong();
        if (plots.containsKey(key)) {
            if (!kind.isEmpty()) plots.put(key, kind);
        } else if (plots.size() < MAX_PLOTS) {
            plots.put(key, kind);
        }
    }

    /** An empty tree spot on dirt with a sapling in the pouch that can grow there. */
    private boolean canReplant(ServerLevel level, BlockPos pos) {
        if (!inArea(pos)) return false;
        if (!level.getBlockState(pos.below()).is(BlockTags.DIRT)) {
            plots.remove(pos.asLong());
            return false;
        }
        return findSapling(level, pos, plots.get(pos.asLong())) >= 0;
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
        if (CoreSounds.due(sl, blockPosition(), 50)) CoreSounds.play(sl, blockPosition(), CoreSounds.SAW_WHIR, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    @Override
    protected void onScanWrapped() {
        if (++wraps % 8 == 0) ignored.clear();
    }

    @Override
    protected boolean canAfford(ServerLevel sl, BlockPos t) {
        return energy.getEnergyStored() >= scaledDrain(AutomationConfig.stumpyFePerLog(), 1);
    }

    @Override
    protected boolean act(ServerLevel sl, BlockPos start) {
        BlockState startState = sl.getBlockState(start);
        if (!startState.is(BlockTags.LOGS)) return replant(sl, start, startState);
        if (!startState.is(BlockTags.LOGS) || !mayBreak(sl, start, startState)) return false;
        TreeScan tree = TreeScan.scan(sl, start, AutomationConfig.maxLogs(), true);
        if (tree.naturalLeaves == 0) {
            for (BlockPos p : tree.logs) ignored.add(p.asLong());
            if (ignored.size() > 8192) ignored.clear();
            return false;
        }
        // Part of a tree another Stumpy is felling right now.
        for (BlockPos p : tree.logs) {
            if (isClaimed(sl, p)) return false;
        }
        // Every log costs FE when the tree comes down. A huge tree is capped at half the buffer: the buffer drains a
        // little every working tick, so it is never exactly full and a full-buffer price could never be paid.
        long cost = Math.min((long) scaledDrain(AutomationConfig.stumpyFePerLog(), 1) * tree.logs.size(), energy.getMaxEnergyStored() / 2);
        if (!energy.consume((int) cost)) return false;
        startWave(sl, tree, start, BuiltInRegistries.BLOCK.getKey(startState.getBlock()).getPath());
        workSound(sl, start, CoreSounds.STUMPY_CHOP, 1.0F, 0.9F + sl.random.nextFloat() * 0.2F);
        workFx(start, CHOP);
        return true;
    }

    /**
     * Something blocks the way: fells the closest natural tree in reach (a sapling that grew up next to it, a tree in front
     * of the one it wanted). Outside the area too, so it never stays boxed in.
     */
    @Override
    protected boolean clearWay(ServerLevel sl) {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int r = (int) Math.ceil(REACH);
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 3, r))) {
            if (!sl.isLoaded(p)) continue;
            if (isSkipped(p) || !sl.getBlockState(p).is(BlockTags.LOGS) || !isTree(sl, p)) continue;
            double d = horizontalDistance(p);
            if (d <= REACH && d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        if (best == null) return false;
        TreeScan tree = TreeScan.scan(sl, best, AutomationConfig.maxLogs(), true);
        if (tree.naturalLeaves == 0) {
            for (BlockPos p : tree.logs) ignored.add(p.asLong());
            return false;
        }
        startJob(best);
        return true;
    }

    // ---- felling wave ----

    /** Queues the paid-for tree: logs bottom-up (nearest the trunk first on a level) and leaves bottom-up. */
    private void startWave(ServerLevel sl, TreeScan tree, BlockPos start, String logPath) {
        List<BlockPos> logs = new ArrayList<>(tree.logs);
        logs.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(p -> (p.getX() - start.getX()) * (p.getX() - start.getX()) + (p.getZ() - start.getZ()) * (p.getZ() - start.getZ())));
        List<BlockPos> leaves = new ArrayList<>(tree.leaves);
        leaves.sort(Comparator.comparingInt(BlockPos::getY));
        waveLogs = logs.stream().mapToLong(BlockPos::asLong).toArray();
        waveLeaves = leaves.stream().mapToLong(BlockPos::asLong).toArray();
        logCursor = 0;
        leafCursor = 0;
        waveFelled = 0;
        waveCarry = 0;
        waveBases = trunkBases(sl, logs);
        wavePrefix = logPath.replace("stripped_", "").replace("_log", "").replace("_wood", "")
                .replace("_stem", "").replace("_hyphae", "");
        claimRemaining(sl);
    }

    // ---- claims ----

    private boolean isClaimed(Level level, BlockPos pos) {
        Long2IntOpenHashMap map = CLAIMED.get(level);
        if (map == null || !map.containsKey(pos.asLong())) return false;
        int id = map.get(pos.asLong());
        if (id == getId()) return false;
        if (!(level.getEntity(id) instanceof Stumpy by) || by.isRemoved() || !by.waveActive()) {
            map.remove(pos.asLong());
            return false;
        }
        return true;
    }

    private void claimRemaining(Level level) {
        Long2IntOpenHashMap map = CLAIMED.computeIfAbsent(level, l -> new Long2IntOpenHashMap());
        for (int i = logCursor; i < waveLogs.length; i++) map.put(waveLogs[i], getId());
    }

    private void release(long pos) {
        Long2IntOpenHashMap map = CLAIMED.get(level());
        if (map != null && map.containsKey(pos) && map.get(pos) == getId()) map.remove(pos);
    }

    private void releaseRemaining() {
        Long2IntOpenHashMap map = CLAIMED.get(level());
        if (map == null) return;
        for (int i = logCursor; i < waveLogs.length; i++) {
            if (map.containsKey(waveLogs[i]) && map.get(waveLogs[i]) == getId()) map.remove(waveLogs[i]);
        }
        if (map.isEmpty()) CLAIMED.remove(level());
    }

    /** The lowest logs on dirt inside the area: where saplings go after the wave. */
    private long[] trunkBases(ServerLevel sl, List<BlockPos> sortedLogs) {
        LongArrayList bases = new LongArrayList();
        int lowest = Integer.MAX_VALUE;
        for (BlockPos p : sortedLogs) {
            if (p.getY() > lowest) break;
            if (!inArea(p) || !sl.getBlockState(p.below()).is(BlockTags.DIRT)) continue;
            lowest = p.getY();
            bases.add(p.asLong());
        }
        return bases.toLongArray();
    }

    public boolean waveActive() {
        return logCursor < waveLogs.length || leafCursor < waveLeaves.length;
    }

    @Override
    protected boolean busyTick(ServerLevel sl) {
        if (!waveActive()) return false;
        navigation.stop();
        stepWave(sl);
        return true;
    }

    /** Breaks the next few blocks of the wave, lowest first (a log before a leaf on the same level). Gone or protected blocks are skipped. */
    private void stepWave(ServerLevel sl) {
        int total = waveLogs.length + waveLeaves.length;
        waveCarry += Math.max(total / (float) WAVE_TICKS, 1.0F / 3.0F);
        int budget = (int) waveCarry;
        waveCarry -= budget;
        List<ItemStack> drops = new ArrayList<>();
        int shown = 0;
        while (budget-- > 0 && waveActive()) {
            boolean log = leafCursor >= waveLeaves.length || (logCursor < waveLogs.length
                    && BlockPos.getY(waveLogs[logCursor]) <= BlockPos.getY(waveLeaves[leafCursor]));
            long packed = log ? waveLogs[logCursor++] : waveLeaves[leafCursor++];
            if (log) release(packed);
            BlockPos p = BlockPos.of(packed);
            if (!sl.isLoaded(p)) continue;
            BlockState state = sl.getBlockState(p);
            if (log && state.is(BlockTags.LOGS) && mayBreak(sl, p, state)) {
                for (ItemStack drop : Block.getDrops(state, sl, p, sl.getBlockEntity(p), null, TOOL)) Drops.merge(drops, drop);
                if (shown++ < 2) {
                    if (shown == 1) sl.levelEvent(2001, p, Block.getId(state));
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
                }
                sl.removeBlock(p, false);
                waveFelled++;
            } else if (!log && TreeScan.isLeafLike(state) && TreeScan.isNatural(state) && mayBreak(sl, p, state)) {
                for (ItemStack drop : Block.getDrops(state, sl, p, sl.getBlockEntity(p), null, TOOL)) Drops.merge(drops, drop);
                sl.removeBlock(p, false);
            }
        }
        for (ItemStack drop : drops) output(drop);
        if (!waveActive()) finishWave(sl);
    }

    /** After the wave: replant at the trunk base and rest. */
    private void finishWave(ServerLevel sl) {
        rest = waveFelled * AutomationConfig.stumpyTicksPerLog() / Math.max(1, effectiveSpeedMultiplier());
        int planted = 0;
        for (long base : waveBases) {
            if (planted >= 4) break;
            BlockPos p = BlockPos.of(base);
            remember(p, wavePrefix);
            if (inArea(p) && sl.isLoaded(p) && sl.getBlockState(p).isAir() && sl.getBlockState(p.below()).is(BlockTags.DIRT)
                    && plantSapling(sl, p, wavePrefix)) planted++;
        }
        // Not one log came down (a claim protects the rest): leave this tree alone instead of paying for it again.
        if (waveFelled == 0) {
            for (long log : waveLogs) ignored.add(log);
        }
        clearWave();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) releaseRemaining();
        super.remove(reason);
    }

    private void clearWave() {
        if (!level().isClientSide) releaseRemaining();
        waveLogs = NO_WAVE;
        waveLeaves = NO_WAVE;
        logCursor = 0;
        leafCursor = 0;
        waveBases = NO_WAVE;
        waveFelled = 0;
        waveCarry = 0;
    }

    /** Pouch slot with the sapling to plant at {@code pos}, its own kind ({@code prefix}) first, or -1. */
    private int findSapling(ServerLevel sl, BlockPos pos, String prefix) {
        int bestSlot = -1;
        int bestScore = 0;
        for (int i = 0; i < pouch.getSlots(); i++) {
            ItemStack stack = pouch.getStackInSlot(i);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS) || !(stack.getItem() instanceof BlockItem bi)) continue;
            if (!bi.getBlock().defaultBlockState().canSurvive(sl, pos)) continue;
            String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int score = !prefix.isEmpty() && name.startsWith(prefix + "_") ? 2 : 1;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean plantSapling(ServerLevel sl, BlockPos pos, String prefix) {
        int slot = findSapling(sl, pos, prefix);
        if (slot < 0) return false;
        BlockState sapling = ((BlockItem) pouch.getStackInSlot(slot).getItem()).getBlock().defaultBlockState();
        sl.setBlock(pos, sapling, Block.UPDATE_ALL);
        pouch.extractItem(slot, 1, false);
        return true;
    }

    /** Plants a sapling on a remembered empty tree spot (grass or flowers there make way). */
    private boolean replant(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!canReplant(sl, pos)) return false;
        if (!state.isAir()) {
            if (!mayBreak(sl, pos, state)) return false;
            sl.destroyBlock(pos, true);
        }
        if (!energy.consume(scaledDrain(AutomationConfig.stumpyFePerLog(), 1))) return false;
        if (!plantSapling(sl, pos, plots.get(pos.asLong()))) return false;
        BlockState planted = sl.getBlockState(pos);
        sl.playSound(null, pos, planted.getSoundType(sl, pos, this).getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    // ---- effects (client side) ----

    @Override
    protected void playHitFx(Level level, int kind, BlockPos target) {
        BlockPos bot = blockPosition();
        BotFx.whoosh(level, bot, 0.2F);
        // At the trunk's face on the robot's side, not hidden inside the log.
        double[] face = BotFx.faceToward(bot, target.getX() + 0.5, target.getZ() + 0.5, 0.6);
        double x = face[0], y = target.getY() + 0.4, z = face[1];
        BotFx.sparkLine(level, bot, x, y, z);
        BotFx.slash(level, x, y, z, 0.7F);
        BotFx.burst(level, x, y, z, 3, BotFx.CYAN, 2);
        BotFx.sound(level, x, y, z, CoreSounds.SHOCK_ZAP.get(), 0.3F, 1.3F + level.random.nextFloat() * 0.3F);
    }

    // ---- persistence: an unfinished wave carries on after a reload ----

    @Override
    protected void saveExtra(CompoundTag tag) {
        if (!plots.isEmpty()) {
            ListTag list = new ListTag();
            for (var e : plots.long2ObjectEntrySet()) {
                CompoundTag plot = new CompoundTag();
                plot.putLong("Pos", e.getLongKey());
                plot.putString("Kind", e.getValue());
                list.add(plot);
            }
            tag.put("Plots", list);
        }
        if (!waveActive()) return;
        CompoundTag w = new CompoundTag();
        w.putLongArray("Logs", Arrays.copyOfRange(waveLogs, logCursor, waveLogs.length));
        w.putLongArray("Leaves", Arrays.copyOfRange(waveLeaves, leafCursor, waveLeaves.length));
        w.putInt("Felled", waveFelled);
        w.putFloat("Carry", waveCarry);
        w.putLongArray("Bases", waveBases);
        w.putString("Prefix", wavePrefix);
        tag.put("Wave", w);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        plots.clear();
        for (Tag t : tag.getList("Plots", Tag.TAG_COMPOUND)) {
            if (plots.size() >= MAX_PLOTS) break;
            CompoundTag plot = (CompoundTag) t;
            plots.put(plot.getLong("Pos"), plot.getString("Kind"));
        }
        clearWave();
        if (!tag.contains("Wave")) return;
        CompoundTag w = tag.getCompound("Wave");
        waveLogs = w.getLongArray("Logs");
        waveLeaves = w.getLongArray("Leaves");
        waveFelled = w.getInt("Felled");
        waveCarry = w.getFloat("Carry");
        waveBases = w.getLongArray("Bases");
        wavePrefix = w.getString("Prefix");
        // A robot moved with its data far from its tree drops the wave.
        long first = waveLogs.length > 0 ? waveLogs[0] : waveLeaves.length > 0 ? waveLeaves[0] : 0;
        if (waveActive() && BlockPos.of(first).distToCenterSqr(position()) > 16 * 16) clearWave();
        if (waveActive() && !level().isClientSide) claimRemaining(level());
    }
}
