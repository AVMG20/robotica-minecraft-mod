package com.arno.robotica.automation.rancher;

import com.arno.robotica.Robotica;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry entries of the Rancher (walking animal-farm robot, Mk1 and Mk2). */
public final class RancherContent {
    private RancherContent() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Settings, battery and health of a picked-up Rancher. Energy uses the core energy component. Smithing keeps both. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> STATE =
            COMPONENTS.registerComponentType("rancher_state", b -> b.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredItem<RancherItem> RANCHER = ITEMS.registerItem("rancher", p -> new RancherItem(p.rarity(Rarity.COMMON), 1));
    public static final DeferredItem<RancherItem> RANCHER_MK2 = ITEMS.registerItem("rancher_mk2", p -> new RancherItem(p.rarity(Rarity.UNCOMMON), 2));

    public static final DeferredHolder<EntityType<?>, EntityType<Rancher>> RANCHER_ENTITY =
            ENTITIES.register("rancher", () -> EntityType.Builder.<Rancher>of(Rancher::new, MobCategory.MISC)
                    .sized(0.7F, 1.3F).eyeHeight(1.0F).clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":rancher"));

    public static final DeferredHolder<MenuType<?>, MenuType<RancherMenu>> MENU =
            MENUS.register("rancher", () -> IMenuTypeExtension.create((id, inv, buf) -> new RancherMenu(id, inv, buf.readVarInt())));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(RANCHER_ENTITY.get(), Rancher.createAttributes().build()));
        modBus.addListener(RegisterCapabilitiesEvent.class, e ->
                e.registerEntity(Capabilities.EnergyStorage.ENTITY, RANCHER_ENTITY.get(), (r, side) -> r.energyStorage()));
    }
}
