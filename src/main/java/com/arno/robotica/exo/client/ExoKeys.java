package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoActions;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.net.ExoActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds: J opens the module screen of the worn suit, K toggles Flight, R dashes (Dash Thrusters), N pings (Sonar
 * Pulse), O overclocks (Servo Core set bonus). None of them clash with vanilla defaults or Robotica's V, B, G (tools)
 * and H (drones). A second jump press in mid air is the Jet Assist double jump.
 */
final class ExoKeys {
    private ExoKeys() {}

    static final String CATEGORY = "key.categories.robotica.exo";
    static final KeyMapping OPEN_MODULES = key("open_modules", GLFW.GLFW_KEY_J);
    static final KeyMapping TOGGLE_FLIGHT = key("toggle_flight", GLFW.GLFW_KEY_K);
    static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_R);
    static final KeyMapping SONAR = key("sonar", GLFW.GLFW_KEY_N);
    static final KeyMapping OVERCLOCK = key("overclock", GLFW.GLFW_KEY_O);

    private static boolean jumpWasDown;
    private static int airJumps;
    private static int lastAirJump = -100;

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.robotica.exo." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MODULES);
        event.register(TOGGLE_FLIGHT);
        event.register(DASH);
        event.register(SONAR);
        event.register(OVERCLOCK);
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new ExoActionPayload(action));
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            jumpWasDown = false;
            return;
        }
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
        while (OVERCLOCK.consumeClick()) {
            if (free) send(ExoActions.OVERCLOCK);
        }
        while (DASH.consumeClick()) {
            // No prediction: the server applies the burst and sends the motion, so a refused dash moves nothing.
            if (free && ExoSuit.isActive(player, ModuleKind.DASH_THRUSTERS)
                    && !player.getCooldowns().isOnCooldown(ModuleItems.get(ModuleKind.DASH_THRUSTERS, 1).get())
                    && !player.isPassenger() && !player.isFallFlying()) {
                send(ExoActions.DASH);
            }
        }

        boolean jumpDown = mc.screen == null && mc.options.keyJump.isDown();
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying
                || player.isFallFlying() || player.isPassenger();
        if (grounded) airJumps = 0;
        if (player.tickCount < lastAirJump) lastAirJump = -100; // a new player object (respawn, relog)
        if (jumpDown && !jumpWasDown && !grounded && worn) {
            // Predicted only under the server's own rules: count, spacing and the energy the jump costs.
            ExoSuit.Active active = ExoSuit.active(player);
            int jet = active.level(ModuleKind.JET_ASSIST);
            if (jet > 0 && airJumps < ExoConfig.airJumps(jet) && player.tickCount - lastAirJump >= ExoSuit.AIR_JUMP_GAP
                    && ExoSuit.canPayAirJump(player, active)) {
                airJumps++;
                lastAirJump = player.tickCount;
                send(ExoActions.DOUBLE_JUMP);
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, 0.55, v.z);
            }
        }
        jumpWasDown = jumpDown;
    }
}
