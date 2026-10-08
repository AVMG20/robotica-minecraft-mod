package com.arno.robotica.energy.client;

import com.arno.robotica.energy.block.CoreReactorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Core Reactor: the active core floats and spins in the empty middle of the chamber inside a shell of light in the
 * core's colour. While it runs the shell throbs, every amplifier fires a warm beam into the core and every damper a
 * cold one, and lightning crawls over the shell. Plus the structure highlight of every controller.
 */
public class CoreReactorRenderer extends ControllerHighlightRenderer<CoreReactorBlockEntity> {
    public CoreReactorRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** Shell colour per core: Servo cyan, Magma orange, Antigrav violet, anything else white. */
    private static int coreColor(ItemStack core) {
        String id = BuiltInRegistries.ITEM.getKey(core.getItem()).getPath();
        if (id.contains("magma")) return 0xFF7A2A;
        if (id.contains("antigrav")) return 0xB46CFF;
        if (id.contains("servo")) return 0x4FD8FF;
        return 0xE8F0FF;
    }

    @Override
    public void render(CoreReactorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        Level level = be.getLevel();
        BlockPos center = be.center();
        ItemStack core = be.activeCore();
        if (level == null || center == null || core.isEmpty()) return;
        BlockPos origin = be.getBlockPos();
        Vec3 c = new Vec3(center.getX() - origin.getX() + 0.5, center.getY() - origin.getY() + 0.5, center.getZ() - origin.getZ() + 0.5);
        float time = level.getGameTime() + partialTick;
        boolean running = be.running();
        float throb = running ? 0.5F + 0.5F * Mth.sin(time * 0.25F) : 0.0F;

        // the core itself, full bright, bobbing and spinning (faster while it runs)
        pose.pushPose();
        pose.translate(c.x, c.y + Mth.sin(time * 0.06F) * 0.08F, c.z);
        pose.mulPose(Axis.YP.rotationDegrees(time * (running ? 4.0F : 0.8F)));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.03F) * 15.0F));
        float scale = 0.9F + 0.08F * throb;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(core, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();

        int rgb = coreColor(core);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        if (!running) {
            GlowDraw.cube(vc, m, c, 0.42F, r, g, b, 14);
            return;
        }
        GlowDraw.cube(vc, m, c, 0.45F + 0.05F * throb, r, g, b, 40);
        GlowDraw.cube(vc, m, c, 0.7F + 0.12F * throb, r, g, b, 22);
        GlowDraw.cube(vc, m, c, 1.0F + 0.2F * throb, r, g, b, 10);

        // modulator beams: amplifiers warm, dampers cold, pulsing down the line toward the core
        int i = 0;
        for (CoreReactorBlockEntity.Modulator mod : be.modulators()) {
            Vec3 from = new Vec3(mod.pos().getX() - origin.getX() + 0.5, mod.pos().getY() - origin.getY() + 0.5, mod.pos().getZ() - origin.getZ() + 0.5);
            Vec3 to = c.add(from.subtract(c).normalize().scale(0.35));
            float wave = 0.5F + 0.5F * Mth.sin(time * 0.4F - i * 0.9F);
            int alpha = (int) (90 + 110 * wave);
            if (mod.sign() > 0) GlowDraw.beam(vc, m, from, to, 0.04F + 0.02F * wave, 255, 150, 60, alpha);
            else GlowDraw.beam(vc, m, from, to, 0.04F + 0.02F * wave, 90, 190, 255, alpha);
            GlowDraw.cube(vc, m, from, 0.1F + 0.04F * wave, mod.sign() > 0 ? 255 : 120, mod.sign() > 0 ? 170 : 210, mod.sign() > 0 ? 90 : 255, 60);
            i++;
        }

        // lightning crawling over the shell, re-rolled five times a second
        java.util.Random rnd = new java.util.Random(origin.asLong() * 17 + (long) (time / 4));
        for (int k = 0; k < 3; k++) {
            Vec3 a = c.add(rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5);
            Vec3 e = c.add(rnd.nextGaussian() * 0.9, rnd.nextGaussian() * 0.9, rnd.nextGaussian() * 0.9);
            GlowDraw.bolt(vc, m, a, e, rnd.nextLong(), 4, 0.12, 0.01F, r, g, b, 200, 0);
        }
    }

    @Override
    public AABB getRenderBoundingBox(CoreReactorBlockEntity be) {
        AABB own = super.getRenderBoundingBox(be);
        BlockPos c = be.center();
        return c == null ? own : own.minmax(new AABB(c).inflate(CoreReactorBlockEntity.SIZE / 2 + 1));
    }

    @Override
    public boolean shouldRenderOffScreen(CoreReactorBlockEntity be) {
        return true;
    }
}
