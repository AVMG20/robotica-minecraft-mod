package com.arno.robotica.storage.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.menu.StorageMenu;
import com.arno.robotica.storage.menu.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** Headless tests of the Storage Terminal: ./gradlew runGameTestServer */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class StorageGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static StorageTerminalBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, StorageContent.TERMINAL.get());
        return helper.getBlockEntity(POS);
    }

    private static int total(StorageTerminalBlockEntity be, Item item) {
        return be.count(new ItemStack(item));
    }

    private static int total(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    @GameTest(template = "empty")
    public static void insertAndExtractThroughCapability(GameTestHelper helper) {
        place(helper);
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, null}) {
            IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), side);
            helper.assertTrue(handler != null, "item capability on side " + side);
        }
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), Direction.UP);
        helper.assertTrue(handler.getSlots() == StorageTerminalBlockEntity.BASE_SLOTS, "81 slots, got " + handler.getSlots());
        ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.COBBLESTONE, 150), false);
        helper.assertTrue(rest.isEmpty(), "everything fits");
        int found = 0;
        for (int i = 0; i < handler.getSlots(); i++) found += handler.getStackInSlot(i).getCount();
        helper.assertTrue(found == 150, "150 stored, got " + found);
        ItemStack out = ItemStack.EMPTY;
        for (int i = handler.getSlots() - 1; i >= 0 && out.isEmpty(); i--) out = handler.extractItem(i, 10, false);
        helper.assertTrue(out.getCount() == 10 && out.is(Items.COBBLESTONE), "extracted 10 cobblestone");
        StorageTerminalBlockEntity be = helper.getBlockEntity(POS);
        helper.assertTrue(total(be, Items.COBBLESTONE) == 140, "140 left");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void capacityGrowsWithExpansions(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        helper.assertTrue(be.capacity() == 81, "base 81, got " + be.capacity());
        be.upgrades.setStackInSlot(0, new ItemStack(StorageContent.EXPANSION_MK1.get()));
        helper.assertTrue(be.capacity() == 162, "Mk1 adds 81, got " + be.capacity());
        be.upgrades.setStackInSlot(1, new ItemStack(StorageContent.EXPANSION_MK2.get()));
        helper.assertTrue(be.capacity() == 324, "Mk2 adds 162, got " + be.capacity());
        helper.assertTrue(!be.upgrades.isItemValid(2, new ItemStack(StorageContent.EXPANSION_MK1.get())), "one of each Mk only");
        be.upgrades.setStackInSlot(2, new ItemStack(StorageContent.EXPANSION_MK3.get()));
        helper.assertTrue(be.capacity() == 648, "everything is 648, got " + be.capacity());
        helper.assertTrue(be.expansionCount() == 3, "3 expansions");
        IItemHandler handler = be.access();
        // without power the expansion slots take no new items
        helper.assertTrue(!be.isPowered(), "no power yet");
        helper.assertTrue(handler.insertItem(100, new ItemStack(Items.DIRT), true).getCount() == 1, "slot 100 refuses without power");
        helper.assertTrue(handler.insertItem(5, new ItemStack(Items.DIRT), true).isEmpty(), "base slots still work");
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        helper.succeedWhen(() -> {
            helper.assertTrue(be.isPowered(), "powered once it has energy");
            helper.assertTrue(handler.insertItem(100, new ItemStack(Items.DIRT), true).isEmpty(), "slot 100 takes items with power");
            helper.assertTrue(handler.getSlots() == 648, "all slots visible");
        });
    }

    @GameTest(template = "empty")
    public static void removingAnExpansionCompacts(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        be.upgrades.setStackInSlot(0, new ItemStack(StorageContent.EXPANSION_MK1.get()));
        be.items.setStackInSlot(120, new ItemStack(Items.DIAMOND, 5));
        be.items.setStackInSlot(150, new ItemStack(Items.DIAMOND, 7));
        be.upgrades.setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(be.capacity() == 81, "back to 81");
        helper.assertTrue(total(be, Items.DIAMOND) == 12, "nothing lost, got " + total(be, Items.DIAMOND));
        for (int i = 81; i < StorageTerminalBlockEntity.MAX_SLOTS; i++) {
            helper.assertTrue(be.items.getStackInSlot(i).isEmpty(), "slot " + i + " was emptied");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingGridMakesPlanks(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        menu.getSlot(StorageMenu.GRID_START).set(new ItemStack(Items.OAK_LOG));
        ItemStack result = menu.getSlot(StorageMenu.RESULT).getItem();
        helper.assertTrue(result.is(Items.OAK_PLANKS) && result.getCount() == 4, "one log makes 4 planks, got " + result);
        helper.assertTrue(be.craft.get(0).is(Items.OAK_LOG), "the grid lives in the block entity");
        // shift-click the result: planks go into the terminal, the log is used up
        menu.clicked(StorageMenu.RESULT, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(total(be, Items.OAK_PLANKS) == 4, "4 planks in the terminal, got " + total(be, Items.OAK_PLANKS));
        helper.assertTrue(be.craft.get(0).isEmpty(), "log consumed");
        helper.assertTrue(menu.getSlot(StorageMenu.RESULT).getItem().isEmpty(), "result cleared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void searchFilterReturnsMatchingStacks(GameTestHelper helper) {
        List<ItemStack> stored = List.of(new ItemStack(Items.OAK_PLANKS, 40), new ItemStack(Items.OAK_PLANKS, 40),
                new ItemStack(Items.SPRUCE_PLANKS, 10), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIAMOND, 3),
                new ItemStack(CoreItems.COPPER_GEAR.get(), 2), ItemStack.EMPTY);

        List<ItemStack> all = StorageView.build(stored, "", StorageView.Sort.NAME);
        int sum = all.stream().mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(sum == 40 + 40 + 10 + 64 + 3 + 2, "view keeps every item, got " + sum);
        helper.assertTrue(all.stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()), "view stacks are proper stacks");
        helper.assertTrue(all.stream().filter(s -> s.is(Items.OAK_PLANKS)).count() == 2, "80 planks are one 64 stack and one 16 stack");

        List<ItemStack> planks = StorageView.build(stored, "plank", StorageView.Sort.NAME);
        helper.assertTrue(planks.size() == 3 && planks.stream().allMatch(s -> s.is(net.minecraft.tags.ItemTags.PLANKS)), "search 'plank' finds only planks, got " + planks);
        helper.assertTrue(StorageView.build(stored, "spruce plank", StorageView.Sort.NAME).size() == 1, "all words must match");
        helper.assertTrue(StorageView.build(stored, "zzz", StorageView.Sort.NAME).isEmpty(), "no match, no stacks");
        List<ItemStack> mod = StorageView.build(stored, "@robotica", StorageView.Sort.NAME);
        helper.assertTrue(mod.size() == 1 && mod.get(0).is(CoreItems.COPPER_GEAR.get()), "@mod filters by namespace");
        helper.assertTrue(StorageView.build(stored, "#planks", StorageView.Sort.NAME).size() == 3, "#tag filters by tag");

        List<ItemStack> byCount = StorageView.build(stored, "", StorageView.Sort.COUNT);
        helper.assertTrue(byCount.get(0).is(Items.OAK_PLANKS) && byCount.get(0).getCount() == 64, "most items first when sorting by count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void viewSlotsNeverDuplicateItems(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        be.items.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
        be.items.setStackInSlot(40, new ItemStack(Items.COBBLESTONE, 36));
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        menu.broadcastChanges();
        ItemStack shown = menu.viewStack(0);
        helper.assertTrue(shown.is(Items.COBBLESTONE) && shown.getCount() == 64, "view shows a full 64 stack, got " + shown);
        helper.assertTrue(menu.viewStack(1).getCount() == 36, "and the remaining 36");

        // take the full stack
        menu.clicked(StorageMenu.VIEW_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 64, "carries 64");
        helper.assertTrue(total(be, Items.COBBLESTONE) == 36, "36 left in the terminal, got " + total(be, Items.COBBLESTONE));
        // the view is stale (not rebroadcast): put the carried stack away and click the same stale slot again
        player.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(StorageMenu.VIEW_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 36, "a stale 64 slot only gives the real 36, got " + menu.getCarried().getCount());
        helper.assertTrue(total(be, Items.COBBLESTONE) == 0, "terminal empty");
        helper.assertTrue(total(be, Items.COBBLESTONE) + menu.getCarried().getCount() + total(player, Items.COBBLESTONE) == 100, "100 in total, nothing made");

        // put it all back by clicking with a stack on the cursor, then shift-click out
        menu.clicked(StorageMenu.VIEW_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && total(be, Items.COBBLESTONE) == 36, "right-click style deposit of the cursor stack");
        menu.broadcastChanges();
        menu.clicked(StorageMenu.VIEW_START, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(total(be, Items.COBBLESTONE) == 0 && total(player, Items.COBBLESTONE) == 100, "shift-click moved the stack to the player");
        menu.broadcastChanges();

        // quick moving from the player inventory goes into the terminal
        int slot = StorageMenu.PLAYER_START;
        for (int i = StorageMenu.PLAYER_START; i < StorageMenu.PLAYER_END; i++) {
            if (menu.getSlot(i).getItem().is(Items.COBBLESTONE)) {
                slot = i;
                break;
            }
        }
        menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(total(be, Items.COBBLESTONE) + total(player, Items.COBBLESTONE) == 100, "conserved after shift-click in");
        helper.assertTrue(total(be, Items.COBBLESTONE) >= 36, "stack went into the terminal");

        // double click (pick up all) must not gather from the picture
        menu.broadcastChanges();
        int inTerminal = total(be, Items.COBBLESTONE);
        menu.setCarried(new ItemStack(Items.COBBLESTONE, 1));
        menu.clicked(StorageMenu.PLAYER_END - 1, 0, ClickType.PICKUP_ALL, player);
        helper.assertTrue(total(be, Items.COBBLESTONE) == inTerminal, "pick-all leaves the terminal alone");
        helper.assertTrue(menu.getCarried().getCount() + total(be, Items.COBBLESTONE) + total(player, Items.COBBLESTONE) == 101, "pick-all conserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySlotTakesCells(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.fill(cell);
        helper.assertTrue(StorageTerminalBlockEntity.isBattery(cell), "a charged cell is a battery");
        helper.assertTrue(!StorageTerminalBlockEntity.isBattery(new ItemStack(Items.DIRT)), "dirt is not");
        be.battery.setStackInSlot(0, cell);
        helper.succeedWhen(() -> {
            helper.assertTrue(be.energy.getEnergyStored() > 0 || be.isPowered(), "the cell fed the terminal");
            helper.assertTrue(be.isPowered(), "powered through the battery slot");
        });
    }
}
