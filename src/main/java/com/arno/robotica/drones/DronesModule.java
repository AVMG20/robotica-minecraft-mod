package com.arno.robotica.drones;

import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import com.arno.robotica.drones.net.DroneCommandPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Common (both sides) entry point of the drones module: Mining Drone and Sentry Drone. */
public final class DronesModule {
    private DronesModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        DronesRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, DronesConfig.SPEC, "robotica-drones-server.toml");
        modBus.addListener(DronesModule::registerAttributes);
        modBus.addListener(DronesModule::registerCapabilities);
        modBus.addListener(DroneCommandPayload::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, DroneCommandPayload::onLogout);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DronesRegistry.MINING_DRONE_ENTITY.get(), MiningDrone.createAttributes().build());
        event.put(DronesRegistry.SENTRY_DRONE_ENTITY.get(), SentryDrone.createAttributes().build());
    }

    /** FE can be pushed into a deployed drone (receive only). The loot stays inside until the drone returns. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.EnergyStorage.ENTITY, DronesRegistry.MINING_DRONE_ENTITY.get(), (drone, side) -> drone.energyStorage());
        event.registerEntity(Capabilities.EnergyStorage.ENTITY, DronesRegistry.SENTRY_DRONE_ENTITY.get(), (drone, side) -> drone.energyStorage());
    }
}
