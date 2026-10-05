package com.arno.robotica.power.client;

import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.client.screen.ChargerScreen;
import com.arno.robotica.power.client.screen.CombustionGeneratorScreen;
import com.arno.robotica.power.client.screen.MetalPressScreen;
import com.arno.robotica.power.PowerClientConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.ModContainer;
import net.minecraft.client.RecipeBookCategories;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

/** Client-only entry point of the power module. Called from RoboticaClient. */
public final class PowerClient {
    private PowerClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(PowerClient::registerScreens);
        modBus.addListener(PowerClient::registerRecipeCategories);
        modBus.addListener(PowerClient::registerRenderers);
        container.registerConfig(ModConfig.Type.CLIENT, PowerClientConfig.SPEC, "robotica-power-client.toml");
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PowerRegistry.COMBUSTION_GENERATOR_MENU.get(), CombustionGeneratorScreen::new);
        event.register(PowerRegistry.CHARGER_MENU.get(), ChargerScreen::new);
        event.register(PowerRegistry.METAL_PRESS_MENU.get(), MetalPressScreen::new);
        event.register(PowerRegistry.ENERGY_INFO_MENU.get(), com.arno.robotica.power.client.screen.EnergyInfoScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PowerRegistry.TESLA_COIL_BE.get(), TeslaCoilRenderer::new);
    }

    /** The Metal Press has no recipe book, but the client still looks every recipe up by category; avoid the "Unknown recipe category" warnings. */
    private static void registerRecipeCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(PowerRegistry.PRESSING_TYPE.get(), holder -> RecipeBookCategories.UNKNOWN);
    }
}
