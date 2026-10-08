package com.arno.robotica.core.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.IntTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless smoke tests, run with {@code ./gradlew runGameTestServer}. They boot a real server,
 * so they also prove the mod loads without client classes. They write robotica-audit/items.txt
 * for scripts/audit_assets.py.
 */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class CoreGameTests {

    /** Items that are intentionally not craftable. Keep this short and justify each entry. */
    private static final Set<String> NO_RECIPE_OK = Set.of(
            // industry: ores only come from world generation, the depleted pellet is the waste of the RTG and reactors
            "thorium_ore", "deepslate_thorium_ore", "pyrolite_ore", "resonite_ore", "depleted_fuel_pellet");

    @GameTest(template = "empty")
    public static void energyItemsExposeCapability(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof EnergyItem)) continue;
            if (new ItemStack(item).getCapability(Capabilities.EnergyStorage.ITEM) == null) {
                missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        if (!missing.isEmpty()) helper.fail("Energy items without FE capability: " + missing);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cellsChargeAndMainspringDoesNot(GameTestHelper helper) {
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        IEnergyStorage cap = cell.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(cap != null && cap.receiveEnergy(1000, false) == 1000, "Copper cell should accept 1000 FE");
        helper.assertTrue(cell.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored() == 1000, "Copper cell should store 1000 FE");
        ItemStack spring = new ItemStack(CoreItems.MAINSPRING.get());
        IEnergyStorage springCap = spring.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(springCap != null && springCap.receiveEnergy(1000, false) == 0, "Mainspring must only be wound at the crank");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineEnergyClampsOnLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        MachineEnergyStorage storage = new MachineEnergyStorage(1000, 100, 100, () -> {});
        storage.deserializeNBT(registries, IntTag.valueOf(5000));
        helper.assertTrue(storage.getEnergyStored() == 1000, "Saved FE above the capacity is cut to it, is " + storage.getEnergyStored());
        storage.deserializeNBT(registries, IntTag.valueOf(-50));
        helper.assertTrue(storage.getEnergyStored() == 0, "Negative saved FE loads as 0, is " + storage.getEnergyStored());
        storage.deserializeNBT(registries, IntTag.valueOf(400));
        helper.assertTrue(storage.getEnergyStored() == 400, "FE within the capacity loads as saved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyItemHasARecipe(GameTestHelper helper) {
        var registryAccess = helper.getLevel().registryAccess();
        Set<Item> craftable = new HashSet<>();
        for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
            try {
                ItemStack result = holder.value().getResultItem(registryAccess);
                if (!result.isEmpty()) craftable.add(result.getItem());
            } catch (RuntimeException ignored) {
                // Special recipes may throw without input; they never produce our items.
            }
        }
        Set<String> all = new TreeSet<>();
        List<String> uncraftable = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals(Robotica.MODID)) continue;
            all.add(key.getPath());
            if (!craftable.contains(item) && !NO_RECIPE_OK.contains(key.getPath())) uncraftable.add(key.getPath());
        }
        writeAudit("items.txt", String.join("\n", all) + "\n");
        writeAudit("uncraftable.txt", String.join("\n", uncraftable) + "\n");
        if (!uncraftable.isEmpty()) helper.fail("Items without any recipe: " + uncraftable);
        helper.succeed();
    }

    private static void writeAudit(String name, String content) {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("robotica-audit");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), content);
        } catch (IOException e) {
            Robotica.LOGGER.warn("Could not write audit file {}", name, e);
        }
    }

    /** Stackable cards stack in one slot up to the machine's cap; single cards do not; one slot per kind; silk excludes fortune. */
    @GameTest(template = "empty")
    public static void upgradeCardsStack(GameTestHelper helper) {
        var up = com.arno.robotica.core.upgrade.Upgrades.forMk(() -> 2, java.util.Set.of(
                com.arno.robotica.core.upgrade.UpgradeKind.SPEED, com.arno.robotica.core.upgrade.UpgradeKind.SILK,
                com.arno.robotica.core.upgrade.UpgradeKind.FORTUNE), () -> {});
        var speed = com.arno.robotica.core.upgrade.UpgradeKind.SPEED;
        ItemStack rest = up.insertItem(0, CoreItems.cards(speed, 6), false);
        helper.assertTrue(up.level(speed) == 4 && rest.getCount() == 2, "speed stacks to the cap of 4, rest " + rest.getCount());
        helper.assertTrue(up.insertItem(1, CoreItems.cards(speed, 1), true).getCount() == 1, "a second slot of the same kind is refused");
        ItemStack silk = up.insertItem(1, CoreItems.cards(com.arno.robotica.core.upgrade.UpgradeKind.SILK, 2), false);
        helper.assertTrue(silk.getCount() == 1, "silk is a single card");
        helper.assertTrue(up.insertItem(2, CoreItems.cards(com.arno.robotica.core.upgrade.UpgradeKind.FORTUNE, 1), true).getCount() == 1,
                "fortune is refused next to silk");
        helper.assertTrue(up.insertItem(2, CoreItems.cards(com.arno.robotica.core.upgrade.UpgradeKind.VOID, 1), true).getCount() == 1,
                "a kind the machine does not take is refused");
        helper.assertTrue(com.arno.robotica.core.upgrade.Upgrades.speedMultiplier(4) == 6 && com.arno.robotica.core.upgrade.Upgrades.speedMultiplier(8) == 20,
                "speed steps");
        double prev = 1, prevStep = 0;
        for (int n = 1; n <= 8; n++) {
            double m = com.arno.robotica.core.upgrade.Upgrades.steepEnergyMultiplier(n, 0);
            helper.assertTrue(m - prev > prevStep, "every steep speed card costs more than the last, at " + n);
            prevStep = m - prev;
            prev = m;
        }
        helper.succeed();
    }

    /** A cell in the inventory tops up the FE tool in the hand. */
    @GameTest(template = "empty")
    public static void cellChargesHeldTool(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack drill = new ItemStack(com.arno.robotica.gear.GearItems.BORE_DRILL.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drill);
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.fill(cell);
        int before = ItemEnergy.get(cell);
        helper.onEachTick(() -> cell.inventoryTick(helper.getLevel(), player, 9, false));
        helper.succeedWhen(() -> {
            helper.assertTrue(ItemEnergy.get(player.getMainHandItem()) > 0, "the drill got energy");
            helper.assertTrue(ItemEnergy.get(cell) + ItemEnergy.get(player.getMainHandItem()) == before, "energy moved, none made");
        });
    }
}
