package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Draws the work area outline of an area worker when "show area" is on. The area is far bigger than the block,
 * so the render bounding box is the area itself and the view distance is large.
 */
public class AreaOutlineRenderer<T extends AreaWorkerBlockEntity> implements BlockEntityRenderer<T> {
    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!be.outlineVisible()) return;
        BlockPos pos = be.getBlockPos();
        AABB box = be.areaBox().move(-pos.getX(), -pos.getY(), -pos.getZ());
        float r = 0.36F;
        float g = 0.78F;
        float b = 0.85F;
        if (be instanceof ExcavatorBlockEntity) {
            r = 1.0F;
            g = 0.7F;
            b = 0.25F;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, box, r, g, b, 1.0F);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return be.outlineVisible() ? be.areaBox().minmax(new AABB(be.getBlockPos())) : new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return be.outlineVisible();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
