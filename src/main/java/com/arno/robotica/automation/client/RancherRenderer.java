package com.arno.robotica.automation.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.rancher.Rancher;
import com.arno.robotica.automation.rancher.RancherItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Rancher renderer: tier texture, glowing eyes, the held tool, and the work area outline while you hold a Rancher or look at it. */
public class RancherRenderer extends MobRenderer<Rancher, RancherModel> {
    private static final ResourceLocation MK1 = Robotica.id("textures/entity/rancher.png");
    private static final ResourceLocation MK2 = Robotica.id("textures/entity/rancher_mk2.png");
    private static final ResourceLocation GLOW = Robotica.id("textures/entity/rancher_glow.png");

    public RancherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RancherModel(ctx.bakeLayer(RancherModel.LAYER)), 0.35F);
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        addLayer(new EyesLayer<Rancher, RancherModel>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Rancher entity) {
        return entity.tier() >= 2 ? MK2 : MK1;
    }

    private static boolean outlineVisible(Rancher entity) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (player.getMainHandItem().getItem() instanceof RancherItem || player.getOffhandItem().getItem() instanceof RancherItem) {
            return player.distanceToSqr(entity) < 64.0 * 64.0;
        }
        return mc.crosshairPickEntity == entity;
    }

    @Override
    public boolean shouldRender(Rancher entity, Frustum frustum, double camX, double camY, double camZ) {
        return super.shouldRender(entity, frustum, camX, camY, camZ) || (outlineVisible(entity) && frustum.isVisible(entity.areaBox()));
    }

    @Override
    public void render(Rancher entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (!outlineVisible(entity)) return;
        Vec3 at = entity.getPosition(partialTick);
        AABB box = entity.areaBox().move(-at.x, -at.y, -at.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.55F, 0.85F, 0.35F, 1.0F);
    }
}
