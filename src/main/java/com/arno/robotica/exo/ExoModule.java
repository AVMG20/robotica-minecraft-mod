package com.arno.robotica.exo;

import com.arno.robotica.exo.net.ExoActionPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Common (both sides) entry point of the Exo-Frame armor module. */
public final class ExoModule {
    private ExoModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        ExoRegistry.COMPONENTS.register(modBus);
        ExoRegistry.MATERIALS.register(modBus);
        ExoRegistry.MENUS.register(modBus);
        ExoItems.ITEMS.register(modBus);
        ExoItems.addToTab();
        container.registerConfig(ModConfig.Type.SERVER, ExoConfig.SPEC, "robotica-exo-server.toml");
        modBus.addListener(RegisterPayloadHandlersEvent.class, ExoActionPayload::register);
    }
}
