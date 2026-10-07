package com.arno.robotica.gear.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.AreaShape;
import com.arno.robotica.gear.tool.BreakQueue;
import com.arno.robotica.gear.tool.GearActions;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToggleKind;
import com.arno.robotica.gear.tool.ToolSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class GearGameTests {

    private static Set<BlockPos> set(List<BlockPos> list) {
        return new HashSet<>(list);
    }

    @GameTest(template = "empty")
    public static void areaShapeMath(GameTestHelper helper) {
        BlockPos o = new BlockPos(10, 20, 30);
        int feet = 100; // far above: keep floor is bounded by the origin row

        List<BlockPos> up3 = AreaShape.positions(o, Direction.UP, AreaMode.AREA_3, feet, false);
        helper.assertTrue(up3.size() == 9, "3x3 on top face should have 9 blocks, got " + up3.size());
        helper.assertTrue(up3.get(0).equals(o), "origin should come first");
        for (BlockPos p : up3) {
            helper.assertTrue(p.getY() == 20 && Math.abs(p.getX() - 10) <= 1 && Math.abs(p.getZ() - 30) <= 1, "3x3 up block off plane: " + p);
        }

        List<BlockPos> north3 = AreaShape.positions(o, Direction.NORTH, AreaMode.AREA_3, feet, false);
        helper.assertTrue(north3.size() == 9, "3x3 on north face should have 9 blocks");
        for (BlockPos p : north3) {
            helper.assertTrue(p.getZ() == 30 && Math.abs(p.getX() - 10) <= 1 && Math.abs(p.getY() - 20) <= 1, "3x3 north block off plane: " + p);
        }

        List<BlockPos> east3 = AreaShape.positions(o, Direction.EAST, AreaMode.AREA_3, feet, false);
        for (BlockPos p : east3) {
            helper.assertTrue(p.getX() == 10 && Math.abs(p.getZ() - 30) <= 1 && Math.abs(p.getY() - 20) <= 1, "3x3 east block off plane: " + p);
        }

        // 3x3x3 from the north face goes south (into the block, away from the player), centred in x and y.
        List<BlockPos> cubeNorth = AreaShape.positions(o, Direction.NORTH, AreaMode.CUBE_3, feet, false);
        helper.assertTrue(cubeNorth.size() == 27, "3x3x3 should have 27 blocks");
        for (BlockPos p : cubeNorth) {
            helper.assertTrue(p.getZ() >= 30 && p.getZ() <= 32 && Math.abs(p.getX() - 10) <= 1 && Math.abs(p.getY() - 20) <= 1, "cube north off: " + p);
        }
        helper.assertTrue(set(cubeNorth).size() == 27, "cube positions must be unique");

        List<BlockPos> cubeUp = AreaShape.positions(o, Direction.UP, AreaMode.CUBE_3, feet, false);
        for (BlockPos p : cubeUp) {
            helper.assertTrue(p.getY() <= 20 && p.getY() >= 18 && Math.abs(p.getX() - 10) <= 1 && Math.abs(p.getZ() - 30) <= 1, "cube up off: " + p);
        }
        List<BlockPos> cubeDown = AreaShape.positions(o, Direction.DOWN, AreaMode.CUBE_3, feet, false);
        for (BlockPos p : cubeDown) {
            helper.assertTrue(p.getY() >= 20 && p.getY() <= 22, "cube down should extend up: " + p);
        }
        List<BlockPos> cubeWest = AreaShape.positions(o, Direction.WEST, AreaMode.CUBE_3, feet, false);
        for (BlockPos p : cubeWest) {
            helper.assertTrue(p.getX() >= 10 && p.getX() <= 12, "cube west should extend east: " + p);
        }

        // Even sizes extend one further in the positive direction.
        List<BlockPos> big = AreaShape.positions(o, Direction.UP, AreaMode.AREA_12, feet, false);
        helper.assertTrue(big.size() == 144, "12x12 should have 144 blocks");
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        for (BlockPos p : big) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
        }
        helper.assertTrue(minX == 5 && maxX == 16, "12 wide should span -5..+6, got " + minX + ".." + maxX);
        helper.assertTrue(AreaShape.positions(o, Direction.UP, AreaMode.CUBE_12, feet, false).size() == 1728, "12x12x12 should have 1728 blocks");
        helper.assertTrue(AreaShape.positions(o, Direction.UP, AreaMode.AREA_5, feet, false).size() == 25, "5x5 should have 25 blocks");
        helper.assertTrue(AreaShape.positions(o, Direction.UP, AreaMode.CUBE_5, feet, false).size() == 125, "5x5x5 should have 125 blocks");

        // Keep floor: nothing below min(origin Y, feet Y).
        List<BlockPos> floorOnWall = AreaShape.positions(o, Direction.NORTH, AreaMode.AREA_3, 20, true);
        helper.assertTrue(floorOnWall.size() == 6, "keep floor on a wall at feet height should drop the bottom row, got " + floorOnWall.size());
        for (BlockPos p : floorOnWall) helper.assertTrue(p.getY() >= 20, "below the floor: " + p);
        List<BlockPos> floorCube = AreaShape.positions(o, Direction.NORTH, AreaMode.CUBE_3, 20, true);
        helper.assertTrue(floorCube.size() == 18, "keep floor on a cube should have 18 blocks, got " + floorCube.size());
        // Feet below the origin: the origin row is the limit, so digging a wall from above the feet keeps everything above feet.
        List<BlockPos> feetLow = AreaShape.positions(o, Direction.NORTH, AreaMode.AREA_3, 19, true);
        helper.assertTrue(feetLow.size() == 9, "feet one below origin keeps the whole 3x3, got " + feetLow.size());
        // Mining straight down never gets filtered: the whole plane is at the origin height.
        helper.assertTrue(AreaShape.positions(o, Direction.UP, AreaMode.AREA_3, 25, true).size() == 9, "flat plane stays complete");
        helper.assertTrue(AreaShape.positions(o, Direction.UP, AreaMode.SINGLE, feet, true).equals(List.of(o)), "single is just the origin");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void handPlateRecipeDamagesHammer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Case(String recipe, ItemStack ingot, ItemStack plate) {}
        List<Case> cases = List.of(
                new Case("iron_plate_from_hammer", new ItemStack(Items.IRON_INGOT), new ItemStack(CoreItems.IRON_PLATE.get())),
                new Case("copper_plate_from_hammer", new ItemStack(Items.COPPER_INGOT), new ItemStack(CoreItems.COPPER_PLATE.get())),
                new Case("gold_plate_from_hammer", new ItemStack(Items.GOLD_INGOT), new ItemStack(CoreItems.GOLD_PLATE.get())));
        for (Case c : cases) {
            Optional<RecipeHolder<?>> byKey = level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(Robotica.MODID, c.recipe));
            helper.assertTrue(byKey.isPresent(), "missing recipe " + c.recipe);
            ItemStack hammer = new ItemStack(GearItems.TINKERS_HAMMER.get());
            CraftingInput input = CraftingInput.of(3, 1, List.of(hammer, c.ingot.copy(), c.ingot.copy()));
            Optional<RecipeHolder<CraftingRecipe>> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            helper.assertTrue(match.isPresent() && match.get().id().getPath().equals(c.recipe), c.recipe + " should match hammer + 2 ingots");
            ItemStack result = match.get().value().assemble(input, level.registryAccess());
            helper.assertTrue(result.is(c.plate.getItem()) && result.getCount() == 1, c.recipe + " should give 1 plate");
            NonNullList<ItemStack> left = match.get().value().getRemainingItems(input);
            helper.assertTrue(left.get(0).is(GearItems.TINKERS_HAMMER.get()) && left.get(0).getDamageValue() == 1,
                    c.recipe + " should leave the hammer with 1 damage");
            helper.assertTrue(left.get(1).isEmpty() && left.get(2).isEmpty(), "ingots are consumed");
        }
        ItemStack worn = new ItemStack(GearItems.TINKERS_HAMMER.get());
        worn.setDamageValue(worn.getMaxDamage() - 1);
        helper.assertTrue(worn.getItem().getCraftingRemainingItem(worn).isEmpty(), "a used up hammer should break in the grid");
        helper.succeed();
    }

    static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
        return player;
    }

    @GameTest(template = "empty")
    public static void drillAreaConsumesEnergyPerBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, center);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.setBlock(center.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(center.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.fill(drill);
        drill.set(GearComponents.MODE.get(), AreaMode.AREA_3);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        int before = ItemEnergy.get(player.getMainHandItem());
        int perBlock = GearConfig.fe(GearConfig.BORE_DRILL_COST, 40);
        helper.assertTrue(player.gameMode.destroyBlock(center), "origin block should break");
        int broken = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockState(center.offset(dx, 0, dz)).isAir()) broken++;
            }
        }
        helper.assertTrue(broken == 9, "3x3 should be gone, broken=" + broken);
        helper.assertTrue(level.getBlockState(center.offset(2, 0, 0)).is(Blocks.STONE), "block outside the area must stay");
        int used = before - ItemEnergy.get(player.getMainHandItem());
        helper.assertTrue(used == 9 * perBlock, "should use 9 x " + perBlock + " FE, used " + used);
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bigAreaRunsThroughQueueAndStopsWhenEmpty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, center);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) level.setBlock(center.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
        }
        ItemStack drill = new ItemStack(GearItems.MAGMA_DRILL.get());
        int perBlock = GearConfig.fe(GearConfig.MAGMA_DRILL_COST, 60);
        // Enough energy for 40 blocks only.
        ItemEnergy.set(drill, perBlock * 40);
        drill.set(GearComponents.MODE.get(), AreaMode.AREA_9);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        helper.assertTrue(player.gameMode.destroyBlock(center), "origin block should break");
        helper.assertTrue(BreakQueue.queued(player.getUUID()) > 0, "81 blocks should be queued, not broken at once");
        for (int i = 0; i < 4; i++) BreakQueue.tick(helper.getLevel().getServer());
        int left = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (!level.getBlockState(center.offset(dx, 0, dz)).isAir()) left++;
            }
        }
        helper.assertTrue(BreakQueue.queued(player.getUUID()) == 0, "queue should be drained or cleared");
        helper.assertTrue(left == 81 - 40, "an empty drill must stop after 40 blocks, " + left + " left");
        helper.assertTrue(ItemEnergy.get(player.getMainHandItem()) == 0, "all energy should be used");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyAndSneakingToolsMineSingleBlocks(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, at);
        ItemStack drill = new ItemStack(GearItems.SERVO_DRILL.get());
        drill.set(GearComponents.MODE.get(), AreaMode.AREA_5);
        GearToolItem tool = (GearToolItem) drill.getItem();
        helper.assertTrue(tool.activeMode(drill, player) == AreaMode.SINGLE, "empty tool has no area mode");
        ItemEnergy.fill(drill);
        helper.assertTrue(tool.activeMode(drill, player) == AreaMode.AREA_5, "charged tool uses its mode");
        player.setShiftKeyDown(true);
        helper.assertTrue(tool.activeMode(drill, player) == AreaMode.SINGLE, "sneaking always mines 1x1");
        player.setShiftKeyDown(false);
        drill.set(GearComponents.MODE.get(), AreaMode.CUBE_12);
        helper.assertTrue(tool.mode(drill) == AreaMode.AREA_3, "a mode the tool does not have falls back to the default mode");
        helper.assertTrue(tool.getDestroySpeed(new ItemStack(GearItems.SERVO_DRILL.get()), Blocks.STONE.defaultBlockState()) == GearToolItem.EMPTY_SPEED,
                "empty drill mines at wooden speed");
        helper.assertTrue(tool.getDestroySpeed(drill, Blocks.STONE.defaultBlockState()) > GearToolItem.EMPTY_SPEED, "charged drill is faster");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chainsawFellsTreeAndChargesPerLog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, base);
        for (int i = 0; i < 5; i++) level.setBlock(base.above(i), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(base.above(5), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), 3);
        level.setBlock(base.above(4).east(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), 3);
        ItemStack saw = new ItemStack(GearItems.CHAINSAW.get());
        ItemEnergy.fill(saw);
        player.setItemInHand(InteractionHand.MAIN_HAND, saw);
        int before = ItemEnergy.get(player.getMainHandItem());
        helper.assertTrue(player.gameMode.destroyBlock(base), "origin log should break");
        for (int i = 0; i < 5; i++) helper.assertTrue(level.getBlockState(base.above(i)).isAir(), "log " + i + " should be felled");
        helper.assertTrue(level.getBlockState(base.above(5)).isAir() && level.getBlockState(base.above(4).east()).isAir(),
                "the chainsaw clears the natural leaves of the tree");
        int used = before - ItemEnergy.get(player.getMainHandItem());
        helper.assertTrue(used == 5 * GearConfig.fe(GearConfig.CHAINSAW_COST, 30), "5 logs should cost 5 x 30 FE, used " + used);
        // A log pile without natural leaves is not a tree: only the hit log breaks.
        BlockPos pile = base.offset(3, 0, 0);
        for (int i = 0; i < 3; i++) level.setBlock(pile.above(i), Blocks.OAK_LOG.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pile), "pile log should break");
        helper.assertTrue(level.getBlockState(pile.above()).is(Blocks.OAK_LOG), "log buildings must survive");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void servoVeinMinesConnectedOre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, base);
        for (int i = 0; i < 6; i++) level.setBlock(base.offset(i, i % 2, 0), Blocks.IRON_ORE.defaultBlockState(), 3);
        level.setBlock(base.offset(0, 0, 5), Blocks.IRON_ORE.defaultBlockState(), 3);
        ItemStack drill = new ItemStack(GearItems.SERVO_DRILL.get());
        ItemEnergy.fill(drill);
        drill.set(GearComponents.MODE.get(), AreaMode.VEIN);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        helper.assertTrue(player.gameMode.destroyBlock(base), "origin ore should break");
        for (int i = 0; i < 6; i++) helper.assertTrue(level.getBlockState(base.offset(i, i % 2, 0)).isAir(), "vein block " + i + " should be mined");
        helper.assertTrue(level.getBlockState(base.offset(0, 0, 5)).is(Blocks.IRON_ORE), "a separate ore must stay");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dropsGoToInventoryOrVoid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, pos);
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.fill(drill);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        // No modules installed: the drop lands on the ground.
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone should break");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 0, "auto-pickup needs the Auto-Pickup module");
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
        helper.assertTrue(!dropped.isEmpty(), "without the void module the drop stays");
        dropped.forEach(ItemEntity::discard);
        Modules.setModule(player.getMainHandItem(), 0, new ItemStack(ModuleItems.get(ModuleKind.AUTO_PICKUP, 1).get()));
        Modules.setModule(player.getMainHandItem(), 1, new ItemStack(ModuleItems.get(ModuleKind.VOID_FILTER, 1).get()));
        Modules.setEnabled(player.getMainHandItem(), ModuleKind.VOID_FILTER, false);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone should break");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 1, "auto-pickup should put the cobblestone in the inventory");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "nothing should drop on the ground");
        Modules.setEnabled(player.getMainHandItem(), ModuleKind.VOID_FILTER, true);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone should break again");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 1, "void filter should delete the second cobblestone");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    /**
     * Fortune and Silk Touch are modules: the switched-on one counts as the enchantment for drops, only one is on at a
     * time, and B (CYCLE_DROPS) steps off, Fortune, Silk Touch, off. Silk Touch really drops stone as stone.
     */
    @GameTest(template = "empty")
    public static void fortuneAndSilkTouchModules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, pos);
        player.getInventory().clearContent();
        var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var silk = lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
        var fortune = lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        ItemStack drill = new ItemStack(GearItems.MAGMA_DRILL.get());
        ItemEnergy.fill(drill);
        helper.assertTrue(drill.getEnchantmentLevel(fortune) == 0, "no built-in fortune any more");
        Modules.setModule(drill, 0, new ItemStack(ModuleItems.get(ModuleKind.FORTUNE, 3).get()));
        Modules.setModule(drill, 1, new ItemStack(ModuleItems.get(ModuleKind.SILK_TOUCH, 1).get()));
        helper.assertTrue(drill.getEnchantmentLevel(fortune) == 3 && drill.getEnchantmentLevel(silk) == 0,
                "Fortune III is on; Silk Touch went in switched off (one of the two at a time)");
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(fortune, drill) == 3,
                "loot sees Fortune III");
        helper.assertTrue(drill.getTagEnchantments().isEmpty(), "nothing is written as a real enchantment");
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        GearActions.apply(player, GearActions.CYCLE_DROPS, 0);
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.getEnchantmentLevel(silk) == 1 && held.getEnchantmentLevel(fortune) == 0, "B: Fortune -> Silk Touch");
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        player.getMainHandItem().set(GearComponents.MODE.get(), AreaMode.SINGLE);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone breaks");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.STONE)), "Silk Touch drops stone");
        drops.forEach(ItemEntity::discard);
        GearActions.apply(player, GearActions.CYCLE_DROPS, 0);
        held = player.getMainHandItem();
        helper.assertTrue(held.getEnchantmentLevel(silk) == 0 && held.getEnchantmentLevel(fortune) == 0, "B: Silk Touch -> off");
        GearActions.apply(player, GearActions.CYCLE_DROPS, 0);
        helper.assertTrue(player.getMainHandItem().getEnchantmentLevel(fortune) == 3, "B: off -> Fortune");
        // The G switch keeps the rule: switching Silk Touch on turns Fortune off.
        GearActions.apply(player, GearActions.MODULE_TOGGLE, ModuleKind.SILK_TOUCH.ordinal());
        helper.assertTrue(Modules.active(player.getMainHandItem(), ModuleKind.SILK_TOUCH) == 1
                && Modules.active(player.getMainHandItem(), ModuleKind.FORTUNE) == 0, "G: Silk Touch on, Fortune off");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    /** Every area tool offers plain 1x1 as a normal mode, starts in its signature mode, and V cycles through all of them. */
    @GameTest(template = "empty")
    public static void modesDefaultsAndCycling(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, at);
        for (var item : List.of(GearItems.TINKERS_HAMMER, GearItems.FELLING_AXE, GearItems.BORE_DRILL, GearItems.CHAINSAW,
                GearItems.SERVO_DRILL, GearItems.MAGMA_DRILL, GearItems.NULL_DRILL)) {
            GearToolItem tool = item.get();
            helper.assertTrue(tool.spec.hasMode(AreaMode.SINGLE), tool + " must offer 1x1 as a mode");
            ItemStack stack = new ItemStack(tool);
            AreaMode expected = tool.spec.isAxe() ? AreaMode.TREE : AreaMode.AREA_3;
            helper.assertTrue(tool.mode(stack) == expected, tool + " should start in " + expected + ", got " + tool.mode(stack));
            helper.assertTrue(Modules.accepts(stack) == tool.spec.isEnergy(), tool + ": only power tools take modules");
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            java.util.Set<AreaMode> seen = new java.util.HashSet<>();
            for (int i = 0; i < tool.spec.modes.size(); i++) {
                GearActions.apply(player, GearActions.CYCLE_MODE, 1);
                seen.add(tool.mode(player.getMainHandItem()));
            }
            helper.assertTrue(seen.size() == tool.spec.modes.size(), tool + ": V must reach every mode, saw " + seen);
            helper.assertTrue(tool.mode(player.getMainHandItem()) == expected, tool + ": a full cycle comes back to the start");
            GearActions.apply(player, GearActions.CYCLE_MODE, -1);
            int back = tool.spec.modes.indexOf(tool.mode(player.getMainHandItem()));
            helper.assertTrue(back == Math.floorMod(tool.spec.modes.indexOf(expected) - 1, tool.spec.modes.size()), tool + ": sneak+V goes back");
        }
        // A forged payload for a toggle the tool does not have is ignored.
        ItemStack axe = new ItemStack(GearItems.FELLING_AXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        int before = ToolSettings.flags(player.getMainHandItem());
        GearActions.apply(player, GearActions.TOGGLE, ToggleKind.AUTO_SMELT.ordinal());
        GearActions.apply(player, GearActions.TOGGLE, 99);
        helper.assertTrue(ToolSettings.flags(player.getMainHandItem()) == before, "invalid toggles must change nothing");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    /** The Felling Axe in 1x1 mode takes one log; in tree mode it fells the tree and replants from the inventory. */
    @GameTest(template = "empty")
    public static void fellingAxeSingleAndReplant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = survivalPlayer(helper, base);
        level.setBlock(base.below(), Blocks.DIRT.defaultBlockState(), 3);
        for (int i = 0; i < 4; i++) level.setBlock(base.above(i), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(base.above(2).east(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), 3);
        ItemStack axe = new ItemStack(GearItems.FELLING_AXE.get());
        axe.set(GearComponents.MODE.get(), AreaMode.SINGLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        helper.assertTrue(player.gameMode.destroyBlock(base.above(3)), "top log breaks");
        helper.assertTrue(level.getBlockState(base.above(2)).is(Blocks.OAK_LOG), "1x1 mode takes only one log");
        player.getMainHandItem().set(GearComponents.MODE.get(), AreaMode.TREE);
        player.getInventory().add(new ItemStack(Items.OAK_SAPLING, 2));
        helper.assertTrue(player.gameMode.destroyBlock(base), "bottom log breaks");
        for (int i = 1; i < 3; i++) helper.assertTrue(level.getBlockState(base.above(i)).isAir(), "log " + i + " felled");
        BreakQueue.tick(level.getServer());
        helper.assertTrue(level.getBlockState(base).is(Blocks.OAK_SAPLING), "a sapling is planted where the trunk stood, found " + level.getBlockState(base));
        helper.assertTrue(player.getInventory().countItem(Items.OAK_SAPLING) >= 1, "one sapling was used");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    /**
     * No enchanting on FE tools, FE weapons and Exo armor: not on the table (no enchantability, nothing rolls), no anvil
     * books, not in the vanilla enchantable tags. The Age 0 durability tools stay enchantable.
     */
    @GameTest(template = "empty")
    public static void noEnchantingOnFeGear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var all = List.of(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY, net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,
                net.minecraft.world.item.enchantment.Enchantments.MENDING, net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,
                net.minecraft.world.item.enchantment.Enchantments.PROTECTION, net.minecraft.world.item.enchantment.Enchantments.FORTUNE,
                net.minecraft.world.item.enchantment.Enchantments.LOOTING);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        List<net.minecraft.world.item.Item> fe = new java.util.ArrayList<>(List.of(GearItems.BORE_DRILL.get(), GearItems.CHAINSAW.get(),
                GearItems.SERVO_DRILL.get(), GearItems.MAGMA_DRILL.get(), GearItems.NULL_DRILL.get(), GearItems.SHOCK_BATON.get(),
                GearItems.RIVET_GUN.get(), GearItems.ARC_BLADE.get(), GearItems.NULL_LANCE.get()));
        for (int mk = 1; mk <= 4; mk++) {
            for (var slot : com.arno.robotica.exo.ExoSuit.SLOTS) fe.add(com.arno.robotica.exo.ExoItems.piece(mk, slot).get());
        }
        var random = net.minecraft.util.RandomSource.create(42);
        for (var item : fe) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(!stack.isEnchantable() && stack.getItem().getEnchantmentValue() == 0, item + " must not go on an enchanting table");
            for (var key : all) {
                var e = lookup.getOrThrow(key);
                helper.assertTrue(!stack.supportsEnchantment(e) && !stack.isPrimaryItemFor(e), item + " must not take " + key.location());
            }
            helper.assertTrue(!stack.isBookEnchantable(book), item + " takes no anvil books");
            ItemStack rolled = net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(random, stack.copy(), 30, level.registryAccess(),
                    Optional.empty());
            helper.assertTrue(rolled.getTagEnchantments().isEmpty(), item + ": enchanted loot rolls nothing");
            for (var tag : List.of(net.minecraft.tags.ItemTags.MINING_ENCHANTABLE, net.minecraft.tags.ItemTags.DURABILITY_ENCHANTABLE,
                    net.minecraft.tags.ItemTags.WEAPON_ENCHANTABLE, net.minecraft.tags.ItemTags.SWORD_ENCHANTABLE,
                    net.minecraft.tags.ItemTags.ARMOR_ENCHANTABLE, net.minecraft.tags.ItemTags.EQUIPPABLE_ENCHANTABLE)) {
                helper.assertTrue(!stack.is(tag), item + " must not be in " + tag.location());
            }
        }
        for (var item : List.of(GearItems.TINKERS_HAMMER.get(), GearItems.FELLING_AXE.get(), GearItems.GEARBLADE.get())) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(stack.isEnchantable() && stack.getItem().getEnchantmentValue() > 0, item + " stays enchantable");
            helper.assertTrue(stack.supportsEnchantment(lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)),
                    item + " takes Unbreaking");
        }
        helper.succeed();
    }

    /** Overclock mines faster (and costs a bit more FE per block); the Power Regulator lowers the cost of tools and weapons. */
    @GameTest(template = "empty")
    public static void overclockAndPowerRegulator(GameTestHelper helper) {
        var stone = Blocks.STONE.defaultBlockState();
        GearToolItem tool = GearItems.SERVO_DRILL.get();
        ItemStack drill = new ItemStack(tool);
        ItemEnergy.fill(drill);
        float speed = drill.getDestroySpeed(stone);
        int cost = tool.cost(drill, stone);
        Modules.setModule(drill, 0, new ItemStack(ModuleItems.get(ModuleKind.OVERCLOCK, 2).get()));
        float fast = drill.getDestroySpeed(stone);
        helper.assertTrue(Math.abs(fast - speed * (1.0 + GearConfig.overclockSpeed(2))) < 1.0E-3, "Overclock II: " + speed + " -> " + fast);
        helper.assertTrue(tool.cost(drill, stone) == (int) Math.round(cost * (1.0 + GearConfig.overclockCost(2))), "Overclock costs more FE");
        helper.assertTrue(drill.getDestroySpeed(Blocks.OAK_PLANKS.defaultBlockState()) <= 1.0F, "no speed on blocks the drill is not for");
        Modules.setEnabled(drill, ModuleKind.OVERCLOCK, false);
        helper.assertTrue(drill.getDestroySpeed(stone) == speed, "switched off: normal speed");

        Modules.setModule(drill, 1, new ItemStack(ModuleItems.get(ModuleKind.POWER_REGULATOR, 1).get()));
        double keep = 1.0 - com.arno.robotica.core.module.ModuleConfig.regulatorSaving(1);
        helper.assertTrue(tool.cost(drill, stone) == (int) Math.round(cost * keep), "the regulator lowers the FE per block");
        var lance = GearItems.NULL_LANCE.get();
        ItemStack weapon = new ItemStack(lance);
        Modules.setModule(weapon, 0, new ItemStack(ModuleItems.get(ModuleKind.POWER_REGULATOR, 3).get()));
        double keep3 = 1.0 - com.arno.robotica.core.module.ModuleConfig.regulatorSaving(3);
        helper.assertTrue(lance.cost(weapon) == (int) Math.round(lance.cost() * keep3), "the regulator lowers the FE per shot");
        ItemStack bore = new ItemStack(GearItems.BORE_DRILL.get());
        helper.assertTrue(Modules.refusal(bore, 0, new ItemStack(ModuleItems.get(ModuleKind.POWER_REGULATOR, 1).get()), List.of()) != null,
                "Power Regulator I needs Age 2");
        helper.succeed();
    }

    /** The FE tools come from the Age 0 tools at a smithing table: hammer to Bore Drill, felling axe to Chainsaw. */
    @GameTest(template = "empty")
    public static void ageOneToolsAreSmithingUpgrades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String id : List.of("bore_drill_from_tinkers_hammer", "chainsaw_from_felling_axe", "shock_baton_from_gearblade")) {
            helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(Robotica.MODID, id)).isPresent(), "missing " + id);
        }
        helper.succeed();
    }

    /** Plain settings toggle right away; a module switch only works once the module is installed. */
    @GameTest(template = "empty")
    public static void settingsAndModuleSwitches(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GearItems.BORE_DRILL.get()));
        GearActions.apply(player, GearActions.TOGGLE, ToggleKind.KEEP_FLOOR.ordinal());
        helper.assertTrue(ToolSettings.has(player.getMainHandItem(), ToggleKind.KEEP_FLOOR), "keep floor (off by default) toggles on");
        GearActions.apply(player, GearActions.MODULE_TOGGLE, ModuleKind.VOID_FILTER.ordinal());
        helper.assertTrue(!player.getMainHandItem().has(com.arno.robotica.core.module.ModuleComponents.MODULES_OFF.get()),
                "no Void Filter installed: nothing to switch");
        Modules.setModule(player.getMainHandItem(), 0, new ItemStack(ModuleItems.get(ModuleKind.VOID_FILTER, 1).get()));
        helper.assertTrue(Modules.active(player.getMainHandItem(), ModuleKind.VOID_FILTER) == 1, "a new module starts on");
        GearActions.apply(player, GearActions.MODULE_TOGGLE, ModuleKind.VOID_FILTER.ordinal());
        helper.assertTrue(Modules.active(player.getMainHandItem(), ModuleKind.VOID_FILTER) == 0, "installed: the switch works");
        helper.succeed();
    }

    /** A cell charging the drill in your hand must not restart the block being mined (only the energy changed). */
    @GameTest(template = "empty")
    public static void chargingDoesNotResetMining(GameTestHelper helper) {
        ItemStack before = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.set(before, 1_000);
        ItemStack after = before.copy();
        ItemEnergy.set(after, 3_000);
        helper.assertTrue(!before.getItem().shouldCauseBlockBreakReset(before, after), "only energy changed: keep mining");
        helper.assertTrue(!before.getItem().shouldCauseReequipAnimation(before, after, false), "no equip animation for charging");
        ItemStack otherMode = after.copy();
        otherMode.set(GearComponents.MODE.get(), AreaMode.SINGLE);
        helper.assertTrue(before.getItem().shouldCauseBlockBreakReset(before, otherMode), "a real change still resets");
        helper.succeed();
    }
}
