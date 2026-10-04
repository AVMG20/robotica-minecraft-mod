package com.arno.robotica.gear;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Common (both sides) entry point of the gear module. See docs/DESIGN.md. */
public final class GearModule {
    private GearModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        GearComponents.REGISTER.register(modBus);
        GearItems.ITEMS.register(modBus);
        GearItems.addToTab();
        container.registerConfig(ModConfig.Type.SERVER, GearConfig.SPEC, "robotica-gear-server.toml");
        modBus.addListener(RegisterPayloadHandlersEvent.class, GearActionPayload::register);
    }
}
