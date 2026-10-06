package com.arno.robotica.industry.client;

import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.recipe.Machine;
import net.minecraft.client.RecipeBookCategories;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

/** Client-only entry point of the industry module. Called from RoboticaClient. JEI support: client/jei (optional). */
public final class IndustryClient {
    private IndustryClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(IndustryClient::registerScreens);
        modBus.addListener(IndustryClient::registerRecipeCategories);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(IndustryRegistry.PROCESSING_MENU.get(), ProcessingScreen::new);
        event.register(IndustryRegistry.RTG_MENU.get(), RtgScreen::new);
    }

    /** The machines have no recipe book; this keeps the client from warning about unknown recipe categories. */
    private static void registerRecipeCategories(RegisterRecipeBookCategoriesEvent event) {
        for (Machine machine : Machine.values()) {
            event.registerRecipeCategoryFinder(IndustryRegistry.recipeType(machine).get(), holder -> RecipeBookCategories.UNKNOWN);
        }
    }
}
