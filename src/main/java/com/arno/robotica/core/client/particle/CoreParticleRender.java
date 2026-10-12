package com.arno.robotica.core.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/** Render type of the glowing Robotica particles, plus a particle budget for client effects that spawn in loops. */
public final class CoreParticleRender {
    private CoreParticleRender() {}

    /** Particle sheet with additive blending and no depth writes. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "robotica:additive";
        }
    };

    /**
     * How many of {@code count} cosmetic particles a client effect should spawn: all at All, half at Decreased, none at
     * Minimal.
     */
    public static int budget(int count) {
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        return switch (status) {
            case ALL -> count;
            case DECREASED -> (count + 1) / 2;
            case MINIMAL -> 0;
        };
    }
}
