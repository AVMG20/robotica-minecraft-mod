package com.arno.robotica.automation.farmbot;

import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.entity.BotFx;
import com.arno.robotica.automation.entity.Drops;
import com.arno.robotica.core.CoreSounds;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Crop robot: walks to the nearest ripe crop and works the 3x3 around it in one action. Harvests mature crops
 * (CropBlock including modded ones via isMaxAge, nether wart, cocoa, pitcher plants, torchflowers on farmland), replants
 * from the drops or its pouch, picks sweet and glow berries, breaks melons and pumpkins next to their attached stem,
 * plants seeds from its pouch on empty farmland and tills dirt or grass next to water. Cactus, sugar cane, bamboo and
 * kelp are cut above their bottom block, which stays and grows back.
 */
public class Sprout extends FarmBot {
    private enum Kind {NONE, HARVEST, FRUIT, CUT, PLANT, TILL}

    /** Effect kinds (the top value, {@link #FX_SPARKLE}, is the plain sparkle). */
    static final int FX_HARVEST = 0, FX_PLANT = 1, FX_TILL = 2;

    public Sprout(EntityType<? extends Sprout> type, Level level) {
        super(type, level);
    }

    @Override
    protected List<DeferredItem<FarmBotItem>> items() {
        return FarmBotContent.SPROUT_ITEMS;
    }

    @Override
    protected int baseFePerTick() {
        return AutomationConfig.sproutFe();
    }

    @Override
    protected int layerOffset() {
        return -1;
    }

    @Override
    protected int layerCount() {
        return 3;
    }

    @Override
    protected double reach() {
        return 2.0;
    }

    @Override
    protected int keepAmount(ItemStack stack) {
        return CropRules.isFarmSeed(stack) ? 16 : 0;
    }

    @Override
    protected boolean isGrowable(BlockState state) {
        Block block = state.getBlock();
        // Only the pitcher's growing bottom half: a random tick on the top half would grow a third block.
        if (block instanceof PitcherCropBlock) return state.isRandomlyTicking();
        if (state.is(Blocks.KELP_PLANT)) return false;
        return block instanceof CaveVinesBlock || state.is(Blocks.BAMBOO_SAPLING) || block instanceof CropBlock || block instanceof StemBlock
                || block instanceof NetherWartBlock || block instanceof SweetBerryBushBlock || block instanceof CocoaBlock
                || state.is(BlockTags.CROPS) || CropRules.isTallPlant(state);
    }

    // ---- crop rules ----

    private boolean hasFarmlandSeed() {
        for (int i = 0; i < pouch.getSlots(); i++) {
            ItemStack stack = pouch.getStackInSlot(i);
            if (!stack.isEmpty() && CropRules.isFarmlandSeed(stack)) return true;
        }
        return false;
    }

    /** Pouch slot with a seed that can survive at the (air) position, or -1. */
    private int findSeed(ServerLevel sl, BlockPos plantPos) {
        for (int i = 0; i < pouch.getSlots(); i++) {
            ItemStack stack = pouch.getStackInSlot(i);
            if (stack.isEmpty() || !CropRules.isFarmlandSeed(stack)) continue;
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

    /** Ground next to a melon or pumpkin stem stays free for the fruit. */
    private static boolean nextToStem(ServerLevel sl, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (!sl.isLoaded(n)) continue;
            Block block = sl.getBlockState(n).getBlock();
            if (block instanceof StemBlock || block instanceof AttachedStemBlock) return true;
        }
        return false;
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty() && !CropRules.isHarvestable(state) && !CropRules.isTallPlant(state));
    }

    private Kind classify(ServerLevel sl, BlockPos pos, BlockState state) {
        return classify(sl, pos, state, false);
    }

    /** {@code quick}: leaves out tilling, which looks for water around the spot. */
    private Kind classify(ServerLevel sl, BlockPos pos, BlockState state, boolean quick) {
        if (CropRules.isHarvestable(state)) return CropRules.isMature(state) ? Kind.HARVEST : Kind.NONE;
        // An attached stem always points at its grown melon or pumpkin.
        if (state.getBlock() instanceof AttachedStemBlock) return Kind.FRUIT;
        // A torchflower crop turns into the flower when grown.
        if (state.is(Blocks.TORCHFLOWER)) return sl.getBlockState(pos.below()).is(Blocks.FARMLAND) ? Kind.HARVEST : Kind.NONE;
        // The second block of a column is the cut point: the bottom block stays and grows back.
        if (CropRules.isTallPlant(state)) {
            Block col = CropRules.column(state);
            return CropRules.column(sl.getBlockState(pos.below())) == col && CropRules.column(sl.getBlockState(pos.below(2))) != col ? Kind.CUT : Kind.NONE;
        }
        boolean farmland = state.is(Blocks.FARMLAND);
        boolean tillable = state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK);
        if (!farmland && !tillable) return Kind.NONE;
        BlockPos above = pos.above();
        BlockState aboveState = sl.getBlockState(above);
        if (farmland) {
            return aboveState.isAir() && findSeed(sl, above) >= 0 && !nextToStem(sl, above) ? Kind.PLANT : Kind.NONE;
        }
        if (quick || !replaceable(aboveState) || !hasFarmlandSeed() || sl.getRawBrightness(above, 0) < 8 || nextToStem(sl, above)) return Kind.NONE;
        scanCost += 24;
        return nearWater(sl, pos) ? Kind.TILL : Kind.NONE;
    }

    @Override
    protected boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        return classify(level, pos, state) != Kind.NONE;
    }

    // ---- actions ----

    /** Work per column in the 5x5 around a job, for {@link #bestCenter}. */
    private final int[] columnWork = new int[25];
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    /**
     * The 3x3 that holds the job and the most other work: the job is often at the edge of a field (the nearest crop),
     * so centering on it would leave a row for another stop.
     */
    private BlockPos bestCenter(ServerLevel sl, BlockPos job) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int n = 0;
                for (int layer = 0; layer < layerCount(); layer++) {
                    probe.set(job.getX() + i - 2, layerBase() + layer, job.getZ() + j - 2);
                    if (inArea(probe) && sl.isLoaded(probe) && classify(sl, probe, sl.getBlockState(probe), true) != Kind.NONE) n++;
                }
                columnWork[i * 5 + j] = n;
            }
        }
        int best = -1, bestX = 0, bestZ = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int sum = 0;
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) sum += columnWork[(2 + dx + a) * 5 + 2 + dz + b];
                }
                // Ties keep the job in the middle.
                if (sum > best || (sum == best && dx == 0 && dz == 0)) {
                    best = sum;
                    bestX = dx;
                    bestZ = dz;
                }
            }
        }
        return job.offset(bestX, 0, bestZ);
    }

    /** Works the 3x3 columns with the most work around the job: harvests first, then plants and tills what the harvest left bare. */
    @Override
    protected boolean act(ServerLevel sl, BlockPos job) {
        BlockPos center = bestCenter(sl, job);
        int done = 0;
        BlockPos.MutableBlockPos pos = probe;
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int layer = 0; layer < layerCount(); layer++) {
                        pos.set(center.getX() + dx, layerBase() + layer, center.getZ() + dz);
                        if (!inArea(pos) || !sl.isLoaded(pos)) continue;
                        BlockState state = sl.getBlockState(pos);
                        Kind kind = classify(sl, pos, state);
                        if (kind == Kind.NONE || (pass == 0) != (kind == Kind.HARVEST || kind == Kind.FRUIT || kind == Kind.CUT)) continue;
                        if (actOn(sl, pos.immutable(), state, kind)) done++;
                    }
                }
            }
        }
        return done > 0;
    }

    private boolean actOn(ServerLevel sl, BlockPos pos, BlockState state, Kind kind) {
        return switch (kind) {
            case HARVEST -> harvest(sl, pos, state);
            case FRUIT -> pickFruit(sl, pos, state);
            case CUT -> cut(sl, pos, state);
            case PLANT -> plant(sl, pos.above());
            case TILL -> till(sl, pos);
            case NONE -> false;
        };
    }

    @Override
    protected boolean canAfford(ServerLevel sl, BlockPos t) {
        return energy.getEnergyStored() >= scaledDrain(AutomationConfig.sproutFePerHarvest(), 1);
    }

    private boolean payHarvest() {
        // Every harvest costs FE; not enough yet and the crop waits for the next round.
        return energy.consume(scaledDrain(AutomationConfig.sproutFePerHarvest(), 1));
    }

    private boolean harvest(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!mayBreak(sl, pos, state) || !payHarvest()) return false;
        Block block = state.getBlock();
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : Block.getDrops(state, sl, pos, null, null, new ItemStack(Items.STONE_HOE))) Drops.merge(drops, drop);
        if (CropRules.isBerryPlant(block)) {
            BlockState picked = block instanceof CaveVines ? state.setValue(CaveVines.BERRIES, false) : state.setValue(SweetBerryBushBlock.AGE, 1);
            sl.setBlock(pos, picked, Block.UPDATE_CLIENTS);
        } else {
            breakFx(sl, pos, state);
            ItemStack seed = state.is(Blocks.TORCHFLOWER) ? new ItemStack(Items.TORCHFLOWER_SEEDS) : block.getCloneItemStack(sl, pos, state);
            boolean paid = !seed.isEmpty() && (Drops.takeOne(drops, seed.getItem()) || takeFromPouch(seed.getItem()));
            // The pitcher's top half goes first without shape updates, otherwise it would pop the bottom half loose.
            if (block instanceof PitcherCropBlock && sl.getBlockState(pos.above()).is(block)) {
                sl.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            sl.setBlock(pos, paid ? CropRules.freshState(state) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        workSound(sl, pos, CoreSounds.SPROUT_SNIP, 0.8F, 0.9F + sl.random.nextFloat() * 0.2F);
        workFx(pos, FX_HARVEST);
        for (ItemStack drop : drops) output(drop);
        return true;
    }

    /** Breaks the melon or pumpkin the attached stem points at; the stem stays and grows a new one. */
    private boolean pickFruit(ServerLevel sl, BlockPos stemPos, BlockState stem) {
        BlockPos pos = stemPos.relative(stem.getValue(AttachedStemBlock.FACING));
        if (!sl.isLoaded(pos)) return false;
        BlockState fruit = sl.getBlockState(pos);
        if (!isFruit(stem, fruit) || fruit.getDestroySpeed(sl, pos) < 0 || !mayBreak(sl, pos, fruit) || !payHarvest()) return false;
        List<ItemStack> drops = Block.getDrops(fruit, sl, pos, sl.getBlockEntity(pos));
        breakFx(sl, pos, fruit);
        workSound(sl, pos, CoreSounds.SPROUT_SNIP, 0.8F, 0.9F + sl.random.nextFloat() * 0.2F);
        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        workFx(pos, FX_HARVEST);
        for (ItemStack drop : drops) output(drop);
        return true;
    }

    private static final net.minecraft.tags.TagKey<Block> MELONS =
            BlockTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "melons"));

    /** The stem's own fruit: pumpkin or melon for the vanilla stems, else a pumpkin or melon tagged block. Never a block entity. */
    private static boolean isFruit(BlockState stem, BlockState fruit) {
        if (fruit.isAir() || fruit.hasBlockEntity()) return false;
        if (stem.is(Blocks.ATTACHED_PUMPKIN_STEM)) return fruit.is(Blocks.PUMPKIN);
        if (stem.is(Blocks.ATTACHED_MELON_STEM)) return fruit.is(Blocks.MELON);
        return fruit.is(Blocks.PUMPKIN) || fruit.is(Blocks.MELON) || fruit.is(net.neoforged.neoforge.common.Tags.Blocks.PUMPKINS_NORMAL)
                || fruit.is(MELONS);
    }

    /** Breaks the column from {@code pos} up, top first, so nothing pops loose. One harvest's FE for the whole column. Kelp leaves water. */
    private boolean cut(ServerLevel sl, BlockPos pos, BlockState state) {
        Block col = CropRules.column(state);
        BlockPos top = pos;
        while (top.getY() - pos.getY() < 32 && CropRules.column(sl.getBlockState(top.above())) == col) top = top.above();
        for (BlockPos p = top; p.getY() >= pos.getY(); p = p.below()) {
            if (!mayBreak(sl, p, sl.getBlockState(p))) return false;
        }
        if (!payHarvest()) return false;
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos p = top; p.getY() >= pos.getY(); p = p.below()) {
            BlockState at = sl.getBlockState(p);
            for (ItemStack drop : Block.getDrops(at, sl, p, null)) Drops.merge(drops, drop);
            sl.setBlock(p, at.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        }
        breakFx(sl, pos, state);
        workSound(sl, pos, CoreSounds.SPROUT_SNIP, 0.8F, 0.9F + sl.random.nextFloat() * 0.2F);
        workFx(pos, FX_HARVEST);
        for (ItemStack drop : drops) output(drop);
        return true;
    }

    private boolean plant(ServerLevel sl, BlockPos plantPos) {
        int slot = findSeed(sl, plantPos);
        if (slot < 0 || !sl.getBlockState(plantPos).isAir()) return false;
        BlockState crop = ((BlockItem) pouch.getStackInSlot(slot).getItem()).getBlock().defaultBlockState();
        sl.setBlock(plantPos, crop, Block.UPDATE_ALL);
        pouch.extractItem(slot, 1, false);
        workSound(sl, plantPos, () -> SoundEvents.CROP_PLANTED, 0.8F, 1.0F);
        workFx(plantPos, FX_PLANT);
        return true;
    }

    private boolean till(ServerLevel sl, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = sl.getBlockState(above);
        if (!mayBreak(sl, pos, sl.getBlockState(pos))) return false;
        if (!aboveState.isAir()) {
            if (!mayBreak(sl, above, aboveState)) return false;
            sl.destroyBlock(above, true);
        }
        sl.setBlock(pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
        workSound(sl, pos, () -> SoundEvents.HOE_TILL, 0.8F, 1.0F);
        workFx(pos, FX_TILL);
        plant(sl, above);
        return true;
    }

    // ---- effects (client side) ----

    @Override
    protected void playHitFx(Level level, int kind, BlockPos target) {
        BlockPos bot = blockPosition();
        BotFx.whoosh(level, bot, kind == FX_PLANT ? 0.1F : 0.15F);
        double x = target.getX() + 0.5, z = target.getZ() + 0.5;
        switch (kind) {
            case FX_HARVEST -> {
                double y = target.getY() + 0.35;
                BotFx.sparkLine(level, bot, x, y, z);
                BotFx.slash(level, x, y, z, 0.35F);
                BotFx.burst(level, x, y + 0.1, z, 2, BotFx.CYAN, 2);
            }
            case FX_PLANT -> {
                double y = target.getY() + 0.2;
                BotFx.sparkLine(level, bot, x, y, z);
                BotFx.burst(level, x, y, z, 2, BotFx.MINT, 3);
            }
            default -> {
                double y = target.getY() + 1.05;
                BotFx.sparkLine(level, bot, x, y, z);
                BotFx.puffs(level, target, level.getBlockState(target), 4);
                BotFx.burst(level, x, y, z, 1, BotFx.CYAN, 1);
            }
        }
    }
}
