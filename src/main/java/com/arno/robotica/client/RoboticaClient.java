package com.arno.robotica.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.client.ArchitectClient;
import com.arno.robotica.boss.client.BossClient;
import com.arno.robotica.storage.client.StorageClient;
import com.arno.robotica.automation.client.AutomationClient;
import com.arno.robotica.codex.client.CodexClient;
import com.arno.robotica.core.client.CoreClient;
import com.arno.robotica.drones.client.DronesClient;
import com.arno.robotica.energy.client.EnergyClient;
import com.arno.robotica.exo.client.ExoClient;
import com.arno.robotica.gear.client.GearClient;
import com.arno.robotica.industry.client.IndustryClient;
import com.arno.robotica.power.client.PowerClient;
import com.arno.robotica.processing.client.ProcessingClient;
import com.arno.robotica.replicator.client.ReplicatorClient;
import com.arno.robotica.warp.client.WarpClient;
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
        CodexClient.init(modBus, container);
        PowerClient.init(modBus, container);
        IndustryClient.init(modBus, container);
        EnergyClient.init(modBus, container);
        ProcessingClient.init(modBus, container);
        AutomationClient.init(modBus, container);
        GearClient.init(modBus, container);
        ArchitectClient.init(modBus, container);
        ReplicatorClient.init(modBus, container);
        WarpClient.init(modBus, container);
        DronesClient.init(modBus, container);
        ExoClient.init(modBus, container);
        BossClient.init(modBus, container);
        StorageClient.init(modBus, container);
    }
}
