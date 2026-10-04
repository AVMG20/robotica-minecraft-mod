package com.arno.robotica.power.client;

import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.client.screen.ChargerScreen;
import com.arno.robotica.power.client.screen.CombustionGeneratorScreen;
import com.arno.robotica.power.client.screen.MetalPressScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point of the power module. Called from RoboticaClient. */
public final class PowerClient {
    private PowerClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(PowerClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PowerRegistry.COMBUSTION_GENERATOR_MENU.get(), CombustionGeneratorScreen::new);
        event.register(PowerRegistry.CHARGER_MENU.get(), ChargerScreen::new);
        event.register(PowerRegistry.METAL_PRESS_MENU.get(), MetalPressScreen::new);
    }
}
