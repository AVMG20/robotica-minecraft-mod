package com.arno.robotica.automation.farmbot;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** What Sprout counts as a crop, when it is ripe and which seeds it keeps. */
public final class CropRules {
    private CropRules() {}

    public static boolean isMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3;
        if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= 2;
        if (block instanceof CaveVines) return state.getValue(CaveVines.BERRIES);
        if (block instanceof PitcherCropBlock) return state.getValue(PitcherCropBlock.AGE) >= PitcherCropBlock.MAX_AGE
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
        return false;
    }

    /** Cactus, sugar cane, bamboo and kelp: plants that grow as a column from one bottom block. */
    public static boolean isTallPlant(BlockState state) {
        return state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    /** The column a tall plant belongs to (kelp body and tip count as one), AIR for anything else. */
    public static Block column(BlockState state) {
        if (state.is(Blocks.KELP_PLANT)) return Blocks.KELP;
        return isTallPlant(state) ? state.getBlock() : Blocks.AIR;
    }

    public static boolean isHarvestable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof SweetBerryBushBlock || block instanceof CocoaBlock
                || block instanceof CaveVines || block instanceof PitcherCropBlock;
    }

    /** Berry plants: picked in place, nothing to replant. */
    public static boolean isBerryPlant(Block block) {
        return block instanceof SweetBerryBushBlock || block instanceof CaveVines;
    }

    /** Seeds that the bot keeps and replants (crop seeds, nether wart, cocoa beans, pitcher pods). */
    public static boolean isFarmSeed(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        Block block = bi.getBlock();
        return isHarvestable(block.defaultBlockState()) && !isBerryPlant(block);
    }

    /** Seeds that can be planted on farmland. */
    public static boolean isFarmlandSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && (bi.getBlock() instanceof CropBlock || bi.getBlock() instanceof PitcherCropBlock);
    }

    /** The state a harvested crop is replanted as. */
    public static BlockState freshState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.getStateForAge(0);
        if (block instanceof NetherWartBlock) return state.setValue(NetherWartBlock.AGE, 0);
        if (block instanceof CocoaBlock) return state.setValue(CocoaBlock.AGE, 0);
        if (block instanceof PitcherCropBlock) return state.setValue(PitcherCropBlock.AGE, 0);
        if (state.is(Blocks.TORCHFLOWER)) return Blocks.TORCHFLOWER_CROP.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }
}
