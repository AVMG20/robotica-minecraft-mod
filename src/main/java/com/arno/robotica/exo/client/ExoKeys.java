package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoActions;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.net.ExoActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds: J opens the module screen of the worn suit, K toggles Flight or Jet Assist III hover (with both it cycles
 * Flight, hover, off), R dashes (Dash Thrusters), N pings (Sonar Pulse). None of
 * them clash with vanilla defaults or Robotica's V, B, G (tools) and H (drones). Jump is the Jet Assist
 * ({@link ExoJetpack}).
 */
final class ExoKeys {
    private ExoKeys() {}

    static final String CATEGORY = "key.categories.robotica.exo";
    static final KeyMapping OPEN_MODULES = key("open_modules", GLFW.GLFW_KEY_J);
    static final KeyMapping TOGGLE_FLIGHT = key("toggle_flight", GLFW.GLFW_KEY_K);
    static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_R);
    static final KeyMapping SONAR = key("sonar", GLFW.GLFW_KEY_N);

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.robotica.exo." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MODULES);
        event.register(TOGGLE_FLIGHT);
        event.register(DASH);
        event.register(SONAR);
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new ExoActionPayload(action));
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        boolean worn = ExoSuit.wearingAny(player);
        boolean free = worn && mc.screen == null;
        while (OPEN_MODULES.consumeClick()) {
            if (free) send(ExoActions.OPEN_MODULES);
        }
        while (TOGGLE_FLIGHT.consumeClick()) {
            if (free) send(ExoActions.TOGGLE_FLIGHT);
        }
        while (SONAR.consumeClick()) {
            if (free) send(ExoActions.SONAR);
        }
        while (DASH.consumeClick()) {
            // No prediction: the server applies the burst and sends the motion, so a refused dash moves nothing.
            if (!free || !ExoSuit.isActive(player, ModuleKind.DASH_THRUSTERS)) continue;
            if (player.getCooldowns().isOnCooldown(ModuleItems.get(ModuleKind.DASH_THRUSTERS, 1).get())) ExoFx.denied();
            else if (!player.isPassenger() && !player.isFallFlying()) send(ExoActions.DASH);
        }

        ExoJetpack.tick(mc, player, worn);
    }
}
