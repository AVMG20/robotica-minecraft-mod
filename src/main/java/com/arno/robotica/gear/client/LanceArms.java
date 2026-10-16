package com.arno.robotica.gear.client;

import com.arno.robotica.gear.weapon.NullLanceItem;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * The two-handed Null Lance rifle hold in third person, for every player holding it in the main hand: carried low and
 * across the body (barrel forward and down) at rest, raised to the shoulder and aimed along the head's look while the
 * beam charges, with an arm kick on each shot. The pose is the extra {@link HumanoidModel.ArmPose} ROBOTICA_LANCE_RIFLE
 * (META-INF/enumextensions.json); HumanoidModel only exists on the client, so a dedicated server never loads it.
 * The rifle's place in the hand is the thirdperson display of scripts/data/gear_lance_model.py.
 */
public final class LanceArms {
    private LanceArms() {}

    public static final EnumProxy<HumanoidModel.ArmPose> RIFLE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            true, (IArmPoseTransformer) LanceArms::pose);

    private static final float HALF_PI = Mth.HALF_PI;
    // At rest: the gun arm forward and in, the support arm across to the barrel (radians; mirrored for left-handers).
    private static final float READY_MAIN_X = -1.05F, READY_MAIN_Y = 0.42F;
    private static final float READY_SUPPORT_X = -1.2F, READY_SUPPORT_Y = 0.9F, READY_SUPPORT_Z = -1.5F;
    // Aimed: both arms along the look, the gun arm a little below it (the display tilts the rifle back up to level).
    private static final float AIM_MAIN_DROP = 0.17F, AIM_MAIN_Y = 0.22F;
    private static final float AIM_SUPPORT_DROP = 0.08F, AIM_SUPPORT_Y = 0.78F, AIM_SUPPORT_Z = -3.0F;
    /** Arm kick of a beam and of a bolt (radians, up), and how long they last. */
    private static final float BEAM_KICK = 0.42F, BOLT_KICK = 0.14F;
    private static final long BEAM_MS = 420, BOLT_MS = 200;
    /** Other players: ticks to raise the rifle, and how long it stays up after a beam before it is lowered. */
    private static final float RAISE_TICKS = 4.5F;
    private static final long HOLD_MS = 350, LOWER_MS = 400;

    /** Entity id to the wall time of its last beam (positive) or bolt (negative); client thread only. */
    private static final Int2LongOpenHashMap SHOTS = new Int2LongOpenHashMap();

    /** The pose for the lance in this hand: the rifle hold in the main hand, a plain held item in the off hand. */
    static HumanoidModel.ArmPose armPose(LivingEntity entity, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? RIFLE.getValue() : null;
    }

    /** A shot from this entity's lance (LanceFx gets the server's beam and bolt effects). */
    static void shot(int entityId, boolean beam) {
        if (SHOTS.size() > 64) SHOTS.clear();
        long now = Util.getMillis();
        SHOTS.put(entityId, beam ? now : -now);
    }

    static void clear() {
        SHOTS.clear();
    }

    private static void pose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart support = right ? model.leftArm : model.rightArm;
        float s = right ? 1.0F : -1.0F;
        float t = aim(entity);
        float pitch = model.head.xRot, yaw = model.head.yRot;
        float kick = kick(entity.getId());
        main.xRot = Mth.lerp(t, READY_MAIN_X, -HALF_PI + pitch + AIM_MAIN_DROP) - kick;
        main.yRot = Mth.lerp(t, -s * READY_MAIN_Y, -s * AIM_MAIN_Y + yaw);
        main.zRot = 0.0F;
        support.xRot = Mth.lerp(t, READY_SUPPORT_X, -HALF_PI + pitch + AIM_SUPPORT_DROP) - kick * 0.8F;
        support.yRot = Mth.lerp(t, s * READY_SUPPORT_Y, s * AIM_SUPPORT_Y + yaw);
        support.zRot = 0.0F;
        support.z = Mth.lerp(t, READY_SUPPORT_Z, AIM_SUPPORT_Z);
    }

    /** 0 at rest, 1 aimed (eased); also places LanceFx's muzzle effects. */
    static float aim(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        if (entity == mc.player) return LanceClient.aim(partial);
        float t;
        if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof NullLanceItem) {
            t = Mth.clamp((entity.getTicksUsingItem() + partial) / RAISE_TICKS, 0.0F, 1.0F);
        } else {
            long at = SHOTS.getOrDefault(entity.getId(), 0L);
            if (at <= 0) return 0.0F;
            long since = Util.getMillis() - at;
            t = since < HOLD_MS ? 1.0F : 1.0F - Mth.clamp((since - HOLD_MS) / (float) LOWER_MS, 0.0F, 1.0F);
        }
        return t * t * (3.0F - 2.0F * t);
    }

    /** Arm kick of this entity's last shot: up fast, back down slower. */
    private static float kick(int entityId) {
        long at = SHOTS.getOrDefault(entityId, 0L);
        if (at == 0) return 0.0F;
        boolean beam = at > 0;
        float t = (Util.getMillis() - Math.abs(at)) / (float) (beam ? BEAM_MS : BOLT_MS);
        return (beam ? BEAM_KICK : BOLT_KICK) * LanceClient.kickCurve(t);
    }
}
