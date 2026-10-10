package com.arno.robotica.storage.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.menu.StorageMenu;
import com.arno.robotica.storage.menu.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/** Headless tests of the Storage Terminal: ./gradlew runGameTestServer */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class StorageGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** A powered terminal (without power it takes no new items). */
    private static StorageTerminalBlockEntity place(GameTestHelper helper) {
        StorageTerminalBlockEntity be = placeUnpowered(helper);
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        be.updatePowerNow();
        return be;
    }

    private static StorageTerminalBlockEntity placeUnpowered(GameTestHelper helper) {
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
        StorageTerminalBlockEntity be = placeUnpowered(helper);
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
        // without power nothing new goes in, but stored items still come out
        helper.assertTrue(!be.isPowered(), "no power yet");
        helper.assertTrue(handler.insertItem(100, new ItemStack(Items.DIRT), true).getCount() == 1, "slot 100 refuses without power");
        helper.assertTrue(handler.insertItem(5, new ItemStack(Items.DIRT), true).getCount() == 1, "base slots refuse too");
        be.items.setStackInSlot(5, new ItemStack(Items.DIRT, 3));
        helper.assertTrue(handler.extractItem(5, 3, true).getCount() == 3, "taking out works without power");
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

    /** The battery tops the buffer up all the way, not only to half. */
    @GameTest(template = "empty")
    public static void batteryFillsPastHalf(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.fill(cell);
        be.battery.setStackInSlot(0, cell);
        int start = be.energy.getMaxEnergyStored() / 2 + 100;
        be.energy.setEnergy(start);
        be.updatePowerNow();
        helper.assertTrue(be.energy.getEnergyStored() > start, "the cell should charge a half full buffer, got " + be.energy.getEnergyStored());
        helper.succeed();
    }

    /**
     * Two players on one terminal: both see the same picture, but only the real items can be taken. A stale view slot or
     * a stale crafting result in the second menu gives nothing; number keys and Q never make items either.
     */
    @GameTest(template = "empty")
    public static void twoViewersCannotDuplicate(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer pa = helper.makeMockServerPlayerInLevel();
        ServerPlayer pb = helper.makeMockServerPlayerInLevel();
        be.items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        StorageMenu a = new StorageMenu(1, pa.getInventory(), be);
        StorageMenu b = new StorageMenu(1, pb.getInventory(), be);
        a.broadcastChanges();
        b.broadcastChanges();
        helper.assertTrue(b.viewStack(0).getCount() == 64, "B sees 64");

        a.clicked(StorageMenu.VIEW_START, 0, ClickType.PICKUP, pa);
        helper.assertTrue(a.getCarried().getCount() == 64, "A carries 64");
        int itemsAround = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pb.blockPosition()).inflate(4)).size();
        b.clicked(StorageMenu.VIEW_START, 0, ClickType.PICKUP, pb);
        b.clicked(StorageMenu.VIEW_START, 0, ClickType.SWAP, pb);
        b.clicked(StorageMenu.VIEW_START, 1, ClickType.THROW, pb);
        b.clicked(StorageMenu.VIEW_START, 0, ClickType.QUICK_MOVE, pb);
        helper.assertTrue(b.getCarried().isEmpty() && total(pb, Items.COBBLESTONE) == 0, "B's stale picture gives nothing");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pb.blockPosition()).inflate(4)).size() == itemsAround,
                "nothing was thrown");
        helper.assertTrue(total(be, Items.COBBLESTONE) == 0, "the terminal is empty");

        // shared crafting grid: A takes the log back out, B's result is stale and must not craft from air
        a.getSlot(StorageMenu.GRID_START).set(new ItemStack(Items.OAK_LOG));
        a.broadcastChanges();
        b.broadcastChanges();
        helper.assertTrue(b.getSlot(StorageMenu.RESULT).getItem().is(Items.OAK_PLANKS), "B sees planks");
        a.clicked(StorageMenu.GRID_START, 0, ClickType.QUICK_MOVE, pa);
        helper.assertTrue(be.craft.get(0).isEmpty() && total(be, Items.OAK_LOG) == 1, "the log went into the terminal");
        b.clicked(StorageMenu.RESULT, 0, ClickType.QUICK_MOVE, pb);
        b.clicked(StorageMenu.RESULT, 0, ClickType.PICKUP, pb);
        helper.assertTrue(total(be, Items.OAK_PLANKS) == 0 && total(pb, Items.OAK_PLANKS) == 0 && b.getCarried().isEmpty(),
                "no planks from an empty grid");
        helper.succeed();
    }

    /** Hoppers and pipes change the stock without a click: the picture follows within a few ticks, not every tick. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void viewFollowsExternalChanges(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        menu.broadcastChanges();
        helper.assertTrue(menu.viewStack(0).isEmpty(), "empty at first");
        be.access().insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
        helper.succeedWhen(() -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.viewStack(0).is(Items.DIAMOND) && menu.viewStack(0).getCount() == 3, "the view shows the hopper's diamonds");
        });
    }

    /** JEI transfer: the grid fills from the terminal, then the player; shift fills as many crafts as possible. */
    @GameTest(template = "empty")
    public static void fillGridForRecipe(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        be.insert(new ItemStack(Items.OAK_PLANKS, 10), false);
        player.getInventory().add(new ItemStack(Items.STICK, 1));
        menu.getSlot(StorageMenu.GRID_START + 8).set(new ItemStack(Items.DIRT, 2));
        // a wooden pickaxe: 3 planks on top, 2 sticks down the middle; birch or oak planks are both fine
        List<ItemStack> planks = List.of(new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.OAK_PLANKS));
        List<ItemStack> stick = List.of(new ItemStack(Items.STICK));
        List<List<ItemStack>> recipe = List.of(planks, planks, planks, List.of(), stick, List.of(), List.of(), stick, List.of());
        menu.fillGrid(recipe, false);
        helper.assertTrue(be.craft.get(0).is(Items.OAK_PLANKS) && be.craft.get(0).getCount() == 1, "one plank per slot");
        helper.assertTrue(be.craft.get(4).is(Items.STICK) && be.craft.get(7).isEmpty(), "only one stick was there");
        helper.assertTrue(total(be, Items.DIRT) == 2, "what was in the grid went back to the terminal");
        helper.assertTrue(total(be, Items.OAK_PLANKS) == 7, "3 planks taken, got " + total(be, Items.OAK_PLANKS));
        be.insert(new ItemStack(Items.STICK, 9), false);
        menu.fillGrid(recipe, true);
        helper.assertTrue(menu.resultItem().is(Items.WOODEN_PICKAXE), "full recipe now, got " + menu.resultItem());
        helper.assertTrue(be.craft.get(0).getCount() == 3 && be.craft.get(4).getCount() == 3, "shift: 10 planks make 3 crafts, got "
                + be.craft.get(0).getCount() + " / " + be.craft.get(4).getCount());
        helper.assertTrue(total(be, Items.OAK_PLANKS) == 1, "the last plank stays stored");
        helper.succeed();
    }

    /** JEI shows a new Tinker's Hammer; the grid still takes a worn one (from the player here) for the plate recipe. */
    @GameTest(template = "empty")
    public static void fillGridTakesWornTool(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        ItemStack worn = new ItemStack(com.arno.robotica.gear.GearItems.TINKERS_HAMMER.get());
        worn.setDamageValue(5);
        player.getInventory().add(worn);
        be.insert(new ItemStack(Items.IRON_INGOT, 4), false);
        List<ItemStack> hammer = List.of(new ItemStack(com.arno.robotica.gear.GearItems.TINKERS_HAMMER.get()));
        List<ItemStack> iron = List.of(new ItemStack(Items.IRON_INGOT));
        menu.fillGrid(List.of(hammer, iron, iron), false);
        helper.assertTrue(be.craft.get(0).is(com.arno.robotica.gear.GearItems.TINKERS_HAMMER.get()) && be.craft.get(0).getDamageValue() == 5,
                "the worn hammer went in, got " + be.craft.get(0));
        helper.assertTrue(player.getInventory().countItem(com.arno.robotica.gear.GearItems.TINKERS_HAMMER.get()) == 0, "taken from the player");
        helper.assertTrue(menu.resultItem().is(com.arno.robotica.core.item.CoreItems.IRON_PLATE.get()), "plate recipe complete, got " + menu.resultItem());
        helper.succeed();
    }

    /** Shift with a tool recipe: the hammer slot keeps one, the ingots fill up to the hammer's uses left. */
    @GameTest(template = "empty")
    public static void fillGridMaxWithTool(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        be.insert(new ItemStack(com.arno.robotica.gear.GearItems.TINKERS_HAMMER.get()), false);
        be.insert(new ItemStack(Items.IRON_INGOT, 20), false);
        List<ItemStack> hammer = List.of(new ItemStack(com.arno.robotica.gear.GearItems.TINKERS_HAMMER.get()));
        List<ItemStack> iron = List.of(new ItemStack(Items.IRON_INGOT));
        menu.fillGrid(List.of(hammer, iron, iron), true);
        helper.assertTrue(be.craft.get(0).getCount() == 1, "one hammer, got " + be.craft.get(0));
        helper.assertTrue(be.craft.get(1).getCount() == 10 && be.craft.get(2).getCount() == 10, "shift: 20 ingots make 10 crafts, got "
                + be.craft.get(1).getCount() + " / " + be.craft.get(2).getCount());

        ItemStack worn = be.craft.get(0);
        worn.setDamageValue(worn.getMaxDamage() - 4);
        menu.fillGrid(List.of(hammer, iron, iron), true);
        helper.assertTrue(be.craft.get(0).getDamageValue() == be.craft.get(0).getMaxDamage() - 4, "the same worn hammer, got " + be.craft.get(0));
        helper.assertTrue(be.craft.get(1).getCount() == 4 && be.craft.get(2).getCount() == 4, "4 uses left: 4 crafts, got "
                + be.craft.get(1).getCount() + " / " + be.craft.get(2).getCount());
        helper.assertTrue(total(be, Items.IRON_INGOT) == 12, "the rest stays stored");
        helper.succeed();
    }

    /** A broken terminal drops as one item that holds everything; placing it again brings it all back. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void terminalKeepsItemsWhenBroken(GameTestHelper helper) {
        StorageTerminalBlockEntity be = placeUnpowered(helper);
        helper.assertTrue(!be.collectComponents().has(CoreComponents.CONTENTS.get()), "an empty terminal drops plain (it stacks)");
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        be.updatePowerNow();
        be.insert(new ItemStack(Items.DIAMOND, 40), false);
        be.upgrades.setStackInSlot(0, new ItemStack(StorageContent.EXPANSION_MK1.get()));
        be.craft.set(0, new ItemStack(Items.OAK_PLANKS, 3));

        BlockPos abs = helper.absolutePos(POS);
        helper.getLevel().destroyBlock(abs, true);
        ItemStack terminal = onlyDrop(helper, abs);
        helper.assertTrue(terminal.has(CoreComponents.CONTENTS.get()), "the dropped terminal carries its contents");

        StorageTerminalBlockEntity again = placeUnpowered(helper);
        again.applyComponentsFromItemStack(terminal);
        helper.assertTrue(total(again, Items.DIAMOND) == 40, "placed again: the diamonds are back");
        helper.assertTrue(again.upgrades.getStackInSlot(0).is(StorageContent.EXPANSION_MK1.get()), "and the expansion");
        helper.assertTrue(again.craft.get(0).is(Items.OAK_PLANKS) && again.craft.get(0).getCount() == 3, "and the crafting grid");
        helper.assertTrue(again.energy.getEnergyStored() > 0, "and the FE");
        again.updatePowerNow();
        helper.assertTrue(again.isPowered() && !again.insert(terminal.copy(), true).isEmpty(), "a filled terminal never goes into a terminal");
        helper.succeed();
    }

    /** Creative breaking hands out the filled terminal once (no loot there), and nothing for an empty one. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void creativeBreakKeepsItems(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        placeUnpowered(helper);
        player.gameMode.destroyBlock(abs);
        helper.assertTrue(helper.getLevel().getBlockState(abs).isAir(), "broken");
        helper.assertTrue(drops(helper, abs).isEmpty(), "an empty terminal drops nothing in creative");

        place(helper).insert(new ItemStack(Items.DIAMOND, 40), false);
        player.gameMode.destroyBlock(abs);
        ItemStack terminal = onlyDrop(helper, abs);
        StorageTerminalBlockEntity again = placeUnpowered(helper);
        again.applyComponentsFromItemStack(terminal);
        helper.assertTrue(total(again, Items.DIAMOND) == 40, "placed again: the diamonds are back");
        helper.succeed();
    }

    /** An explosion drops the terminal with everything in it, every time. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void explosionKeepsItems(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        be.insert(new ItemStack(Items.DIAMOND, 40), false);
        BlockPos abs = helper.absolutePos(POS);
        LootParams.Builder loot = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(abs))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.EXPLOSION_RADIUS, 4.0F)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, be);
        for (int i = 0; i < 20; i++) {
            List<ItemStack> drops = helper.getLevel().getBlockState(abs).getDrops(loot);
            helper.assertTrue(drops.size() == 1 && drops.get(0).has(CoreComponents.CONTENTS.get()),
                    "one terminal with its contents, got " + drops);
        }
        helper.succeed();
    }

    private static List<ItemStack> drops(GameTestHelper helper, BlockPos abs) {
        List<ItemEntity> entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3));
        List<ItemStack> out = entities.stream().map(e -> e.getItem().copy()).toList();
        entities.forEach(ItemEntity::discard);
        return out;
    }

    /** The one item a broken terminal dropped: the terminal itself, no spilled items. */
    private static ItemStack onlyDrop(GameTestHelper helper, BlockPos abs) {
        List<ItemStack> drops = drops(helper, abs);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(StorageContent.TERMINAL_ITEM.get()),
                "only the terminal drops, got " + drops);
        return drops.get(0);
    }

    /** Filled shulker boxes and bundles stay out of the terminal and its grid; empty ones go in. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void terminalRefusesFilledContainers(GameTestHelper helper) {
        StorageTerminalBlockEntity be = place(helper);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.DIAMOND, 16))));
        helper.assertTrue(be.insert(shulker.copy(), false).getCount() == 1, "a filled shulker box is refused");
        helper.assertTrue(be.insert(bundle.copy(), false).getCount() == 1, "a filled bundle is refused");
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), Direction.UP);
        helper.assertTrue(handler != null && ItemHandlerHelper.insertItemStacked(handler, shulker.copy(), false).getCount() == 1,
                "hoppers and pipes cannot put one in either");
        helper.assertTrue(be.insert(new ItemStack(Items.SHULKER_BOX), false).isEmpty(), "an empty shulker box goes in");
        helper.assertTrue(be.insert(new ItemStack(Items.BUNDLE), false).isEmpty(), "an empty bundle goes in");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StorageMenu menu = new StorageMenu(1, player.getInventory(), be);
        helper.assertTrue(!menu.getSlot(StorageMenu.GRID_START).mayPlace(shulker), "the crafting grid refuses it too");
        helper.assertTrue(menu.getSlot(StorageMenu.GRID_START).mayPlace(new ItemStack(Items.SHULKER_BOX)), "the grid takes an empty one");
        helper.succeed();
    }
}
