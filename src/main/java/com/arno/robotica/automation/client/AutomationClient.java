package com.arno.robotica.automation.client;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.rancher.RancherContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point of the automation module. Called from RoboticaClient. */
public final class AutomationClient {
    private AutomationClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(AutomationClient::registerScreens);
        modBus.addListener(AutomationClient::registerRenderers);
        modBus.addListener(ModelEvent.RegisterAdditional.class, FarmBotRenderer::registerModels);
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, e -> e.registerLayerDefinition(RancherModel.LAYER, RancherModel::createLayer));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(RancherContent.MENU.get(), RancherScreen::new);
        event.register(AutomationContent.WORKER_MENU.get(), AreaWorkerScreen::new);
        event.register(AutomationContent.CRATE_MENU.get(), SupplyCrateScreen::new);
        event.register(AutomationContent.SURVEY_RIG_MENU.get(), SurveyRigScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AutomationContent.STUMPY_BE.get(), ctx -> new FarmBotRenderer<>(true));
        event.registerBlockEntityRenderer(AutomationContent.SPROUT_BE.get(), ctx -> new FarmBotRenderer<>(false));
        event.registerBlockEntityRenderer(AutomationContent.EXCAVATOR_BE.get(), ctx -> new AreaOutlineRenderer<>());
        event.registerEntityRenderer(RancherContent.RANCHER_ENTITY.get(), RancherRenderer::new);
    }
}
