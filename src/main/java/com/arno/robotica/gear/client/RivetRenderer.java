package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.entity.RivetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the rivet as a small 3D bolt along its flight direction: a steel shaft with a wide head at the back and a hot
 * glowing tip, always full bright so it reads as a tracer. Texture: textures/entity/rivet.png (16x16, from
 * scripts/textures/gear.py). The top half of the texture is the shaft and tip, the bottom half the head.
 */
public class RivetRenderer extends EntityRenderer<RivetEntity> {
    private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/rivet.png");

    public RivetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RivetEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // Like vanilla thrown items: skip the first ticks right next to the camera, so the bolt never fills the screen.
        if (entity.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        // Spin around the flight axis while flying.
        if (!entity.isStuck()) pose.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTick) * 40.0F));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int full = LightTexture.FULL_BRIGHT;
        // shaft + tip: x -0.16 .. 0.18, 0.09 thick
        box(pose, vc, -0.16F, 0.18F, 0.045F, 0.0F, 0.0F, 1.0F, 0.5F, full);
        // head: x -0.22 .. -0.15, 0.17 wide
        box(pose, vc, -0.22F, -0.15F, 0.085F, 0.0F, 0.5F, 0.5F, 1.0F, full);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** A square-section cuboid from x0 to x1 with half width h; every face maps the UV rect (u0,v0)-(u1,v1). */
    private static void box(PoseStack pose, VertexConsumer vc, float x0, float x1, float h, float u0, float v0, float u1, float v1, int light) {
        PoseStack.Pose p = pose.last();
        // four long sides
        quad(p, vc, x0, -h, -h, x1, -h, -h, x1, h, -h, x0, h, -h, u0, v0, u1, v1, 0, 0, -1, light);
        quad(p, vc, x0, h, h, x1, h, h, x1, -h, h, x0, -h, h, u0, v0, u1, v1, 0, 0, 1, light);
        quad(p, vc, x0, h, -h, x1, h, -h, x1, h, h, x0, h, h, u0, v0, u1, v1, 0, 1, 0, light);
        quad(p, vc, x0, -h, h, x1, -h, h, x1, -h, -h, x0, -h, -h, u0, v0, u1, v1, 0, -1, 0, light);
        // end caps (use the left / right end of the UV rect)
        float uc = u0 + (u1 - u0) * 0.15F;
        quad(p, vc, x0, -h, h, x0, -h, -h, x0, h, -h, x0, h, h, u0, v0, uc, v1, -1, 0, 0, light);
        float ut = u1 - (u1 - u0) * 0.15F;
        quad(p, vc, x1, -h, -h, x1, -h, h, x1, h, h, x1, h, -h, ut, v0, u1, v1, 1, 0, 0, light);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light) {
        vertex(p, vc, ax, ay, az, u0, v1, nx, ny, nz, light);
        vertex(p, vc, bx, by, bz, u1, v1, nx, ny, nz, light);
        vertex(p, vc, cx, cy, cz, u1, v0, nx, ny, nz, light);
        vertex(p, vc, dx, dy, dz, u0, v0, nx, ny, nz, light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light) {
        vc.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(RivetEntity entity) {
        return TEXTURE;
    }
}
