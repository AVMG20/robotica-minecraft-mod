package com.arno.robotica.architect.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.matter.MatterTable;
import com.arno.robotica.architect.plan.BlockOp;
import com.arno.robotica.architect.plan.ModuleType;
import com.arno.robotica.architect.plan.Piece;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Headless tests of the architect module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class ArchitectGameTests {
    /** The 9x9 plots do not fit the 3x3x3 template, so the tables stand high above the test structures. */
    private static final BlockPos HIGH = new BlockPos(1, 40, 1);

    // ---------------------------------------------------------------- matter

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void matterConversionFromInput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);

        // Table values straight from the tag driven conversion table
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.COBBLESTONE)).equals(new Matter(1, 0, 0)), "cobblestone is 1 rustic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.OAK_LOG)).equals(new Matter(4, 0, 0)), "logs are 4 rustic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.COPPER_INGOT)).equals(new Matter(0, 4, 0)), "copper ingot is 4 refined");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.IRON_INGOT)).equals(new Matter(0, 8, 0)), "iron ingot is 8 refined");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.OBSIDIAN)).equals(new Matter(0, 0, 4)), "obsidian is 4 exotic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.ENDER_PEARL)).equals(new Matter(0, 0, 16)), "ender pearl is 16 exotic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.DIAMOND)).equals(new Matter(0, 0, 32)), "diamond is 32 exotic");
        helper.assertTrue(!MatterTable.hasValue(new ItemStack(Items.STICK)), "sticks are worthless");

        // Any item handler can insert, but only matter items and nothing can come back out
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "table should expose an item handler");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.STICK, 4), false).getCount() == 4, "worthless items are refused");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 10), false).isEmpty(), "cobblestone is accepted");
        helper.assertTrue(handler.extractItem(0, 10, true).isEmpty(), "nothing can be extracted");
        table.input.insertItem(1, new ItemStack(Items.COPPER_INGOT, 2), false);
        table.input.insertItem(2, new ItemStack(Items.OBSIDIAN, 1), false);
        table.input.insertItem(3, new ItemStack(Items.OAK_LOG, 3), false);

        helper.succeedWhen(() -> {
            Matter m = table.matter();
            helper.assertTrue(m.equals(new Matter(10 + 12, 8, 4)), "matter should be 22/8/4 but is " + m);
            helper.assertTrue(table.input.getStackInSlot(0).isEmpty(), "input should be consumed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void casingUnlocksStyles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(table.unlocked(BuildStyle.TIMBERFRAME), "Timberframe is always unlocked");
        helper.assertTrue(!table.unlocked(BuildStyle.COPPER_WORKS), "Copper Works needs a casing");
        table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.IRON_CASING.get()));
        helper.assertTrue(table.unlocked(BuildStyle.COPPER_WORKS) && !table.unlocked(BuildStyle.STEEL_LAB), "Iron Casing unlocks Copper Works only");
        table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.REINFORCED_CASING.get()));
        helper.assertTrue(table.unlocked(BuildStyle.STEEL_LAB) && !table.unlocked(BuildStyle.NULL_SPIRE), "Reinforced Casing unlocks Steel Lab");
        table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.NULL_CASING.get()));
        helper.assertTrue(table.unlocked(BuildStyle.NULL_SPIRE), "Null Casing unlocks Null Spire");
        helper.succeed();
    }

    // ---------------------------------------------------------------- generator (no world)

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void generatorBlockLists(GameTestHelper helper) {
        for (ModuleType type : ModuleType.values()) {
            for (int mask = 0; mask < 16; mask++) {
                List<BlockOp> ops = type.generate(mask);
                helper.assertTrue(!ops.isEmpty(), type + " mask " + mask + " generated nothing");
                Set<Long> seen = new HashSet<>();
                int lastY = 0;
                boolean floor = false;
                for (BlockOp op : ops) {
                    helper.assertTrue(op.x() >= 0 && op.x() < Plots.SIZE && op.z() >= 0 && op.z() < Plots.SIZE
                            && op.y() >= 0 && op.y() < Plots.HEIGHT, type + " op out of the plot: " + op);
                    helper.assertTrue(seen.add(BlockPos.asLong(op.x(), op.y(), op.z())), type + " places twice at " + op);
                    helper.assertTrue(op.y() >= lastY, type + " is not built bottom-up");
                    lastY = op.y();
                    if (op.y() == 0 && !op.piece().isAir()) floor = true;
                }
                helper.assertTrue(floor, type + " has no floor");
            }
        }

        // Corridor without neighbours runs north-south: 5 wide, lane floor 5x9 = 45 floor/light-free cells
        List<BlockOp> corridor = ModuleType.CORRIDOR.generate(0);
        long corridorFloor = corridor.stream().filter(o -> o.y() == 0 && o.piece().equals(Piece.FLOOR)).count();
        helper.assertTrue(corridorFloor == 45, "corridor floor should be 45 blocks, was " + corridorFloor);
        helper.assertTrue(has(corridor, 4, 1, 0, Piece.AIR) && has(corridor, 4, 1, 8, Piece.AIR), "corridor is open at both ends");
        helper.assertTrue(has(corridor, 2, 1, 4, Piece.PILLAR_Y) || has(corridor, 2, 1, 4, Piece.WALL), "corridor has side walls");

        // Doors open toward the mask and nowhere else; a lone hall gets an entrance on the south
        List<BlockOp> south = ModuleType.HALL.generate(0);
        helper.assertTrue(has(south, 4, 1, 8, Piece.AIR) && has(south, 3, 3, 8, Piece.AIR) && has(south, 5, 2, 8, Piece.AIR), "default hall door on the south");
        helper.assertTrue(has(south, 4, 1, 0, Piece.WALL), "default hall is closed on the north");
        List<BlockOp> north = ModuleType.HALL.generate(Plots.bit(Plots.N) | Plots.bit(Plots.W));
        helper.assertTrue(has(north, 4, 1, 0, Piece.AIR) && has(north, 0, 1, 4, Piece.AIR), "hall opens north and west");
        helper.assertTrue(has(north, 4, 1, 8, Piece.WALL) && has(north, 8, 1, 4, Piece.WALL), "hall stays closed south and east");
        helper.assertTrue(ModuleType.STAIRWELL.effectiveMask(Plots.bit(Plots.N)) == Plots.bit(Plots.S), "stairwell has no north door");
        helper.succeed();
    }

    private static boolean has(List<BlockOp> ops, int x, int y, int z, Piece piece) {
        for (BlockOp op : ops) {
            if (op.x() == x && op.y() == y && op.z() == z) return op.piece().equals(piece);
        }
        return false;
    }

    // ---------------------------------------------------------------- building

    /** A corridor builds completely out of matter and FE; the table keeps its own cell and the bill is paid. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void corridorBuildsFully(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, false);
        BlockPos tablePos = table.getBlockPos();
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, Plots.CENTER, ModuleType.CORRIDOR.id()) == null, "queueing should work");
        helper.assertTrue(table.queueSize() == 1, "one build queued");
        int energyBefore = table.energy.getEnergyStored();
        Matter matterBefore = table.matter();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.queueSize() == 0, "build should be finished, progress " + table.progressPermille() + ", status " + table.status());
            List<BlockOp> ops = ModuleType.CORRIDOR.generate(0);
            BlockPos origin = Plots.origin(tablePos, Plots.CENTER);
            int checked = 0;
            for (BlockOp op : ops) {
                if (Plots.isTableCell(Plots.CENTER, op.x(), op.y(), op.z())) continue;
                BlockPos at = origin.offset(op.x(), op.y(), op.z());
                BlockState expected = op.piece().resolve(BuildStyle.TIMBERFRAME);
                BlockState actual = helper.getLevel().getBlockState(at);
                helper.assertTrue(actual == expected, "at " + op + " expected " + expected + " but found " + actual);
                checked++;
            }
            helper.assertTrue(checked > 100, "should have checked a real corridor, checked " + checked);
            helper.assertTrue(helper.getLevel().getBlockState(tablePos).is(ArchitectRegistry.ARCHITECT_TABLE.get()), "table must survive");
            helper.assertTrue(table.energy.getEnergyStored() < energyBefore, "energy should have been spent");
            Matter after = table.matter();
            helper.assertTrue(after.rustic() < matterBefore.rustic(), "rustic matter should have been spent");
            helper.assertTrue(after.refined() == matterBefore.refined(), "Timberframe uses no refined matter");
            helper.assertTrue(table.status() == ArchitectTableBlockEntity.ST_IDLE, "table should be idle");
        });
    }

    /** Clear terrain removes plain blocks in the footprint (and pays rustic matter) but never block entities. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void clearTerrainRemovesBlocksButKeepsContainers(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, true);
        BlockPos tablePos = table.getBlockPos();
        BlockPos origin = Plots.origin(tablePos, Plots.CENTER);
        // Interior lane cells of the corridor: local (3,1,3) and (5,2,3) are stone, (3,1,5) a chest
        BlockPos stone1 = origin.offset(3, 1, 3);
        BlockPos stone2 = origin.offset(5, 2, 3);
        BlockPos chest = origin.offset(3, 1, 5);
        helper.getLevel().setBlockAndUpdate(stone1, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(stone2, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        table.setMatter(new Matter(300, 0, 0));
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_CLEAR, 1, 0) == null, "toggle clear terrain");
        helper.assertTrue(table.clearTerrain(), "clear terrain should be on");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, Plots.CENTER, ModuleType.CORRIDOR.id()) == null, "queueing should work");

        helper.succeedWhen(() -> {
            helper.assertTrue(table.queueSize() == 0, "build should be finished, status " + table.status());
            helper.assertTrue(helper.getLevel().getBlockState(stone1).isAir(), "stone in the footprint should be gone");
            helper.assertTrue(helper.getLevel().getBlockState(stone2).isAir(), "stone in the footprint should be gone");
            helper.assertTrue(helper.getLevel().getBlockState(chest).is(Blocks.CHEST), "containers must stay");
        });
    }

    /** Without clear terrain, solid blocks stay and only air is built into. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void withoutClearTerrainBlocksStay(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, false);
        BlockPos origin = Plots.origin(table.getBlockPos(), Plots.CENTER);
        BlockPos stone = origin.offset(3, 1, 3);
        helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, Plots.CENTER, ModuleType.CORRIDOR.id());
        helper.succeedWhen(() -> {
            helper.assertTrue(table.queueSize() == 0, "build should be finished");
            helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "stone must not be replaced");
        });
    }

    /** Missing matter pauses the build and names the grade. Style gates are enforced at queue time. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void missingMatterPausesTheBuild(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, false);
        table.setMatter(new Matter(0, 0, 0));
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, Plots.CENTER, ModuleType.CORRIDOR.id());
        helper.succeedWhen(() -> {
            helper.assertTrue(table.queueSize() == 1, "build must wait");
            helper.assertTrue(table.status() == ArchitectTableBlockEntity.ST_NO_RUSTIC, "status should say rustic matter is missing, was " + table.status());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void queueIsValidatedAndSurvivesSaving(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, false);
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, 99, 1) != null, "bad plot is refused");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, 3, 0) != null, "bad module is refused");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_STYLE, BuildStyle.STEEL_LAB.ordinal(), 0) == null, "style action works");
        helper.assertTrue(table.selectedStyle() == BuildStyle.TIMBERFRAME, "a locked style cannot be selected");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, 3, ModuleType.HALL.id()) == null, "valid queue");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, 3, ModuleType.HALL.id()) != null, "plot taken is refused");
        // A hall next to it (east) must also be queued; the first one gets a door toward it when it starts
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_QUEUE, 4, ModuleType.CORRIDOR.id()) == null, "second plot");
        helper.assertTrue(table.queueSize() == 2, "two builds queued");

        var provider = helper.getLevel().registryAccess();
        var tag = table.saveWithoutMetadata(provider);
        BlockPos other = new BlockPos(1, 41, 1);
        helper.setBlock(other.above(5), ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity copy = (ArchitectTableBlockEntity) helper.getBlockEntity(other.above(5));
        copy.loadWithComponents(tag, provider);
        helper.assertTrue(copy.queueSize() == 2, "queue should be restored");
        helper.assertTrue(copy.gridWord(0) == table.gridWord(0) && copy.gridWord(1) == table.gridWord(1), "plot records should be restored");
        helper.assertTrue(copy.matter().equals(table.matter()), "matter should be restored");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onlyTheOwnerCanUseTheTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(table.canUse(player), "an ownerless table can be used");
        table.claimIfFree(player);
        helper.assertTrue(table.canUse(player), "the owner can use the table");
        helper.assertTrue(table.ownerName().equals(player.getGameProfile().getName()), "owner name is stored");
        var other = helper.makeMockPlayer(GameType.SURVIVAL);
        if (other.getUUID().equals(player.getUUID())) {
            helper.succeed();
            return;
        }
        helper.assertTrue(!table.canUse(other), "a stranger must not use the table");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static ArchitectTableBlockEntity preparedTable(GameTestHelper helper, boolean clear) {
        helper.setBlock(HIGH, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(HIGH);
        table.setMatter(new Matter(5000, 5000, 5000));
        table.energy.setEnergy(table.energy.getMaxEnergyStored());
        table.upgrades.setStackInSlot(0, new ItemStack(CoreItems.card(com.arno.robotica.core.upgrade.UpgradeKind.SPEED, 4).get()));
        if (clear) table.handleAction(null, ArchitectTableBlockEntity.ACTION_CLEAR, 1, 0);
        return table;
    }
}
