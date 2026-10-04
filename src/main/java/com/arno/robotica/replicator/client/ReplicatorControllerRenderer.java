package com.arno.robotica.replicator.client;

import com.arno.robotica.replicator.block.ReplicatorControllerBlock;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the vial's mob as a small, slowly spinning, see-through hologram in the hollow centre of the frame.
 * Each finished cycle makes it flash and swell for a moment. The mob entity is a cached client side instance.
 */
public class ReplicatorControllerRenderer implements BlockEntityRenderer<ReplicatorControllerBlockEntity> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float FLASH_TICKS = 16.0F;
    /** Degrees per tick: one turn every 12 seconds. */
    private static final float SPIN = 1.5F;

    private final EntityRenderDispatcher dispatcher;

    public ReplicatorControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getEntityRenderer();
    }

    @Override
    public void render(ReplicatorControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        ResourceLocation id = be.clientVialType();
        if (level == null || id == null || !state.getValue(ReplicatorControllerBlock.FORMED)) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return;
        Entity entity = HologramMobs.get(type);
        if (entity == null) return;

        boolean working = state.getValue(ReplicatorControllerBlock.LIT);
        float time = level.getGameTime() + partialTick;
        float flash = (time - be.flashStart()) / FLASH_TICKS;
        float pulse = flash >= 0.0F && flash <= 1.0F ? Mth.sin(flash * Mth.PI) : 0.0F;

        float width = Math.max(0.1F, entity.getBbWidth());
        float height = Math.max(0.1F, entity.getBbHeight());
        float scale = Mth.clamp(0.8F / Math.max(width, height), 0.12F, 1.0F) * (1.0F + 0.3F * pulse);

        Direction facing = state.getValue(ReplicatorControllerBlock.FACING);
        entity.tickCount = (int) time;

        // Idle mobs are dim; a working one glows. The flash goes toward white and almost opaque.
        int alpha = Math.round(255.0F * Mth.clamp((working ? 0.6F : 0.3F) + 0.4F * pulse, 0.0F, 1.0F));
        int red = Math.round(Mth.lerp(pulse, 150.0F, 255.0F));
        int green = 255;
        int blue = Math.round(Mth.lerp(pulse, 225.0F, 255.0F));

        pose.pushPose();
        pose.translate(0.5 - facing.getStepX(), 0.5 - facing.getStepY() + Mth.sin(time * 0.08F) * 0.03F, 0.5 - facing.getStepZ());
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(time * SPIN));
        pose.translate(0.0, -height * 0.5F, 0.0);

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(entity);
        ResourceLocation texture = renderer.getTextureLocation(entity);
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderShadow(false);
        dispatcher.setRenderHitBoxes(false);
        try {
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, partialTick, pose,
                    new HologramBuffer(buffers, texture, red, green, blue, alpha), FULL_BRIGHT);
        } finally {
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(ReplicatorControllerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.0);
    }
}
