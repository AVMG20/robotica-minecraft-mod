package com.arno.robotica.processing.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.ElectricFurnaceBlockEntity;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.arno.robotica.processing.block.ProcessingMachineBlockEntity;
import com.arno.robotica.processing.media.GrindingMedia;
import com.arno.robotica.processing.recipe.GrindingLogic;
import com.arno.robotica.processing.recipe.GrindingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/** Headless tests of the processing module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class ProcessingGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static <T extends ProcessingMachineBlockEntity> T place(GameTestHelper helper, DeferredBlock<ProcessingMachineBlock> block) {
        helper.setBlock(POS, block.get().defaultBlockState());
        @SuppressWarnings("unchecked") T be = (T) helper.getBlockEntity(POS);
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        return be;
    }

    private static int count(GrinderBlockEntity be, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < GrinderBlockEntity.OUT_COUNT; i++) {
            ItemStack s = be.items.getStackInSlot(GrinderBlockEntity.OUT_FIRST + i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }


    /**
     * Right-clicking a placed Grinder with the next Mk swaps it in place: inputs, outputs, battery, cards, side config
     * and energy stay, nothing spills, the old Mk comes back to the player and a Mk that is not the next one is refused.
     */
    @GameTest(template = "empty")
    public static void inPlaceUpgradeKeepsContents(GameTestHelper helper) {
        GrinderBlockEntity old = place(helper, ProcessingRegistry.GRINDER_MK1);
        old.energy.setEnergy(12_345);
        old.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE, 7));
        old.items.setStackInSlot(GrinderBlockEntity.OUT_FIRST, new ItemStack(ProcessingRegistry.IRON_DUST.get(), 3));
        old.upgrades.setStackInSlot(0, new ItemStack(CoreItems.card(UpgradeKind.SPEED).get()));
        old.sides.set(com.arno.robotica.core.side.RelativeSide.TOP, com.arno.robotica.core.side.SideMode.NONE);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(POS);
        var level = helper.getLevel();
        ItemStack mk3 = new ItemStack(ProcessingRegistry.GRINDER_MK3.get());
        var skip = level.getBlockState(abs).useItemOn(mk3, level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(abs.getCenter(), net.minecraft.core.Direction.UP, abs, false));
        helper.assertTrue(level.getBlockState(abs).is(ProcessingRegistry.GRINDER_MK1.get()) && mk3.getCount() == 1, "Mk3 does not fit on Mk1: " + skip);

        ItemStack full = new ItemStack(ProcessingRegistry.GRINDER_MK2.get());
        full.set(CoreComponents.CONTENTS.get(), new net.minecraft.nbt.CompoundTag());
        level.getBlockState(abs).useItemOn(full, level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(abs.getCenter(), net.minecraft.core.Direction.UP, abs, false));
        helper.assertTrue(level.getBlockState(abs).is(ProcessingRegistry.GRINDER_MK1.get()) && full.getCount() == 1, "a Mk2 with contents must be placed first");

        ItemStack mk2 = new ItemStack(ProcessingRegistry.GRINDER_MK2.get());
        mk2.set(CoreComponents.ENERGY.get(), 1_000);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, mk2);
        level.getBlockState(abs).useItemOn(mk2, level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(abs.getCenter(), net.minecraft.core.Direction.UP, abs, false));
        helper.assertTrue(level.getBlockState(abs).is(ProcessingRegistry.GRINDER_MK2.get()), "the Grinder is Mk2 now");
        GrinderBlockEntity fresh = (GrinderBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(fresh.tier() == 2, "the block entity is Mk2");
        int expected = Math.min(fresh.energy.getMaxEnergyStored(), 13_345);
        helper.assertTrue(fresh.energy.getEnergyStored() == expected, "energy stays and the Mk2 item's energy is added, got " + fresh.energy.getEnergyStored());
        helper.assertTrue(fresh.items.getStackInSlot(GrinderBlockEntity.INPUT).is(Items.IRON_ORE) && fresh.items.getStackInSlot(GrinderBlockEntity.INPUT).getCount() == 7, "inputs stay");
        helper.assertTrue(fresh.items.getStackInSlot(GrinderBlockEntity.OUT_FIRST).getCount() == 3, "outputs stay");
        helper.assertTrue(fresh.upgrades.level(UpgradeKind.SPEED) == 1, "cards stay");
        helper.assertTrue(fresh.sides.mode(com.arno.robotica.core.side.RelativeSide.TOP) == com.arno.robotica.core.side.SideMode.NONE, "side config stays");
        helper.assertTrue(mk2.isEmpty(), "the Mk2 item is used");
        helper.assertTrue(player.getInventory().countItem(ProcessingRegistry.GRINDER_MK1.get().asItem()) == 1, "the Mk1 comes back");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3)).isEmpty(), "nothing spills");
        helper.succeed();
    }
    /** Generic tag rules: a vanilla ore doubles into our dust, raw ore gives 1 + chance, an ingot 1. */
    @GameTest(template = "empty")
    public static void tagRulesDoubleVanillaOre(GameTestHelper helper) {
        var level = helper.getLevel();
        GrindingLogic.Plan ore = GrindingLogic.find(level, new ItemStack(Items.IRON_ORE), 1).plan();
        helper.assertTrue(ore != null && ore.main().is(ProcessingRegistry.IRON_DUST.get())
                && ore.main().getCount() == ProcessingConfig.oreDustCount(), "iron ore -> 2 iron dust, got " + (ore == null ? null : ore.main()));
        helper.assertTrue(ore.boostable(), "ores are boosted by media");
        GrindingLogic.Plan deep = GrindingLogic.find(level, new ItemStack(Items.DEEPSLATE_GOLD_ORE), 1).plan();
        helper.assertTrue(deep != null && deep.main().is(ProcessingRegistry.GOLD_DUST.get()) && deep.main().getCount() == 2, "deepslate gold ore -> 2 gold dust");
        GrindingLogic.Plan raw = GrindingLogic.find(level, new ItemStack(Items.RAW_COPPER), 1).plan();
        helper.assertTrue(raw != null && raw.main().is(ProcessingRegistry.COPPER_DUST.get()) && raw.main().getCount() == 1
                && raw.extras().size() == 1, "raw copper -> 1 copper dust + a chance");
        GrindingLogic.Plan ingot = GrindingLogic.find(level, new ItemStack(Items.IRON_INGOT), 1).plan();
        helper.assertTrue(ingot != null && ingot.main().getCount() == 1 && !ingot.boostable(), "iron ingot -> 1 dust, never boosted");
        GrindingLogic.Plan diamond = GrindingLogic.find(level, new ItemStack(Items.DIAMOND_ORE), 1).plan();
        helper.assertTrue(diamond != null && diamond.main().is(Items.DIAMOND), "gem ore without a dust -> gems");
        helper.assertTrue(GrindingLogic.find(level, new ItemStack(Items.ANCIENT_DEBRIS), 4).plan() == null, "ancient debris does not grind");
        helper.assertTrue(GrindingLogic.find(level, new ItemStack(Items.DIRT), 4).plan() == null, "dirt does not grind");
        helper.succeed();
    }

    /** Explicit robotica:grinding recipes beat the tag rules, and min_tier gates them by Mk. */
    @GameTest(template = "empty")
    public static void explicitRecipeAndMinTier(GameTestHelper helper) {
        var level = helper.getLevel();
        GrindingLogic.Plan cobble = GrindingLogic.find(level, new ItemStack(Items.COBBLESTONE), 1).plan();
        helper.assertTrue(cobble != null && cobble.main().is(Items.GRAVEL), "cobblestone -> gravel");
        GrindingLogic.Plan redstone = GrindingLogic.find(level, new ItemStack(Items.REDSTONE_ORE), 1).plan();
        helper.assertTrue(redstone != null && redstone.main().is(Items.REDSTONE) && redstone.main().getCount() == 6,
                "redstone ore recipe beats the tag rule (6 redstone)");
        GrindingRecipe mk3 = new GrindingRecipe(Ingredient.of(Items.OBSIDIAN), new ItemStack(Items.FLINT), List.of(), 3, 0, false);
        GrindingLogic.Lookup low = GrindingLogic.pick(List.of(mk3), 2, GrindingLogic.Boost.NONE);
        helper.assertTrue(low.plan() == null && low.neededTier() == 3, "a Mk3 recipe refuses a Mk2 grinder and names Mk3");
        helper.assertTrue(GrindingLogic.pick(List.of(mk3), 3, GrindingLogic.Boost.NONE).plan() != null, "a Mk3 grinder runs it");
        helper.succeed();
    }

    /** The grinder runs: a Mk4 with speed cards grinds an iron ore into two dust and uses energy. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void grinderGrindsOre(GameTestHelper helper) {
        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK4);
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 8));
        be.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE));
        int before = be.energy.getEnergyStored();
        helper.succeedWhen(() -> {
            helper.assertTrue(count(be, ProcessingRegistry.IRON_DUST.get()) == 2, "two iron dust expected");
            helper.assertTrue(be.items.getStackInSlot(GrinderBlockEntity.INPUT).isEmpty(), "ore used up");
            helper.assertTrue(be.energy.getEnergyStored() < before, "energy used");
        });
    }

    /** Media: bonus output, loading and using up, and a media above the Mk does not fit. */
    @GameTest(template = "empty")
    public static void mediaBonusWearAndTierGate(GameTestHelper helper) {
        GrinderBlockEntity mk1 = place(helper, ProcessingRegistry.GRINDER_MK1);
        helper.assertTrue(!mk1.isMedia(new ItemStack(ProcessingRegistry.FERROTHORIUM_GRINDING_BALLS.get())), "Mk2 media refused by Mk1");
        helper.assertTrue(mk1.isMedia(new ItemStack(ProcessingRegistry.IRON_GRINDING_BALLS.get())), "iron balls fit Mk1");
        helper.assertTrue(mk1.isMedia(new ItemStack(Items.FLINT)), "flint fits Mk1");
        helper.assertTrue(mk1.items.insertItem(GrinderBlockEntity.MEDIA, new ItemStack(ProcessingRegistry.RESONANT_GRINDING_BALLS.get()), true).getCount() == 1,
                "resonant balls do not go into a Mk1");

        // Flint lasts 8 ores: the first grind loads one flint, after 8 the next one is loaded.
        GrindingMedia flint = GrindingMedia.of(new ItemStack(Items.FLINT));
        helper.assertTrue(flint != null && flint.uses() == 8, "flint data map entry");
        mk1.items.setStackInSlot(GrinderBlockEntity.MEDIA, new ItemStack(Items.FLINT, 2));
        mk1.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.RAW_IRON, 16));
        var random = helper.getLevel().random;
        for (int i = 0; i < 8; i++) {
            GrindingLogic.Plan plan = GrindingLogic.find(helper.getLevel(), mk1.items.getStackInSlot(GrinderBlockEntity.INPUT), 1).plan();
            helper.assertTrue(mk1.grindOne(random, plan), "grind " + i);
            for (int s = 0; s < GrinderBlockEntity.OUT_COUNT; s++) mk1.items.setStackInSlot(GrinderBlockEntity.OUT_FIRST + s, ItemStack.EMPTY);
        }
        helper.assertTrue(mk1.items.getStackInSlot(GrinderBlockEntity.MEDIA).isEmpty(), "the second flint is loaded after 8 ores");
        helper.assertTrue(mk1.mediaLeft() == 8 && mk1.loadedMedia() == Items.FLINT, "a fresh flint is loaded");

        // Resonant balls (+150%) in a Mk4: an ore's 2 dust become exactly 5.
        BlockPos other = new BlockPos(1, 1, 0);
        helper.setBlock(other, ProcessingRegistry.GRINDER_MK4.get().defaultBlockState());
        GrinderBlockEntity mk4 = (GrinderBlockEntity) helper.getBlockEntity(other);
        mk4.items.setStackInSlot(GrinderBlockEntity.MEDIA, new ItemStack(ProcessingRegistry.RESONANT_GRINDING_BALLS.get()));
        mk4.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE));
        GrindingLogic.Plan plan = GrindingLogic.find(helper.getLevel(), new ItemStack(Items.IRON_ORE), 4).plan();
        helper.assertTrue(mk4.grindOne(random, plan), "mk4 grind");
        helper.assertTrue(count(mk4, ProcessingRegistry.IRON_DUST.get()) == 5, "2 dust +150% = 5, got " + count(mk4, ProcessingRegistry.IRON_DUST.get()));
        helper.assertTrue(mk4.mediaLeft() == 127 && mk4.mediaUses() == 128, "one use of 128 used");
        helper.assertTrue(mk4.items.getStackInSlot(GrinderBlockEntity.MEDIA).isEmpty(), "the ball was loaded into the machine");
        helper.succeed();
    }

    /** Each Mk has one more upgrade slot and higher caps. */
    @GameTest(template = "empty")
    public static void upgradeSlotsPerMk(GameTestHelper helper) {
        List<DeferredBlock<ProcessingMachineBlock>> grinders = ProcessingRegistry.GRINDERS;
        for (int i = 0; i < 4; i++) {
            int mk = i + 1;
            GrinderBlockEntity be = place(helper, grinders.get(i));
            helper.assertTrue(be.tier() == mk, "tier from the block");
            helper.assertTrue(be.upgrades.activeSlots() == mk + 1, "Mk" + mk + " has " + (mk + 1) + " slots, got " + be.upgrades.activeSlots());
            helper.assertTrue(be.upgrades.insertItem(mk + 1 > 4 ? 4 : mk + 1, CoreItems.cards(UpgradeKind.VOID, 1), true).getCount() == (mk + 1 > 4 ? 0 : 1),
                    "Mk" + mk + ": the slot after the last is closed");
            ItemStack rest = be.upgrades.insertItem(0, CoreItems.cards(UpgradeKind.SPEED, 8), false);
            helper.assertTrue(be.upgrades.level(UpgradeKind.SPEED) == ProcessingConfig.speedCap(mk), "speed cap per Mk");
            helper.assertTrue(rest.getCount() == 8 - ProcessingConfig.speedCap(mk), "the rest is refused");
        }
        ElectricFurnaceBlockEntity furnace = place(helper, ProcessingRegistry.ELECTRIC_FURNACE_MK2);
        helper.assertTrue(furnace.upgrades.activeSlots() == 3, "furnace Mk2: 3 slots");
        helper.succeed();
    }

    /** The Electric Furnace smelts one lane per Mk1, four lanes in parallel at Mk3; a closed lane refuses input. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void furnaceParallelPerMk(GameTestHelper helper) {
        int[] expected = {1, 2, 4, 8};
        for (int i = 0; i < 4; i++) {
            ElectricFurnaceBlockEntity be = place(helper, ProcessingRegistry.FURNACES.get(i));
            helper.assertTrue(be.lanes() == expected[i], "Mk" + (i + 1) + " lanes");
        }
        ElectricFurnaceBlockEntity be = place(helper, ProcessingRegistry.ELECTRIC_FURNACE_MK3);
        for (int lane = 0; lane < 4; lane++) be.items.setStackInSlot(lane, new ItemStack(Items.RAW_IRON));
        helper.assertTrue(be.items.insertItem(4, new ItemStack(Items.RAW_IRON), true).getCount() == 1, "lane 5 is closed on a Mk3");
        helper.assertTrue(be.items.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "dirt does not smelt");
        helper.succeedWhen(() -> {
            for (int lane = 0; lane < 4; lane++) {
                ItemStack out = be.items.getStackInSlot(ElectricFurnaceBlockEntity.OUT_FIRST + lane);
                helper.assertTrue(out.is(Items.IRON_INGOT) && out.getCount() == 1, "lane " + lane + " smelted");
            }
            helper.assertTrue(be.storedXp() > 0, "experience stored");
        });
    }

    /** Crafting the next Mk keeps the energy and Carry contents; the dust smelts back into an ingot. */
    @GameTest(template = "empty")
    public static void upgradeRecipeKeepsEnergy(GameTestHelper helper) {
        var level = helper.getLevel();
        var holder = level.getRecipeManager().byKey(Robotica.id("grinder_mk2")).orElseThrow();
        ItemStack mk1 = new ItemStack(ProcessingRegistry.GRINDER_MK1.get());
        mk1.set(CoreComponents.ENERGY.get(), 12_345);
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(1, new ItemStack(CoreItems.ADVANCED_CIRCUIT.get()));
        grid.set(3, new ItemStack(CoreItems.REINFORCED_CASING.get()));
        grid.set(4, mk1);
        grid.set(5, new ItemStack(CoreItems.REINFORCED_CASING.get()));
        grid.set(6, new ItemStack(Items.DIAMOND));
        grid.set(8, new ItemStack(Items.DIAMOND));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        @SuppressWarnings("unchecked")
        var recipe = (net.minecraft.world.item.crafting.Recipe<CraftingInput>) holder.value();
        helper.assertTrue(recipe.matches(input, level), "Mk2 recipe matches");
        ItemStack out = recipe.assemble(input, level.registryAccess());
        helper.assertTrue(out.is(ProcessingRegistry.GRINDER_MK2.get().asItem()), "makes a Grinder Mk2");
        helper.assertTrue(out.getOrDefault(CoreComponents.ENERGY.get(), 0) == 12_345, "energy carried over");

        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK2);
        be.energy.setEnergy(0);
        be.applyComponents(out.getComponents(), DataComponentPatch.EMPTY);
        helper.assertTrue(be.energy.getEnergyStored() == 12_345, "placed machine has the energy");

        var smelt = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ProcessingRegistry.GOLD_DUST.get())), level);
        helper.assertTrue(smelt.isPresent() && smelt.get().value().getResultItem(level.registryAccess()).is(Items.GOLD_INGOT), "gold dust smelts");
        helper.succeed();
    }

    /**
     * Media is loaded straight away (one item leaves the slot), so the slot takes more of it and the player can take it
     * out any time; when the loaded item runs out the next one is loaded. Old worn stacks are loaded with what they had
     * left and stack again.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void mediaLoadsStraightAway(GameTestHelper helper) {
        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK1);
        be.items.setStackInSlot(GrinderBlockEntity.MEDIA, new ItemStack(Items.FLINT, 3));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(be.items.getStackInSlot(GrinderBlockEntity.MEDIA).getCount() == 2, "one flint loaded without any ore");
            helper.assertTrue(be.mediaLeft() == 8, "a full flint loaded");
            helper.assertTrue(be.items.insertItem(GrinderBlockEntity.MEDIA, new ItemStack(Items.FLINT, 10), false).isEmpty(), "more flint stacks in");
            helper.assertTrue(be.items.extractItem(GrinderBlockEntity.MEDIA, 64, false).getCount() == 12, "and comes out again");
            helper.assertTrue(be.mediaLeft() == 8, "the loaded one stays");

            be.items.setStackInSlot(GrinderBlockEntity.MEDIA, new ItemStack(Items.FLINT, 1));
            be.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.RAW_IRON, 16));
            var random = helper.getLevel().random;
            GrindingLogic.Plan plan = GrindingLogic.find(helper.getLevel(), new ItemStack(Items.RAW_IRON), 1).plan();
            for (int i = 0; i < 8; i++) {
                helper.assertTrue(be.grindOne(random, plan), "grind " + i);
                for (int s = 0; s < GrinderBlockEntity.OUT_COUNT; s++) be.items.setStackInSlot(GrinderBlockEntity.OUT_FIRST + s, ItemStack.EMPTY);
            }
            helper.assertTrue(be.items.getStackInSlot(GrinderBlockEntity.MEDIA).isEmpty() && be.mediaLeft() == 8, "the next flint was loaded");

            // An old stack with wear on it: loaded with what it had left, the rest stacks like new flint.
            BlockPos other = new BlockPos(1, 1, 0);
            helper.setBlock(other, ProcessingRegistry.GRINDER_MK1.get().defaultBlockState());
            GrinderBlockEntity old = (GrinderBlockEntity) helper.getBlockEntity(other);
            ItemStack worn = new ItemStack(Items.FLINT, 3);
            worn.set(ProcessingRegistry.MEDIA_WEAR.get(), 5);
            old.items.setStackInSlot(GrinderBlockEntity.MEDIA, worn);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(old.mediaLeft() == 3, "worn flint loaded with 3 ores left, got " + old.mediaLeft());
                ItemStack rest = old.items.getStackInSlot(GrinderBlockEntity.MEDIA);
                helper.assertTrue(rest.getCount() == 2 && ItemStack.isSameItemSameComponents(rest, new ItemStack(Items.FLINT)), "the rest is plain flint");
                helper.succeed();
            });
        });
    }

    /**
     * Gem ores give gems (even when another mod adds a dust of the same name) with no media or Fortune bonus, ores and
     * raw ores keep the full bonus, and only metal inputs fall back to random byproducts.
     */
    @GameTest(template = "empty")
    public static void boostRules(GameTestHelper helper) {
        var level = helper.getLevel();
        GrindingLogic.Plan diamond = GrindingLogic.find(level, new ItemStack(Items.DIAMOND_ORE), 4).plan();
        helper.assertTrue(diamond != null && diamond.main().is(Items.DIAMOND) && !diamond.boostable(), "diamond ore: gems, no boost");
        helper.assertTrue(GrindingLogic.mainAmount(diamond, 1.5, 3) == ProcessingConfig.gemOreCount(), "resonant media + 3 Fortune: still 2 diamonds");
        helper.assertTrue(GrindingLogic.byproductOptions(new ItemStack(Items.DIAMOND_ORE), diamond.boost().fallback()).isEmpty(),
                "no random metal dust from diamond ore");
        helper.assertTrue(!diamond.boost().media(), "media is not used up on diamond ore");

        var pyroliteOre = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Robotica.id("pyrolite_ore"));
        var pyroliteShard = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Robotica.id("pyrolite_shard"));
        if (pyroliteOre != Items.AIR && pyroliteShard != Items.AIR) {
            GrindingLogic.Plan pyro = GrindingLogic.find(level, new ItemStack(pyroliteOre), 4).plan();
            helper.assertTrue(pyro != null && pyro.main().is(pyroliteShard), "an ore with both a gem and a dust tag gives gems, got "
                    + (pyro == null ? null : pyro.main()));
        }

        GrindingLogic.Plan coal = GrindingLogic.find(level, new ItemStack(Items.COAL_ORE), 4).plan();
        helper.assertTrue(coal != null && coal.boostable() && !coal.boost().fallback(), "coal ore: boosted, but no fallback byproduct");
        helper.assertTrue(GrindingLogic.byproductOptions(new ItemStack(Items.COAL_ORE), coal.boost().fallback()).isEmpty(), "coal: no byproduct");

        GrindingLogic.Plan iron = GrindingLogic.find(level, new ItemStack(Items.IRON_ORE), 4).plan();
        helper.assertTrue(iron.boostable() && iron.boost().fallback(), "iron ore: boosted, metal");
        double fortune = ProcessingConfig.fortuneBonus();
        helper.assertTrue(Math.abs(GrindingLogic.mainAmount(iron, 1.5, 3) - 2 * (2.5 + 3 * fortune)) < 1e-6, "iron ore: full media and Fortune bonus");
        GrindingLogic.Plan raw = GrindingLogic.find(level, new ItemStack(Items.RAW_IRON), 4).plan();
        helper.assertTrue(Math.abs(GrindingLogic.mainAmount(raw, 1.5, 3) - (2.5 + 3 * fortune)) < 1e-6, "raw iron: full bonus");

        GrindingLogic.Plan ingot = GrindingLogic.find(level, new ItemStack(Items.IRON_INGOT), 4).plan();
        helper.assertTrue(GrindingLogic.mainAmount(ingot, 1.5, 3) == 1.0 && !ingot.boost().media(), "ingots: never boosted");
        helper.succeed();
    }

    /** Dusts smelt back for 0.1 experience and Fortune cards never boost them: no ingot -> dust -> ingot farm. */
    @GameTest(template = "empty")
    public static void dustSmeltingFarmsNoExperience(GameTestHelper helper) {
        var level = helper.getLevel();
        var smelt = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ProcessingRegistry.IRON_DUST.get())), level).orElseThrow();
        helper.assertTrue(smelt.value().getExperience() <= 0.1F + 1e-6, "iron dust smelts for 0.1 experience");
        ElectricFurnaceBlockEntity be = place(helper, ProcessingRegistry.ELECTRIC_FURNACE_MK4);
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.FORTUNE, 3));
        helper.assertTrue(be.xpBoost(new ItemStack(ProcessingRegistry.IRON_DUST.get())) == 1.0, "no Fortune experience for dusts");
        helper.assertTrue(be.xpBoost(new ItemStack(Items.RAW_IRON)) > 1.0, "Fortune experience for raw iron");
        helper.succeed();
    }

    /** Pipes on a null side get the sided rules, and a charged tool is never quick-inserted as a battery. */
    @GameTest(template = "empty")
    public static void nullSideAndQuickInsertRules(GameTestHelper helper) {
        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK1);
        be.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE, 4));
        helper.assertTrue(be.automation(null).extractItem(GrinderBlockEntity.INPUT, 4, true).isEmpty(), "null side can not pull the input");
        helper.assertTrue(be.quickInsertTarget().insertItem(GrinderBlockEntity.BATTERY, new ItemStack(Items.DIAMOND_PICKAXE), true).getCount() == 1,
                "a tool is not a battery");
        helper.assertTrue(be.quickInsertTarget().insertItem(GrinderBlockEntity.BATTERY, new ItemStack(CoreItems.COPPER_CELL.get()), true).isEmpty(),
                "a cell is");
        helper.succeed();
    }

    private static int dropped(GameTestHelper helper, BlockPos rel, net.minecraft.world.item.Item item) {
        BlockPos abs = helper.absolutePos(rel);
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.5))) {
            if (e.getItem().is(item)) {
                helper.assertTrue(!e.getItem().has(CoreComponents.CONTENTS.get()), "no contents ride in a dropped item");
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static GrinderBlockEntity filledGrinder(GameTestHelper helper) {
        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK2);
        be.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE, 10));
        be.items.setStackInSlot(GrinderBlockEntity.MEDIA, new ItemStack(Items.FLINT, 3));
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 2));
        return be;
    }

    private static void assertSpilled(GameTestHelper helper) {
        helper.assertTrue(dropped(helper, POS, Items.IRON_ORE) == 10, "ore spilled, got " + dropped(helper, POS, Items.IRON_ORE));
        helper.assertTrue(dropped(helper, POS, Items.FLINT) == 3, "media spilled");
        helper.assertTrue(dropped(helper, POS, CoreItems.cards(UpgradeKind.SPEED, 1).getItem()) == 2, "cards spilled");
    }

    /** Removes the dropped items again, so they do not wander into tests running next door (item magnets). */
    private static void clearDrops(GameTestHelper helper, BlockPos rel) {
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(rel)).inflate(3))) e.discard();
    }

    /** Broken with loot: the machine drops once with its energy only, everything inside spills, nothing twice. */
    @GameTest(template = "empty")
    public static void breakingSpillsContentsKeepsEnergy(GameTestHelper helper) {
        GrinderBlockEntity be = filledGrinder(helper);
        helper.assertTrue(!be.collectComponents().has(CoreComponents.CONTENTS.get()), "the item never carries contents");
        var item = ProcessingRegistry.GRINDER_MK2.get().asItem();
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.assertTrue(dropped(helper, POS, item) == 1, "one machine dropped, got " + dropped(helper, POS, item));
        boolean energy = false;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2.5))) {
            if (e.getItem().is(item)) energy = e.getItem().getOrDefault(CoreComponents.ENERGY.get(), 0) > 0;
        }
        helper.assertTrue(energy, "the stored energy rides in the item");
        assertSpilled(helper);
        clearDrops(helper, POS);
        helper.succeed();
    }

    /** Broken without loot (wrong tool) or blown up: the contents still spill. */
    @GameTest(template = "empty")
    public static void breakingWithoutLootStillSpills(GameTestHelper helper) {
        filledGrinder(helper);
        helper.getLevel().destroyBlock(helper.absolutePos(POS), false);
        assertSpilled(helper);
        clearDrops(helper, POS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explosionSpillsContents(GameTestHelper helper) {
        filledGrinder(helper);
        BlockPos abs = helper.absolutePos(POS);
        // Only this block is hit (a real explosion would reach the tests next door).
        var level = helper.getLevel();
        var explosion = new net.minecraft.world.level.Explosion(level, null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 4.0F, false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY_WITH_DECAY);
        level.getBlockState(abs).onExplosionHit(level, abs, explosion, (stack, at) -> Block.popResource(level, at, stack));
        helper.assertTrue(helper.getLevel().getBlockState(abs).isAir(), "the machine was blown up");
        assertSpilled(helper);
        clearDrops(helper, POS);
        helper.succeed();
    }

    /**
     * The Carry card has no job in these machines; a machine item from an older version that still carries contents
     * moves them into the placed machine (nothing lost, nothing doubled) and an old Carry card pops out.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void oldCarriedContentsAreHarmless(GameTestHelper helper) {
        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK2);
        helper.assertTrue(!be.upgrades.insertOne(CoreItems.cards(UpgradeKind.CARRY, 1), true), "Carry card is not accepted");
        var registries = helper.getLevel().registryAccess();
        ItemStackHandler oldItems = new ItemStackHandler(GrinderBlockEntity.SLOTS);
        oldItems.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE, 10));
        ItemStackHandler oldCards = new ItemStackHandler(5);
        oldCards.setStackInSlot(1, CoreItems.cards(UpgradeKind.CARRY, 1));
        CompoundTag contents = new CompoundTag();
        contents.put("items", oldItems.serializeNBT(registries));
        contents.put("upgrades", oldCards.serializeNBT(registries));
        BlockPos other = new BlockPos(1, 1, 0);
        helper.setBlock(other, ProcessingRegistry.GRINDER_MK2.get().defaultBlockState());
        GrinderBlockEntity placed = (GrinderBlockEntity) helper.getBlockEntity(other);
        placed.applyComponents(DataComponentMap.builder().set(CoreComponents.CONTENTS.get(), contents).build(), DataComponentPatch.EMPTY);
        helper.assertTrue(placed.items.getStackInSlot(GrinderBlockEntity.INPUT).getCount() == 10, "old contents moved into the machine");
        helper.succeedWhen(() -> {
            helper.assertTrue(placed.upgrades.getStackInSlot(1).isEmpty(), "the old Carry card popped out");
            int cards = 0;
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(other)).inflate(3))) {
                if (e.getItem().is(CoreItems.cards(UpgradeKind.CARRY, 1).getItem())) cards += e.getItem().getCount();
            }
            helper.assertTrue(cards == 1, "exactly one Carry card came out");
            clearDrops(helper, other);
        });
    }
}
