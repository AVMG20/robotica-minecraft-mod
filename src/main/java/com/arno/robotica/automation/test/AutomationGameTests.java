package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless tests of the Excavator: ./gradlew runGameTestServer. Stumpy and Sprout are in {@link FarmBotGameTests}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class AutomationGameTests {

    private static ItemStack chargedCell() {
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.fill(cell);
        return cell;
    }

    private static int count(ChestBlockEntity chest, Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void excavatorMinesIntoChest(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 2, 1);
        BlockPos chestPos = new BlockPos(2, 2, 1);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(machine, AutomationContent.EXCAVATOR.get());
        ExcavatorBlockEntity excavator = helper.getBlockEntity(machine);
        excavator.setSizeOverride(3);
        excavator.battery.setStackInSlot(0, chargedCell());
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.COBBLESTONE) >= 1, "Chest should hold cobblestone from the mined stone");
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(1, 1, 1));
        });
    }

    /** Fluids and blocks the BreakEvent vetoes must be left alone, and the cursor must still move on. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void excavatorHonoursBreakEventVeto(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 2, 1);
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos lava = new BlockPos(0, 1, 1);
        BlockPos freeStone = new BlockPos(2, 1, 1);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(freeStone, Blocks.STONE);
        helper.setBlock(lava, Blocks.LAVA);
        helper.setBlock(machine, AutomationContent.EXCAVATOR.get());
        BlockPos absStone = helper.absolutePos(stone);
        BlockPos absLava = helper.absolutePos(lava);
        Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPos().equals(absStone) || event.getPos().equals(absLava)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        ExcavatorBlockEntity excavator = helper.getBlockEntity(machine);
        excavator.setSizeOverride(3);
        excavator.battery.setStackInSlot(0, chargedCell());
        helper.runAfterDelay(200, () -> {
            NeoForge.EVENT_BUS.unregister(veto);
            helper.assertBlockNotPresent(Blocks.STONE, freeStone);
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.assertBlockPresent(Blocks.LAVA, lava);
            helper.succeed();
        });
    }
}
