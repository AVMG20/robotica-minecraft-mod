package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.weapon.NullLanceItem;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Null Lance charge around the crosshair, first person only: 4 corner brackets, one per barrel coil, light up clockwise
 * from the top left at 25/50/75/100 % with a short white flash each. Once ready they close in and pulse; the beam shot
 * throws them outward in a fading white flash ({@link #fired}, called for the own shot by {@link LanceFx#beam}).
 */
final class LanceHud {
    private LanceHud() {}

    private static final int COILS = 4;
    private static final float COIL_TICKS = NullLanceItem.CHARGE_TICKS / (float) COILS;
    /** Bracket corner distance from the crosshair centre while charging, once ready, and how far the shot throws them. */
    private static final int GAP = 9, GAP_READY = 7, GAP_FIRE = 6;
    /** Bracket arm length in GUI pixels. */
    private static final int ARM = 4;
    private static final float LIGHT_MS = 150, CLOSE_MS = 120, LOCK_MS = 220, FIRE_MS = 260;
    private static final int DIM = 0x8C3A2466, LIT = 0xFFB07CFF, WHITE = 0xFFF4ECFF, SHADOW = 0x000000;

    /** Wall time of the last own beam shot, 0 when its flash is over. */
    private static long firedAt;

    static void fired() {
        firedAt = Util.getMillis();
    }

    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Robotica.id("lance_charge"), LanceHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null || mc.options.getCameraType() != CameraType.FIRST_PERSON
                || mc.getCameraEntity() != player) return;
        // the vanilla crosshair is 15 px, drawn from ((w - 15) / 2, (h - 15) / 2)
        int cx = (g.guiWidth() - 15) / 2 + 7, cy = (g.guiHeight() - 15) / 2 + 7;
        long now = Util.getMillis();
        if (LanceFx.charging(player)) {
            float ticks = player.getTicksUsingItem() + delta.getGameTimeDeltaPartialTick(false);
            float fullMs = (ticks - NullLanceItem.CHARGE_TICKS) * 50.0F;
            if (fullMs >= 0.0F) {
                float close = ease(Mth.clamp(fullMs / CLOSE_MS, 0.0F, 1.0F));
                int gap = Math.round(Mth.lerp(close, GAP, GAP_READY));
                float pulse = 0.5F + 0.5F * Mth.sin(now / 140.0F);
                float lock = 1.0F - Mth.clamp(fullMs / LOCK_MS, 0.0F, 1.0F);
                int color = blend(LIT, WHITE, Math.max(0.15F + 0.35F * pulse, lock));
                for (int k = 0; k < COILS; k++) bracket(g, cx, cy, k, gap, color);
            } else {
                for (int k = 0; k < COILS; k++) {
                    float litMs = (ticks - COIL_TICKS * (k + 1)) * 50.0F;
                    int color = litMs < 0.0F ? DIM : blend(LIT, WHITE, 1.0F - Mth.clamp(litMs / LIGHT_MS, 0.0F, 1.0F));
                    bracket(g, cx, cy, k, GAP, color);
                }
            }
            return;
        }
        if (firedAt == 0) return;
        float t = (now - firedAt) / FIRE_MS;
        if (t < 0.0F || t >= 1.0F) {
            firedAt = 0;
            return;
        }
        int gap = GAP_READY + Math.round(GAP_FIRE * ease(t));
        int alpha = Math.round(255 * (1.0F - t) * (1.0F - t));
        for (int k = 0; k < COILS; k++) bracket(g, cx, cy, k, gap, alpha << 24 | (WHITE & 0xFFFFFF));
    }

    private static float ease(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    /**
     * Corner bracket {@code k} (0 top left, then clockwise) with its corner {@code gap} px out from the centre on both
     * axes and both arms pointing back along the edges, over a soft shadow.
     */
    private static void bracket(GuiGraphics g, int cx, int cy, int k, int gap, int color) {
        int sx = k == 0 || k == 3 ? -1 : 1, sy = k < 2 ? -1 : 1;
        int x = cx + sx * gap, y = cy + sy * gap;
        int hx0 = Math.min(x, x - sx * (ARM - 1)), hx1 = Math.max(x, x - sx * (ARM - 1)) + 1;
        int vy0 = Math.min(y, y - sy * (ARM - 1)), vy1 = Math.max(y, y - sy * (ARM - 1)) + 1;
        int shadow = ((color >>> 24) / 2) << 24 | SHADOW;
        g.fill(hx0 + 1, y + 1, hx1 + 1, y + 2, shadow);
        g.fill(x + 1, vy0 + 1, x + 2, vy1 + 1, shadow);
        g.fill(hx0, y, hx1, y + 1, color);
        g.fill(x, vy0, x + 1, vy1, color);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
