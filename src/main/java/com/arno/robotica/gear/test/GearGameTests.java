package com.arno.robotica.gear.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.core.upgrade.UpgradeKind;
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

    private static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos at) {
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
        ToolSettings.set(drill, ToggleKind.AUTO_PICKUP, true);
        ToolSettings.set(drill, ToggleKind.VOID_FILTER, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        // Switched on, but no modules installed: the drop lands on the ground.
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone should break");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 0, "auto-pickup needs the Auto-Pickup module");
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
        helper.assertTrue(!dropped.isEmpty(), "without the void module the drop stays");
        dropped.forEach(ItemEntity::discard);
        ToolSettings.set(player.getMainHandItem(), ToggleKind.VOID_FILTER, false);
        ToolSettings.setInstalled(player.getMainHandItem(), ToggleKind.AUTO_PICKUP, true);
        ToolSettings.setInstalled(player.getMainHandItem(), ToggleKind.VOID_FILTER, true);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone should break");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 1, "auto-pickup should put the cobblestone in the inventory");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "nothing should drop on the ground");
        ToolSettings.set(player.getMainHandItem(), ToggleKind.VOID_FILTER, true);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "stone should break again");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 1, "void filter should delete the second cobblestone");
        BreakQueue.clear(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantSwapKeepsPlayerEnchantments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var silk = lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
        var fortune = lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        GearToolItem tool = GearItems.MAGMA_DRILL.get();
        ItemStack drill = new ItemStack(tool);
        // Player applied Fortune I on an anvil; the swap wants Fortune II in fortune mode.
        drill.enchant(fortune, 1);
        ToolSettings.setEnchantMode(drill, ToolSettings.ENCHANT_FORTUNE);
        ToolSettings.syncEnchantments(drill, tool, level.registryAccess());
        helper.assertTrue(drill.getEnchantmentLevel(fortune) == 2, "fortune mode should top up to level 2");
        ToolSettings.setEnchantMode(drill, ToolSettings.ENCHANT_SILK);
        ToolSettings.syncEnchantments(drill, tool, level.registryAccess());
        helper.assertTrue(drill.getEnchantmentLevel(silk) == 1, "silk mode should add silk touch");
        helper.assertTrue(drill.getEnchantmentLevel(fortune) == 1, "the player's Fortune I must survive, got " + drill.getEnchantmentLevel(fortune));
        ToolSettings.setEnchantMode(drill, ToolSettings.ENCHANT_NONE);
        ToolSettings.syncEnchantments(drill, tool, level.registryAccess());
        helper.assertTrue(drill.getEnchantmentLevel(silk) == 0, "injected silk touch should be removed");
        helper.assertTrue(drill.getEnchantmentLevel(fortune) == 1, "the player's Fortune I must survive the none mode");
        // A player-applied Silk Touch stays when the swap switches away from silk.
        ItemStack other = new ItemStack(tool);
        other.enchant(silk, 1);
        ToolSettings.setEnchantMode(other, ToolSettings.ENCHANT_SILK);
        ToolSettings.syncEnchantments(other, tool, level.registryAccess());
        ToolSettings.setEnchantMode(other, ToolSettings.ENCHANT_NONE);
        ToolSettings.syncEnchantments(other, tool, level.registryAccess());
        helper.assertTrue(other.getEnchantmentLevel(silk) == 1, "the player's Silk Touch must survive");
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
            helper.assertTrue(!tool.toggleActive(stack, ToggleKind.AUTO_PICKUP), tool + ": no auto-pickup without the Auto-Pickup module");
            helper.assertTrue(tool.spec.isEnergy() == tool.spec.toggles.contains(ToggleKind.AUTO_PICKUP)
                    && tool.spec.isEnergy() == tool.spec.toggles.contains(ToggleKind.VOID_FILTER), tool + ": only power tools take modules");
            ItemStack withPickup = stack.copy();
            ToolSettings.setInstalled(withPickup, ToggleKind.AUTO_PICKUP, true);
            helper.assertTrue(tool.toggleActive(withPickup, ToggleKind.AUTO_PICKUP) == tool.spec.isEnergy(),
                    tool + ": with the Auto-Pickup card installed auto-pickup is on by default");
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

    /** Unbreaking on an FE tool lowers the FE per block (III: 40 %); Mending is not offered for FE tools. */
    @GameTest(template = "empty")
    public static void unbreakingSavesEnergy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        GearToolItem tool = GearItems.BORE_DRILL.get();
        ItemStack drill = new ItemStack(tool);
        int base = tool.cost(drill, Blocks.STONE.defaultBlockState());
        drill.enchant(lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        int cheaper = tool.cost(drill, Blocks.STONE.defaultBlockState());
        helper.assertTrue(cheaper == (int) Math.ceil(base / 2.5), "Unbreaking III should cost 40%: " + base + " -> " + cheaper);
        helper.assertTrue(!drill.supportsEnchantment(lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING)), "no Mending on FE tools");
        helper.assertTrue(drill.supportsEnchantment(lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY)), "Efficiency works");
        helper.assertTrue(new ItemStack(GearItems.TINKERS_HAMMER.get()).supportsEnchantment(lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING)),
                "the hammer has durability, so Mending works");
        helper.assertTrue(new ItemStack(tool).isEnchantable(), "drills go on an enchanting table");
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

    /** A module toggle cannot be switched on before its card is installed; plain settings work right away. */
    @GameTest(template = "empty")
    public static void moduleTogglesNeedTheirCard(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GearItems.BORE_DRILL.get()));
        GearActions.apply(player, GearActions.TOGGLE, ToggleKind.VOID_FILTER.ordinal());
        helper.assertTrue(!ToolSettings.has(player.getMainHandItem(), ToggleKind.VOID_FILTER), "the void filter needs its card first");
        GearActions.apply(player, GearActions.TOGGLE, ToggleKind.KEEP_FLOOR.ordinal());
        helper.assertTrue(ToolSettings.has(player.getMainHandItem(), ToggleKind.KEEP_FLOOR), "keep floor (off by default) toggles on without a card");
        ToolSettings.setInstalled(player.getMainHandItem(), ToggleKind.VOID_FILTER, true);
        GearActions.apply(player, GearActions.TOGGLE, ToggleKind.VOID_FILTER.ordinal());
        helper.assertTrue(ToolSettings.has(player.getMainHandItem(), ToggleKind.VOID_FILTER), "installed: the toggle works");
        helper.succeed();
    }

    /**
     * Tinker's Bench: only power tools go in; a card in a module slot installs the module and is used up, taking it out
     * gives the card back; closing the screen returns the tool with its modules and energy.
     */
    @GameTest(template = "empty")
    public static void tinkersBenchInstallsAndRemovesModules(GameTestHelper helper) {
        BlockPos benchPos = new BlockPos(1, 1, 1);
        helper.setBlock(benchPos, GearBlocks.TINKERS_BENCH.get());
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(benchPos));
        player.getInventory().clearContent();
        TinkersBenchMenu menu = new TinkersBenchMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(benchPos)));
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(GearItems.TINKERS_HAMMER.get())), "the hammer takes no modules");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(GearItems.FELLING_AXE.get())), "the felling axe takes no modules");
        ItemStack pickup = CoreItems.cards(UpgradeKind.PICKUP, 3);
        helper.assertTrue(!menu.getSlot(1).isActive() && !menu.getSlot(1).mayPlace(pickup), "no tool: the module slots are closed");

        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.fill(drill);
        menu.getSlot(0).set(drill);
        helper.assertTrue(menu.getSlot(1).mayPlace(pickup) && !menu.getSlot(2).mayPlace(pickup), "the pickup card goes in its own slot");
        menu.getSlot(1).set(pickup.split(1));
        helper.assertTrue(pickup.getCount() == 2, "one card is used");
        helper.assertTrue(ToolSettings.installed(menu.tool(), ToggleKind.AUTO_PICKUP), "the pickup module is installed");
        helper.assertTrue(menu.getSlot(1).getItem().is(CoreItems.card(UpgradeKind.PICKUP).get()), "the slot shows the installed card");

        // Shift-click from the player's inventory (menu slot 3 = inventory slot 9) installs one void card.
        player.getInventory().setItem(9, CoreItems.cards(UpgradeKind.VOID, 2));
        menu.quickMoveStack(player, 1 + TinkersBenchMenu.MODULES.length);
        helper.assertTrue(ToolSettings.installed(menu.tool(), ToggleKind.VOID_FILTER), "shift-click installs the void card");
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 1, "only one void card is used, " + player.getInventory().getItem(9).getCount() + " left");

        // Taking a module out gives the card back.
        ItemStack back = menu.getSlot(1).remove(1);
        helper.assertTrue(back.is(CoreItems.card(UpgradeKind.PICKUP).get()) && !ToolSettings.installed(menu.tool(), ToggleKind.AUTO_PICKUP),
                "removing the module returns the card");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "the slot is empty again");

        // Closing hands the tool back.
        menu.removed(player);
        helper.assertTrue(menu.tool().isEmpty(), "the bench keeps nothing");
        ItemStack returned = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(GearItems.BORE_DRILL.get())) returned = player.getInventory().getItem(i);
        }
        helper.assertTrue(!returned.isEmpty(), "the drill comes back on close");
        helper.assertTrue(ToolSettings.installed(returned, ToggleKind.VOID_FILTER) && ItemEnergy.get(returned) == ItemEnergy.capacity(returned),
                "modules and energy survive the bench");
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
