package com.arno.robotica.power.client;

import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaEmitterBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Tesla Coil and Tesla Emitter items: the platform from the block model plus the floating crystal (and a coil's shards)
 * that {@link TeslaCoilRenderer} draws in the world, hovering and turning the same way. The item models keep their
 * JSON transforms; they are only marked as custom rendered so this renderer runs.
 */
final class TeslaItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static TeslaItemRenderer instance;

    static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (instance == null) {
                Minecraft mc = Minecraft.getInstance();
                instance = new TeslaItemRenderer(mc);
            }
            return instance;
        }
    };

    private TeslaItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    private static List<DeferredItem<BlockItem>> items() {
        return List.of(PowerRegistry.TESLA_COIL_1_ITEM, PowerRegistry.TESLA_COIL_2_ITEM, PowerRegistry.TESLA_COIL_3_ITEM,
                PowerRegistry.TESLA_COIL_4_ITEM, PowerRegistry.TESLA_COIL_5_ITEM, PowerRegistry.TESLA_EMITTER_1_ITEM,
                PowerRegistry.TESLA_EMITTER_2_ITEM, PowerRegistry.TESLA_EMITTER_3_ITEM, PowerRegistry.TESLA_EMITTER_4_ITEM,
                PowerRegistry.TESLA_EMITTER_5_ITEM);
    }

    static void registerExtensions(RegisterClientExtensionsEvent event) {
        for (DeferredItem<BlockItem> item : items()) event.registerItem(EXTENSIONS, item.get());
    }

    /** Marks the Tesla item models as custom rendered (transforms and everything else stay the JSON model's). */
    static void wrapModels(ModelEvent.ModifyBakingResult event) {
        for (DeferredItem<BlockItem> item : items()) {
            ModelResourceLocation id = ModelResourceLocation.inventory(item.getId());
            BakedModel model = event.getModels().get(id);
            if (model != null) event.getModels().put(id, new BakedModelWrapper<>(model) {
                @Override
                public boolean isCustomRenderer() {
                    return true;
                }
            });
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof TeslaCoilBlock block)) return;
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers, light, overlay);
        TeslaCoilRenderer.Crystal c = block instanceof TeslaEmitterBlock ? TeslaCoilRenderer.EMITTER_CRYSTAL : TeslaCoilRenderer.COIL_CRYSTAL;
        double time = TeslaCoilRenderer.clientTicks();
        int tier = Math.max(0, Math.min(4, block.tier().ordinal()));
        Quaternionf rot = TeslaCoilRenderer.facingRotation(Direction.UP);
        TextureAtlasSprite sprite = TeslaCoilRenderer.sprite(tier);
        VertexConsumer vc = buffers.getBuffer(TeslaCoilRenderer.CRYSTAL_TYPE);
        PoseStack.Pose last = pose.last();
        double y = c.y() - 0.5 + c.hover() * Math.sin(time * Math.PI * 2 / TeslaCoilRenderer.HOVER_TICKS);
        TeslaCoilRenderer.crystal(vc, last, sprite, rot, 0, y, 0, c.radius(), c.bottom(), c.body(), c.top(), time * TeslaCoilRenderer.SPIN, c.sides());
        if (!(block instanceof TeslaEmitterBlock)) TeslaCoilRenderer.shardCrystals(vc, last, sprite, rot, time * TeslaCoilRenderer.ORBIT_IDLE, time);
    }
}
