package com.arno.robotica.gear.client;

import com.arno.robotica.gear.GearActionPayload;
import com.arno.robotica.gear.tool.GearActions;
import com.arno.robotica.gear.tool.GearToolItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Keybinds: V cycles the mode, B swaps silk/fortune, G opens the toggle screen. Sneak + scroll also cycles the mode. */
final class GearKeys {
    private GearKeys() {}

    static final String CATEGORY = "key.categories.robotica.gear";
    static final KeyMapping CYCLE_MODE = new KeyMapping("key.robotica.gear.cycle_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    static final KeyMapping SWAP_ENCHANT = new KeyMapping("key.robotica.gear.swap_enchant", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    static final KeyMapping OPEN_TOGGLES = new KeyMapping("key.robotica.gear.open_toggles", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    static void register(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_MODE);
        event.register(SWAP_ENCHANT);
        event.register(OPEN_TOGGLES);
    }

    static GearToolItem heldTool(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof GearToolItem tool ? tool : null;
    }

    static void send(int action, int arg) {
        PacketDistributor.sendToServer(new GearActionPayload(action, arg));
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        GearToolItem tool = heldTool(player);
        while (CYCLE_MODE.consumeClick()) {
            if (tool != null && tool.spec.modes.size() > 1) send(GearActions.CYCLE_MODE, player.isShiftKeyDown() ? -1 : 1);
        }
        while (SWAP_ENCHANT.consumeClick()) {
            if (tool != null && tool.spec.fortuneLevel > 0) send(GearActions.CYCLE_ENCHANT, 0);
        }
        while (OPEN_TOGGLES.consumeClick()) {
            if (tool != null && mc.screen == null) mc.setScreen(new ToggleScreen());
        }
    }

    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || !player.isShiftKeyDown()) return;
        GearToolItem tool = heldTool(player);
        if (tool == null || tool.spec.modes.size() <= 1 || event.getScrollDeltaY() == 0) return;
        send(GearActions.CYCLE_MODE, event.getScrollDeltaY() > 0 ? -1 : 1);
        event.setCanceled(true);
    }
}
