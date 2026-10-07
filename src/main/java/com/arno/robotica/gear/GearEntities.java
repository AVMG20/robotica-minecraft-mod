package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.entity.RivetEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entities of the gear module: the Rivet Gun's rivet. */
public final class GearEntities {
    private GearEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Robotica.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RivetEntity>> RIVET =
            ENTITIES.register("rivet", () -> EntityType.Builder.<RivetEntity>of(RivetEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(6).updateInterval(10)
                    .build(Robotica.MODID + ":rivet"));
}
