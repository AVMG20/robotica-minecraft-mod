package com.arno.robotica.power.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.block.AccumulatorBlockEntity;
import com.arno.robotica.power.block.ChargerBlockEntity;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import com.arno.robotica.power.block.PowerBlock;
import com.arno.robotica.power.block.WindingCrankBlockEntity;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaNetwork;
import com.arno.robotica.power.tesla.TeslaTier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import com.arno.robotica.power.block.AccumulatorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;

/** Headless tests of the power module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class PowerGameTests {

    /**
     * Solar Mk1-Mk4: each tier makes more than the one before (a fifth in rain), has a buffer and an FE port, and its
     * recipe consumes the tier below (Mk3 and Mk4 are the Age 3 and Age 4 panels).
     */
    @GameTest(template = "empty")
    public static void solarTiersClimb(GameTestHelper helper) {
        var tiers = java.util.List.of(PowerRegistry.SOLAR_PANEL_MK1, PowerRegistry.SOLAR_PANEL_MK2, PowerRegistry.SOLAR_PANEL_MK3, PowerRegistry.SOLAR_PANEL_MK4);
        int lastOutput = 0;
        for (int i = 0; i < tiers.size(); i++) {
            var block = tiers.get(i).get();
            int output = block.tier().output();
            helper.assertTrue(output > lastOutput, "Mk" + (i + 1) + " makes more than the tier below: " + output);
            helper.assertTrue(com.arno.robotica.power.block.SolarPanelBlockEntity.weatherOutput(output, true) == output / 5,
                    "Mk" + (i + 1) + " makes a fifth in rain");
            lastOutput = output;
            BlockPos pos = new BlockPos(i % 3, 1, i / 3);
            helper.setBlock(pos, block.defaultBlockState());
            com.arno.robotica.power.block.SolarPanelBlockEntity be = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(be.capacity() == block.tier().buffer, "Mk" + (i + 1) + " buffer");
            IEnergyStorage cap = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), Direction.DOWN);
            helper.assertTrue(cap != null && cap.canExtract() && !cap.canReceive(), "Mk" + (i + 1) + " gives FE out and takes none");
            if (i == 0) continue;
            var recipe = helper.getLevel().getRecipeManager().byKey(Robotica.id("solar_panel_mk" + (i + 1)));
            helper.assertTrue(recipe.isPresent(), "recipe for Mk" + (i + 1));
            ItemStack below = new ItemStack(tiers.get(i - 1).get());
            helper.assertTrue(recipe.get().value().getIngredients().stream().anyMatch(ing -> ing.test(below)),
                    "Mk" + (i + 1) + " consumes the Mk" + i);
        }
        // a lone block grows into its square on its first tick: clear them so none grows out of this test's area
        for (int i = 0; i < tiers.size(); i++) helper.setBlock(new BlockPos(i % 3, 1, i / 3), Blocks.AIR);
        helper.succeed();
    }

    private static final String SOLAR_AREA = "energy_arena";   // empty 9x9x9

    private static com.arno.robotica.power.block.SolarArrayBlock solar(int mk) {
        return (com.arno.robotica.power.block.SolarArrayBlock) java.util.List.of(PowerRegistry.SOLAR_PANEL_MK1, PowerRegistry.SOLAR_PANEL_MK2,
                PowerRegistry.SOLAR_PANEL_MK3, PowerRegistry.SOLAR_PANEL_MK4).get(mk - 1).get();
    }

    private static boolean placeSolar(GameTestHelper helper, int mk, BlockPos at) {
        ItemStack item = new ItemStack(solar(mk));
        BlockPos clicked = helper.absolutePos(at);
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false);
        return ((BlockItem) item.getItem()).place(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, item, hit)).consumesAction();
    }

    private static void assertSolarSquare(GameTestHelper helper, com.arno.robotica.power.block.SolarArrayBlock block, BlockPos origin, String what) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertTrue(block.isCell(helper.getBlockState(origin.offset(x, 0, z)), x, z), what + ": block " + x + "," + z);
            }
        }
    }

    private static int solarDrops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(1)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    /**
     * Every Mk places a 3x3 panel around the placed block, every block hands out the one buffer, a panel with no free
     * 3x3 places nothing, shaded blocks lower the share of open blocks, and breaking one block removes the whole panel
     * and drops one item.
     */
    @GameTest(template = SOLAR_AREA, timeoutTicks = 60)
    public static void solarPanelCoversItsSquare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int mk = 1; mk <= 4; mk++) {
            BlockPos mid = new BlockPos(1 + (mk - 1) % 2 * 4, 1, 1 + (mk - 1) / 2 * 4);
            helper.assertTrue(placeSolar(helper, mk, mid), "the Mk" + mk + " is placed");
            assertSolarSquare(helper, solar(mk), mid.offset(-1, 0, -1), "Mk" + mk);
        }
        var block = solar(1);
        var panel = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        helper.assertTrue(level.getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2))) == null, "only the north-west block has a block entity");
        for (BlockPos p : new BlockPos[]{new BlockPos(2, 1, 2), new BlockPos(1, 1, 1), new BlockPos(0, 1, 2)}) {
            IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(p), Direction.DOWN);
            helper.assertTrue(cap == panel.energy, "every block hands out the panel's buffer");
        }

        // blocked: a Mk1 in the gap between the Mk1 and the Mk2 has no free 3x3; a stone in one corner blocks a Mk3 on top
        helper.assertFalse(placeSolar(helper, 1, new BlockPos(3, 1, 1)), "a Mk1 overlapping two panels is not placed");
        helper.assertBlockNotPresent(block, new BlockPos(3, 1, 1));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.assertFalse(placeSolar(helper, 3, new BlockPos(1, 2, 1)), "a stone in the square, no panel");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 2 || z != 2) helper.assertBlockNotPresent(solar(3), new BlockPos(x, 2, z));
            }
        }
        // shade: stones over the other three corners
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(0, 2, 2), Blocks.STONE);

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(panel.openCells() == 5, "the stones shade the four corners: " + panel.openCells() + " of 9 open");
            helper.assertTrue(panel.formed(), "a full square is formed");
            level.destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2)), true);
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) helper.assertBlockNotPresent(block, new BlockPos(x, 1, z));
            }
            assertSolarSquare(helper, solar(2), new BlockPos(4, 1, 0), "the Mk2 next to it");
            helper.assertTrue(solarDrops(helper) == 1, "breaking one block drops one panel, got " + solarDrops(helper));
            helper.succeed();
        });
    }

    /** A block standing alone (a panel block moved on its own by another mod) grows into its square. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void loneSolarBlockGrows(GameTestHelper helper) {
        var block = solar(2);
        helper.setBlock(new BlockPos(0, 1, 0), block.defaultBlockState());
        helper.succeedWhen(() -> assertSolarSquare(helper, block, new BlockPos(0, 1, 0), "the lone Mk2 grew"));
    }

    /**
     * Upgrading in place swaps all nine blocks for the next Mk and keeps the energy; an old single panel becomes a full
     * panel around itself when that square is free and stays as it is when it is not.
     */
    @GameTest(template = SOLAR_AREA, timeoutTicks = 20)
    public static void solarPanelUpgradesInPlace(GameTestHelper helper) {
        var level = helper.getLevel();
        solar(1).placeAll(level, helper.absolutePos(new BlockPos(0, 1, 0)));
        var mk1 = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        mk1.energy.setEnergy(1_000);
        helper.assertTrue(solar(1).upgradeInPlace(level, helper.absolutePos(new BlockPos(0, 1, 0)), solar(2), null), "Mk1 to Mk2");
        assertSolarSquare(helper, solar(2), new BlockPos(0, 1, 0), "upgraded");
        var mk2 = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        helper.assertTrue(mk2.energy.getEnergyStored() == 1_000, "keeps its energy: " + mk2.energy.getEnergyStored());
        helper.assertTrue(solarDrops(helper) == 0, "no drops");

        BlockPos free = new BlockPos(5, 1, 5), boxed = new BlockPos(5, 1, 1);
        helper.setBlock(free, solar(2).single());
        helper.setBlock(boxed, solar(2).single());
        helper.setBlock(boxed.east(), Blocks.STONE);
        helper.assertTrue(solar(2).upgradeInPlace(level, helper.absolutePos(free), solar(3), null), "an old single panel with room");
        assertSolarSquare(helper, solar(3), free.offset(-1, 0, -1), "the single grew around itself");
        helper.assertFalse(solar(2).upgradeInPlace(level, helper.absolutePos(boxed), solar(3), null), "an old single panel without room");
        helper.assertTrue(helper.getBlockState(boxed).is(solar(2)) && helper.getBlockState(boxed).getValue(com.arno.robotica.power.block.SolarArrayBlock.SINGLE),
                "the boxed single stays");
        helper.succeed();
    }

    private static com.arno.robotica.power.block.SolarPanelBlockEntity oldSolar(GameTestHelper helper, int mk, BlockPos origin, int width, boolean square) {
        var block = solar(mk);
        // older versions placed the corner first; setBlock skips setPlacedBy, so nothing fills the 3x3
        for (int x = width - 1; x >= 0; x--) {
            for (int z = width - 1; z >= 0; z--) helper.setBlock(origin.offset(x, 0, z), block.cell(x, z));
        }
        var be = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(origin);
        var tag = new net.minecraft.nbt.CompoundTag();
        if (width > 1) tag.putBoolean("formed", true);
        if (square) tag.putBoolean("square", true);
        be.loadWithComponents(tag, helper.getLevel().registryAccess());
        return be;
    }

    /**
     * Panels saved by 0.9.7 and older move to 3x3 without placing a block or dropping anything: a 1x1 Mk1 and a 2x2 Mk2
     * become single panels with their full output, a 4x4 Mk4 drops its outer ring, a 3x3 Mk3 stays as it is, and a lone
     * 0.9.7 Mk2 block that never formed grows like a moved block.
     */
    @GameTest(template = SOLAR_AREA, timeoutTicks = 60)
    public static void oldSolarPanelsMigrate(GameTestHelper helper) {
        BlockPos mk1 = new BlockPos(0, 1, 0), mk2 = new BlockPos(0, 1, 5), mk4 = new BlockPos(4, 1, 0), mk3 = new BlockPos(4, 1, 5);
        var oldMk1 = oldSolar(helper, 1, mk1, 1, true);
        var oldMk2 = oldSolar(helper, 2, mk2, 2, true);
        var oldMk4 = oldSolar(helper, 4, mk4, 4, true);
        var oldMk3 = oldSolar(helper, 3, mk3, 3, true);
        BlockPos lone = mk3.above(2);
        oldSolar(helper, 2, lone, 1, true);
        helper.runAfterDelay(45, () -> {
            for (var c : new Object[][]{{mk1, oldMk1, 1}, {mk2, oldMk2, 2}}) {
                BlockPos p = (BlockPos) c[0];
                var state = helper.getBlockState(p);
                helper.assertTrue(state.getValue(com.arno.robotica.power.block.SolarArrayBlock.SINGLE), "Mk" + c[2] + " is single now");
                helper.assertTrue(((com.arno.robotica.power.block.SolarPanelBlockEntity) c[1]).openCells() == 9, "Mk" + c[2] + " keeps its full output");
                for (int x = 0; x < 3; x++) {
                    for (int z = 0; z < 3; z++) if (x != 0 || z != 0) helper.assertBlockNotPresent(solar((int) c[2]), p.offset(x, 0, z));
                }
            }
            // an old single Mk1 keeps the 20 FE/t it made as one block
            int old = com.arno.robotica.core.CoreConfig.scaleGeneration(com.arno.robotica.power.block.SolarPanelBlockEntity.OLD_MK1_OUTPUT);
            int expect = com.arno.robotica.power.block.SolarPanelBlockEntity.weatherOutput(old, oldMk1.flag() == 2);
            helper.assertTrue(oldMk1.flag() == 0 || oldMk1.rate() == expect, "the old single Mk1 makes " + expect + ", got " + oldMk1.rate());
            assertSolarSquare(helper, solar(4), mk4, "the Mk4 keeps 3x3");
            for (int i = 0; i < 4; i++) {
                helper.assertBlockNotPresent(solar(4), mk4.offset(3, 0, i));
                helper.assertBlockNotPresent(solar(4), mk4.offset(i, 0, 3));
            }
            helper.assertTrue(oldMk4.formed() && oldMk4.openCells() == 9, "the Mk4 is formed with all 9 open");
            assertSolarSquare(helper, solar(3), mk3, "the Mk3 stays");
            helper.assertTrue(oldMk3.formed(), "the Mk3 stays formed");
            assertSolarSquare(helper, solar(2), lone, "the lone 0.9.7 Mk2 grew");
            helper.assertTrue(solarDrops(helper) == 0, "no drops");
            helper.succeed();
        });
    }

    /** A panel under another panel sees no sky; the top one still does. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void stackedSolarPanelsShade(GameTestHelper helper) {
        solar(1).placeAll(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
        solar(1).placeAll(helper.getLevel(), helper.absolutePos(new BlockPos(0, 2, 0)));
        var lower = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        var upper = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 0));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(upper.openCells() == 9, "the top panel sees the sky");
            helper.assertTrue(lower.openCells() == 0, "the panel below is shaded");
            helper.succeed();
        });
    }

    /**
     * A formed Mk2 that loses a block without the break reaching it (here: the chunk section edited directly) removes
     * itself and drops nothing; a Mk2 whose block entity ends up in another cell (rotated in place) does the same.
     */
    @GameTest(template = SOLAR_AREA, timeoutTicks = 100)
    public static void solarPanelMissingCellRemovesItself(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = solar(2);
        block.placeAll(level, helper.absolutePos(new BlockPos(0, 1, 0)));
        block.placeAll(level, helper.absolutePos(new BlockPos(4, 1, 0)));
        var first = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        var second = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 0));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(first.formed() && second.formed(), "both panels formed");
            BlockPos cut = helper.absolutePos(new BlockPos(1, 1, 1));
            var chunk = level.getChunkAt(cut);
            chunk.getSection(chunk.getSectionIndex(cut.getY())).setBlockState(cut.getX() & 15, cut.getY() & 15, cut.getZ() & 15, Blocks.AIR.defaultBlockState());
            BlockPos nw = helper.absolutePos(new BlockPos(4, 1, 0));
            level.setBlock(nw, level.getBlockState(nw).rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            helper.assertTrue(level.getBlockEntity(nw) == second, "the block entity stays in the rotated block");
        });
        helper.runAfterDelay(50, () -> {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 3; z++) helper.assertBlockNotPresent(block, new BlockPos(x, 1, z));
            }
            helper.assertTrue(solarDrops(helper) == 0, "no drops");
            helper.succeed();
        });
    }

    /** Rotating or mirroring a placed panel (structures) renumbers its cells so the new north-west block is cell 0,0. */
    @GameTest(template = "empty")
    public static void solarPanelRotates(GameTestHelper helper) {
        var block = solar(3);
        int n = com.arno.robotica.power.block.SolarArrayBlock.SIZE;
        for (var mirror : net.minecraft.world.level.block.Mirror.values()) {
            for (var rotation : net.minecraft.world.level.block.Rotation.values()) {
                BlockPos[] moved = new BlockPos[n * n];
                int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                for (int i = 0; i < n * n; i++) {
                    moved[i] = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                            new BlockPos(i % n, 0, i / n), mirror, rotation, BlockPos.ZERO);
                    minX = Math.min(minX, moved[i].getX());
                    minZ = Math.min(minZ, moved[i].getZ());
                }
                for (int i = 0; i < n * n; i++) {
                    var state = block.cell(i % n, i / n).mirror(mirror).rotate(rotation);
                    helper.assertTrue(block.isCell(state, moved[i].getX() - minX, moved[i].getZ() - minZ),
                            mirror + " " + rotation + ": cell " + (i % n) + "," + (i / n) + " became " + state);
                }
                helper.assertTrue(block.single().mirror(mirror).rotate(rotation) == block.single(), "a single panel stays single");
            }
        }
        helper.succeed();
    }

    /** The metal press turns an iron ingot into an iron plate, and a speed card makes it much faster. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void metalPressMakesIronPlate(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(PowerRegistry.PRESSING_TYPE.get(),
                new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "robotica:pressing recipe for iron ingots is missing");
        helper.assertTrue(recipe.get().value().result().is(CoreItems.IRON_PLATE.get()), "iron ingot should press into an iron plate");

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS.get().defaultBlockState());
        MetalPressBlockEntity press = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        press.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        press.energy.setEnergy(press.energy.getMaxEnergyStored());

        helper.succeedWhen(() -> {
            ItemStack out = press.items.getStackInSlot(1);
            helper.assertTrue(out.is(CoreItems.IRON_PLATE.get()) && out.getCount() == 1, "Press should output one iron plate");
            helper.assertTrue(press.items.getStackInSlot(0).isEmpty(), "Ingot should be consumed");
        });
    }

    /** The press reports the FE it draws per tick while working (the energy bar tooltip), and 0 once it is done. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void metalPressReportsUse(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS.get().defaultBlockState());
        MetalPressBlockEntity press = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        press.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        press.energy.setEnergy(press.energy.getMaxEnergyStored());

        helper.runAfterDelay(10, () -> {
            int used = press.energy.getMaxEnergyStored() - press.energy.getEnergyStored();
            helper.assertTrue(press.lastUse() > 0 && used == press.lastUse() * press.progress(),
                    "use per tick " + press.lastUse() + " should match the drain " + used + " over " + press.progress() + " ticks");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(press.items.getStackInSlot(1).is(CoreItems.IRON_PLATE.get()), "Press should output an iron plate");
            helper.assertTrue(press.lastUse() == 0, "no use once the input is gone");
        });
    }

    /** Generic tag rule: a c:ingots/<m> item presses into the c:plates/<m> item; explicit recipes still win. */
    @GameTest(template = "empty")
    public static void metalPressTagRule(GameTestHelper helper) {
        var level = helper.getLevel();
        var rule = com.arno.robotica.power.recipe.PressingLogic.tagRecipe(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(rule != null && rule.result().is(CoreItems.GOLD_PLATE.get()) && rule.result().getCount() == 1,
                "gold ingot -> gold plate by the c: tags, got " + (rule == null ? null : rule.result()));
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.tagRecipe(new ItemStack(Items.NETHERITE_INGOT)) == null,
                "no c:plates/netherite: no rule");
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.find(level, new ItemStack(Items.DIRT)) == null, "dirt does not press");
        var explicit = level.getRecipeManager().getRecipeFor(PowerRegistry.PRESSING_TYPE.get(),
                new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), level);
        helper.assertTrue(explicit.isPresent() && com.arno.robotica.power.recipe.PressingLogic.find(level, new ItemStack(Items.IRON_INGOT))
                == explicit.get().value(), "the robotica:pressing recipe beats the tag rule");
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.tagRules(s -> false).stream()
                .anyMatch(h -> h.value().ingredient().test(new ItemStack(Items.COPPER_INGOT))), "JEI rules list copper");
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.tagRules(s -> true).isEmpty(), "covered inputs are left out");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void metalPressSpeedCardIsFaster(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS.get().defaultBlockState());
        MetalPressBlockEntity press = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        press.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        press.items.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 2));
        press.energy.setEnergy(press.energy.getMaxEnergyStored());

        // Four speed cards are x6: 17 ticks per plate, so both ingots are done inside 60 ticks (base speed would need 200).
        helper.succeedWhen(() -> {
            ItemStack out = press.items.getStackInSlot(1);
            helper.assertTrue(out.is(CoreItems.COPPER_PLATE.get()) && out.getCount() == 2, "Two copper plates expected");
        });
    }

    /** Mk4 presses 5x as fast as Mk1 (20 ticks per plate) at 5x the FE/t, with a bigger buffer. */
    @GameTest(template = "empty", timeoutTicks = 75)
    public static void metalPressMk4IsFaster(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS_MK4.get().defaultBlockState());
        MetalPressBlockEntity press = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(press.mk() == 4 && press.energy.getMaxEnergyStored() > 20_000, "Mk4 should have a bigger buffer");
        press.items.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 3));
        press.energy.setEnergy(press.energy.getMaxEnergyStored());

        helper.runAfterDelay(5, () -> helper.assertTrue(press.lastUse() == (int) Math.round(PowerConfig.pressPower() * PowerConfig.pressSpeed(4)),
                "Mk4 should draw " + PowerConfig.pressSpeed(4) + "x the base FE/t, drew " + press.lastUse()));
        // Mk1 would need 300 ticks for three plates.
        helper.succeedWhen(() -> {
            ItemStack out = press.items.getStackInSlot(1);
            helper.assertTrue(out.is(CoreItems.COPPER_PLATE.get()) && out.getCount() == 3, "Three copper plates expected");
        });
    }

    /** Right-clicking a placed press with the next Mk swaps it in place and keeps input, output, cards and energy. */
    @GameTest(template = "empty")
    public static void metalPressUpgradesInPlace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS.get().defaultBlockState().setValue(PowerBlock.FACING, Direction.EAST));
        MetalPressBlockEntity old = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        old.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
        old.items.setStackInSlot(1, new ItemStack(CoreItems.IRON_PLATE.get(), 7));
        old.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 2));
        old.energy.setEnergy(15_000);

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerRegistry.METAL_PRESS_MK2_ITEM.get()));
        BlockPos abs = helper.absolutePos(pos);
        helper.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

        helper.assertBlockPresent(PowerRegistry.METAL_PRESS_MK2.get(), pos);
        helper.assertTrue(helper.getBlockState(pos).getValue(PowerBlock.FACING) == Direction.EAST, "facing kept");
        MetalPressBlockEntity fresh = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(fresh.mk() == 2, "the new block entity is Mk2");
        helper.assertTrue(fresh.items.getStackInSlot(0).getCount() == 5 && fresh.items.getStackInSlot(1).getCount() == 7, "items kept");
        helper.assertTrue(fresh.upgrades.getStackInSlot(0).getCount() == 2, "cards kept");
        helper.assertTrue(fresh.energy.getEnergyStored() == 15_000, "energy kept, got " + fresh.energy.getEnergyStored());
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the Mk2 is used up");
        helper.assertTrue(player.getInventory().countItem(PowerRegistry.METAL_PRESS_ITEM.get()) == 1, "the Mk1 comes back");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(abs).inflate(2)).isEmpty(), "nothing spills");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void windingCrankWindsMainspring(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.WINDING_CRANK.get().defaultBlockState());
        WindingCrankBlockEntity crank = (WindingCrankBlockEntity) helper.getBlockEntity(pos);
        BlockPos abs = helper.absolutePos(pos);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, null);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, null);
        helper.assertTrue(energy != null && items != null, "Crank must expose FE and item capabilities");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "Empty crank accepts no energy");

        ItemStack spring = new ItemStack(CoreItems.MAINSPRING.get());
        helper.assertTrue(items.insertItem(0, spring, false).isEmpty(), "Automation may insert a Mainspring");
        helper.assertTrue(helper.getBlockState(pos).getValue(com.arno.robotica.power.block.WindingCrankBlock.SPRING), "Block state should show the spring");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 200, "A source winds at 200 FE/t");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "Only 200 FE per tick in total");
        helper.assertTrue(crank.wind(2000) == 2000, "Hand winding adds 2000 FE");
        helper.assertTrue(ItemEnergy.get(crank.spring.getStackInSlot(0)) == 2200, "Spring should hold 2200 FE");
        helper.assertTrue(items.extractItem(0, 1, true).isEmpty(), "Automation cannot extract a half-wound spring");

        ItemEnergy.fill(crank.spring.getStackInSlot(0));
        helper.assertTrue(!items.extractItem(0, 1, false).isEmpty(), "Automation can extract a full spring");
        // Other FE items fit (an FE source charges them) but only a Mainspring winds by hand.
        helper.assertTrue(items.insertItem(0, new ItemStack(CoreItems.COPPER_CELL.get()), false).isEmpty(), "A cell fits in the crank");
        helper.assertTrue(crank.wind(2000) == 0, "Hand winding only works on a Mainspring");
        helper.assertTrue(!WindingCrankBlockEntity.isWindable(new ItemStack(PowerRegistry.ACCUMULATOR_1_ITEM.get())),
                "An item that takes no FE does not fit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void accumulatorKeepsEnergyInItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.ACCUMULATOR_2.get().defaultBlockState());
        AccumulatorBlockEntity be = (AccumulatorBlockEntity) helper.getBlockEntity(pos);
        be.energy.setEnergy(123_456);
        DataComponentMap components = be.collectComponents();
        helper.assertTrue(components.getOrDefault(CoreComponents.ENERGY.get(), 0) == 123_456, "Energy must be written to the item components");

        BlockPos other = new BlockPos(2, 1, 1);
        helper.setBlock(other, PowerRegistry.ACCUMULATOR_2.get().defaultBlockState());
        AccumulatorBlockEntity placed = (AccumulatorBlockEntity) helper.getBlockEntity(other);
        placed.applyComponents(components, DataComponentPatch.EMPTY);
        helper.assertTrue(placed.energy.getEnergyStored() == 123_456, "Placed accumulator must restore the energy");
        helper.assertTrue(placed.energy.getMaxEnergyStored() == PowerConfig.accumulatorCapacity(2)
                && PowerConfig.accumulatorCapacity(2) == 16_000_000, "Tier II holds 16M FE (config)");

        ItemStack item = new ItemStack(PowerRegistry.ACCUMULATOR_2_ITEM.get());
        ItemEnergy.set(item, 5_000);
        IEnergyStorage itemCap = item.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(itemCap != null && itemCap.getEnergyStored() == 5_000 && itemCap.receiveEnergy(100, false) == 0,
                "Accumulator item shows its energy and cannot be charged from outside");
        helper.succeed();
    }

    /** The front gauge (block state CHARGE) follows the stored energy, and a placed item shows its charge at once. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void accumulatorGaugeShowsCharge(GameTestHelper helper) {
        helper.assertTrue(AccumulatorBlock.chargeLevel(0, 1_000_000) == 0, "empty: no cell");
        helper.assertTrue(AccumulatorBlock.chargeLevel(1, 1_000_000) == 1, "any charge: one cell");
        helper.assertTrue(AccumulatorBlock.chargeLevel(1_000_000, 1_000_000) == 5, "full: five cells");

        BlockPos pos = new BlockPos(1, 1, 1);
        AccumulatorBlockEntity be = accumulator(helper, pos, Direction.NORTH, 600_000);

        ItemStack item = new ItemStack(PowerRegistry.ACCUMULATOR_1_ITEM.get());
        ItemEnergy.set(item, 1_000_000);
        BlockPos other = helper.absolutePos(new BlockPos(1, 1, 2));
        var hit = new BlockHitResult(Vec3.atCenterOf(other), Direction.UP, other, false);
        var context = new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, item, hit);
        helper.assertTrue(((BlockItem) item.getItem()).place(context).consumesAction(), "accumulator item placed");
        helper.assertTrue(helper.getLevel().getBlockState(other).getValue(AccumulatorBlock.CHARGE) == 5,
                "a full item is placed with a full gauge");
        helper.assertTrue(helper.getLevel().getBlockEntity(other) instanceof AccumulatorBlockEntity placed
                && placed.energy.getEnergyStored() == 1_000_000, "the placed block keeps the energy");

        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(pos).getValue(AccumulatorBlock.CHARGE) == 3,
                "60% shows three cells, got " + helper.getBlockState(pos).getValue(AccumulatorBlock.CHARGE)
                        + " at " + be.energy.getEnergyStored() + " FE"));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void chargerChargesCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(!ChargerBlockEntity.canCharge(new ItemStack(CoreItems.MAINSPRING.get())), "Mainspring only winds at the crank");
        charger.slot.setStackInSlot(0, new ItemStack(CoreItems.COPPER_CELL.get()));
        charger.energy.setEnergy(charger.energy.getMaxEnergyStored());

        helper.succeedWhen(() -> {
            int stored = ItemEnergy.get(charger.slot.getStackInSlot(0));
            int rate = charger.rate();
            helper.assertTrue(stored >= 3 * rate, "Cell should be charging at " + rate + " FE/t, has " + stored);
            helper.assertTrue(stored % rate == 0, "Charge rate is " + rate + " FE/t, has " + stored);
        });
    }

    /** Each Charger Mk takes its rate from the config, charges 4x faster than the one before and buffers 10 ticks of it. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void chargerTiersChargeFaster(GameTestHelper helper) {
        net.minecraft.world.level.block.Block[] tiers = {PowerRegistry.CHARGER.get(), PowerRegistry.CHARGER_MK2.get(),
                PowerRegistry.CHARGER_MK3.get(), PowerRegistry.CHARGER_MK4.get()};
        ChargerBlockEntity[] chargers = new ChargerBlockEntity[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            BlockPos pos = new BlockPos(1 + i, 1, 1);
            helper.setBlock(pos, tiers[i].defaultBlockState());
            ChargerBlockEntity charger = helper.getBlockEntity(pos);
            int rate = com.arno.robotica.power.PowerConfig.chargerRate(i + 1);
            helper.assertTrue(charger.rate() == rate, "Mk" + (i + 1) + " rate " + charger.rate() + ", config " + rate);
            helper.assertTrue(charger.energy.getMaxEnergyStored() == rate * ChargerBlockEntity.BUFFER_TICKS,
                    "Mk" + (i + 1) + " buffer " + charger.energy.getMaxEnergyStored());
            if (i > 0) helper.assertTrue(rate == 4 * chargers[i - 1].rate(), "Mk" + (i + 1) + " is 4x Mk" + i);
            // an Ender Cell takes up to 32,000 FE/t: Mk4 pulls ahead of Mk3 once Mk3's buffer runs dry (no FE comes in)
            charger.slot.setStackInSlot(0, new ItemStack(CoreItems.ENDER_CELL.get()));
            charger.energy.setEnergy(charger.energy.getMaxEnergyStored());
            chargers[i] = charger;
        }
        helper.succeedWhen(() -> {
            for (int i = 0; i < 3; i++) {
                int slow = ItemEnergy.get(chargers[i].slot.getStackInSlot(0));
                int fast = ItemEnergy.get(chargers[i + 1].slot.getStackInSlot(0));
                helper.assertTrue(slow >= 2 * chargers[i].rate(), "Mk" + (i + 1) + " charging, has " + slow);
                helper.assertTrue(fast > slow, "Mk" + (i + 2) + " charges faster than Mk" + (i + 1) + ": " + fast + " vs " + slow);
            }
        });
    }

    // ---- Tesla Relays and Emitters ----

    private static TeslaCoilBlockEntity coil(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block block) {
        helper.setBlock(pos, block.defaultBlockState().setValue(TeslaCoilBlock.FACING, Direction.UP));
        return (TeslaCoilBlockEntity) helper.getBlockEntity(pos);
    }

    /** A Tesla Relay I on an iron block under it. */
    private static TeslaCoilBlockEntity relay(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.IRON_BLOCK);
        return coil(helper, pos, PowerRegistry.TESLA_COIL_1.get());
    }

    /** A Tesla Emitter I on top of an Accumulator I holding {@code energy}. */
    private static TeslaCoilBlockEntity emitterOn(GameTestHelper helper, BlockPos accumulator, int energy) {
        accumulator(helper, accumulator, Direction.NORTH, energy);
        return coil(helper, accumulator.above(), PowerRegistry.TESLA_EMITTER_1.get());
    }

    private static AccumulatorBlockEntity accumulator(GameTestHelper helper, BlockPos pos, Direction front, int energy) {
        helper.setBlock(pos, PowerRegistry.ACCUMULATOR_1.get().defaultBlockState().setValue(PowerBlock.FACING, front));
        AccumulatorBlockEntity be = (AccumulatorBlockEntity) helper.getBlockEntity(pos);
        be.energy.setEnergy(energy);
        return be;
    }

    private static void link(GameTestHelper helper, TeslaCoilBlockEntity from, BlockPos to, Direction face, String what) {
        TeslaNetwork.LinkResult result = TeslaNetwork.toggle(from, to, face);
        helper.assertTrue(result == TeslaNetwork.LinkResult.LINKED, what + ": " + result);
    }

    /** Accumulator -> Emitter on it -> Relay -> Charger: the emitter puts the Accumulator's FE into the network. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaEmitterFeedsMachine(GameTestHelper helper) {
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), 500_000);
        TeslaCoilBlockEntity relay = relay(helper, new BlockPos(2, 2, 2));
        BlockPos chargerPos = new BlockPos(2, 1, 0);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(chargerPos);
        link(helper, emitter, relay.getBlockPos(), Direction.UP, "emitter -> relay");
        link(helper, relay, helper.absolutePos(chargerPos), Direction.UP, "relay -> charger");
        helper.succeedWhen(() -> {
            helper.assertTrue(emitter.isRoot(), "an emitter on an Accumulator sends");
            helper.assertTrue(charger.energy.getEnergyStored() > 2_000, "Charger should fill, has " + charger.energy.getEnergyStored());
        });
    }

    /** A relay on an Accumulator does not pull from it: only emitters put energy in. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaRelayOnlyHandsOut(GameTestHelper helper) {
        accumulator(helper, new BlockPos(0, 1, 0), Direction.NORTH, 500_000);
        TeslaCoilBlockEntity relay = coil(helper, new BlockPos(0, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        AccumulatorBlockEntity sink = accumulator(helper, new BlockPos(2, 1, 0), Direction.NORTH, 0);
        link(helper, relay, sink.getBlockPos(), Direction.EAST, "relay -> sink");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!relay.isRoot(), "a relay is never a source");
            helper.assertTrue(sink.energy.getEnergyStored() == 0, "nothing flows without an emitter, has " + sink.energy.getEnergyStored());
            helper.succeed();
        });
    }

    /**
     * Emitter -> relay A -> relay B -> Accumulator: nothing is lost on the way. A relay link works both ways: clicking A
     * from B unlinks them, and linking B to A works just as well.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void teslaNetworkIsLossless(GameTestHelper helper) {
        int start = 500_000;
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), start);
        AccumulatorBlockEntity source = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        TeslaCoilBlockEntity a = relay(helper, new BlockPos(2, 2, 2));
        TeslaCoilBlockEntity b = relay(helper, new BlockPos(2, 2, 0));
        AccumulatorBlockEntity target = accumulator(helper, new BlockPos(0, 1, 2), Direction.NORTH, 0);
        link(helper, emitter, a.getBlockPos(), Direction.UP, "emitter -> A");
        link(helper, a, b.getBlockPos(), Direction.UP, "A -> B");
        helper.assertTrue(TeslaNetwork.toggle(b, a.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.UNLINKED, "B -> A unlinks A -> B");
        helper.assertTrue(a.linkCount() == 0, "A has no links left");
        link(helper, b, a.getBlockPos(), Direction.UP, "B -> A");
        link(helper, b, target.getBlockPos(), Direction.EAST, "B -> accumulator");
        helper.succeedWhen(() -> {
            long received = target.energy.getEnergyStored();
            long drained = start - source.energy.getEnergyStored();
            helper.assertTrue(received >= 20_000, "target should fill, has " + received);
            helper.assertTrue(received == drained, "nothing lost: drained " + drained + ", got " + received);
        });
    }

    /** Each link syncs its own flow level: a link to a full Accumulator idles while one to an empty one works. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void teslaFlowLevelPerLink(GameTestHelper helper) {
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), 500_000);
        TeslaCoilBlockEntity relay = relay(helper, new BlockPos(2, 2, 2));
        AccumulatorBlockEntity empty = accumulator(helper, new BlockPos(0, 1, 2), Direction.NORTH, 0);
        AccumulatorBlockEntity full = accumulator(helper, new BlockPos(2, 1, 0), Direction.NORTH, 0);
        full.energy.setEnergy(full.energy.getMaxEnergyStored());
        link(helper, emitter, relay.getBlockPos(), Direction.UP, "emitter -> relay");
        link(helper, relay, empty.getBlockPos(), Direction.EAST, "link to the empty one");
        link(helper, relay, full.getBlockPos(), Direction.EAST, "link to the full one");
        helper.succeedWhen(() -> {
            helper.assertTrue(relay.linkFlowLevel(0) > 0, "the empty Accumulator's link works");
            helper.assertTrue(relay.linkFlowLevel(1) == 0, "the full Accumulator's link idles, has " + relay.linkFlowLevel(1));
        });
    }

    /** Tier I takes 4 links; a fifth is refused until one is unlinked. Tiers hold 4/8/12/16/32. */
    @GameTest(template = "empty")
    public static void teslaLinkLimitPerTier(GameTestHelper helper) {
        TeslaCoilBlockEntity coil = relay(helper, new BlockPos(1, 1, 1));
        BlockPos[] targets = {new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(0, 1, 2), new BlockPos(2, 1, 2), new BlockPos(0, 1, 1)};
        for (BlockPos t : targets) helper.setBlock(t, PowerRegistry.CHARGER.get().defaultBlockState());
        for (int i = 0; i < 4; i++) link(helper, coil, helper.absolutePos(targets[i]), Direction.UP, "link " + i);
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(targets[4]), Direction.UP) == TeslaNetwork.LinkResult.FULL, "fifth link is refused");
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(targets[0]), Direction.UP) == TeslaNetwork.LinkResult.UNLINKED, "unlink frees a slot");
        link(helper, coil, helper.absolutePos(targets[4]), Direction.UP, "now it fits");
        helper.assertTrue(coil.linkCount() == 4, "four links");
        int[] expected = {4, 8, 12, 16, 32};
        for (TeslaTier tier : TeslaTier.values()) helper.assertTrue(tier.maxLinks == expected[tier.ordinal()], tier + " links");
        helper.succeed();
    }

    /** A Tesla Emitter can power a machine straight away, but not the block it sits on. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaEmitterPowersMachine(GameTestHelper helper) {
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), 500_000);
        BlockPos chargerPos = new BlockPos(2, 1, 0);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(chargerPos);
        helper.assertTrue(TeslaNetwork.toggle(emitter, emitter.supportPos(), Direction.EAST) == TeslaNetwork.LinkResult.OWN_SUPPORT, "not the block it sits on");
        link(helper, emitter, helper.absolutePos(chargerPos), Direction.UP, "emitter -> charger");
        helper.succeedWhen(() -> helper.assertTrue(charger.energy.getEnergyStored() > 2_000,
                "Charger should fill from the emitter, has " + charger.energy.getEnergyStored()));
    }

    /**
     * Tesla Emitter: one link at its tier's rate, and nothing links to it. A new target moves its one link instead of
     * being refused.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void teslaEmitterMovesItsLink(GameTestHelper helper) {
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), 500_000);
        TeslaCoilBlockEntity first = relay(helper, new BlockPos(2, 2, 2));
        TeslaCoilBlockEntity second = relay(helper, new BlockPos(0, 2, 2));
        BlockPos chargerPos = new BlockPos(2, 1, 0);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(chargerPos);
        BlockPos chargerAbs = helper.absolutePos(chargerPos);
        helper.assertTrue(emitter.isEmitter() && emitter.maxLinks() == 1, "an emitter has one link");
        helper.assertTrue(emitter.maxIo() == TeslaTier.I.rate(), "an emitter sends at its tier's rate");
        helper.assertTrue(TeslaNetwork.toggle(first, emitter.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.EMITTER, "nothing links to an emitter");
        link(helper, emitter, chargerAbs, Direction.UP, "emitter -> charger");
        link(helper, emitter, first.getBlockPos(), Direction.UP, "a relay moves the link");
        link(helper, emitter, second.getBlockPos(), Direction.UP, "another relay moves it again");
        helper.assertTrue(emitter.linkCount() == 1 && emitter.link(0).pos().equals(second.getBlockPos()), "one link, to the second relay");
        link(helper, second, chargerAbs, Direction.UP, "relay -> charger");
        helper.succeedWhen(() -> helper.assertTrue(charger.energy.getEnergyStored() > 2_000,
                "Charger should fill through the relay, has " + charger.energy.getEnergyStored()));
    }

    /** The clicked face is where the power goes in: an Accumulator's front only gives energy out. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void teslaFaceSpecificInsertion(GameTestHelper helper) {
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), 500_000);
        TeslaCoilBlockEntity relay = relay(helper, new BlockPos(0, 2, 2));
        AccumulatorBlockEntity front = accumulator(helper, new BlockPos(2, 1, 0), Direction.WEST, 0);
        AccumulatorBlockEntity side = accumulator(helper, new BlockPos(2, 1, 2), Direction.WEST, 0);
        link(helper, emitter, relay.getBlockPos(), Direction.UP, "emitter -> relay");
        link(helper, relay, front.getBlockPos(), Direction.WEST, "link front face");
        link(helper, relay, side.getBlockPos(), Direction.EAST, "link side face");
        helper.succeedWhen(() -> {
            helper.assertTrue(side.energy.getEnergyStored() > 5_000, "side face takes energy, has " + side.energy.getEnergyStored());
            helper.assertTrue(front.energy.getEnergyStored() == 0, "front face is output only, has " + front.energy.getEnergyStored());
        });
    }

    /** Range is centre to centre per tier: 12 blocks for Tier I, 36 for Tier V. */
    @GameTest(template = "empty")
    public static void teslaRangeLimit(GameTestHelper helper) {
        TeslaCoilBlockEntity small = relay(helper, new BlockPos(1, 1, 1));
        BlockPos at = small.getBlockPos();
        helper.assertTrue(TeslaNetwork.toggle(small, at.above(13), Direction.UP) == TeslaNetwork.LinkResult.OUT_OF_RANGE, "13 blocks is out of range");
        helper.assertTrue(TeslaNetwork.toggle(small, at.above(12), Direction.UP) == TeslaNetwork.LinkResult.NO_ENERGY, "12 blocks is in range (air there; straight up, away from other tests)");
        helper.setBlock(new BlockPos(1, 1, 1), PowerRegistry.TESLA_COIL_5.get().defaultBlockState().setValue(TeslaCoilBlock.FACING, Direction.UP));
        TeslaCoilBlockEntity big = (TeslaCoilBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
        helper.assertTrue(TeslaNetwork.toggle(big, at.above(20), Direction.UP) == TeslaNetwork.LinkResult.NO_ENERGY, "Tier V reaches 20 blocks");
        helper.assertTrue(TeslaNetwork.toggle(big, at.above(37), Direction.UP) == TeslaNetwork.LinkResult.OUT_OF_RANGE, "but not 37");
        helper.succeed();
    }

    /** Clicking a linked machine on another face moves the link; on the same face it unlinks, and the machine gets nothing. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaUnlinking(GameTestHelper helper) {
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), 500_000);
        TeslaCoilBlockEntity coil = relay(helper, new BlockPos(0, 2, 2));
        link(helper, emitter, coil.getBlockPos(), Direction.UP, "emitter -> relay");
        BlockPos chargerPos = new BlockPos(2, 1, 2);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(chargerPos);
        BlockPos abs = helper.absolutePos(chargerPos);
        link(helper, coil, abs, Direction.UP, "link");
        helper.assertTrue(TeslaNetwork.toggle(coil, abs, Direction.NORTH) == TeslaNetwork.LinkResult.FACE_CHANGED, "another face moves the link");
        helper.assertTrue(coil.linkCount() == 1 && coil.link(0).face() == Direction.NORTH, "one link, on the north face");
        helper.assertTrue(TeslaNetwork.toggle(coil, abs, Direction.NORTH) == TeslaNetwork.LinkResult.UNLINKED, "same face unlinks");
        helper.assertTrue(coil.linkCount() == 0, "no links left");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(charger.energy.getEnergyStored() == 0, "an unlinked charger gets nothing");
            helper.succeed();
        });
    }

    /**
     * Relays A -> B -> C -> A form a loop, and C also feeds the very Accumulator the emitter sits on. Energy is neither
     * made nor lost, a machine on the loop still gets its share, and nothing recurses forever.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teslaLoopsCreateNoEnergy(GameTestHelper helper) {
        int start = 500_000;
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), start);
        AccumulatorBlockEntity source = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        TeslaCoilBlockEntity a = relay(helper, new BlockPos(4, 2, 0));
        TeslaCoilBlockEntity b = relay(helper, new BlockPos(2, 2, 0));
        TeslaCoilBlockEntity c = relay(helper, new BlockPos(2, 2, 2));
        AccumulatorBlockEntity sink = accumulator(helper, new BlockPos(0, 1, 2), Direction.NORTH, 0);
        link(helper, emitter, a.getBlockPos(), Direction.UP, "emitter -> A");
        link(helper, a, b.getBlockPos(), Direction.UP, "A -> B");
        link(helper, b, c.getBlockPos(), Direction.UP, "B -> C");
        link(helper, c, a.getBlockPos(), Direction.UP, "C -> A closes a loop");
        link(helper, c, source.getBlockPos(), Direction.SOUTH, "C -> the emitter's own Accumulator");
        link(helper, b, sink.getBlockPos(), Direction.EAST, "B -> a machine");
        helper.runAfterDelay(60, () -> {
            long total = (long) source.energy.getEnergyStored() + sink.energy.getEnergyStored();
            helper.assertTrue(sink.energy.getEnergyStored() > 0, "the machine on the loop gets energy");
            helper.assertTrue(total == start, "the loop neither makes nor loses energy: " + total + " of " + start);
            helper.succeed();
        });
    }

    /**
     * Two Tesla Emitters on full Accumulators -> relay (also linked to two full machines) -> relay -> a hungry machine.
     * What the full machines do not take goes on to the next relay, so each emitter sends its Accumulator's whole output.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teslaFullLinksPassTheRestOn(GameTestHelper helper) {
        int full = PowerConfig.accumulatorCapacity(1), io = PowerConfig.accumulatorIo(1);
        TeslaCoilBlockEntity e1 = emitterOn(helper, new BlockPos(0, 1, 0), full);
        TeslaCoilBlockEntity e2 = emitterOn(helper, new BlockPos(0, 1, 2), full);
        AccumulatorBlockEntity a = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        AccumulatorBlockEntity b = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 2));
        TeslaCoilBlockEntity first = relay(helper, new BlockPos(2, 2, 0));
        TeslaCoilBlockEntity second = relay(helper, new BlockPos(2, 2, 2));
        AccumulatorBlockEntity full1 = accumulator(helper, new BlockPos(1, 1, 1), Direction.NORTH, full);
        AccumulatorBlockEntity full2 = accumulator(helper, new BlockPos(3, 1, 1), Direction.NORTH, full);
        BlockPos sinkPos = new BlockPos(3, 1, 3);
        helper.setBlock(sinkPos, PowerRegistry.ACCUMULATOR_2.get().defaultBlockState().setValue(PowerBlock.FACING, Direction.NORTH));
        AccumulatorBlockEntity sink = (AccumulatorBlockEntity) helper.getBlockEntity(sinkPos);
        link(helper, e1, first.getBlockPos(), Direction.UP, "emitter 1 -> first");
        link(helper, e2, first.getBlockPos(), Direction.UP, "emitter 2 -> first");
        link(helper, first, full1.getBlockPos(), Direction.EAST, "first -> full 1");
        link(helper, first, second.getBlockPos(), Direction.UP, "first -> second");
        link(helper, first, full2.getBlockPos(), Direction.EAST, "first -> full 2");
        link(helper, second, sink.getBlockPos(), Direction.EAST, "second -> sink");
        long[] mark = new long[2];
        helper.runAfterDelay(20, () -> {
            mark[0] = sink.energy.getEnergyStored();
            mark[1] = (long) a.energy.getEnergyStored() + b.energy.getEnergyStored();
        });
        helper.runAfterDelay(60, () -> {
            long got = sink.energy.getEnergyStored() - mark[0];
            long drained = mark[1] - a.energy.getEnergyStored() - b.energy.getEnergyStored();
            helper.assertTrue(got >= 39L * 2 * io, "both Accumulators give their full " + io + " FE/t, the sink got " + got + " in 40 ticks");
            helper.assertTrue(got == drained, "nothing lost: drained " + drained + ", got " + got);
            helper.assertTrue(full1.energy.getEnergyStored() == full && full2.energy.getEnergyStored() == full, "the full machines stay full");
            helper.succeed();
        });
    }

    /** Two emitters feeding a machine that takes less than both give: each gives half, so neither source drains first. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teslaEmittersShareTheLoad(GameTestHelper helper) {
        int full = PowerConfig.accumulatorCapacity(1), io = PowerConfig.accumulatorIo(1);
        TeslaCoilBlockEntity e1 = emitterOn(helper, new BlockPos(0, 1, 0), full);
        TeslaCoilBlockEntity e2 = emitterOn(helper, new BlockPos(0, 1, 2), full);
        AccumulatorBlockEntity a = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        AccumulatorBlockEntity b = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 2));
        TeslaCoilBlockEntity relay = relay(helper, new BlockPos(2, 2, 1));
        AccumulatorBlockEntity sink = accumulator(helper, new BlockPos(4, 1, 1), Direction.NORTH, 0);
        link(helper, e1, relay.getBlockPos(), Direction.UP, "emitter 1 -> relay");
        link(helper, e2, relay.getBlockPos(), Direction.UP, "emitter 2 -> relay");
        link(helper, relay, sink.getBlockPos(), Direction.EAST, "relay -> sink");
        helper.runAfterDelay(40, () -> {
            long da = full - a.energy.getEnergyStored(), db = full - b.energy.getEnergyStored();
            helper.assertTrue(da + db == sink.energy.getEnergyStored(), "the sink got what both gave");
            helper.assertTrue(da > 0 && db > 0 && Math.abs(da - db) <= io, "both give about half: " + da + " and " + db);
            helper.succeed();
        });
    }

    /** Two relays of one network linked to the same machine: it is fed once, and nothing is lost. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teslaSharedMachineLosesNothing(GameTestHelper helper) {
        int start = 500_000;
        TeslaCoilBlockEntity emitter = emitterOn(helper, new BlockPos(0, 1, 0), start);
        AccumulatorBlockEntity source = (AccumulatorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0));
        TeslaCoilBlockEntity a = relay(helper, new BlockPos(2, 2, 0));
        TeslaCoilBlockEntity b = relay(helper, new BlockPos(2, 2, 2));
        AccumulatorBlockEntity sink = accumulator(helper, new BlockPos(0, 1, 2), Direction.NORTH, 0);
        link(helper, emitter, a.getBlockPos(), Direction.UP, "emitter -> A");
        link(helper, a, b.getBlockPos(), Direction.UP, "A -> B");
        link(helper, a, sink.getBlockPos(), Direction.EAST, "A -> sink");
        link(helper, b, sink.getBlockPos(), Direction.WEST, "B -> the same sink");
        helper.runAfterDelay(40, () -> {
            long drained = start - source.energy.getEnergyStored();
            helper.assertTrue(sink.energy.getEnergyStored() > 20_000, "the sink fills, has " + sink.energy.getEnergyStored());
            helper.assertTrue(drained == sink.energy.getEnergyStored(), "nothing lost: drained " + drained + ", got " + sink.energy.getEnergyStored());
            helper.succeed();
        });
    }

    /**
     * A generator's push rate is one budget for all six faces, split evenly over the receivers, with or without the
     * neighbour cache; what one receiver does not take goes to the others.
     */
    @GameTest(template = "empty")
    public static void pushToNeighborsSharesOneBudget(GameTestHelper helper) {
        BlockPos center = new BlockPos(1, 2, 1);
        java.util.List<AccumulatorBlockEntity> receivers = new java.util.ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos at = center.relative(dir);
            // the front (output face, horizontal) points away, so the face touching the centre takes FE in
            Direction front = dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
            helper.setBlock(at, PowerRegistry.ACCUMULATOR_1.get().defaultBlockState().setValue(PowerBlock.FACING, front));
            receivers.add((AccumulatorBlockEntity) helper.getBlockEntity(at));
        }
        var source = new com.arno.robotica.core.energy.MachineEnergyStorage(1_000_000, 0, 1_000_000, () -> {});
        source.setEnergy(1_000_000);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(center);
        int sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 600);
        helper.assertTrue(sent == 600, "600 FE in total, not per face: sent " + sent);
        helper.assertTrue(source.getEnergyStored() == 1_000_000 - 600, "the source paid what was sent");
        for (AccumulatorBlockEntity r : receivers) helper.assertTrue(r.stored() == 100, "an even share each, got " + r.stored());

        var cache = new com.arno.robotica.core.energy.EnergyNeighbors();
        sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 6_000, cache);
        int total = receivers.stream().mapToInt(AccumulatorBlockEntity::stored).sum();
        helper.assertTrue(sent == 6_000 && total == 6_600, "cached lookups share the budget too: sent " + sent + ", stored " + total);

        // a full receiver leaves its share to the others
        receivers.get(0).energy.setEnergy(receivers.get(0).capacity());
        sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 500, cache);
        helper.assertTrue(sent == 500, "the rest take the full budget: sent " + sent);

        // never more than the source holds
        source.setEnergy(50);
        sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 600, cache);
        helper.assertTrue(sent == 50 && source.getEnergyStored() == 0, "only what the source holds: sent " + sent);
        helper.succeed();
    }
}
