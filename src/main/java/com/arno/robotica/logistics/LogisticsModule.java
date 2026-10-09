package com.arno.robotica.logistics;

import com.arno.robotica.core.RoboticaTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * Common (both sides) entry point of the logistics module: item pipes (Mk1 to Mk4) that pull from
 * Extract links and push into Insert links through standard item capabilities.
 * Client side: the per-face pipe GUI ({@code client/LogisticsClient}); the pipes are plain multipart block models.
 */
public final class LogisticsModule {
    private LogisticsModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        LogisticsContent.BLOCKS.register(modBus);
        LogisticsContent.ITEMS.register(modBus);
        LogisticsContent.BLOCK_ENTITIES.register(modBus);
        LogisticsContent.MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, LogisticsConfig.SPEC, "robotica-logistics-server.toml");

        RoboticaTab.add(LogisticsContent.ITEM_PIPE_ITEM);
        RoboticaTab.add(LogisticsContent.ITEM_PIPE_MK2_ITEM);
        RoboticaTab.add(LogisticsContent.ITEM_PIPE_MK3_ITEM);
        RoboticaTab.add(LogisticsContent.ITEM_PIPE_MK4_ITEM);
    }
}
