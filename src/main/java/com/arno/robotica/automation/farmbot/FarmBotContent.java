package com.arno.robotica.automation.farmbot;

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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Registry entries of the walking farm robots (Stumpy and Sprout, Mk1 to Mk4). The Mk1 items keep the ids of the old robot blocks. */
public final class FarmBotContent {
    private FarmBotContent() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Mk, battery, cards, pouch and health of a picked-up robot. Energy uses the core energy component. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> STATE =
            COMPONENTS.registerComponentType("farm_bot_state", b -> b.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredHolder<EntityType<?>, EntityType<Stumpy>> STUMPY_ENTITY =
            ENTITIES.register("stumpy", () -> EntityType.Builder.<Stumpy>of(Stumpy::new, MobCategory.MISC)
                    .sized(0.7F, 0.95F).eyeHeight(0.75F).fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":stumpy"));
    public static final DeferredHolder<EntityType<?>, EntityType<Sprout>> SPROUT_ENTITY =
            ENTITIES.register("sprout", () -> EntityType.Builder.<Sprout>of(Sprout::new, MobCategory.MISC)
                    .sized(0.7F, 0.95F).eyeHeight(0.75F).fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":sprout"));

    /** Index 0 = Mk1 ("stumpy"), then stumpy_mk2..mk4. */
    public static final List<DeferredItem<FarmBotItem>> STUMPY_ITEMS = items("stumpy", STUMPY_ENTITY);
    public static final List<DeferredItem<FarmBotItem>> SPROUT_ITEMS = items("sprout", SPROUT_ENTITY);
    public static final DeferredItem<FarmBotItem> STUMPY_ITEM = STUMPY_ITEMS.get(0);
    public static final DeferredItem<FarmBotItem> SPROUT_ITEM = SPROUT_ITEMS.get(0);

    public static final DeferredHolder<MenuType<?>, MenuType<FarmBotMenu>> MENU =
            MENUS.register("farm_bot", () -> IMenuTypeExtension.create((id, inv, buf) -> new FarmBotMenu(id, inv, buf.readVarInt())));

    /** Age of each Mk on the component ladder (Mk1 to Mk4). */
    private static final int[] AGES = {0, 1, 2, 4};
    private static final Rarity[] RARITIES = {Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE};

    private static List<DeferredItem<FarmBotItem>> items(String name, Supplier<? extends EntityType<? extends FarmBot>> type) {
        List<DeferredItem<FarmBotItem>> list = new ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            int t = tier;
            list.add(ITEMS.registerItem(tier == 1 ? name : name + "_mk" + tier,
                    p -> new FarmBotItem(p.rarity(RARITIES[t - 1]), type, t, AGES[t - 1], "tooltip.robotica." + name)));
        }
        return List.copyOf(list);
    }

    /** The item of a Mk (clamped to 1-4). */
    public static FarmBotItem item(List<DeferredItem<FarmBotItem>> items, int tier) {
        return items.get(Math.max(1, Math.min(4, tier)) - 1).get();
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> {
            e.put(STUMPY_ENTITY.get(), FarmBot.createAttributes().build());
            e.put(SPROUT_ENTITY.get(), FarmBot.createAttributes().build());
        });
        modBus.addListener(RegisterCapabilitiesEvent.class, e -> {
            e.registerEntity(Capabilities.EnergyStorage.ENTITY, STUMPY_ENTITY.get(), (bot, side) -> bot.energy);
            e.registerEntity(Capabilities.EnergyStorage.ENTITY, SPROUT_ENTITY.get(), (bot, side) -> bot.energy);
        });
    }
}
