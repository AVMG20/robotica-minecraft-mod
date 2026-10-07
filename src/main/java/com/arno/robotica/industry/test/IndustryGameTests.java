package com.arno.robotica.industry.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.block.ProcessingBlock;
import com.arno.robotica.industry.block.ProcessingBlockEntity;
import com.arno.robotica.industry.block.RtgBlockEntity;
import com.arno.robotica.industry.menu.ProcessingMenu;
import com.arno.robotica.industry.recipe.Machine;
import com.arno.robotica.industry.recipe.ProcessingInput;
import com.arno.robotica.industry.recipe.ProcessingRecipe;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.SolarPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/** Headless tests of the industry module and the generator cards: {@code scripts/docker-build.sh runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class IndustryGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static ProcessingBlockEntity place(GameTestHelper helper, Machine machine, int tier) {
        helper.setBlock(POS, IndustryRegistry.machineBlock(machine, tier).get().defaultBlockState());
        ProcessingBlockEntity be = (ProcessingBlockEntity) helper.getBlockEntity(POS);
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        return be;
    }

    /** Iron + thorium make Ferrothorium in the Alloy Smelter, in any input slots; the inputs are used up. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void alloySmelterMakesFerrothorium(GameTestHelper helper) {
        var input = new ProcessingInput(List.of(ItemStack.EMPTY, new ItemStack(IndustryRegistry.THORIUM_INGOT.get()), new ItemStack(Items.IRON_INGOT)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(IndustryRegistry.recipeType(Machine.ALLOY_SMELTER).get(), input, helper.getLevel());
        helper.assertTrue(recipe.isPresent() && recipe.get().value().getResultItem(helper.getLevel().registryAccess()).is(IndustryRegistry.FERROTHORIUM_INGOT.get()),
                "iron + thorium should alloy into ferrothorium (shapeless)");
        var stray = new ProcessingInput(List.of(new ItemStack(Items.DIRT), new ItemStack(IndustryRegistry.THORIUM_INGOT.get()), new ItemStack(Items.IRON_INGOT)));
        helper.assertTrue(recipe.get().value().assign(stray) == null, "a stray item in an input slot blocks the recipe");

        ProcessingBlockEntity be = place(helper, Machine.ALLOY_SMELTER, 1);
        be.items.setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        be.items.setStackInSlot(2, new ItemStack(IndustryRegistry.THORIUM_INGOT.get()));
        helper.succeedWhen(() -> {
            ItemStack out = be.items.getStackInSlot(3);
            helper.assertTrue(out.is(IndustryRegistry.FERROTHORIUM_INGOT.get()) && out.getCount() == 1, "one ferrothorium ingot expected");
            helper.assertTrue(be.items.getStackInSlot(1).isEmpty() && be.items.getStackInSlot(2).isEmpty(), "inputs used up");
            helper.assertTrue(be.energy.getEnergyStored() < be.energy.getMaxEnergyStored(), "alloying costs FE");
        });
    }

    /** Pipes see one item kind per input slot, may only take from the outputs, and nothing works without FE. */
    @GameTest(template = "empty")
    public static void machineAutomationRules(GameTestHelper helper) {
        ProcessingBlockEntity be = place(helper, Machine.ALLOY_SMELTER, 1);
        be.energy.setEnergy(0);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), null);
        helper.assertTrue(items != null, "item capability expected");
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false).isEmpty(), "iron goes into slot 0");
        helper.assertTrue(items.insertItem(1, new ItemStack(Items.IRON_INGOT, 4), true).getCount() == 4, "a second slot of iron is refused");
        helper.assertTrue(items.insertItem(1, new ItemStack(Items.DIRT), true).getCount() == 1, "items no recipe uses are refused");
        helper.assertTrue(items.insertItem(1, new ItemStack(IndustryRegistry.THORIUM_INGOT.get()), false).isEmpty(), "thorium goes into slot 1");
        helper.assertTrue(items.extractItem(0, 1, true).isEmpty(), "inputs cannot be pulled out by pipes");
        helper.assertTrue(items.insertItem(3, new ItemStack(Items.IRON_INGOT), true).getCount() == 1, "nothing goes into the output");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.status() == ProcessingBlockEntity.NO_ENERGY && be.progress() == 0, "no FE, no work");
            helper.succeed();
        });
    }

    /** Higher Mks are faster and have one more card slot; upgrading in place keeps inputs, cards and energy. */
    @GameTest(template = "empty")
    public static void machineTiersAndUpgradeInPlace(GameTestHelper helper) {
        ProcessingBlockEntity mk1 = place(helper, Machine.ASSEMBLER, 1);
        helper.assertTrue(mk1.upgrades.activeSlots() == 2, "Mk1 has 2 card slots");
        mk1.items.setStackInSlot(0, new ItemStack(IndustryRegistry.THORIUM_PLATE.get(), 5));
        mk1.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 2));
        mk1.energy.setEnergy(12_345);
        ProcessingRecipe recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(IndustryRegistry.recipeType(Machine.ASSEMBLER).get())
                .stream().map(RecipeHolder::value).filter(r -> r.getResultItem(helper.getLevel().registryAccess()).is(IndustryRegistry.THERMOCOUPLE.get()))
                .findFirst().orElseThrow();
        int mk1Ticks = mk1.ticksFor(recipe);
        helper.assertTrue(mk1.upgrades.insertItem(1, CoreItems.cards(UpgradeKind.FORTUNE, 2), true).getCount() == 1, "Mk1 takes one fortune card");
        helper.assertTrue(mk1.upgrades.insertItem(1, CoreItems.cards(UpgradeKind.VOID, 1), true).getCount() == 1, "only the Centrifuge takes a void card");

        BlockPos abs = helper.absolutePos(POS);
        ProcessingBlock.upgradeInPlace(helper.getLevel(), abs, helper.getBlockState(POS),
                IndustryRegistry.machineBlock(Machine.ASSEMBLER, 2).get(), null);
        ProcessingBlockEntity mk2 = (ProcessingBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(mk2.tier == 2 && mk2.upgrades.activeSlots() == 3, "Mk2 with 3 card slots expected");
        helper.assertTrue(mk2.items.getStackInSlot(0).getCount() == 5, "inputs kept");
        helper.assertTrue(mk2.upgrades.level(UpgradeKind.SPEED) == 2, "cards kept");
        helper.assertTrue(mk2.energy.getEnergyStored() == 12_345, "energy kept");
        helper.assertTrue(mk2.ticksFor(recipe) < mk1Ticks, "Mk2 is faster: " + mk2.ticksFor(recipe) + " vs " + mk1Ticks);
        helper.assertTrue(mk2.upgrades.insertItem(2, CoreItems.cards(UpgradeKind.FORTUNE, 3), true).getCount() == 1, "Mk2 takes two fortune cards");
        helper.succeed();
    }

    private static ProcessingRecipe recipeMaking(GameTestHelper helper, Machine machine, Item item) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(IndustryRegistry.recipeType(machine).get())
                .stream().map(RecipeHolder::value).filter(r -> r.getResultItem(helper.getLevel().registryAccess()).is(item))
                .findFirst().orElseThrow();
    }

    /**
     * A Mk4 with 8 speed cards on a 1,000 FE/t recipe wants far more FE/t than its input lets in: it draws only what
     * it can sustain, takes longer, and still finishes (it used to stall forever).
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mk4FullSpeedFinishesExpensiveRecipe(GameTestHelper helper) {
        ProcessingBlockEntity be = place(helper, Machine.ASSEMBLER, 4);
        be.energy.setEnergy(0);
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 8));
        ProcessingRecipe recipe = recipeMaking(helper, Machine.ASSEMBLER, IndustryRegistry.FUSION_FUEL_PELLET.get());
        helper.assertTrue(be.powerFor(recipe) > be.maxDraw(), "the recipe wants more than the input can give: " + be.powerFor(recipe));
        helper.assertTrue(be.drawFor(recipe) <= be.maxDraw() && be.effectiveTicksFor(recipe) > be.ticksFor(recipe), "the draw is clamped and the craft stretched");
        be.items.setStackInSlot(0, new ItemStack(IndustryRegistry.RESONITE_DUST.get(), 2));
        be.items.setStackInSlot(1, new ItemStack(IndustryRegistry.RADIANT_ISOTOPE.get()));
        long[] fed = new long[1];
        // Feed it like a cable would: at most the input rate per tick.
        helper.onEachTick(() -> {
            fed[0] += be.energy.receiveEnergy(Integer.MAX_VALUE, false);
            helper.assertTrue(be.lastUse() <= be.maxDraw(), "never draws more than it can sustain: " + be.lastUse());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(be.items.getStackInSlot(6).is(IndustryRegistry.FUSION_FUEL_PELLET.get()), "the pellet is made");
            long used = fed[0] - be.energy.getEnergyStored();
            helper.assertTrue(used == be.workFor(recipe), "FE per craft stays as designed: " + used + " vs " + be.workFor(recipe));
        });
    }

    /** Fortune only works on recipes that allow it: alloying by default, centrifuging and assembling not. */
    @GameTest(template = "empty")
    public static void fortuneIsPerRecipe(GameTestHelper helper) {
        helper.assertTrue(recipeMaking(helper, Machine.ALLOY_SMELTER, IndustryRegistry.FERROTHORIUM_INGOT.get()).fortune(), "alloying takes fortune");
        helper.assertTrue(!recipeMaking(helper, Machine.CENTRIFUGE, Items.SLIME_BALL).fortune(), "centrifuging magma cream does not");
        helper.assertTrue(!recipeMaking(helper, Machine.ASSEMBLER, IndustryRegistry.THERMOCOUPLE.get()).fortune(), "assembling does not");
        ProcessingBlockEntity be = place(helper, Machine.ASSEMBLER, 4);
        ProcessingRecipe recipe = recipeMaking(helper, Machine.ASSEMBLER, IndustryRegistry.THERMOCOUPLE.get());
        int before = be.powerFor(recipe);
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.FORTUNE, 3));
        helper.assertTrue(be.powerFor(recipe) == before, "fortune cards cost nothing where they do nothing");
        helper.succeed();
    }

    /**
     * Shift-clicking out of a machine writes the change back through the item handler (so the block entity is saved),
     * and shift-clicking cards in respects the machine's card cap.
     */
    @GameTest(template = "empty")
    public static void shiftClickUpdatesMachine(GameTestHelper helper) {
        ProcessingBlockEntity be = place(helper, Machine.ALLOY_SMELTER, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
        inv.setItem(9, new ItemStack(IndustryRegistry.FERROTHORIUM_INGOT.get(), 60));
        be.items.setStackInSlot(3, new ItemStack(IndustryRegistry.FERROTHORIUM_INGOT.get(), 10));
        ProcessingMenu menu = new ProcessingMenu(0, inv, be);
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(POS));
        chunk.setUnsaved(false);

        menu.quickMoveStack(player, 3);
        helper.assertTrue(inv.getItem(9).getCount() == 64, "4 ingots fit into the player's stack, has " + inv.getItem(9).getCount());
        helper.assertTrue(be.items.getStackInSlot(3).getCount() == 6, "6 stay in the output, has " + be.items.getStackInSlot(3).getCount());
        helper.assertTrue(chunk.isUnsaved(), "the partial move marked the machine changed");

        // Menu slots: 3 inputs, 1 output, battery, 2 card slots, then the player inventory (slot 9 first).
        int firstPlayerSlot = Machine.ALLOY_SMELTER.slots() + 1 + be.upgrades.getSlots();
        inv.setItem(9, CoreItems.cards(UpgradeKind.SPEED, 16));
        menu.quickMoveStack(player, firstPlayerSlot);
        helper.assertTrue(be.upgrades.getStackInSlot(0).getCount() == 2 && be.upgrades.getStackInSlot(1).isEmpty(),
                "Mk1 takes 2 speed cards in one slot, has " + be.upgrades.getStackInSlot(0).getCount() + " + " + be.upgrades.getStackInSlot(1).getCount());
        helper.assertTrue(inv.getItem(9).getCount() == 14, "the rest stays with the player, has " + inv.getItem(9).getCount());
        helper.assertTrue(be.upgrades.level(UpgradeKind.SPEED) == 2, "the card cache sees the cards");
        helper.succeed();
    }

    /** The Centrifuge gets thorium back out of a depleted pellet. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void centrifugeRecyclesDepletedPellet(GameTestHelper helper) {
        ProcessingBlockEntity be = place(helper, Machine.CENTRIFUGE, 4);
        be.items.setStackInSlot(0, new ItemStack(IndustryRegistry.DEPLETED_FUEL_PELLET.get()));
        helper.succeedWhen(() -> {
            boolean dust = false;
            for (int i = 1; i <= 4; i++) dust |= be.items.getStackInSlot(i).is(IndustryRegistry.THORIUM_DUST.get());
            helper.assertTrue(dust && be.items.getStackInSlot(0).isEmpty(), "thorium dust expected from the depleted pellet");
        });
    }

    /** The RTG burns one pellet at a time at its FE/t and leaves a depleted pellet. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rtgBurnsPellets(GameTestHelper helper) {
        helper.setBlock(POS, IndustryRegistry.RTG.get().defaultBlockState());
        RtgBlockEntity rtg = (RtgBlockEntity) helper.getBlockEntity(POS);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), null);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.COAL), true).getCount() == 1, "the RTG only takes fuel pellets");
        helper.assertTrue(items.insertItem(0, new ItemStack(IndustryRegistry.THORIUM_FUEL_PELLET.get(), 2), false).isEmpty(), "pellets go in");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(rtg.items.getStackInSlot(RtgBlockEntity.FUEL).getCount() == 1, "one pellet decays at a time");
            int energy = rtg.energy.getEnergyStored();
            helper.assertTrue(energy > 0 && energy % RtgBlockEntity.output() == 0, "it makes " + RtgBlockEntity.output() + " FE/t, has " + energy);
            rtg.setDecay(2);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(rtg.items.getStackInSlot(RtgBlockEntity.WASTE).is(IndustryRegistry.DEPLETED_FUEL_PELLET.get()), "a depleted pellet is left");
            helper.assertTrue(rtg.items.getStackInSlot(RtgBlockEntity.FUEL).isEmpty() && rtg.decay() > 0, "the second pellet started");
            helper.assertTrue(items.extractItem(RtgBlockEntity.WASTE, 1, false).is(IndustryRegistry.DEPLETED_FUEL_PELLET.get()), "pipes take the waste");
            helper.succeed();
        });
    }

    /** Speed cards raise the Combustion Generator's FE/t, efficiency cards stretch its fuel (the machine energy math). */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void generatorCards(GameTestHelper helper) {
        helper.assertTrue(CombustionGeneratorBlockEntity.fuelPerTick(0, 0) == 1.0, "no cards: one fuel tick per tick");
        double fourCards = CombustionGeneratorBlockEntity.fuelPerTick(0, 4);
        helper.assertTrue(Math.abs(fourCards - (1.0 - 4 * com.arno.robotica.power.PowerConfig.generatorEfficiencyPerCard())) < 1e-9,
                "4 efficiency cards: the generator's own step per card");
        helper.assertTrue(fourCards > Upgrades.energyMultiplier(0, 4), "efficiency cards save less fuel than in machines (1.67x by default, not 2.5x)");
        helper.assertTrue(CombustionGeneratorBlockEntity.fuelPerTick(3, 0) == 4 * Upgrades.energyMultiplier(3, 0), "speed burns fuel faster than it adds FE");

        helper.setBlock(POS, PowerRegistry.COMBUSTION_GENERATOR.get().defaultBlockState());
        CombustionGeneratorBlockEntity gen = (CombustionGeneratorBlockEntity) helper.getBlockEntity(POS);
        int base = gen.output();
        gen.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 3));
        helper.assertTrue(gen.output() == base * 4, "3 speed cards: x4 FE/t");
        gen.fuel.setStackInSlot(0, new ItemStack(Items.COAL));
        helper.runAfterDelay(10, () -> {
            int energy = gen.energy.getEnergyStored();
            helper.assertTrue(energy > 0 && energy % (base * 4) == 0, "FE comes in steps of " + base * 4 + ", has " + energy);
            helper.assertTrue(gen.burnTime() < gen.burnTotal() - 30, "speed burns the coal faster than one tick per tick");
            helper.succeed();
        });
    }

    /** The ore features exist and the biome modifiers put them into the overworld. */
    @GameTest(template = "empty")
    public static void oreFeaturesRegistered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        for (String name : List.of("ore_thorium", "ore_pyrolite", "ore_pyrolite_deltas", "ore_resonite")) {
            helper.assertTrue(placed.containsKey(Robotica.id(name)), "placed feature " + name);
            helper.assertTrue(access.registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(Robotica.id(name)), "configured feature " + name);
            helper.assertTrue(access.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).containsKey(Robotica.id(name)), "biome modifier " + name);
        }
        Holder<PlacedFeature> thorium = placed.getHolderOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Robotica.id("ore_thorium")));
        var plains = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).value();
        boolean found = plains.getGenerationSettings().features().stream().anyMatch(set -> set.contains(thorium));
        helper.assertTrue(found, "thorium ore generates in plains");
        var desert = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.NETHER_WASTES).value();
        helper.assertTrue(desert.getGenerationSettings().features().stream().noneMatch(set -> set.contains(thorium)), "no thorium in the Nether");
        helper.succeed();
    }
}
