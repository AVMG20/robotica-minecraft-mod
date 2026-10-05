package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoActions;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoModuleKind;
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

/** Keybinds: J opens the module screen of the worn suit, K toggles Flight. A second jump press in mid air is the Jet Assist double jump. */
final class ExoKeys {
    private ExoKeys() {}

    static final String CATEGORY = "key.categories.robotica.exo";
    static final KeyMapping OPEN_MODULES = new KeyMapping("key.robotica.exo.open_modules", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    static final KeyMapping TOGGLE_FLIGHT = new KeyMapping("key.robotica.exo.toggle_flight", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private static boolean jumpWasDown;
    private static int airJumps;

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MODULES);
        event.register(TOGGLE_FLIGHT);
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
        while (OPEN_MODULES.consumeClick()) {
            if (worn && mc.screen == null) send(ExoActions.OPEN_MODULES);
        }
        while (TOGGLE_FLIGHT.consumeClick()) {
            if (worn && mc.screen == null) send(ExoActions.TOGGLE_FLIGHT);
        }

        boolean jumpDown = mc.screen == null && mc.options.keyJump.isDown();
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying
                || player.isFallFlying() || player.isPassenger();
        if (grounded) airJumps = 0;
        if (jumpDown && !jumpWasDown && !grounded && worn && airJumps < ExoConfig.airJumps()
                && ExoSuit.isActive(player, ExoModuleKind.JET_ASSIST)) {
            airJumps++;
            send(ExoActions.DOUBLE_JUMP);
            // Predict the boost; the server sends the same motion.
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, 0.55, v.z);
        }
        jumpWasDown = jumpDown;
    }
}
