package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.core.CoreSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Crop bot: harvests mature crops (CropBlock including modded ones via isMaxAge, nether wart, sweet berries, cocoa),
 * replants from the drops, plants seeds from its buffer on empty farmland and tills dirt or grass next to water.
 */
public class SproutBlockEntity extends FarmBotBlockEntity {
    private enum Kind {NONE, HARVEST, PLANT, TILL}

    public SproutBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.SPROUT_BE.get(), pos, state);
    }

    @Override
    public String blockKey() {
        return "block.robotica.sprout";
    }

    @Override
    protected int baseFePerTick() {
        return AutomationConfig.sproutFe();
    }

    @Override
    protected int layerBase() {
        return worldPosition.getY() - 1;
    }

    @Override
    protected int layerCount() {
        return 3;
    }

    // ---- crop rules ----

    public static boolean isMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3;
        if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= 2;
        return false;
    }

    private static boolean isHarvestable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof SweetBerryBushBlock || block instanceof CocoaBlock;
    }

    /** Seeds that the bot keeps and replants (crop, nether wart, berries, cocoa beans). */
    public static boolean isFarmSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && isHarvestable(bi.getBlock().defaultBlockState());
    }

    /** Seeds that can be planted on farmland. */
    private static boolean isFarmlandSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    @Override
    protected int keepAmount(ItemStack stack) {
        return isFarmSeed(stack) ? 16 : 0;
    }

    @Override
    protected boolean isGrowable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock || block instanceof StemBlock || block instanceof NetherWartBlock
                || block instanceof SweetBerryBushBlock || block instanceof CocoaBlock || state.is(BlockTags.CROPS);
    }

    private boolean hasFarmlandSeed() {
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (!stack.isEmpty() && isFarmlandSeed(stack)) return true;
        }
        return false;
    }

    /** Buffer slot with a seed that can survive at the (air) position, or -1. */
    private int findSeed(ServerLevel sl, BlockPos plantPos) {
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (stack.isEmpty() || !isFarmlandSeed(stack)) continue;
            BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
            if (state.canSurvive(sl, plantPos)) return i;
        }
        return -1;
    }

    private static boolean nearWater(ServerLevel sl, BlockPos pos) {
        // The 9x2x9 box can reach into neighbouring chunks: probing an unloaded one would force-load it.
        if (!sl.hasChunksAt(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) return false;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (sl.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty() && !isHarvestable(state));
    }

    private Kind classify(ServerLevel sl, BlockPos pos, BlockState state) {
        if (isHarvestable(state)) return isMature(state) ? Kind.HARVEST : Kind.NONE;
        boolean farmland = state.is(Blocks.FARMLAND);
        boolean tillable = state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK);
        if (!farmland && !tillable) return Kind.NONE;
        BlockPos above = pos.above();
        BlockState aboveState = sl.getBlockState(above);
        if (farmland) {
            return aboveState.isAir() && findSeed(sl, above) >= 0 ? Kind.PLANT : Kind.NONE;
        }
        if (!replaceable(aboveState) || !hasFarmlandSeed() || sl.getRawBrightness(above, 0) < 8) return Kind.NONE;
        scanCost += 24;
        return nearWater(sl, pos) ? Kind.TILL : Kind.NONE;
    }

    @Override
    protected boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        return classify(level, pos, state) != Kind.NONE;
    }

    // ---- actions ----

    @Override
    protected boolean act(ServerLevel sl, BlockPos pos) {
        BlockState state = sl.getBlockState(pos);
        return switch (classify(sl, pos, state)) {
            case HARVEST -> harvest(sl, pos, state);
            case PLANT -> plant(sl, pos.above());
            case TILL -> till(sl, pos);
            case NONE -> false;
        };
    }

    private boolean harvest(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!mayBreak(sl, pos, state)) return false;
        // Every harvest costs FE; not enough yet and the crop waits for the next round.
        if (!energy.consume(scaledDrain(AutomationConfig.sproutFePerHarvest(), 1))) return false;
        Block block = state.getBlock();
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : Block.getDrops(state, sl, pos, null, null, new ItemStack(Items.STONE_HOE))) Drops.merge(drops, drop);
        if (block instanceof SweetBerryBushBlock) {
            sl.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
            workSound(sl, pos, CoreSounds.SPROUT_SNIP, 0.8F, 0.9F + sl.random.nextFloat() * 0.2F);
        } else {
            sl.levelEvent(2001, pos, Block.getId(state));
            workSound(sl, pos, CoreSounds.SPROUT_SNIP, 0.8F, 0.9F + sl.random.nextFloat() * 0.2F);
            ItemStack seed = block.getCloneItemStack(sl, pos, state);
            boolean paid = !seed.isEmpty() && (Drops.takeOne(drops, seed.getItem()) || takeFromBuffer(seed.getItem()));
            sl.setBlock(pos, paid ? freshState(state) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (ItemStack drop : drops) output(drop);
        return true;
    }

    private static BlockState freshState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.getStateForAge(0);
        if (block instanceof NetherWartBlock) return state.setValue(NetherWartBlock.AGE, 0);
        if (block instanceof CocoaBlock) return state.setValue(CocoaBlock.AGE, 0);
        return Blocks.AIR.defaultBlockState();
    }

    private boolean takeFromBuffer(Item item) {
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (!stack.isEmpty() && stack.is(item)) {
                buffer.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    private boolean plant(ServerLevel sl, BlockPos plantPos) {
        int slot = findSeed(sl, plantPos);
        if (slot < 0 || !sl.getBlockState(plantPos).isAir()) return false;
        BlockState crop = ((BlockItem) buffer.getStackInSlot(slot).getItem()).getBlock().defaultBlockState();
        sl.setBlock(plantPos, crop, Block.UPDATE_ALL);
        buffer.extractItem(slot, 1, false);
        workSound(sl, plantPos, () -> SoundEvents.CROP_PLANTED, 0.8F, 1.0F);
        return true;
    }

    private boolean till(ServerLevel sl, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = sl.getBlockState(above);
        if (!mayBreak(sl, pos, sl.getBlockState(pos))) return false;
        if (!aboveState.isAir()) sl.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        sl.setBlock(pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
        workSound(sl, pos, () -> SoundEvents.HOE_TILL, 0.8F, 1.0F);
        plant(sl, above);
        return true;
    }
}
