package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoItems;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoSuit;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;
import java.util.List;

/** Small HUD in the top left corner while wearing Exo pieces: battery bar, one icon per installed module, and Robot HUD readouts. */
final class ExoHud {
    private ExoHud() {}

    private static final int X = 6;
    private static final int Y = 6;
    private static final int BAR_W = 72;

    private static List<DroneBase> drones = List.of();
    private static int lastScan = -100;

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Robotica.id("exo_hud"), ExoHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !ExoSuit.wearingAny(player)) return;

        int stored = ExoSuit.totalEnergy(player);
        int capacity = ExoSuit.totalCapacity(player);
        boolean low = capacity > 0 && stored * 100L < capacity * 15L;
        int filled = capacity <= 0 ? 0 : (int) ((long) BAR_W * stored / capacity);
        g.fill(X - 1, Y - 1, X + BAR_W + 1, Y + 6, 0xAA000000);
        g.fill(X, Y, X + BAR_W, Y + 5, 0xFF1C2A2E);
        g.fill(X, Y, X + filled, Y + 5, low ? 0xFFC9302A : 0xFF5CC8D8);
        g.fill(X, Y, X + filled, Y + 1, low ? 0xFFFF6B5E : 0xFF9BF0F8);
        g.pose().pushPose();
        g.pose().translate(X + BAR_W + 4, Y - 0.5F, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(mc.font, Fmt.compact(stored) + " FE", 0, 0, low ? 0xFFFF6B5E : 0xFFD8FBFF, true);
        g.pose().popPose();

        int ix = X;
        int iy = Y + 9;
        boolean robotHud = false;
        for (EquipmentSlot slot : ExoSuit.SLOTS) {
            ItemStack piece = ExoSuit.piece(player, slot);
            if (piece.isEmpty()) continue;
            for (int i = 0; i < ExoData.slotCount(piece); i++) {
                ExoModuleKind kind = ExoData.kind(piece, i);
                if (kind == null) continue;
                boolean on = ExoData.isEnabled(piece, i);
                if (kind == ExoModuleKind.ROBOT_HUD && on && stored > 0) robotHud = true;
                g.pose().pushPose();
                g.pose().translate(ix, iy, 0);
                g.pose().scale(0.75F, 0.75F, 1.0F);
                g.renderItem(new ItemStack(ExoItems.module(kind).get()), 0, 0);
                if (!on || stored <= 0) {
                    g.pose().translate(0, 0, 200);
                    g.fill(0, 0, 16, 16, 0xA0101010);
                }
                g.pose().popPose();
                ix += 13;
            }
        }
        if (robotHud) renderDrones(g, mc, X, iy + 15);
    }

    private static void renderDrones(GuiGraphics g, Minecraft mc, int x, int y) {
        var player = mc.player;
        if (mc.level == null) return;
        if (player.tickCount - lastScan >= 10 || player.tickCount < lastScan) {
            lastScan = player.tickCount;
            drones = new ArrayList<>(mc.level.getEntitiesOfClass(DroneBase.class, player.getBoundingBox().inflate(24.0)));
            drones.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        }
        int shown = 0;
        for (DroneBase drone : drones) {
            if (!drone.isAlive()) continue;
            if (shown++ >= 4) break;
            int color = drone.isActive() ? 0xFF3DBE5A : 0xFFE8A020;
            g.fill(x, y + 1, x + 4, y + 5, color);
            int pct = (int) Math.round(100.0 * drone.getHealth() / Math.max(1.0F, drone.getMaxHealth()));
            Component line = Component.translatable("exo.robotica.hud.drone", drone.getDisplayName(), (int) Math.sqrt(drone.distanceToSqr(player)), pct);
            g.pose().pushPose();
            g.pose().translate(x + 7, y, 0);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawString(mc.font, line, 0, 0, 0xFFD8FBFF, true);
            g.pose().popPose();
            y += 9;
        }
    }
}
