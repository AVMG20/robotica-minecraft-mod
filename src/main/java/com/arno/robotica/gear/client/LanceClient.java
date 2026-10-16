package com.arno.robotica.gear.client;

import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.weapon.NullLanceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Null Lance rifle in the own first-person view. At rest it sits at the hip like the Rivet Gun; holding right-click
 * raises it to the shoulder over a few ticks (more centred, higher, barrel level toward the crosshair), it trembles a
 * little more as each coil lights, settles with a small lock when full and then hums in the hands. A beam shot kicks it
 * hard (back and muzzle up) before it is lowered again; each bolt kicks it a little. No vanilla swing or use pose is
 * ever drawn. The third-person hold is {@link LanceArms}, the charge display LanceHud, the effects LanceFx.
 */
final class LanceClient {
    private LanceClient() {}

    // Hand positions (camera space, right hand; x mirrors for the left). The hip one is RivetGunClient.heldPose.
    private static final float HIP_X = 0.56F, HIP_Y = -0.52F, HIP_Z = -0.72F;
    private static final float AIM_X = 0.50F, AIM_Y = -0.46F, AIM_Z = -0.62F;
    /** Turn of the aimed rifle in toward the crosshair and its muzzle up (degrees, about the hand near the butt). */
    private static final float AIM_YAW = 5.0F, AIM_PITCH = 2.0F;
    /** Raise and lower speed of the aim, per tick. */
    private static final float RAISE = 1.0F / 4.5F, LOWER = 1.0F / 7.0F;
    /** Ticks the aim stays up after a full charge is let go (until the beam is out), and after the beam. */
    private static final int HOLD_RELEASE = 7, HOLD_SHOT = 5;

    // Recoil: beam (heavy) and bolt (snappy), wall time in ms.
    private static final long BEAM_MS = 460, BOLT_MS = 190;
    private static final float BEAM_BACK = 0.17F, BEAM_UP = 0.03F, BEAM_PITCH = 13.0F, BEAM_ROLL = 3.0F;
    private static final float BOLT_BACK = 0.09F, BOLT_UP = 0.015F, BOLT_PITCH = 6.5F;
    /** Lock-in settle at full charge. */
    private static final long LOCK_MS = 220;

    /** Aim of the own lance, 0 at the hip to 1 shouldered, this tick and last tick. */
    private static float aim, aimO;
    private static int hold;
    private static boolean wasFull;
    /** Wall time the current charge got full (0 while not full), and of the own last beam. */
    private static long fullSince, beamAt;

    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equipProcess, float swingProcess) {
            handPose(pose, player, arm, partialTick, equipProcess);
            return true;
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return LanceArms.armPose(entity, hand);
        }
    };

    static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(EXTENSIONS, GearItems.NULL_LANCE.get());
    }

    /** Charge of the own lance being used from the main hand, 0..1, or -1 when not charging one. */
    private static float charge(LocalPlayer player, float partialTick) {
        if (!charging(player)) return -1.0F;
        return Mth.clamp((player.getTicksUsingItem() + partialTick) / NullLanceItem.CHARGE_TICKS, 0.0F, 1.0F);
    }

    private static boolean charging(LocalPlayer player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().getItem() instanceof NullLanceItem;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof NullLanceItem)) {
            aim = aimO = 0.0F;
            hold = 0;
            wasFull = false;
            fullSince = 0;
            if (player == null) LanceArms.clear();
            return;
        }
        if (mc.isPaused()) return;
        aimO = aim;
        boolean charging = charging(player);
        boolean full = charging && charge(player, 0.0F) >= 1.0F;
        if (wasFull && !charging) hold = HOLD_RELEASE;
        wasFull = full;
        if (!full) fullSince = 0;
        else if (fullSince == 0) fullSince = Util.getMillis();
        boolean up = charging || hold > 0;
        if (hold > 0) hold--;
        aim = up ? Math.min(1.0F, aim + RAISE) : Math.max(0.0F, aim - LOWER);
    }

    /** The own beam left the lance (LanceFx, from the server's shot): recoil, and keep it shouldered a moment. */
    static void beamFired() {
        beamAt = Util.getMillis();
        hold = Math.max(hold, HOLD_SHOT);
    }

    /** Own aim, 0 at the hip to 1 shouldered, eased; also used for the third-person pose and the muzzle effects. */
    static float aim(float partialTick) {
        float t = Mth.lerp(partialTick, aimO, aim);
        return t * t * (3.0F - 2.0F * t);
    }

    /** A recoil curve over t = 0..1: up to 1 in the first tenth, then eased back to 0. */
    static float kickCurve(float t) {
        if (t < 0.0F || t >= 1.0F) return 0.0F;
        if (t < 0.1F) {
            float r = t / 0.1F;
            return r * (2.0F - r);
        }
        float r = 1.0F - (t - 0.1F) / 0.9F;
        return r * r * r;
    }

    private static float since(long at, long ms) {
        return at == 0 ? 1.0F : (Util.getMillis() - at) / (float) ms;
    }

    private static void handPose(PoseStack pose, LocalPlayer player, HumanoidArm arm, float partialTick, float equipProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (arm != player.getMainArm()) {
            // the off hand only carries it
            pose.translate(side * HIP_X, HIP_Y + equipProcess * -0.6F, HIP_Z);
            return;
        }
        float a = aim(partialTick);
        // starting to charge resets the vanilla equip height (itemUsed); never dip the rifle for that
        float equip = player.isUsingItem() ? 0.0F : equipProcess * (1.0F - a);
        pose.translate(side * Mth.lerp(a, HIP_X, AIM_X), Mth.lerp(a, HIP_Y, AIM_Y) - 0.6F * equip, Mth.lerp(a, HIP_Z, AIM_Z));
        if (a > 0.0F) {
            pose.mulPose(Axis.YP.rotationDegrees(side * AIM_YAW * a));
            pose.mulPose(Axis.XP.rotationDegrees(AIM_PITCH * a));
        }

        float c = charge(player, partialTick);
        if (c >= 0.0F) {
            float time = player.tickCount + partialTick;
            if (c < 1.0F) {
                // the build-up: a tremble that grows as the coils light
                float amp = 0.004F * c * c;
                pose.translate(Mth.sin(time * 2.9F) * amp, Mth.sin(time * 3.7F + 1.0F) * amp, 0.0F);
            } else {
                // locked in: a small settle back, then a faint hum in the hands
                float lock = 1.0F - Mth.clamp(since(fullSince, LOCK_MS), 0.0F, 1.0F);
                lock *= lock;
                pose.translate(Mth.sin(time * 9.3F) * 0.0012F, Mth.sin(time * 11.7F + 2.0F) * 0.0012F - 0.012F * lock, 0.04F * lock);
                pose.mulPose(Axis.XP.rotationDegrees(-1.5F * lock));
            }
        }

        float beam = kickCurve(since(beamAt, BEAM_MS));
        if (beam > 0.0F) {
            pose.translate(0.0F, BEAM_UP * beam, BEAM_BACK * beam);
            pose.mulPose(Axis.XP.rotationDegrees(BEAM_PITCH * beam));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -BEAM_ROLL * beam));
        }
        float bolt = kickCurve(since(RivetGunClient.lastShot(), BOLT_MS));
        if (bolt > 0.0F) {
            pose.translate(0.0F, BOLT_UP * bolt, BOLT_BACK * bolt);
            pose.mulPose(Axis.XP.rotationDegrees(BOLT_PITCH * bolt));
        }
    }
}
