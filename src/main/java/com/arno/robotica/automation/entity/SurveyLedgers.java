package com.arno.robotica.automation.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Per dimension record of Survey Rig chunks: the ore ledger of a chunk while it is being scanned or mined, and the set
 * of chunks that are surveyed for good. Kept here rather than in the block entity so that breaking and replacing a
 * rig resumes its ledger instead of scanning the same ores again.
 */
public class SurveyLedgers extends SavedData {
    private static final String NAME = "robotica_survey_ledgers";

    public enum Phase {
        SCANNING, MINING, DONE;

        static Phase byOrdinal(int i) {
            Phase[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** One chunk: scan cursor, ore counts by block, and the ore positions still to strip from the world. */
    public static final class Entry {
        Phase phase = Phase.SCANNING;
        /** Lowest Y the scan reaches (inclusive). */
        int scanBottom;
        /** Next Y to scan downwards from (inclusive); the scan is done when it drops below scanBottom. */
        int scanCursor;
        int scanTop;
        /** The rig working this chunk, or null. */
        @Nullable
        BlockPos rig;
        final List<Block> keys = new ArrayList<>();
        final IntArrayList counts = new IntArrayList();
        int total;
        final LongArrayList stripPos = new LongArrayList();
        final IntArrayList stripKey = new IntArrayList();

        public Phase phase() {
            return phase;
        }

        /** Ores still in the ledger. */
        public int left() {
            int n = 0;
            for (int i = 0; i < counts.size(); i++) n += counts.getInt(i);
            return n;
        }

        /** Ores the scan found so far. */
        public int total() {
            return total;
        }

        public int count(Block block) {
            int i = keys.indexOf(block);
            return i < 0 ? 0 : counts.getInt(i);
        }

        public int stripQueued() {
            return stripPos.size();
        }

        /** Scan progress 0..1. */
        public float scanProgress() {
            int span = scanTop - scanBottom + 1;
            if (phase != Phase.SCANNING || span <= 0) return 1.0F;
            return Math.max(0, Math.min(1, (float) (scanTop - scanCursor) / span));
        }

        int key(Block block) {
            int i = keys.indexOf(block);
            if (i >= 0) return i;
            keys.add(block);
            counts.add(0);
            return keys.size() - 1;
        }

        void add(Block block, @Nullable BlockPos pos) {
            int k = key(block);
            counts.set(k, counts.getInt(k) + 1);
            total++;
            if (pos != null) {
                stripPos.add(pos.asLong());
                stripKey.add(k);
            }
        }

        /** Takes one of this ledger key off, if any is left. */
        boolean take(int k) {
            if (k < 0 || k >= counts.size() || counts.getInt(k) <= 0) return false;
            counts.set(k, counts.getInt(k) - 1);
            return true;
        }

        /** A random ore weighted by count among those {@code allowed}, or null when none is left. */
        @Nullable
        Block pick(RandomSource random, Predicate<Block> allowed) {
            int sum = 0;
            for (int i = 0; i < keys.size(); i++) if (allowed.test(keys.get(i))) sum += counts.getInt(i);
            if (sum <= 0) return null;
            int roll = random.nextInt(sum);
            for (int i = 0; i < keys.size(); i++) {
                if (!allowed.test(keys.get(i))) continue;
                roll -= counts.getInt(i);
                if (roll < 0) return keys.get(i);
            }
            return null;
        }

        /** Mined out: keep only the "surveyed" mark. */
        void finish() {
            phase = Phase.DONE;
            rig = null;
            keys.clear();
            counts.clear();
            stripPos.clear();
            stripKey.clear();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("phase", phase.ordinal());
            tag.putInt("total", total);
            if (phase == Phase.DONE) return tag;
            tag.putInt("scanBottom", scanBottom);
            tag.putInt("scanCursor", scanCursor);
            tag.putInt("scanTop", scanTop);
            if (rig != null) tag.put("rig", NbtUtils.writeBlockPos(rig));
            ListTag names = new ListTag();
            for (Block b : keys) names.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(b).toString()));
            tag.put("keys", names);
            tag.putIntArray("counts", counts.toIntArray());
            tag.putLongArray("stripPos", stripPos.toLongArray());
            tag.putIntArray("stripKey", stripKey.toIntArray());
            return tag;
        }

        static Entry load(CompoundTag tag) {
            Entry e = new Entry();
            e.phase = Phase.byOrdinal(tag.getInt("phase"));
            e.total = tag.getInt("total");
            if (e.phase == Phase.DONE) return e;
            e.scanBottom = tag.getInt("scanBottom");
            e.scanCursor = tag.getInt("scanCursor");
            e.scanTop = tag.getInt("scanTop");
            e.rig = NbtUtils.readBlockPos(tag, "rig").orElse(null);
            ListTag names = tag.getList("keys", Tag.TAG_STRING);
            int[] counts = tag.getIntArray("counts");
            // Unknown blocks (a removed mod) keep their slot as air with no count so the strip keys stay aligned.
            for (int i = 0; i < names.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(names.getString(i));
                Block b = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
                e.keys.add(b);
                e.counts.add(b == Blocks.AIR || i >= counts.length ? 0 : counts[i]);
            }
            long[] pos = tag.getLongArray("stripPos");
            int[] keys = tag.getIntArray("stripKey");
            for (int i = 0; i < Math.min(pos.length, keys.length); i++) {
                e.stripPos.add(pos[i]);
                e.stripKey.add(keys[i]);
            }
            return e;
        }
    }

    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

    public static SurveyLedgers get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SurveyLedgers::new, SurveyLedgers::load), NAME);
    }

    @Nullable
    public Entry get(ChunkPos chunk) {
        return entries.get(chunk.toLong());
    }

    public Entry getOrCreate(ChunkPos chunk) {
        return entries.computeIfAbsent(chunk.toLong(), k -> new Entry());
    }

    public boolean isSurveyed(ChunkPos chunk) {
        Entry e = entries.get(chunk.toLong());
        return e != null && e.phase == Phase.DONE;
    }

    /** Marks a chunk surveyed (game tests and admins). */
    public void markSurveyed(ChunkPos chunk) {
        getOrCreate(chunk).finish();
        setDirty();
    }

    /** Forgets a chunk entirely (game tests). */
    public void forget(ChunkPos chunk) {
        if (entries.remove(chunk.toLong()) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : entries.long2ObjectEntrySet()) {
            CompoundTag t = e.getValue().save();
            t.putLong("chunk", e.getLongKey());
            list.add(t);
        }
        tag.put("chunks", list);
        return tag;
    }

    private static SurveyLedgers load(CompoundTag tag, HolderLookup.Provider registries) {
        SurveyLedgers data = new SurveyLedgers();
        for (Tag t : tag.getList("chunks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.entries.put(c.getLong("chunk"), Entry.load(c));
        }
        return data;
    }
}
