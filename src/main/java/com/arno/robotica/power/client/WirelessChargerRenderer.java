package com.arno.robotica.power.client;

import com.arno.robotica.power.PowerClientConfig;
import com.arno.robotica.power.block.WirelessChargerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A soft, jagged arc from the charger's emitter to every player it is charging (additive lightning render type, no
 * textures). The path is re-rolled every few ticks from a hash, so nothing is stored per frame. The client config
 * {@code teslaArcs} turns it off with the Tesla arcs.
 */
public class WirelessChargerRenderer implements BlockEntityRenderer<WirelessChargerBlockEntity> {
    private static final int SEGMENTS = 7;

    public WirelessChargerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WirelessChargerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !PowerClientConfig.teslaArcs()) return;
        int[] ids = be.targetIds();
        byte[] states = be.targetStates();
        if (ids.length == 0) return;
        BlockPos origin = be.getBlockPos();
        Vec3 from = new Vec3(0.5, 1.22, 0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        long roll = level.getGameTime() / 3;
        for (int i = 0; i < ids.length && i < states.length; i++) {
            if (states[i] != WirelessChargerBlockEntity.CHARGING) continue;
            Entity target = level.getEntity(ids[i]);
            if (target == null) continue;
            Vec3 at = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.55, 0);
            Vec3 to = at.subtract(origin.getX(), origin.getY(), origin.getZ());
            Vec3 prev = from;
            double len = to.subtract(from).length();
            double jitter = Math.min(0.35, len * 0.06);
            for (int s = 1; s <= SEGMENTS; s++) {
                double t = (double) s / SEGMENTS;
                Vec3 next = from.add(to.subtract(from).scale(t));
                if (s < SEGMENTS) {
                    next = next.add(noise(roll, i, s, 0) * jitter, noise(roll, i, s, 1) * jitter, noise(roll, i, s, 2) * jitter);
                }
                ribbon(vc, m, prev, next, 0.025F, 120, 200, 255, 60);
                ribbon(vc, m, prev, next, 0.009F, 220, 245, 255, 150);
                prev = next;
            }
        }
    }

    /** Deterministic noise in [-1, 1] for a segment of an arc. */
    private static double noise(long roll, int arc, int segment, int axis) {
        long h = roll * 0x9E3779B97F4A7C15L + arc * 0xC2B2AE3D27D4EB4FL + segment * 0x165667B19E3779F9L + axis * 0x27D4EB2F165667C5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFF) / 32767.5) - 1.0;
    }

    /** A straight ribbon from a to b: two crossed quads, both windings. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float width, int r, int g, int bl, int alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 dir = d.normalize();
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p1 = dir.cross(ref).normalize().scale(width);
        Vec3 p2 = dir.cross(p1.normalize()).normalize().scale(width);
        quad(vc, m, a, b, p1, r, g, bl, alpha);
        quad(vc, m, a, b, p2, r, g, bl, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 w, int r, int g, int bl, int alpha) {
        vertex(vc, m, a.subtract(w), r, g, bl, alpha);
        vertex(vc, m, a.add(w), r, g, bl, alpha);
        vertex(vc, m, b.add(w), r, g, bl, alpha);
        vertex(vc, m, b.subtract(w), r, g, bl, alpha);
        vertex(vc, m, b.subtract(w), r, g, bl, alpha);
        vertex(vc, m, b.add(w), r, g, bl, alpha);
        vertex(vc, m, a.add(w), r, g, bl, alpha);
        vertex(vc, m, a.subtract(w), r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int alpha) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(WirelessChargerBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(WirelessChargerBlockEntity be) {
        return be.renderBox();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
