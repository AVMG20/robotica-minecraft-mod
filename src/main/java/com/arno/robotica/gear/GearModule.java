package com.arno.robotica.gear;

import com.arno.robotica.core.RoboticaTab;
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
        GearBlocks.BLOCKS.register(modBus);
        GearBlocks.MENUS.register(modBus);
        GearEntities.ENTITIES.register(modBus);
        GearParticles.REGISTER.register(modBus);
        RoboticaTab.add(GearBlocks.TINKERS_BENCH_ITEM);
        GearItems.addToTab();
        container.registerConfig(ModConfig.Type.SERVER, GearConfig.SPEC, "robotica-gear-server.toml");
        GearModuleText.register();
        modBus.addListener(RegisterPayloadHandlersEvent.class, GearActionPayload::register);
    }
}
