package com.arno.robotica.power.client;

import com.arno.robotica.power.PowerClientConfig;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaLink;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Thin animated arcs from a coil's tip to each link (additive lightning render type, no textures). The jagged path is
 * re-rolled every few ticks from a hash, so nothing is stored per frame. Idle links show one faint arc, links that carry
 * energy a bright core with a soft glow and a second, thinner arc. Culled by the coil's link box and the view distance;
 * the client config {@code teslaArcs} turns them off.
 */
public class TeslaCoilRenderer implements BlockEntityRenderer<TeslaCoilBlockEntity> {
    /** Without a Tesla Linker in hand, arcs only show this close, very faint. */
    private static final double FAINT_RANGE = 12.0;

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TeslaCoilBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        List<TeslaLink> links = be.links();
        if (level == null || links.isEmpty() || !PowerClientConfig.teslaArcs()) return;
        BlockState state = be.getBlockState();
        if (!state.hasProperty(TeslaCoilBlock.FACING)) return;
        BlockPos origin = be.getBlockPos();
        Vec3 from = TeslaCoilBlock.tipOffset(state.getValue(TeslaCoilBlock.FACING));
        boolean active = be.isActive();
        // Simple straight lines. Faint and only up close normally; brighter (and visible further) while holding the Linker.
        boolean configuring = TeslaCoilBlock.isConfiguring();
        if (!configuring) {
            var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (cam.distanceToSqr(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5) > FAINT_RANGE * FAINT_RANGE) return;
        }
        float time = level.getGameTime() + partialTick;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int lineAlpha = configuring ? (active ? 150 : 95) : (active ? 40 : 20);
        float width = configuring ? 0.018F : 0.012F;
        for (int i = 0; i < links.size(); i++) {
            TeslaLink link = links.get(i);
            Vec3 to = TeslaCoilBlockEntity.endPoint(level, link).subtract(origin.getX(), origin.getY(), origin.getZ());
            line(vc, m, from, to, width, 120, 210, 255, lineAlpha);
            if (active) {
                // A small bright pulse travelling from the coil to the target shows which way the power flows.
                double len = to.subtract(from).length();
                if (len < 0.2) continue;
                double t = ((time * 0.12 + i * 0.37) % len) / len;
                double seg = Math.min(0.35, len * 0.25) / len;
                Vec3 a = from.add(to.subtract(from).scale(t));
                Vec3 b = from.add(to.subtract(from).scale(Math.min(1.0, t + seg)));
                line(vc, m, a, b, width * 1.8F, 200, 245, 255, configuring ? 210 : 80);
            }
        }
    }

    /** A straight ribbon from a to b: two crossed quads, both windings. */
    private static void line(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float width, int r, int g, int bl, int alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 dir = d.normalize();
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p1 = dir.cross(ref).normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        quad(vc, m, a, b, p1.scale(width), r, g, bl, alpha);
        quad(vc, m, a, b, p2.scale(width), r, g, bl, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 w, int r, int g, int bl, int alpha) {
        float ax0 = (float) (a.x - w.x), ay0 = (float) (a.y - w.y), az0 = (float) (a.z - w.z);
        float ax1 = (float) (a.x + w.x), ay1 = (float) (a.y + w.y), az1 = (float) (a.z + w.z);
        float bx0 = (float) (b.x - w.x), by0 = (float) (b.y - w.y), bz0 = (float) (b.z - w.z);
        float bx1 = (float) (b.x + w.x), by1 = (float) (b.y + w.y), bz1 = (float) (b.z + w.z);
        vc.addVertex(m, ax0, ay0, az0).setColor(r, g, bl, alpha);
        vc.addVertex(m, ax1, ay1, az1).setColor(r, g, bl, alpha);
        vc.addVertex(m, bx1, by1, bz1).setColor(r, g, bl, alpha);
        vc.addVertex(m, bx0, by0, bz0).setColor(r, g, bl, alpha);
        vc.addVertex(m, bx0, by0, bz0).setColor(r, g, bl, alpha);
        vc.addVertex(m, bx1, by1, bz1).setColor(r, g, bl, alpha);
        vc.addVertex(m, ax1, ay1, az1).setColor(r, g, bl, alpha);
        vc.addVertex(m, ax0, ay0, az0).setColor(r, g, bl, alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(TeslaCoilBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TeslaCoilBlockEntity be) {
        return be.renderBox();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
