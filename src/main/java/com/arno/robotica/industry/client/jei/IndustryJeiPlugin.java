package com.arno.robotica.industry.client.jei;

import com.arno.robotica.Robotica;
import com.arno.robotica.industry.IndustryConfig;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.recipe.Machine;
import com.arno.robotica.industry.recipe.ProcessingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.EnumMap;
import java.util.Map;

/**
 * JEI integration of the industry module (optional; JEI finds it through {@link JeiPlugin}, so it is never loaded
 * without JEI). One category per processing machine, every Mk as a catalyst, and info pages for the world-only items.
 */
@JeiPlugin
public class IndustryJeiPlugin implements IModPlugin {
    /** Built from ids, not the registry: JEI loads plugins before registries are bound. */
    private static final Map<Machine, RecipeType<RecipeHolder<ProcessingRecipe>>> TYPES = new EnumMap<>(Machine.class);

    static {
        register();
    }

    @SuppressWarnings("unchecked")
    private static void register() {
        for (Machine machine : Machine.values()) {
            TYPES.put(machine, new RecipeType<>(Robotica.id(machine.recipeId),
                    (Class<RecipeHolder<ProcessingRecipe>>) (Class<?>) RecipeHolder.class));
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Robotica.id("jei_industry");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (Machine machine : Machine.values()) {
            registration.addRecipeCategories(new ProcessingCategory(registration.getJeiHelpers().getGuiHelper(), machine, TYPES.get(machine)));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Machine machine : Machine.values()) {
                registration.addRecipes(TYPES.get(machine), level.getRecipeManager().getAllRecipesFor(IndustryRegistry.recipeType(machine).get()));
            }
        }
        info(registration, IndustryRegistry.THORIUM_ORE.get().asItem(), "jei.robotica.info.thorium_ore");
        info(registration, IndustryRegistry.DEEPSLATE_THORIUM_ORE.get().asItem(), "jei.robotica.info.thorium_ore");
        info(registration, IndustryRegistry.PYROLITE_ORE.get().asItem(), "jei.robotica.info.pyrolite_ore");
        info(registration, IndustryRegistry.RESONITE_ORE.get().asItem(), "jei.robotica.info.resonite_ore");
        registration.addItemStackInfo(new ItemStack(IndustryRegistry.DEPLETED_FUEL_PELLET.get()), Component.translatable("jei.robotica.info.depleted_fuel_pellet"));
        registration.addItemStackInfo(new ItemStack(IndustryRegistry.THORIUM_FUEL_PELLET.get()),
                Component.translatable("jei.robotica.info.thorium_fuel_pellet", IndustryConfig.rtgPower(), IndustryConfig.rtgPelletTicks() / 1200));
    }

    private static void info(IRecipeRegistration registration, net.minecraft.world.item.Item item, String key) {
        registration.addItemStackInfo(new ItemStack(item), Component.translatable(key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Machine machine : Machine.values()) {
            for (int tier = 1; tier <= Machine.TIERS; tier++) {
                registration.addRecipeCatalysts(TYPES.get(machine), IndustryRegistry.machineItem(machine, tier).get());
            }
        }
    }
}
