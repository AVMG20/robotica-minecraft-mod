package com.arno.robotica.replicator.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a buffer source so a mob renders as a tinted, see-through hologram: the body's cutout render type is swapped
 * for the translucent one of the same texture, and every vertex colour is multiplied by the tint and alpha.
 * Extra layers (eyes, glow, overlays) keep their own render types and only get the tint.
 */
final class HologramBuffer implements MultiBufferSource {
    private final MultiBufferSource delegate;
    @Nullable
    private final ResourceLocation bodyTexture;
    private final int red;
    private final int green;
    private final int blue;
    private final int alpha;

    HologramBuffer(MultiBufferSource delegate, @Nullable ResourceLocation bodyTexture, int red, int green, int blue, int alpha) {
        this.delegate = delegate;
        this.bodyTexture = bodyTexture;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        RenderType used = type;
        if (bodyTexture != null && type.equals(RenderType.entityCutoutNoCull(bodyTexture))) {
            used = RenderType.entityTranslucent(bodyTexture);
        }
        return new Tint(delegate.getBuffer(used));
    }

    private final class Tint implements VertexConsumer {
        private final VertexConsumer out;

        Tint(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r * red / 255, g * green / 255, b * blue / 255, a * alpha / 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            return this;
        }
    }
}
