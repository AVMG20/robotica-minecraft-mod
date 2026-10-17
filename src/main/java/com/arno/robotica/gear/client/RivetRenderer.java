package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.entity.RivetEntity;
import com.arno.robotica.gear.weapon.NullLanceItem;
import com.arno.robotica.gear.weapon.RivetGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Draws the rivet as a small 3D bolt along its flight direction: a steel shaft with a wide head at the back and a hot
 * glowing tip, always full bright so it reads as a tracer. Texture: textures/entity/rivet.png (16x16, from
 * scripts/textures/gear.py). The top half of the texture is the shaft and tip, the bottom half the head. A Null Lance
 * bolt is the same shape tinted violet. The server fires a shot from the shooter's eyes along the crosshair; each client
 * draws a fresh one from the gun's muzzle instead, closing onto that line over its first ticks
 * ({@link RivetEntity#setMuzzleOffset}).
 */
public class RivetRenderer extends EntityRenderer<RivetEntity> {
    private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/rivet.png");
    private static final int NULL_TINT = 0xFFC8A0FF;
    /**
     * Rivet Gun muzzle in the own first-person view (camera space: along the look, to the gun side, down; the hold of
     * RivetGunClient with the rivet_gun model's firstperson display) and held by others (along the look, to the gun arm,
     * down from the eyes, as RivetGunItem's muzzle flash).
     */
    private static final double OWN_FORWARD = 1.05, OWN_SIDE = 0.60, OWN_DOWN = 0.24;
    private static final double HELD_FORWARD = 0.3, HELD_SIDE = 0.35, HELD_DOWN = 0.65;
    /** A shot appears this close to the shooter's eyes; a rivet seen further out is an older one coming into view. */
    private static final double FRESH = 2.0;
    /** Scratch muzzle (client thread only). */
    private static final double[] MUZ = new double[6];

    public RivetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RivetEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // Like vanilla thrown items: skip the first ticks right next to the camera, so the bolt never fills the screen.
        // A fresh shot drawn from the muzzle never comes that close.
        if (!entity.fromMuzzle() && entity.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) return;
        pose.pushPose();
        float share = entity.muzzleShare(Math.max(0.0F, entity.tickCount - 1 + partialTick));
        if (share > 0.0F) pose.translate(entity.muzzleX() * share, entity.muzzleY() * share, entity.muzzleZ() * share);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        // Spin around the flight axis while flying.
        if (!entity.isStuck()) pose.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTick) * 40.0F));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int full = LightTexture.FULL_BRIGHT;
        int color = entity.isNullBolt() ? NULL_TINT : -1;
        // shaft + tip: x -0.16 .. 0.18, 0.09 thick
        box(pose, vc, -0.16F, 0.18F, 0.045F, 0.0F, 0.0F, 1.0F, 0.5F, full, color);
        // head: x -0.22 .. -0.15, 0.17 wide
        box(pose, vc, -0.22F, -0.15F, 0.085F, 0.0F, 0.5F, 0.5F, 1.0F, full, color);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** A fresh shot came in: draw it from the muzzle of the gun its shooter holds. */
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof RivetEntity rivet) || !(rivet.getOwner() instanceof Player owner)) return;
        if (rivet.distanceToSqr(owner.getX(), owner.getEyeY(), owner.getZ()) > FRESH * FRESH) return;
        Minecraft mc = Minecraft.getInstance();
        boolean own = owner == mc.getCameraEntity() && mc.options.getCameraType() == CameraType.FIRST_PERSON;
        Item gun = owner.getMainHandItem().getItem();
        if (gun instanceof NullLanceItem) LanceFx.muzzle(owner, own, MUZ);
        else if (gun instanceof RivetGunItem) rivetMuzzle(owner, own);
        else return;
        rivet.setMuzzleOffset(MUZ[0] - rivet.getX(), MUZ[1] - rivet.getY(), MUZ[2] - rivet.getZ());
    }

    /** Muzzle of the Rivet Gun held by {@code p} into {@link #MUZ}. */
    private static void rivetMuzzle(Player p, boolean own) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD, pitch = p.getXRot() * Mth.DEG_TO_RAD;
        double cp = Mth.cos(pitch), sp = Mth.sin(pitch), cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        double lx = -sy * cp, ly = -sp, lz = cy * cp;
        int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        double rx = -cy * side, rz = -sy * side;
        double x = p.getX(), y = p.getEyeY(), z = p.getZ();
        if (own) {
            // up of the view: right x look
            double ux = sy * ly, uy = -sy * lx + cy * lz, uz = -cy * ly;
            MUZ[0] = x + lx * OWN_FORWARD + rx * OWN_SIDE - ux * OWN_DOWN;
            MUZ[1] = y + ly * OWN_FORWARD - uy * OWN_DOWN;
            MUZ[2] = z + lz * OWN_FORWARD + rz * OWN_SIDE - uz * OWN_DOWN;
        } else {
            MUZ[0] = x + lx * HELD_FORWARD + rx * HELD_SIDE;
            MUZ[1] = y + ly * HELD_FORWARD - HELD_DOWN;
            MUZ[2] = z + lz * HELD_FORWARD + rz * HELD_SIDE;
        }
    }

    /** A square-section cuboid from x0 to x1 with half width h; every face maps the UV rect (u0,v0)-(u1,v1). */
    private static void box(PoseStack pose, VertexConsumer vc, float x0, float x1, float h, float u0, float v0, float u1, float v1, int light, int color) {
        PoseStack.Pose p = pose.last();
        // four long sides
        quad(p, vc, x0, -h, -h, x1, -h, -h, x1, h, -h, x0, h, -h, u0, v0, u1, v1, 0, 0, -1, light, color);
        quad(p, vc, x0, h, h, x1, h, h, x1, -h, h, x0, -h, h, u0, v0, u1, v1, 0, 0, 1, light, color);
        quad(p, vc, x0, h, -h, x1, h, -h, x1, h, h, x0, h, h, u0, v0, u1, v1, 0, 1, 0, light, color);
        quad(p, vc, x0, -h, h, x1, -h, h, x1, -h, -h, x0, -h, -h, u0, v0, u1, v1, 0, -1, 0, light, color);
        // end caps (use the left / right end of the UV rect)
        float uc = u0 + (u1 - u0) * 0.15F;
        quad(p, vc, x0, -h, h, x0, -h, -h, x0, h, -h, x0, h, h, u0, v0, uc, v1, -1, 0, 0, light, color);
        float ut = u1 - (u1 - u0) * 0.15F;
        quad(p, vc, x1, -h, -h, x1, -h, h, x1, h, h, x1, h, -h, ut, v0, u1, v1, 1, 0, 0, light, color);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light, int color) {
        vertex(p, vc, ax, ay, az, u0, v1, nx, ny, nz, light, color);
        vertex(p, vc, bx, by, bz, u1, v1, nx, ny, nz, light, color);
        vertex(p, vc, cx, cy, cz, u1, v0, nx, ny, nz, light, color);
        vertex(p, vc, dx, dy, dz, u0, v0, nx, ny, nz, light, color);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light, int color) {
        vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(RivetEntity entity) {
        return TEXTURE;
    }
}
