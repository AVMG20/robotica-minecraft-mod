package com.arno.robotica.processing.client.jei;

import com.arno.robotica.Robotica;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.arno.robotica.processing.media.GrindingMedia;
import com.arno.robotica.processing.recipe.GrindingLogic;
import com.arno.robotica.processing.recipe.GrindingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration of the processing module (optional, found through {@link JeiPlugin}, never loaded without JEI):
 * a Grinder category with the robotica:grinding recipes and every generic c: tag rule of the pack, the Electric
 * Furnaces as smelting catalysts, and an info page per grinding media item.
 */
@JeiPlugin
public class ProcessingJeiPlugin implements IModPlugin {
    static final RecipeType<GrindingDisplay> GRINDING = new RecipeType<>(Robotica.id("grinding"), GrindingDisplay.class);

    @Override
    public ResourceLocation getPluginUid() {
        return Robotica.id("processing_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GrindingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        List<RecipeHolder<GrindingRecipe>> recipes = level.getRecipeManager().getAllRecipesFor(ProcessingRegistry.GRINDING_TYPE.get());
        List<GrindingDisplay> displays = new ArrayList<>();
        for (RecipeHolder<GrindingRecipe> holder : recipes) {
            GrindingRecipe r = holder.value();
            if (r.byTag()) continue;   // shown below with the live tag rule and config counts
            boolean boostable = false;
            for (ItemStack s : r.ingredient().getItems()) boostable |= GrindingLogic.isBoostable(s);
            displays.add(new GrindingDisplay(holder.id(), r.ingredient(), r.result(), r.extras(), r.minTier(), r.time(), boostable));
        }
        for (GrindingLogic.TagRule rule : GrindingLogic.tagRules(stack -> GrindingLogic.hasRecipe(level, stack))) {
            Ingredient input = Ingredient.of(rule.inputs().toArray(Item[]::new));
            displays.add(new GrindingDisplay(rule.id(), input, rule.main(), rule.extras(), 1, 0, rule.kind() != GrindingLogic.Kind.INGOT));
        }
        registration.addRecipes(GRINDING, displays);

        for (Item item : BuiltInRegistries.ITEM) {
            GrindingMedia media = item.builtInRegistryHolder().getData(GrindingMedia.TYPE);
            if (media == null) continue;
            registration.addItemStackInfo(new ItemStack(item), Component.translatable("jei.robotica.info.grinding_media",
                    Math.round(media.bonus() * 100), Math.round(media.secondary() * 100), media.uses(), Math.max(1, media.tier())));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(GRINDING, ProcessingRegistry.GRINDERS.stream().map(DeferredBlock::get).toArray(ProcessingMachineBlock[]::new));
        registration.addRecipeCatalysts(RecipeTypes.SMELTING, ProcessingRegistry.FURNACES.stream().map(DeferredBlock::get).toArray(ProcessingMachineBlock[]::new));
    }
}
