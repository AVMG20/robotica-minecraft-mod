package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.weapon.NullLanceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Null Lance beam charge on the client. In first person the lance draws back over the whole charge (vanilla's spear
 * pose is drawn after half of it), snaps back a little when it is full and then hums in the hand. A thin meter under
 * the crosshair fills while charging and pulses bright with brackets once the beam is ready. Left-click bolts and
 * their recoil are in RivetGunClient.
 */
final class LanceClient {
    private LanceClient() {}

    private static final long SNAP_MS = 160;
    private static final long FLASH_MS = 200;
    private static final int METER_W = 25;
    private static final int EMPTY = 0xFF2A1A40;
    private static final int FILL = 0xFF8A5CD8;
    private static final int READY = 0xFFB07CFF;
    private static final int READY_BRIGHT = 0xFFF1E6FF;

    /** Wall time the current charge got full, 0 while not full. */
    private static long fullSince;

    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equipProcess, float swingProcess) {
            InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                chargePose(pose, player, arm, partialTick, equipProcess);
            } else {
                RivetGunClient.heldPose(pose, player, arm, equipProcess);
            }
            return true;
        }
    };

    static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(EXTENSIONS, GearItems.NULL_LANCE.get());
    }

    static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Robotica.id("lance_charge"), LanceClient::renderMeter);
    }

    /** Charge of the lance being used, 0..1, or -1 when the player is not charging one. */
    private static float charge(LocalPlayer player, float partialTick) {
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof NullLanceItem)) return -1.0F;
        return Mth.clamp((player.getTicksUsingItem() + partialTick) / NullLanceItem.CHARGE_TICKS, 0.0F, 1.0F);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean full = player != null && charge(player, 0.0F) >= 1.0F;
        if (!full) fullSince = 0;
        else if (fullSince == 0) fullSince = Util.getMillis();
    }

    /** 1 right when the charge got full, easing out to 0 over {@code ms}. */
    private static float sinceFull(long ms) {
        if (fullSince == 0) return 0.0F;
        float t = (Util.getMillis() - fullSince) / (float) ms;
        return t >= 1.0F ? 0.0F : (1.0F - t) * (1.0F - t);
    }

    /** Vanilla's spear pose, drawn back over the whole charge; a snap and a hum once it is full. */
    private static void chargePose(PoseStack pose, LocalPlayer player, HumanoidArm arm, float partialTick, float equipProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
        pose.translate(side * -0.5F, 0.7F, 0.1F);
        pose.mulPose(Axis.XP.rotationDegrees(-55.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * 35.3F));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -9.785F));
        float used = player.getTicksUsingItem() + partialTick - 1.0F;
        float p = Mth.clamp(used / NullLanceItem.CHARGE_TICKS, 0.0F, 1.0F);
        if (p >= 1.0F) {
            pose.translate(Mth.sin(used * 2.7F) * 0.007F, Mth.sin(used * 3.9F + 1.0F) * 0.007F, 0.06F * sinceFull(SNAP_MS));
        } else if (p > 0.1F) {
            pose.translate(0.0F, Mth.sin((used - 0.1F) * 1.3F) * (p - 0.1F) * 0.004F, 0.0F);
        }
        pose.translate(0.0F, 0.0F, p * 0.2F);
        pose.scale(1.0F, 1.0F, 1.0F + p * 0.2F);
        pose.mulPose(Axis.YN.rotationDegrees(side * 45.0F));
    }

    /** The charge meter under the crosshair, first person only. */
    private static void renderMeter(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null || !mc.options.getCameraType().isFirstPerson()) return;
        float p = charge(player, delta.getGameTimeDeltaPartialTick(false));
        if (p < 0.0F) return;
        int x = g.guiWidth() / 2 - METER_W / 2;
        int y = g.guiHeight() / 2 + 9;
        g.fill(x - 1, y - 1, x + METER_W + 1, y + 4, 0xAA000000);
        g.fill(x, y, x + METER_W, y + 3, EMPTY);
        if (p < 1.0F) {
            g.fill(x, y, x + Math.round(METER_W * p), y + 3, FILL);
            return;
        }
        float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 110.0F);
        int color = blend(READY, READY_BRIGHT, Math.max(pulse * 0.5F, sinceFull(FLASH_MS)));
        g.fill(x, y, x + METER_W, y + 3, color);
        g.fill(x - 4, y - 2, x - 2, y + 5, color);
        g.fill(x + METER_W + 2, y - 2, x + METER_W + 4, y + 5, color);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
