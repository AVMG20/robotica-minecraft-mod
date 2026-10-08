package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.rancher.Rancher;
import com.arno.robotica.automation.rancher.RancherContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Rancher game tests in a walled pen with a 9x9 inside (scripts/data/automation_rancher_pen.py; its floor is at test y 1, so everything
 * stands at y 2): ./gradlew runGameTestServer
 */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class RancherGameTests {
    private static final BlockPos HOME = new BlockPos(5, 2, 5);
    private static final BlockPos CHEST = new BlockPos(5, 2, 8);

    private static Rancher rancher(GameTestHelper helper, int target) {
        helper.setBlock(CHEST, Blocks.CHEST);
        Rancher r = helper.spawn(RancherContent.RANCHER_ENTITY.get(), HOME);
        r.setTier(1);
        r.setHome(helper.absolutePos(HOME));
        r.setOwnerUUID(UUID.randomUUID());
        r.setEnergy(r.getEnergyCapacity());
        r.setTarget(target);
        return r;
    }

    private static ChestBlockEntity chest(GameTestHelper helper) {
        return helper.getBlockEntity(CHEST);
    }

    private static int count(ChestBlockEntity chest, Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }

    private static <T extends Animal> T animal(GameTestHelper helper, EntityType<T> type, int x, int z) {
        return helper.spawn(type, new BlockPos(x, 2, z));
    }

    private static int adults(GameTestHelper helper, EntityType<? extends Animal> type) {
        return (int) helper.getEntities(type).stream().filter(a -> !a.isBaby()).count();
    }

    private static int babies(GameTestHelper helper, EntityType<? extends Animal> type) {
        return (int) helper.getEntities(type).stream().filter(Animal::isBaby).count();
    }

    @GameTest(template = "empty")
    public static void rancherSharesSplitEvenly(GameTestHelper helper) {
        helper.assertTrue(Arrays.equals(Rancher.shares(12, 3), new int[]{4, 4, 4}), "12 over 3 species: 4 each");
        helper.assertTrue(Arrays.equals(Rancher.shares(8, 3), new int[]{3, 3, 2}), "8 over 3 species: 3, 3, 2");
        helper.assertTrue(Arrays.equals(Rancher.shares(2, 3), new int[]{2, 2, 2}), "every species keeps a breeding pair");
        helper.succeed();
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 900)
    public static void rancherBreedsBelowTarget(GameTestHelper helper) {
        rancher(helper, 8);
        chest(helper).setItem(0, new ItemStack(Items.CARROT, 4));
        animal(helper, EntityType.PIG, 3, 3);
        animal(helper, EntityType.PIG, 7, 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(babies(helper, EntityType.PIG) >= 1, "A piglet should be born");
            helper.assertTrue(count(chest(helper), Items.CARROT) == 2, "Two carrots should be fed, chest has " + count(chest(helper), Items.CARROT));
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 1200)
    public static void rancherCullsToTarget(GameTestHelper helper) {
        rancher(helper, 2);
        for (int i = 0; i < 5; i++) animal(helper, EntityType.PIG, 2 + i, 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(adults(helper, EntityType.PIG) == 2, "Pigs should be culled to 2, are " + adults(helper, EntityType.PIG));
            helper.assertTrue(count(chest(helper), Items.PORKCHOP) >= 3, "Porkchops should be in the chest");
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 1000)
    public static void rancherKeepsSpeciesEven(GameTestHelper helper) {
        rancher(helper, 4);
        for (int i = 0; i < 4; i++) animal(helper, EntityType.PIG, 2 + i, 3);
        animal(helper, EntityType.CHICKEN, 3, 7);
        animal(helper, EntityType.CHICKEN, 7, 7);
        helper.succeedWhen(() -> {
            helper.assertTrue(adults(helper, EntityType.PIG) == 2, "Pigs should be culled to their share of 2, are " + adults(helper, EntityType.PIG));
            helper.assertTrue(adults(helper, EntityType.CHICKEN) == 2, "Chickens should keep their share of 2");
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 900)
    public static void rancherSkipsBabiesAndNamed(GameTestHelper helper) {
        rancher(helper, 2);
        Pig named = animal(helper, EntityType.PIG, 2, 3);
        named.setCustomName(Component.literal("Wilbur"));
        animal(helper, EntityType.PIG, 4, 3);
        animal(helper, EntityType.PIG, 6, 3);
        animal(helper, EntityType.PIG, 3, 7).setBaby(true);
        animal(helper, EntityType.PIG, 7, 7).setBaby(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(adults(helper, EntityType.PIG) == 2, "One unnamed adult should be culled");
            helper.assertTrue(named.isAlive(), "The named pig stays");
            helper.assertTrue(babies(helper, EntityType.PIG) == 2, "Piglets stay");
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 900)
    public static void rancherShearsSheep(GameTestHelper helper) {
        rancher(helper, 2);
        List<Sheep> sheep = List.of(animal(helper, EntityType.SHEEP, 2, 3), animal(helper, EntityType.SHEEP, 5, 2), animal(helper, EntityType.SHEEP, 8, 3));
        helper.succeedWhen(() -> {
            for (Sheep s : sheep) {
                helper.assertTrue(s.isAlive(), "Sheep are never culled while shearing is on");
                helper.assertTrue(s.isSheared(), "Every sheep should be sheared");
            }
            helper.assertTrue(count(chest(helper), Items.WHITE_WOOL) >= 3, "Wool should be in the chest");
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 600)
    public static void rancherMilksCow(GameTestHelper helper) {
        rancher(helper, 2);
        chest(helper).setItem(0, new ItemStack(Items.BUCKET));
        var cow = animal(helper, EntityType.COW, 5, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest(helper), Items.MILK_BUCKET) == 1, "The bucket should come back as milk");
            helper.assertTrue(count(chest(helper), Items.BUCKET) == 0, "The empty bucket is used");
            helper.assertTrue(cow.isAlive(), "Cows are never culled while milking is on");
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 400)
    public static void rancherIgnoresLoneAnimal(GameTestHelper helper) {
        rancher(helper, 4);
        for (int i = 0; i < 4; i++) animal(helper, EntityType.PIG, 2 + i, 3);
        animal(helper, EntityType.CHICKEN, 5, 7);
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(adults(helper, EntityType.PIG) == 4, "A lone chicken takes no share, pigs are " + adults(helper, EntityType.PIG));
            helper.assertTrue(adults(helper, EntityType.CHICKEN) == 1, "The lone chicken stays");
            helper.succeed();
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 600)
    public static void rancherMilksIntoFullChest(GameTestHelper helper) {
        rancher(helper, 2);
        for (int i = 0; i < 27; i++) chest(helper).setItem(i, new ItemStack(Items.STONE, 64));
        chest(helper).setItem(0, new ItemStack(Items.BUCKET, 3));
        chest(helper).setItem(1, new ItemStack(Items.BUCKET, 1));
        animal(helper, EntityType.COW, 5, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest(helper), Items.MILK_BUCKET) == 1, "The milk bucket replaces the single bucket");
            helper.assertTrue(count(chest(helper), Items.BUCKET) == 3, "The stack of 3 buckets stays");
            helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "Nothing drops");
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 300)
    public static void rancherLeavesDisplayItems(GameTestHelper helper) {
        rancher(helper, 2);
        var display = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.DIAMOND));
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        display.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        display.setNeverPickUp();
        display.setUnlimitedLifetime();
        helper.getLevel().addFreshEntity(display);
        var dropped = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), at.getX() + 2.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.FEATHER));
        helper.getLevel().addFreshEntity(dropped);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(display.isAlive(), "Never-pickup items stay");
            helper.assertTrue(count(chest(helper), Items.FEATHER) == 1, "Loose drops are collected");
            display.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "rancher_pen", timeoutTicks = 400)
    public static void rancherStopsWhenStorageFull(GameTestHelper helper) {
        Rancher r = rancher(helper, 2);
        for (int i = 0; i < 27; i++) chest(helper).setItem(i, new ItemStack(Items.STONE, 64));
        for (int i = 0; i < 4; i++) animal(helper, EntityType.PIG, 2 + i, 3);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(adults(helper, EntityType.PIG) == 4, "No culling while the storage is full");
            helper.assertTrue(r.status() == Rancher.Status.STORAGE_FULL, "Status should be storage full, is " + r.status());
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void rancherSaveLoadKeepsSettings(GameTestHelper helper) {
        Rancher r = helper.spawn(RancherContent.RANCHER_ENTITY.get(), new BlockPos(1, 1, 1));
        r.setTier(2);
        BlockPos home = helper.absolutePos(new BlockPos(1, 1, 1));
        r.setHome(home);
        r.setTarget(13);
        r.setShear(false);
        r.setMilk(false);
        r.setEnergy(12_345);
        CompoundTag tag = new CompoundTag();
        r.saveWithoutId(tag);
        Rancher copy = RancherContent.RANCHER_ENTITY.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.tier() == 2 && copy.home().equals(home) && copy.target() == 13 && !copy.shearOn() && !copy.milkOn()
                && copy.getEnergy() == 12_345, "Saved entity keeps tier, home and settings");

        ItemStack item = r.toItemStack(false);
        helper.assertTrue(item.is(RancherContent.RANCHER_MK2.get()), "Mk2 picks up as the Mk2 item");
        Rancher placed = RancherContent.RANCHER_ENTITY.get().create(helper.getLevel());
        placed.initFromStack(item, null);
        helper.assertTrue(placed.tier() == 2 && placed.target() == 13 && !placed.shearOn() && !placed.milkOn() && placed.getEnergy() == 12_345,
                "The item keeps tier, energy and settings");
        r.discard();
        helper.succeed();
    }
}
