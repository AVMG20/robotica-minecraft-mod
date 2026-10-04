package com.arno.robotica.core.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final Set<String> NO_RECIPE_OK = Set.of();

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
}
