package com.arno.robotica.core.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Client setup for shared core content. */
public final class CoreClient {
    private CoreClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DetailTooltips::onTooltip);
    }
}
