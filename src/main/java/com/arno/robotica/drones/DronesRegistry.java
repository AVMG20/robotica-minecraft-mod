package com.arno.robotica.drones;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import com.arno.robotica.drones.item.DroneItem;
import com.arno.robotica.drones.menu.MiningDroneMenu;
import com.arno.robotica.drones.menu.SentryDroneMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the drones module. Recipes and models are written by scripts/data/drones_*.py. */
public final class DronesRegistry {
    private DronesRegistry() {}

    /** The two drone families. */
    public enum Kind {
        MINING, SENTRY
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Everything a drone carries that is not energy: inventory, battery, settings and health. Kept by pick-up and smithing. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> DRONE_STATE =
            COMPONENTS.registerComponentType("drone_state", b -> b.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredItem<DroneItem> MINING_DRONE = ITEMS.registerItem("mining_drone",
            p -> new DroneItem(p.rarity(Rarity.COMMON), Kind.MINING, 1));
    public static final DeferredItem<DroneItem> MINING_DRONE_MK2 = ITEMS.registerItem("mining_drone_mk2",
            p -> new DroneItem(p.rarity(Rarity.UNCOMMON), Kind.MINING, 2));
    public static final DeferredItem<DroneItem> SENTRY_DRONE = ITEMS.registerItem("sentry_drone",
            p -> new DroneItem(p.rarity(Rarity.COMMON), Kind.SENTRY, 1));
    public static final DeferredItem<DroneItem> SENTRY_DRONE_MK2 = ITEMS.registerItem("sentry_drone_mk2",
            p -> new DroneItem(p.rarity(Rarity.UNCOMMON), Kind.SENTRY, 2));

    public static final DeferredHolder<EntityType<?>, EntityType<MiningDrone>> MINING_DRONE_ENTITY =
            ENTITIES.register("mining_drone", () -> EntityType.Builder.<MiningDrone>of(MiningDrone::new, MobCategory.MISC)
                    .sized(0.6F, 0.5F).eyeHeight(0.3F).fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":mining_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<SentryDrone>> SENTRY_DRONE_ENTITY =
            ENTITIES.register("sentry_drone", () -> EntityType.Builder.<SentryDrone>of(SentryDrone::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).eyeHeight(0.3F).fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":sentry_drone"));

    public static final DeferredHolder<MenuType<?>, MenuType<MiningDroneMenu>> MINING_MENU =
            MENUS.register("mining_drone", () -> IMenuTypeExtension.create((id, inv, buf) -> new MiningDroneMenu(id, inv, buf.readVarInt())));

    public static final DeferredHolder<MenuType<?>, MenuType<SentryDroneMenu>> SENTRY_MENU =
            MENUS.register("sentry_drone", () -> IMenuTypeExtension.create((id, inv, buf) -> new SentryDroneMenu(id, inv, buf.readVarInt())));

    /** Registers everything and adds the items to the creative tab in progression order. */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        RoboticaTab.add(MINING_DRONE);
        RoboticaTab.add(SENTRY_DRONE);
        RoboticaTab.add(MINING_DRONE_MK2);
        RoboticaTab.add(SENTRY_DRONE_MK2);
    }
}
