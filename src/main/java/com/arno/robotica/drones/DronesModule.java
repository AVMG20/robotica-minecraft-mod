package com.arno.robotica.drones;

import com.arno.robotica.drones.entity.BuddyBot;
import com.arno.robotica.drones.net.DroneCommandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Common (both sides) entry point of the drones module: Buddy Bot, Courier and Hauler Drones. */
public final class DronesModule {
    private DronesModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        DronesRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, DronesConfig.SPEC, "robotica-drones-server.toml");
        modBus.addListener(DronesModule::registerAttributes);
        modBus.addListener(DronesModule::registerCapabilities);
        modBus.addListener(DroneCommandPayload::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, DroneCommandPayload::onLogout);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, DronesModule::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, DronesModule::onRespawn);
        com.arno.robotica.drones.entity.HaulerEvents.register();
    }

    /** Following Buddy Bots go along to the new dimension. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BuddyBot.joinOwnerFromOtherDimensions(player);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BuddyBot.joinOwnerFromOtherDimensions(player);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DronesRegistry.BUDDY_BOT_ENTITY.get(), BuddyBot.createAttributes().build());
        event.put(DronesRegistry.COURIER_DRONE_ENTITY.get(), com.arno.robotica.drones.entity.CourierDrone.createAttributes().build());
        event.put(DronesRegistry.HAULER_DRONE_ENTITY.get(), com.arno.robotica.drones.entity.HaulerDrone.createAttributes().build());
    }

    /** FE can be pushed into a deployed drone (receive only). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.EnergyStorage.ENTITY, DronesRegistry.BUDDY_BOT_ENTITY.get(), (drone, side) -> drone.energyStorage());
        event.registerEntity(Capabilities.EnergyStorage.ENTITY, DronesRegistry.COURIER_DRONE_ENTITY.get(), (drone, side) -> drone.energyStorage());
        event.registerEntity(Capabilities.EnergyStorage.ENTITY, DronesRegistry.HAULER_DRONE_ENTITY.get(), (drone, side) -> drone.energyStorage());
    }
}
