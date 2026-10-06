package com.arno.robotica.processing.client;

import com.arno.robotica.processing.ProcessingRegistry;
import net.minecraft.client.RecipeBookCategories;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

/** Client-only entry point of the processing module. Called from RoboticaClient. */
public final class ProcessingClient {
    private ProcessingClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(ProcessingClient::registerScreens);
        modBus.addListener(ProcessingClient::registerRecipeCategories);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ProcessingRegistry.GRINDER_MENU.get(), GrinderScreen::new);
        event.register(ProcessingRegistry.ELECTRIC_FURNACE_MENU.get(), ElectricFurnaceScreen::new);
    }

    /** Grinding recipes have no recipe book; avoid the "Unknown recipe category" warnings. */
    private static void registerRecipeCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ProcessingRegistry.GRINDING_TYPE.get(), holder -> RecipeBookCategories.UNKNOWN);
    }
}
