package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.core.CoreSounds;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Lumber bot: fells whole trees (with their natural leaves) as one action, replants saplings from its buffer, picks up drops. */
public class StumpyBlockEntity extends FarmBotBlockEntity {
    /** Log clusters without natural leaves (player builds). Transient. */
    private final LongOpenHashSet ignored = new LongOpenHashSet();
    private int wraps;

    public StumpyBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.STUMPY_BE.get(), pos, state);
    }

    @Override
    public String blockKey() {
        return "block.robotica.stumpy";
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
        // Every log costs FE when the tree comes down; a huge tree is capped at the buffer so it can still be felled.
        // Not enough yet: skip this round, the battery tops the buffer up and the next scan finds the tree again.
        long cost = Math.min((long) scaledDrain(AutomationConfig.stumpyFePerLog(), 1) * tree.logs.size(), energy.getMaxEnergyStored());
        if (!energy.consume((int) cost)) return false;
        ItemStack tool = new ItemStack(Items.IRON_AXE);
        List<ItemStack> drops = new ArrayList<>();
        int felled = 0;
        for (BlockPos p : tree.logs) {
            BlockState state = sl.getBlockState(p);
            if (!state.is(BlockTags.LOGS) || !mayBreak(sl, p, state)) continue;
            for (ItemStack drop : Block.getDrops(state, sl, p, sl.getBlockEntity(p), null, tool)) Drops.merge(drops, drop);
            if (felled < 16) sl.levelEvent(2001, p, Block.getId(state));
            sl.removeBlock(p, false);
            felled++;
        }
        for (BlockPos p : tree.leaves) {
            BlockState state = sl.getBlockState(p);
            if (!TreeScan.isLeafLike(state) || !TreeScan.isNatural(state) || !mayBreak(sl, p, state)) continue;
            for (ItemStack drop : Block.getDrops(state, sl, p, sl.getBlockEntity(p), null, tool)) Drops.merge(drops, drop);
            sl.removeBlock(p, false);
        }
        workSound(sl, start, CoreSounds.STUMPY_CHOP, 1.0F, 0.9F + sl.random.nextFloat() * 0.2F);
        for (ItemStack drop : drops) output(drop);
        replant(sl, tree, BuiltInRegistries.BLOCK.getKey(startState.getBlock()).getPath());
        return true;
    }

    /** Plants a sapling at the trunk base (every base log of a 2x2 trunk, up to four). */
    private void replant(ServerLevel sl, TreeScan tree, String logPath) {
        int lowest = Integer.MAX_VALUE;
        List<BlockPos> bases = new ArrayList<>();
        for (BlockPos p : tree.logs) {
            if (!inArea(p) || !sl.getBlockState(p).isAir() || !sl.getBlockState(p.below()).is(BlockTags.DIRT)) continue;
            if (p.getY() < lowest) {
                lowest = p.getY();
                bases.clear();
            }
            if (p.getY() == lowest) bases.add(p);
        }
        String prefix = logPath.replace("stripped_", "").replace("_log", "").replace("_wood", "")
                .replace("_stem", "").replace("_hyphae", "");
        int planted = 0;
        for (BlockPos base : bases) {
            if (planted >= 4) break;
            if (plantSapling(sl, base, prefix)) planted++;
        }
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

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveExtra(tag, registries);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadExtra(tag, registries);
    }
}
