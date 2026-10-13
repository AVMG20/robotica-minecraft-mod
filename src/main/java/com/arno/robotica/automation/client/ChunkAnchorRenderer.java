package com.arno.robotica.automation.client;

import com.arno.robotica.automation.block.ChunkAnchorBlock;
import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Chunk Anchor: two glowing rings spin around the core while it loads (cyan; slow and red when dry or over the cap), and
 * for 10 seconds after a sneak-right-click the loaded chunks are outlined, like the area of the other machines.
 * Additive lightning render type, no textures.
 */
public class ChunkAnchorRenderer implements BlockEntityRenderer<ChunkAnchorBlockEntity> {
    private static final int SEGMENTS = 24;
    private static final float CORE_Y = 0.53F;

    public ChunkAnchorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ChunkAnchorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        ChunkAnchorBlock.Mode mode = be.getBlockState().hasProperty(ChunkAnchorBlock.MODE)
                ? be.getBlockState().getValue(ChunkAnchorBlock.MODE) : ChunkAnchorBlock.Mode.IDLE;
        boolean alert = mode == ChunkAnchorBlock.Mode.ALERT;
        if (mode != ChunkAnchorBlock.Mode.IDLE) {
            float time = (level.getGameTime() % 7200L) + partialTick;
            float speed = alert ? 0.8F : 4.5F;
            int r = alert ? 255 : 95, g = alert ? 80 : 227, b = alert ? 60 : 240;
            int alpha = alert ? (int) (90 + 50 * Mth.sin(time * 0.25F)) : 150;
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            pose.pushPose();
            pose.translate(0.5F, CORE_Y, 0.5F);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * speed));
            pose.mulPose(Axis.XP.rotationDegrees(18.0F));
            ring(vc, pose.last().pose(), 0.34F, 0.028F, r, g, b, alpha);
            pose.popPose();
            pose.mulPose(Axis.YP.rotationDegrees(-time * speed * 1.4F + 45.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(64.0F));
            ring(vc, pose.last().pose(), 0.27F, 0.022F, r, g, b, alpha * 3 / 4);
            pose.popPose();
        }
        if (be.outlineVisible()) {
            BlockPos pos = be.getBlockPos();
            AABB area = be.area();
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            float cr = alert ? 1.0F : 0.36F, cg = alert ? 0.35F : 0.86F, cb = alert ? 0.3F : 0.95F;
            double minY = -3 + 0.01, maxY = 6 - 0.01;
            int side = be.side();
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    double x0 = area.minX + i * 16 - pos.getX(), z0 = area.minZ + j * 16 - pos.getZ();
                    LevelRenderer.renderLineBox(pose, lines, x0 + 0.02, minY, z0 + 0.02, x0 + 15.98, maxY, z0 + 15.98, cr, cg, cb, 0.55F);
                }
            }
            LevelRenderer.renderLineBox(pose, lines, area.minX - pos.getX(), minY - 1, area.minZ - pos.getZ(),
                    area.maxX - pos.getX(), maxY + 1, area.maxZ - pos.getZ(), cr, cg, cb, 1.0F);
        }
    }

    /** A dashed glowing band of radius {@code radius} in the local XZ plane, both windings. */
    private static void ring(VertexConsumer vc, Matrix4f m, float radius, float half, int r, int g, int b, int alpha) {
        float step = Mth.TWO_PI / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            if (i % 4 == 3) continue;    // gaps make the spin visible
            float a0 = i * step, a1 = a0 + step * 0.9F;
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            vertex(vc, m, x0, -half, z0, r, g, b, alpha);
            vertex(vc, m, x0, half, z0, r, g, b, alpha);
            vertex(vc, m, x1, half, z1, r, g, b, alpha);
            vertex(vc, m, x1, -half, z1, r, g, b, alpha);
            vertex(vc, m, x1, -half, z1, r, g, b, alpha);
            vertex(vc, m, x1, half, z1, r, g, b, alpha);
            vertex(vc, m, x0, half, z0, r, g, b, alpha);
            vertex(vc, m, x0, -half, z0, r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int alpha) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(ChunkAnchorBlockEntity be) {
        return be.outlineVisible();
    }

    @Override
    public AABB getRenderBoundingBox(ChunkAnchorBlockEntity be) {
        return be.outlineVisible() ? be.area() : be.blockBox();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
