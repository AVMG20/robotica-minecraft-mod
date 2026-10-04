package com.arno.robotica.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.client.ArchitectClient;
import com.arno.robotica.automation.client.AutomationClient;
import com.arno.robotica.core.client.CoreClient;
import com.arno.robotica.gear.client.GearClient;
import com.arno.robotica.power.client.PowerClient;
import com.arno.robotica.replicator.client.ReplicatorClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point. This class and everything it reaches is never loaded on a dedicated server.
 */
@Mod(value = Robotica.MODID, dist = Dist.CLIENT)
public class RoboticaClient {
    public RoboticaClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        CoreClient.init(modBus, container);
        PowerClient.init(modBus, container);
        AutomationClient.init(modBus, container);
        GearClient.init(modBus, container);
        ArchitectClient.init(modBus, container);
        ReplicatorClient.init(modBus, container);
    }
}
