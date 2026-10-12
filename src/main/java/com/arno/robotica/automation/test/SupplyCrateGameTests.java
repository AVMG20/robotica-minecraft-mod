package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.SupplyCrateBlock;
import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Supply Crate Mk1-Mk4: slot count per Mk and the in-place upgrade that keeps the contents. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class SupplyCrateGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private static void use(GameTestHelper helper, Player player, ItemStack stack) {
        BlockPos abs = helper.absolutePos(POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(POS).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /** Each Mk holds more: 27, 54, 84, 108. */
    @GameTest(template = "empty")
    public static void crateSlotsGrowPerMk(GameTestHelper helper) {
        int[] expected = {27, 54, 84, 108};
        var blocks = new SupplyCrateBlock[]{AutomationContent.SUPPLY_CRATE.get(), AutomationContent.SUPPLY_CRATE_MK2.get(),
                AutomationContent.SUPPLY_CRATE_MK3.get(), AutomationContent.SUPPLY_CRATE_MK4.get()};
        for (int i = 0; i < blocks.length; i++) {
            helper.setBlock(POS, blocks[i]);
            SupplyCrateBlockEntity crate = helper.getBlockEntity(POS);
            helper.assertTrue(crate.items.getSlots() == expected[i], "Mk" + (i + 1) + " has " + crate.items.getSlots() + " slots");
        }
        helper.succeed();
    }

    /** Saving and loading keeps the Mk's slot count, not the size written in the tag. */
    @GameTest(template = "empty")
    public static void crateLoadKeepsItsSize(GameTestHelper helper) {
        helper.setBlock(POS, AutomationContent.SUPPLY_CRATE.get());
        SupplyCrateBlockEntity mk1 = helper.getBlockEntity(POS);
        mk1.items.setStackInSlot(26, new ItemStack(Items.DIAMOND, 3));
        CompoundTag saved = mk1.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.setBlock(POS, AutomationContent.SUPPLY_CRATE_MK4.get());
        SupplyCrateBlockEntity mk4 = helper.getBlockEntity(POS);
        mk4.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(mk4.items.getSlots() == 108, "still 108 slots after loading a Mk1 tag: " + mk4.items.getSlots());
        helper.assertTrue(mk4.items.getStackInSlot(26).is(Items.DIAMOND), "the item came along");
        helper.succeed();
    }

    /** Right-click with the next Mk: block swapped, facing and items kept, the old crate given back, no drops. */
    @GameTest(template = "empty")
    public static void crateUpgradesInPlace(GameTestHelper helper) {
        helper.setBlock(POS, AutomationContent.SUPPLY_CRATE.get().defaultBlockState().setValue(SupplyCrateBlock.FACING, Direction.EAST));
        SupplyCrateBlockEntity old = helper.getBlockEntity(POS);
        for (int i = 0; i < old.items.getSlots(); i++) old.items.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        old.items.setStackInSlot(5, new ItemStack(Items.DIAMOND, 7));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack mk3 = new ItemStack(AutomationContent.SUPPLY_CRATE_MK3_ITEM.get());
        use(helper, player, mk3);
        helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.SUPPLY_CRATE.get()) && mk3.getCount() == 1, "Mk3 can not skip Mk2");

        ItemStack mk2 = new ItemStack(AutomationContent.SUPPLY_CRATE_MK2_ITEM.get());
        use(helper, player, mk2);
        helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.SUPPLY_CRATE_MK2.get()), "the block is now Mk2");
        helper.assertTrue(helper.getBlockState(POS).getValue(SupplyCrateBlock.FACING) == Direction.EAST, "facing kept");
        helper.assertTrue(mk2.isEmpty(), "the Mk2 item was used");
        helper.assertTrue(player.getInventory().contains(new ItemStack(AutomationContent.SUPPLY_CRATE_ITEM.get())), "the Mk1 comes back");
        SupplyCrateBlockEntity fresh = helper.getBlockEntity(POS);
        helper.assertTrue(fresh.items.getSlots() == 54, "Mk2 slots: " + fresh.items.getSlots());
        helper.assertTrue(fresh.items.getStackInSlot(5).is(Items.DIAMOND) && fresh.items.getStackInSlot(5).getCount() == 7, "items kept");
        helper.assertTrue(fresh.items.getStackInSlot(26).getCount() == 64 && fresh.items.getStackInSlot(27).isEmpty(), "the new slots are empty");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(POS)).inflate(3)).isEmpty(),
                "nothing dropped");
        helper.succeed();
    }
}
