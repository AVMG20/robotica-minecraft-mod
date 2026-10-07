package com.arno.robotica.power;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.power.block.AccumulatorBlock;
import com.arno.robotica.power.block.AccumulatorBlockEntity;
import com.arno.robotica.power.block.AccumulatorItem;
import com.arno.robotica.power.block.ChargerBlock;
import com.arno.robotica.power.block.ChargerBlockEntity;
import com.arno.robotica.power.block.CombustionGeneratorBlock;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.MetalPressBlock;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import com.arno.robotica.power.block.SolarPanelBlock;
import com.arno.robotica.power.block.SolarPanelBlockEntity;
import com.arno.robotica.power.block.WindingCrankBlock;
import com.arno.robotica.power.block.WindingCrankBlockEntity;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaLinkerItem;
import com.arno.robotica.power.tesla.TeslaTier;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.level.material.PushReaction;
import com.arno.robotica.power.menu.ChargerMenu;
import com.arno.robotica.power.menu.CombustionGeneratorMenu;
import com.arno.robotica.power.menu.MetalPressMenu;
import com.arno.robotica.power.recipe.PressingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the power module. Recipes: data/robotica/recipe (scripts/data/power_recipes.py). */
public final class PowerRegistry {
    private PowerRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Robotica.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    // ---- Blocks ----
    public static final DeferredBlock<WindingCrankBlock> WINDING_CRANK = BLOCKS.registerBlock("winding_crank", WindingCrankBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<CombustionGeneratorBlock> COMBUSTION_GENERATOR = BLOCKS.registerBlock("combustion_generator",
            CombustionGeneratorBlock::new, machine().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 13 : 0));
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_MK1 = BLOCKS.registerBlock("solar_panel_mk1",
            p -> new SolarPanelBlock(p, SolarPanelBlock.Tier.MK1), machine().noOcclusion());
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_MK2 = BLOCKS.registerBlock("solar_panel_mk2",
            p -> new SolarPanelBlock(p, SolarPanelBlock.Tier.MK2), machine().noOcclusion());
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_MK3 = BLOCKS.registerBlock("solar_panel_mk3",
            p -> new SolarPanelBlock(p, SolarPanelBlock.Tier.MK3), machine().noOcclusion());
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_MK4 = BLOCKS.registerBlock("solar_panel_mk4",
            p -> new SolarPanelBlock(p, SolarPanelBlock.Tier.MK4), machine().noOcclusion());
    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR_1 = accumulator(AccumulatorBlock.Tier.I);
    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR_2 = accumulator(AccumulatorBlock.Tier.II);
    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR_3 = accumulator(AccumulatorBlock.Tier.III);
    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL_1 = teslaCoil(TeslaTier.I);
    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL_2 = teslaCoil(TeslaTier.II);
    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL_3 = teslaCoil(TeslaTier.III);
    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL_4 = teslaCoil(TeslaTier.IV);
    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL_5 = teslaCoil(TeslaTier.V);
    public static final DeferredBlock<ChargerBlock> CHARGER = BLOCKS.registerBlock("charger", ChargerBlock::new, machine());
    public static final DeferredBlock<MetalPressBlock> METAL_PRESS = BLOCKS.registerBlock("metal_press", MetalPressBlock::new, machine());
    public static final DeferredBlock<com.arno.robotica.power.block.WirelessChargerBlock> WIRELESS_CHARGER = BLOCKS.registerBlock("wireless_charger",
            com.arno.robotica.power.block.WirelessChargerBlock::new, machine().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 9 : 3));

    private static DeferredBlock<AccumulatorBlock> accumulator(AccumulatorBlock.Tier tier) {
        return BLOCKS.registerBlock(tier.id(), p -> new AccumulatorBlock(p, tier), machine());
    }

    private static DeferredBlock<TeslaCoilBlock> teslaCoil(TeslaTier tier) {
        return BLOCKS.registerBlock(tier.id(), p -> new TeslaCoilBlock(p, tier),
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 3.0F).sound(SoundType.COPPER)
                        .noOcclusion().lightLevel(s -> 7).pushReaction(PushReaction.DESTROY));
    }

    // ---- Items ----
    public static final DeferredItem<BlockItem> WINDING_CRANK_ITEM = ITEMS.registerSimpleBlockItem(WINDING_CRANK);
    public static final DeferredItem<BlockItem> COMBUSTION_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem(COMBUSTION_GENERATOR);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_MK1_ITEM = ITEMS.registerSimpleBlockItem(SOLAR_PANEL_MK1);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_MK2_ITEM = ITEMS.registerSimpleBlockItem(SOLAR_PANEL_MK2);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_MK3_ITEM = ITEMS.registerSimpleBlockItem(SOLAR_PANEL_MK3);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_MK4_ITEM = ITEMS.registerSimpleBlockItem(SOLAR_PANEL_MK4);
    public static final DeferredItem<Item> ACCUMULATOR_1_ITEM = accumulatorItem(ACCUMULATOR_1);
    public static final DeferredItem<Item> ACCUMULATOR_2_ITEM = accumulatorItem(ACCUMULATOR_2);
    public static final DeferredItem<Item> ACCUMULATOR_3_ITEM = accumulatorItem(ACCUMULATOR_3);
    public static final DeferredItem<BlockItem> TESLA_COIL_1_ITEM = ITEMS.registerSimpleBlockItem(TESLA_COIL_1);
    public static final DeferredItem<BlockItem> TESLA_COIL_2_ITEM = ITEMS.registerSimpleBlockItem(TESLA_COIL_2);
    public static final DeferredItem<BlockItem> TESLA_COIL_3_ITEM = ITEMS.registerSimpleBlockItem(TESLA_COIL_3);
    public static final DeferredItem<BlockItem> TESLA_COIL_4_ITEM = ITEMS.registerSimpleBlockItem(TESLA_COIL_4);
    public static final DeferredItem<BlockItem> TESLA_COIL_5_ITEM = ITEMS.registerSimpleBlockItem(TESLA_COIL_5);
    public static final DeferredItem<TeslaLinkerItem> TESLA_LINKER = ITEMS.registerItem("tesla_linker", TeslaLinkerItem::new);
    public static final DeferredItem<BlockItem> CHARGER_ITEM = ITEMS.registerSimpleBlockItem(CHARGER);
    public static final DeferredItem<BlockItem> METAL_PRESS_ITEM = ITEMS.registerSimpleBlockItem(METAL_PRESS);
    public static final DeferredItem<BlockItem> WIRELESS_CHARGER_ITEM = ITEMS.registerSimpleBlockItem(WIRELESS_CHARGER);

    private static DeferredItem<Item> accumulatorItem(DeferredBlock<AccumulatorBlock> block) {
        return ITEMS.registerItem(block.getId().getPath(), p -> new AccumulatorItem(block.get(), p));
    }

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindingCrankBlockEntity>> WINDING_CRANK_BE =
            BLOCK_ENTITIES.register("winding_crank", () -> BlockEntityType.Builder.of(WindingCrankBlockEntity::new, WINDING_CRANK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR_BE =
            BLOCK_ENTITIES.register("combustion_generator", () -> BlockEntityType.Builder.of(CombustionGeneratorBlockEntity::new, COMBUSTION_GENERATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_MK1_BE =
            BLOCK_ENTITIES.register("solar_panel_mk1", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL_MK1.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_MK2_BE =
            BLOCK_ENTITIES.register("solar_panel_mk2", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL_MK2.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_MK3_BE =
            BLOCK_ENTITIES.register("solar_panel_mk3", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL_MK3.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_MK4_BE =
            BLOCK_ENTITIES.register("solar_panel_mk4", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL_MK4.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AccumulatorBlockEntity>> ACCUMULATOR_BE =
            BLOCK_ENTITIES.register("accumulator", () -> BlockEntityType.Builder.of(AccumulatorBlockEntity::new,
                    ACCUMULATOR_1.get(), ACCUMULATOR_2.get(), ACCUMULATOR_3.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeslaCoilBlockEntity>> TESLA_COIL_BE =
            BLOCK_ENTITIES.register("tesla_coil", () -> BlockEntityType.Builder.of(TeslaCoilBlockEntity::new, TESLA_COIL_1.get(),
                    TESLA_COIL_2.get(), TESLA_COIL_3.get(), TESLA_COIL_4.get(), TESLA_COIL_5.get()).build(null));

    // ---- Data components ----
    /** The coil a Tesla Linker has selected. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> TESLA_SELECTION =
            COMPONENTS.registerComponentType("tesla_selection", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargerBlockEntity>> CHARGER_BE =
            BLOCK_ENTITIES.register("charger", () -> BlockEntityType.Builder.of(ChargerBlockEntity::new, CHARGER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalPressBlockEntity>> METAL_PRESS_BE =
            BLOCK_ENTITIES.register("metal_press", () -> BlockEntityType.Builder.of(MetalPressBlockEntity::new, METAL_PRESS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.arno.robotica.power.block.WirelessChargerBlockEntity>> WIRELESS_CHARGER_BE =
            BLOCK_ENTITIES.register("wireless_charger", () -> BlockEntityType.Builder.of(com.arno.robotica.power.block.WirelessChargerBlockEntity::new,
                    WIRELESS_CHARGER.get()).build(null));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<CombustionGeneratorMenu>> COMBUSTION_GENERATOR_MENU =
            MENUS.register("combustion_generator", () -> IMenuTypeExtension.create((id, inv, buf) -> new CombustionGeneratorMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<ChargerMenu>> CHARGER_MENU =
            MENUS.register("charger", () -> IMenuTypeExtension.create((id, inv, buf) -> new ChargerMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<com.arno.robotica.power.menu.EnergyInfoMenu>> ENERGY_INFO_MENU =
            MENUS.register("energy_info", () -> IMenuTypeExtension.create((id, inv, buf) -> new com.arno.robotica.power.menu.EnergyInfoMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<com.arno.robotica.power.menu.SolarPanelMenu>> SOLAR_PANEL_MENU =
            MENUS.register("solar_panel", () -> IMenuTypeExtension.create((id, inv, buf) -> new com.arno.robotica.power.menu.SolarPanelMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<MetalPressMenu>> METAL_PRESS_MENU =
            MENUS.register("metal_press", () -> IMenuTypeExtension.create((id, inv, buf) -> new MetalPressMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<com.arno.robotica.power.menu.WirelessChargerMenu>> WIRELESS_CHARGER_MENU =
            MENUS.register("wireless_charger", () -> IMenuTypeExtension.create((id, inv, buf) -> new com.arno.robotica.power.menu.WirelessChargerMenu(id, inv, buf.readBlockPos())));

    // ---- Recipes ----
    public static final DeferredHolder<RecipeType<?>, RecipeType<PressingRecipe>> PRESSING_TYPE =
            RECIPE_TYPES.register("pressing", () -> RecipeType.simple(Robotica.id("pressing")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PressingRecipe>> PRESSING_SERIALIZER =
            RECIPE_SERIALIZERS.register("pressing", PressingRecipe.Serializer::new);

    /** Registers everything and adds the items to the creative tab in progression order. */
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        COMPONENTS.register(modBus);

        RoboticaTab.add(WINDING_CRANK_ITEM);
        RoboticaTab.add(COMBUSTION_GENERATOR_ITEM);
        RoboticaTab.add(SOLAR_PANEL_MK1_ITEM);
        RoboticaTab.add(SOLAR_PANEL_MK2_ITEM);
        RoboticaTab.add(SOLAR_PANEL_MK3_ITEM);
        RoboticaTab.add(SOLAR_PANEL_MK4_ITEM);
        RoboticaTab.add(ACCUMULATOR_1_ITEM);
        RoboticaTab.add(ACCUMULATOR_2_ITEM);
        RoboticaTab.add(ACCUMULATOR_3_ITEM);
        RoboticaTab.add(TESLA_LINKER);
        RoboticaTab.add(TESLA_COIL_1_ITEM);
        RoboticaTab.add(TESLA_COIL_2_ITEM);
        RoboticaTab.add(TESLA_COIL_3_ITEM);
        RoboticaTab.add(TESLA_COIL_4_ITEM);
        RoboticaTab.add(TESLA_COIL_5_ITEM);
        RoboticaTab.add(CHARGER_ITEM);
        RoboticaTab.add(WIRELESS_CHARGER_ITEM);
        RoboticaTab.add(METAL_PRESS_ITEM);
    }
}
