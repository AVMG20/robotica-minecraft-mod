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
        GrindingLogic.Lookup low = GrindingLogic.pick(List.of(mk3), 2, false);
        helper.assertTrue(low.plan() == null && low.neededTier() == 3, "a Mk3 recipe refuses a Mk2 grinder and names Mk3");
        helper.assertTrue(GrindingLogic.pick(List.of(mk3), 3, false).plan() != null, "a Mk3 grinder runs it");
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

    /** Media: bonus output, wear and consumption, and a media above the Mk does not fit. */
    @GameTest(template = "empty")
    public static void mediaBonusWearAndTierGate(GameTestHelper helper) {
        GrinderBlockEntity mk1 = place(helper, ProcessingRegistry.GRINDER_MK1);
        helper.assertTrue(!mk1.isMedia(new ItemStack(ProcessingRegistry.FERROTHORIUM_GRINDING_BALLS.get())), "Mk2 media refused by Mk1");
        helper.assertTrue(mk1.isMedia(new ItemStack(ProcessingRegistry.IRON_GRINDING_BALLS.get())), "iron balls fit Mk1");
        helper.assertTrue(mk1.isMedia(new ItemStack(Items.FLINT)), "flint fits Mk1");
        helper.assertTrue(mk1.items.insertItem(GrinderBlockEntity.MEDIA, new ItemStack(ProcessingRegistry.RESONANT_GRINDING_BALLS.get()), true).getCount() == 1,
                "resonant balls do not go into a Mk1");

        // Flint lasts 8 ores, then one flint is used up.
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
        helper.assertTrue(mk1.items.getStackInSlot(GrinderBlockEntity.MEDIA).getCount() == 1, "one flint worn out after 8 ores");
        helper.assertTrue(mk1.wear() == 0, "wear reset");

        // Resonant balls (+150%) in a Mk4: an ore's 2 dust become exactly 5.
        BlockPos other = new BlockPos(1, 1, 0);
        helper.setBlock(other, ProcessingRegistry.GRINDER_MK4.get().defaultBlockState());
        GrinderBlockEntity mk4 = (GrinderBlockEntity) helper.getBlockEntity(other);
        mk4.items.setStackInSlot(GrinderBlockEntity.MEDIA, new ItemStack(ProcessingRegistry.RESONANT_GRINDING_BALLS.get()));
        mk4.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE));
        GrindingLogic.Plan plan = GrindingLogic.find(helper.getLevel(), new ItemStack(Items.IRON_ORE), 4).plan();
        helper.assertTrue(mk4.grindOne(random, plan), "mk4 grind");
        helper.assertTrue(count(mk4, ProcessingRegistry.IRON_DUST.get()) == 5, "2 dust +150% = 5, got " + count(mk4, ProcessingRegistry.IRON_DUST.get()));
        helper.assertTrue(mk4.wear() == 1 && mk4.mediaLeft() == 127, "one use of 128 worn");
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
            helper.assertTrue(be.upgrades.insertItem(mk + 1 > 4 ? 4 : mk + 1, CoreItems.cards(UpgradeKind.CARRY, 1), true).getCount() == (mk + 1 > 4 ? 0 : 1),
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

    /** With a Carry card the machine keeps its inventory in the item; without it the items spill. */
    @GameTest(template = "empty")
    public static void carryCardKeepsContents(GameTestHelper helper) {
        GrinderBlockEntity be = place(helper, ProcessingRegistry.GRINDER_MK2);
        be.items.setStackInSlot(GrinderBlockEntity.INPUT, new ItemStack(Items.IRON_ORE, 10));
        helper.assertTrue(!be.collectComponents().has(CoreComponents.CONTENTS.get()), "no Carry card: no contents in the item");
        helper.assertTrue(be.upgrades.insertOne(CoreItems.cards(UpgradeKind.CARRY, 1)), "Carry card fits");
        var components = be.collectComponents();
        helper.assertTrue(components.has(CoreComponents.CONTENTS.get()), "Carry card: contents in the item");
        BlockPos other = new BlockPos(1, 1, 0);
        helper.setBlock(other, ProcessingRegistry.GRINDER_MK2.get().defaultBlockState());
        GrinderBlockEntity placed = (GrinderBlockEntity) helper.getBlockEntity(other);
        placed.applyComponents(components, DataComponentPatch.EMPTY);
        helper.assertTrue(placed.items.getStackInSlot(GrinderBlockEntity.INPUT).getCount() == 10 && placed.hasCarry(),
                "ores and the card are back");
        helper.succeed();
    }
}
