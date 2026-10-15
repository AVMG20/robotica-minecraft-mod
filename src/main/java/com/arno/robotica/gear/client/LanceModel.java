package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.weapon.NullLanceItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The Null Lance model's charge stages (scripts/data/gear_lance_model.py): robotica:charge is 0 when not charging,
 * 0.25 / 0.5 / 0.75 while 1 to 3 of the 4 barrel coils are lit and 1 at full charge. Works for every living entity
 * holding the lance, so other players' lances light up too.
 */
final class LanceModel {
    private LanceModel() {}

    static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(GearItems.NULL_LANCE.get(), Robotica.id("charge"), LanceModel::charge));
    }

    private static float charge(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) return 0.0F;
        float progress = entity.getTicksUsingItem() / (float) NullLanceItem.CHARGE_TICKS;
        return Math.min(4, Mth.floor(progress * 4.0F)) / 4.0F;
    }
}
