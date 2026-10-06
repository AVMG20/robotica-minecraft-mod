package com.arno.robotica.energy.client;

import com.arno.robotica.energy.block.StructureControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Draws a controller's {@link StructureHighlight}: the structure box (cyan, amber for the preview of the smallest shape)
 * and a pulsing red box around the first wrong block. Nothing is drawn while no highlight is active.
 */
public class ControllerHighlightRenderer<T extends StructureControllerBlockEntity> implements BlockEntityRenderer<T> {
    public ControllerHighlightRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        StructureHighlight.Entry entry = StructureHighlight.get(be.getBlockPos());
        if (entry == null) return;
        BlockPos origin = be.getBlockPos();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB box = aabb(entry.box()).move(-origin.getX(), -origin.getY(), -origin.getZ()).inflate(0.02);
        if (entry.preview()) {
            LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 0.72F, 0.25F, 1.0F);
        } else {
            LevelRenderer.renderLineBox(pose, lines, box, 0.36F, 0.86F, 0.95F, 1.0F);
        }
        if (entry.problem() != null) {
            float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 150.0);
            BlockPos p = entry.problem();
            AABB bad = new AABB(p).move(-origin.getX(), -origin.getY(), -origin.getZ()).inflate(0.04);
            LevelRenderer.renderLineBox(pose, lines, bad, 1.0F, 0.2F * pulse, 0.15F * pulse, 1.0F);
            LevelRenderer.renderLineBox(pose, lines, bad.deflate(0.08), 1.0F, 0.35F, 0.3F, pulse);
        }
    }

    private static AABB aabb(BoundingBox b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        StructureHighlight.Entry entry = StructureHighlight.get(be.getBlockPos());
        AABB own = new AABB(be.getBlockPos());
        if (entry == null) return own;
        AABB box = aabb(entry.box()).minmax(own);
        return entry.problem() == null ? box : box.minmax(new AABB(entry.problem()));
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return StructureHighlight.active(be.getBlockPos());
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
