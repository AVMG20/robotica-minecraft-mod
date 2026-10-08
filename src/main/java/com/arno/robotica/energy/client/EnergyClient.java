package com.arno.robotica.energy.client;

import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only entry point of the energy module. Called from RoboticaClient. */
public final class EnergyClient {
    private EnergyClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(EnergyRegistry.REACTOR_MENU.get(), CoreReactorScreen::new);
            event.register(EnergyRegistry.BANK_MENU.get(), BankScreen::new);
            event.register(EnergyRegistry.SPIRE_MENU.get(), SpireScreen::new);
            event.register(EnergyRegistry.COLLIDER_MENU.get(), ColliderScreen::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(EnergyRegistry.REACTOR_BE.get(), CoreReactorRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.BANK_BE.get(), ControllerHighlightRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.SPIRE_BE.get(), SpireRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.COLLIDER_BE.get(), ColliderRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> StructureHighlight.clear());
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, EnergyClient::coreWearTooltip);
    }

    /** A core that already burned in a Core Reactor shows how much is left. */
    private static void coreWearTooltip(ItemTooltipEvent event) {
        Integer wear = event.getItemStack().get(EnergyRegistry.CORE_WEAR.get());
        if (wear == null || wear <= 0) return;
        EnergyDataMaps.ReactorCore core = EnergyDataMaps.reactorCore(event.getItemStack());
        if (core == null || core.life() <= 0) return;
        long left = Math.round(100.0 * Math.max(0, core.life() - wear) / core.life());
        event.getToolTip().add(Component.translatable("tooltip.robotica.core_wear", left).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
