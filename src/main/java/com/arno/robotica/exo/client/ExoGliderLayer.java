package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.item.ExoArmorItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Glider wings that fold out of an Exo Chestplate with the Glider module while its wearer glides. */
final class ExoGliderLayer<T extends LivingEntity, M extends EntityModel<T>> extends ElytraLayer<T, M> {
    private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/exo_glider.png");

    private ExoGliderLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent, models);
    }

    @Override
    public boolean shouldRender(ItemStack stack, T entity) {
        return entity.isFallFlying() && stack.getItem() instanceof ExoArmorItem && Modules.active(stack, ModuleKind.GLIDER) > 0;
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, T entity) {
        return TEXTURE;
    }

    static void register(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new ExoGliderLayer<>(renderer, event.getEntityModels()));
        }
    }
}
