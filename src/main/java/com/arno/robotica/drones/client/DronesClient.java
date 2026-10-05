package com.arno.robotica.drones.client;

import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.client.screen.CourierDroneScreen;
import com.arno.robotica.drones.client.screen.MiningDroneScreen;
import com.arno.robotica.drones.client.screen.SentryDroneScreen;
import com.arno.robotica.drones.net.DroneCommandPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only entry point of the drones module. Called from RoboticaClient. */
public final class DronesClient {
    private DronesClient() {}

    private static final String CATEGORY = "key.categories.robotica.drones";
    /** H: nearest Mining Drone digs where you look (again: it returns). Sneak + H: call all drones back. */
    static final KeyMapping COMMAND = new KeyMapping("key.robotica.drones.command", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterMenuScreensEvent.class, DronesClient::registerScreens);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, DronesClient::registerRenderers);
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, DronesClient::registerLayers);
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(COMMAND));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DronesClient::onClientTick);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(DronesRegistry.MINING_MENU.get(), MiningDroneScreen::new);
        event.register(DronesRegistry.SENTRY_MENU.get(), SentryDroneScreen::new);
        event.register(DronesRegistry.COURIER_MENU.get(), CourierDroneScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DronesRegistry.MINING_DRONE_ENTITY.get(), DroneRenderers::mining);
        event.registerEntityRenderer(DronesRegistry.SENTRY_DRONE_ENTITY.get(), DroneRenderers::sentry);
        event.registerEntityRenderer(DronesRegistry.COURIER_DRONE_ENTITY.get(), DroneRenderers::courier);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MiningDroneModel.LAYER, MiningDroneModel::createLayer);
        event.registerLayerDefinition(SentryDroneModel.LAYER, SentryDroneModel::createLayer);
        event.registerLayerDefinition(CourierDroneModel.LAYER, CourierDroneModel::createLayer);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (COMMAND.consumeClick()) {
            if (mc.screen == null) {
                PacketDistributor.sendToServer(new DroneCommandPayload(mc.player.isShiftKeyDown() ? DroneCommandPayload.RECALL : DroneCommandPayload.TUNNEL));
            }
        }
    }
}
