package com.arno.robotica.architect.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.pocket.PocketArchitectItem;
import com.arno.robotica.architect.pocket.PocketMenu;
import com.arno.robotica.architect.pocket.PocketSettings;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Pocket Architect: junk eating, handing out blocks, placing, filling a table, the GUI and the recipe gate. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class PocketArchitectGameTests {
    private static ItemStack pocket(boolean mk2) {
        return new ItemStack(mk2 ? ArchitectRegistry.POCKET_ARCHITECT_MK2.get() : ArchitectRegistry.POCKET_ARCHITECT.get());
    }

    private static PocketArchitectItem item(ItemStack pocket) {
        return (PocketArchitectItem) pocket.getItem();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketEatsJunkIntoMatter(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pocket = pocket(false);
        var inv = player.getInventory();
        inv.setItem(5, pocket);
        inv.setItem(1, new ItemStack(Items.COBBLESTONE, 10));

        helper.assertTrue(PocketArchitectItem.junkList(PocketArchitectItem.settings(pocket)).contains(Items.COBBLESTONE),
                "the starting junk list is the table's voidable junk");
        ItemStack dirt = new ItemStack(Items.DIRT, 5);
        helper.assertTrue(PocketArchitectItem.eatPickup(player, dirt) && dirt.isEmpty(), "picked up dirt is eaten");
        helper.assertTrue(PocketArchitectItem.matter(pocket).equals(new Matter(5, 0)), "junk turns into rustic, got " + PocketArchitectItem.matter(pocket));
        helper.assertTrue(inv.getItem(1).getCount() == 10, "junk already carried stays");
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 2);
        ItemStack logs = new ItemStack(Items.OAK_LOG, 3);
        helper.assertTrue(!PocketArchitectItem.eatPickup(player, diamonds) && diamonds.getCount() == 2, "diamonds are no junk");
        helper.assertTrue(!PocketArchitectItem.eatPickup(player, logs) && logs.getCount() == 3, "logs are not on the starting list");

        PocketArchitectItem.setSettings(pocket, PocketArchitectItem.settings(pocket).withJunk(List.of(Items.OAK_LOG)));
        helper.assertTrue(PocketArchitectItem.eatPickup(player, logs), "an edited list eats what is on it");
        helper.assertTrue(PocketArchitectItem.matter(pocket).rustic() == 5 + 12, "logs are 4 rustic");
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 4);
        helper.assertTrue(!PocketArchitectItem.eatPickup(player, cobble) && cobble.getCount() == 4, "cobble is off the edited list");

        PocketArchitectItem.setSettings(pocket, PocketArchitectItem.settings(pocket).withEatJunk(false));
        ItemStack more = new ItemStack(Items.OAK_LOG, 3);
        helper.assertTrue(!PocketArchitectItem.eatPickup(player, more) && more.getCount() == 3, "eating can be turned off");

        helper.assertTrue(!item(pocket).accepts(new ItemStack(Items.IRON_INGOT)), "the Mk1 holds no refined matter");
        ItemStack mk2 = pocket(true);
        helper.assertTrue(item(mk2).accepts(new ItemStack(Items.IRON_INGOT)), "the Mk2 takes refined items");
        ItemStack ingots = new ItemStack(Items.IRON_INGOT, 3);
        helper.assertTrue(item(mk2).absorb(mk2, ingots) == 3 && PocketArchitectItem.matter(mk2).equals(new Matter(0, 24)), "iron is 8 refined each");

        PocketArchitectItem.setMatter(mk2, new Matter(PocketArchitectItem.CAP - 1, 0));
        ItemStack spill = new ItemStack(Items.COBBLESTONE, 5);
        helper.assertTrue(item(mk2).absorb(mk2, spill) == 1 && spill.getCount() == 4, "a full pocket leaves the rest");
        helper.succeed();
    }

    /** Building blocks go back in for their full price; the Mk1 only takes the rustic-only styles. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketTakesBuildingBlocksBack(GameTestHelper helper) {
        for (BuildStyle style : BuildStyle.values()) {
            for (Role role : Role.values()) {
                Matter v = com.arno.robotica.architect.matter.MatterTable.valueOf(ArchitectRegistry.styleItem(style, role).get());
                helper.assertTrue(v.equals(style.cost), style.blockName(role) + " is worth its price " + style.cost + ", is " + v);
            }
        }
        ItemStack mk1 = pocket(false);
        ItemStack walls = new ItemStack(ArchitectRegistry.styleItem(BuildStyle.DARK_STONE, Role.WALL).get(), 5);
        helper.assertTrue(item(mk1).absorb(mk1, walls) == 5 && walls.isEmpty(), "the Mk1 takes Dark Stone back");
        helper.assertTrue(PocketArchitectItem.matter(mk1).equals(new Matter(10, 0)), "5 walls are 10 rustic, got " + PocketArchitectItem.matter(mk1));
        ItemStack panels = new ItemStack(ArchitectRegistry.styleItem(BuildStyle.PEARL_PANEL, Role.FLOOR).get(), 3);
        helper.assertTrue(!item(mk1).accepts(panels), "the Mk1 holds no refined matter");
        ItemStack mk2 = pocket(true);
        helper.assertTrue(item(mk2).absorb(mk2, panels) == 3 && PocketArchitectItem.matter(mk2).equals(new Matter(6, 3)), "3 panels are 6 rustic and 3 refined");
        helper.succeed();
    }

    /** The pickup event: a junk item on the ground goes into the pocket and the item entity is gone. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketEatsJunkOnPickup(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pocket = pocket(false);
        player.getInventory().setItem(0, pocket);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemEntity gravel = new ItemEntity(helper.getLevel(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, new ItemStack(Items.GRAVEL, 7));
        gravel.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(gravel);
        var event = NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, gravel));
        helper.assertTrue(event.canPickup() == TriState.FALSE && gravel.isRemoved(), "the gravel is eaten, not picked up");
        helper.assertTrue(PocketArchitectItem.matter(pocket).rustic() == 7, "7 gravel is 7 rustic");
        helper.assertTrue(player.getInventory().countItem(Items.GRAVEL) == 0, "no gravel in the inventory");

        ItemEntity diamond = new ItemEntity(helper.getLevel(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        diamond.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(diamond);
        var other = NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, diamond));
        helper.assertTrue(other.canPickup() != TriState.FALSE && !diamond.isRemoved(), "a diamond is picked up as usual");
        diamond.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketHandsOutBuildingBlocks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pocket = pocket(false);
        PocketArchitectItem mk1 = item(pocket);
        int wall = PocketArchitectItem.blockIndex(BuildStyle.TIMBERFRAME, Role.WALL);
        int pearl = PocketArchitectItem.blockIndex(BuildStyle.PEARL_PANEL, Role.WALL);
        PocketArchitectItem.setMatter(pocket, new Matter(10, 0));

        helper.assertTrue(mk1.take(player, pocket, wall, 64) == 5, "10 rustic pays for 5 blocks at 2 each");
        helper.assertTrue(player.getInventory().countItem(ArchitectRegistry.styleItem(BuildStyle.TIMBERFRAME, Role.WALL).get()) == 5, "the blocks are in the inventory");
        helper.assertTrue(PocketArchitectItem.matter(pocket).isZero(), "the matter is spent");
        helper.assertTrue(mk1.take(player, pocket, wall, 1) == PocketArchitectItem.TAKE_NO_MATTER, "no matter, no block");
        PocketArchitectItem.setMatter(pocket, new Matter(100, 100));
        helper.assertTrue(mk1.take(player, pocket, pearl, 1) == PocketArchitectItem.TAKE_LOCKED, "the Mk1 makes no panel blocks");

        ItemStack mk2 = pocket(true);
        PocketArchitectItem.setMatter(mk2, new Matter(20, 3));
        helper.assertTrue(item(mk2).take(player, mk2, pearl, 64) == 3, "refined limits the panel blocks");
        helper.assertTrue(PocketArchitectItem.matter(mk2).equals(new Matter(14, 0)), "a panel block costs 2 rustic and 1 refined");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketPlacesThePickedBlock(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        helper.setBlock(floor, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pocket = pocket(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, pocket);
        int index = PocketArchitectItem.blockIndex(BuildStyle.DARK_STONE, Role.WALL);
        BlockPos abs = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);

        PocketArchitectItem.setMatter(pocket, new Matter(3, 0));
        helper.assertTrue(item(pocket).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.PASS,
                "nothing picked, nothing placed");
        PocketArchitectItem.setSettings(pocket, PocketArchitectItem.settings(pocket).withSelected(index));
        helper.assertTrue(item(pocket).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "the picked block is placed");
        helper.assertBlockPresent(ArchitectRegistry.styleBlock(BuildStyle.DARK_STONE, Role.WALL).get(), floor.above());
        helper.assertTrue(PocketArchitectItem.matter(pocket).equals(new Matter(1, 0)), "one block is paid for");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND) == pocket && pocket.getCount() == 1, "the pocket is not used up");

        BlockPos side = helper.absolutePos(floor.above());
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(side).add(0, 0.5, 0), Direction.UP, side, false);
        helper.assertTrue(item(pocket).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, top)) == InteractionResult.FAIL, "too little matter");
        helper.assertBlockNotPresent(ArchitectRegistry.styleBlock(BuildStyle.DARK_STONE, Role.WALL).get(), floor.above(2));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketFillsTheTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);
        ItemStack pocket = pocket(true);
        PocketArchitectItem.setMatter(pocket, new Matter(500, 40));
        helper.assertTrue(PocketArchitectItem.moveToTable(pocket, table).equals(new Matter(500, 40)), "everything moves");
        helper.assertTrue(table.matter().equals(new Matter(500, 40)) && PocketArchitectItem.matter(pocket).isZero(), "the table holds it now");

        int cap = ArchitectConfig.matterCap();
        table.setMatter(new Matter(cap - 100, cap));
        PocketArchitectItem.setMatter(pocket, new Matter(300, 7));
        helper.assertTrue(PocketArchitectItem.moveToTable(pocket, table).equals(new Matter(100, 0)), "only what fits moves");
        helper.assertTrue(PocketArchitectItem.matter(pocket).equals(new Matter(200, 7)), "the rest stays in the pocket");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketMenuEditsJunkAndFeeds(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pocket = pocket(false);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, pocket);
        PocketMenu menu = new PocketMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        helper.assertTrue(menu.stillValid(player), "menu is valid while the pocket is held");

        // junk slot 0 starts with the first voidable item; set it to sand, then refuse a diamond
        menu.setCarried(new ItemStack(Items.SAND));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        List<net.minecraft.world.item.Item> junk = PocketArchitectItem.junkList(PocketArchitectItem.settings(pocket));
        helper.assertTrue(junk.contains(Items.SAND), "sand was added to the junk list: " + junk);
        helper.assertTrue(menu.getCarried().is(Items.SAND), "a junk slot never takes the real item");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(!PocketArchitectItem.junkList(PocketArchitectItem.settings(pocket)).contains(Items.DIAMOND), "items without matter are no junk");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(!PocketArchitectItem.junkList(PocketArchitectItem.settings(pocket)).contains(Items.SAND), "an empty click removes an entry");

        // feed slot: logs turn into matter at once
        menu.setCarried(new ItemStack(Items.OAK_LOG, 2));
        menu.clicked(menu.feedSlot(), 0, ClickType.PICKUP, player);
        helper.assertTrue(PocketArchitectItem.matter(pocket).rustic() == 8, "fed logs are 8 rustic, got " + PocketArchitectItem.matter(pocket));
        helper.assertTrue(menu.slots.get(menu.feedSlot()).getItem().isEmpty(), "the feed slot is emptied");

        // the slot holding the pocket cannot be picked up
        menu.clicked(menu.lockedSlot(), 0, ClickType.PICKUP, player);
        helper.assertTrue(player.getInventory().getItem(0) == pocket && menu.getCarried().isEmpty(), "the pocket stays in its slot");

        int panel = PocketArchitectItem.blockIndex(BuildStyle.ONYX_PANEL, Role.FLOOR);
        menu.handleAction(PocketMenu.ACTION_SELECT, panel, 0);
        helper.assertTrue(PocketArchitectItem.settings(pocket).selected() == -1, "the Mk1 cannot pick a panel block");
        int floor = PocketArchitectItem.blockIndex(BuildStyle.TIMBERFRAME, Role.FLOOR);
        menu.handleAction(PocketMenu.ACTION_SELECT, floor, 0);
        helper.assertTrue(PocketArchitectItem.settings(pocket).selected() == floor, "a Clean Stone block can be picked");
        menu.handleAction(PocketMenu.ACTION_TAKE, floor, 1);
        helper.assertTrue(player.getInventory().countItem(PocketArchitectItem.blockAt(floor)) == 4, "8 rustic buys 4 floor blocks");
        menu.handleAction(PocketMenu.ACTION_EAT_JUNK, 0, 0);
        helper.assertTrue(!PocketArchitectItem.settings(pocket).eatJunk(), "junk eating toggles off");

        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(!menu.stillValid(player), "the menu closes when the pocket leaves the hand");
        helper.succeed();
    }

    /** Both settings survive a save: the codec round trip keeps an edited (even empty) junk list. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketSettingsSave(GameTestHelper helper) {
        PocketSettings s = PocketSettings.DEFAULT.withSelected(7).withEatJunk(false).withJunk(List.of(Items.SAND, Items.GRAVEL));
        var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var tag = PocketSettings.CODEC.encodeStart(ops, s).getOrThrow();
        helper.assertTrue(PocketSettings.CODEC.parse(ops, tag).getOrThrow().equals(s), "settings round trip");
        PocketSettings empty = PocketSettings.DEFAULT.withJunk(List.of());
        var emptyTag = PocketSettings.CODEC.encodeStart(ops, empty).getOrThrow();
        helper.assertTrue(PocketSettings.CODEC.parse(ops, emptyTag).getOrThrow().junk().isPresent(), "an emptied list stays empty, not default");
        helper.succeed();
    }

    /** The Mk1 needs an Eye of Ender (no day one pocket); the Mk2 is a smithing upgrade of the Mk1. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pocketRecipesAreGated(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var mk1 = recipes.byKey(Robotica.id("pocket_architect")).orElseThrow();
        boolean eye = mk1.value().getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.ENDER_EYE)));
        helper.assertTrue(eye, "the Mk1 recipe takes an Eye of Ender");
        ItemStack fresh = new ItemStack(ArchitectRegistry.ARCHITECT_TABLE_ITEM.get());
        ItemStack used = fresh.copy();
        used.set(ArchitectRegistry.MATTER_COMPONENT.get(), new Matter(500, 0));
        var table = mk1.value().getIngredients().stream().filter(i -> i.test(fresh)).findFirst();
        helper.assertTrue(table.isPresent(), "the Mk1 recipe takes an Architect Table");
        helper.assertTrue(!table.get().test(used), "a table holding matter does not fit, so its matter is never lost");
        var mk2 = recipes.byKey(Robotica.id("pocket_architect_mk2")).orElseThrow();
        helper.assertTrue(mk2.value() instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe smith
                && smith.isBaseIngredient(new ItemStack(ArchitectRegistry.POCKET_ARCHITECT.get())), "the Mk2 is smithed from the Mk1");
        helper.succeed();
    }
}
