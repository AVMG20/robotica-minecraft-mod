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
        // Full arcs only while the player holds a Tesla Linker; otherwise one barely visible thread, and only up close.
        boolean configuring = TeslaCoilBlock.isConfiguring();
        if (!configuring) {
            var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (cam.distanceToSqr(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5) > FAINT_RANGE * FAINT_RANGE) return;
        }
        long time = level.getGameTime();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < links.size(); i++) {
            TeslaLink link = links.get(i);
            Vec3 to = TeslaCoilBlockEntity.endPoint(level, link).subtract(origin.getX(), origin.getY(), origin.getZ());
            long seed = origin.asLong() * 31 + link.pos().asLong() * 17;
            if (!configuring) {
                long step = time / 6;
                arc(vc, m, from, to, seed + step * 7919, 0.012F, 0.08F, 140, 190, 255, active ? 34 : 18);
            } else if (active) {
                long step = time / 2;
                arc(vc, m, from, to, seed + step * 7919, 0.035F, 0.25F, 210, 240, 255, 230);
                arc(vc, m, from, to, seed + step * 7919, 0.09F, 0.25F, 80, 150, 255, 70);
                arc(vc, m, from, to, seed * 3 + step * 104729, 0.02F, 0.35F, 170, 210, 255, 150);
            } else {
                long step = time / 5;
                arc(vc, m, from, to, seed + step * 7919, 0.018F, 0.12F, 110, 170, 255, 90);
            }
        }
    }

    /** One jagged ribbon from a to b: crossed quads per segment, both windings, offsets fade out towards the ends. */
    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, long seed, float width, float jitter,
                            int r, int g, int bl, int alpha) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3) return;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p1 = dir.cross(ref).normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        int segments = Math.max(3, Math.min(32, (int) (len * 1.5)));
        double amp = Math.min(0.45, jitter * Math.sqrt(len));
        Vec3 prev = a;
        for (int s = 1; s <= segments; s++) {
            double t = (double) s / segments;
            Vec3 next = a.add(d.scale(t));
            if (s < segments) {
                double env = Math.sin(Math.PI * t);
                next = next.add(p1.scale(noise(seed, s, 0) * amp * env)).add(p2.scale(noise(seed, s, 1) * amp * env));
            }
            quad(vc, m, prev, next, p1.scale(width), r, g, bl, alpha);
            quad(vc, m, prev, next, p2.scale(width), r, g, bl, alpha);
            prev = next;
        }
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

    /** Deterministic noise in [-1, 1]. */
    private static double noise(long seed, int i, int axis) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (axis * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
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
