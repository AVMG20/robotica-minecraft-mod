package com.arno.robotica.industry;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.core.item.PartItem;
import com.arno.robotica.industry.block.ProcessingBlock;
import com.arno.robotica.industry.block.ProcessingBlockEntity;
import com.arno.robotica.industry.block.RtgBlock;
import com.arno.robotica.industry.block.RtgBlockEntity;
import com.arno.robotica.industry.menu.ProcessingMenu;
import com.arno.robotica.industry.menu.RtgMenu;
import com.arno.robotica.industry.recipe.Machine;
import com.arno.robotica.industry.recipe.ProcessingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every registry entry of the industry module: ores and materials, alloys, fuel pellets, Assembler-only parts, the
 * processing machines (Mk1-Mk4 each), the RTG, their block entities, menu and recipe types.
 * Recipes: data/robotica/recipe (scripts/data/industry_recipes.py).
 */
public final class IndustryRegistry {
    private IndustryRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Robotica.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Robotica.MODID);

    private static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    // ---------------------------------------------------------------- ores and storage blocks

    public static final DeferredBlock<Block> THORIUM_ORE = ore("thorium_ore", ConstantInt.of(0),
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final DeferredBlock<Block> DEEPSLATE_THORIUM_ORE = ore("deepslate_thorium_ore", ConstantInt.of(0),
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                    .strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> PYROLITE_ORE = ore("pyrolite_ore", UniformInt.of(2, 5),
            BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F).sound(SoundType.NETHER_ORE).lightLevel(s -> 4));
    public static final DeferredBlock<Block> RESONITE_ORE = ore("resonite_ore", UniformInt.of(3, 7),
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                    .strength(4.5F, 9.0F).lightLevel(s -> 3));
    public static final DeferredBlock<Block> RAW_THORIUM_BLOCK = storage("raw_thorium_block", MapColor.COLOR_LIGHT_GREEN, SoundType.STONE, 0);
    public static final DeferredBlock<Block> THORIUM_BLOCK = storage("thorium_block", MapColor.COLOR_LIGHT_GREEN, SoundType.METAL, 0);
    public static final DeferredBlock<Block> PYROLITE_BLOCK = storage("pyrolite_block", MapColor.COLOR_ORANGE, SoundType.AMETHYST, 7);
    public static final DeferredBlock<Block> RESONITE_BLOCK = storage("resonite_block", MapColor.COLOR_PURPLE, SoundType.AMETHYST, 6);

    // ---------------------------------------------------------------- materials

    public static final DeferredItem<PartItem> RAW_THORIUM = part("raw_thorium", 1);
    public static final DeferredItem<PartItem> THORIUM_INGOT = part("thorium_ingot", 1);
    public static final DeferredItem<PartItem> THORIUM_DUST = part("thorium_dust", 1);
    public static final DeferredItem<PartItem> THORIUM_PLATE = part("thorium_plate", 1);
    public static final DeferredItem<PartItem> GRAPHITE_DUST = part("graphite_dust", 1);
    public static final DeferredItem<PartItem> PYROLITE_SHARD = part("pyrolite_shard", 3);
    public static final DeferredItem<PartItem> PYROLITE_DUST = part("pyrolite_dust", 3);
    public static final DeferredItem<PartItem> RESONITE_CRYSTAL = part("resonite_crystal", 4, Rarity.UNCOMMON);
    public static final DeferredItem<PartItem> RESONITE_DUST = part("resonite_dust", 4, Rarity.UNCOMMON);

    // Alloys: only the Alloy Smelter makes them
    public static final DeferredItem<PartItem> FERROTHORIUM_INGOT = part("ferrothorium_ingot", 2);
    public static final DeferredItem<PartItem> FERROTHORIUM_PLATE = part("ferrothorium_plate", 2);
    public static final DeferredItem<PartItem> PYROSTEEL_INGOT = part("pyrosteel_ingot", 3);
    public static final DeferredItem<PartItem> PYROSTEEL_PLATE = part("pyrosteel_plate", 3);
    public static final DeferredItem<PartItem> RESONANT_ALLOY_INGOT = part("resonant_alloy_ingot", 4, Rarity.UNCOMMON);
    public static final DeferredItem<PartItem> RESONANT_ALLOY_PLATE = part("resonant_alloy_plate", 4, Rarity.UNCOMMON);

    // Assembler-only parts
    public static final DeferredItem<PartItem> THERMOCOUPLE = part("thermocouple", 2);
    public static final DeferredItem<PartItem> SUPERCONDUCTOR_COIL = part("superconductor_coil", 3, Rarity.UNCOMMON);
    public static final DeferredItem<PartItem> RESONANT_LATTICE = part("resonant_lattice", 4, Rarity.RARE);

    // Reactor fuel (stats in data/robotica/data_maps/item/reactor_fuel.json and fusion_fuel.json)
    public static final DeferredItem<PartItem> THORIUM_FUEL_PELLET = part("thorium_fuel_pellet", 2);
    public static final DeferredItem<PartItem> ENRICHED_FUEL_PELLET = part("enriched_fuel_pellet", 3, Rarity.UNCOMMON);
    public static final DeferredItem<PartItem> DEPLETED_FUEL_PELLET = part("depleted_fuel_pellet", 2);
    public static final DeferredItem<PartItem> RADIANT_ISOTOPE = part("radiant_isotope", 3, Rarity.UNCOMMON);
    public static final DeferredItem<PartItem> FUSION_FUEL_PELLET = part("fusion_fuel_pellet", 4, Rarity.RARE);

    // ---------------------------------------------------------------- machines

    private static final Map<Machine, List<DeferredBlock<ProcessingBlock>>> MACHINE_BLOCKS = new EnumMap<>(Machine.class);
    private static final Map<Machine, List<DeferredItem<BlockItem>>> MACHINE_ITEMS = new EnumMap<>(Machine.class);
    private static final Map<Machine, DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingBlockEntity>>> MACHINE_BES = new EnumMap<>(Machine.class);
    private static final Map<Machine, DeferredHolder<RecipeType<?>, RecipeType<ProcessingRecipe>>> TYPES = new EnumMap<>(Machine.class);
    private static final Map<Machine, DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ProcessingRecipe>>> SERIALIZERS = new EnumMap<>(Machine.class);

    static {
        for (Machine machine : Machine.values()) {
            List<DeferredBlock<ProcessingBlock>> blocks = new ArrayList<>();
            List<DeferredItem<BlockItem>> items = new ArrayList<>();
            for (int tier = 1; tier <= Machine.TIERS; tier++) {
                int t = tier;
                DeferredBlock<ProcessingBlock> block = BLOCKS.registerBlock(machine.id(tier), p -> new ProcessingBlock(p, machine, t), machine());
                blocks.add(block);
                Rarity rarity = tier >= 4 ? Rarity.RARE : tier == 3 ? Rarity.UNCOMMON : Rarity.COMMON;
                items.add(ITEMS.registerItem(machine.id(tier), p -> new BlockItem(block.get(), p.rarity(rarity))));
            }
            MACHINE_BLOCKS.put(machine, blocks);
            MACHINE_ITEMS.put(machine, items);
            MACHINE_BES.put(machine, BLOCK_ENTITIES.register(machine.id(), () -> BlockEntityType.Builder.of(ProcessingBlockEntity::new,
                    blocks.stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null)));
            TYPES.put(machine, RECIPE_TYPES.register(machine.recipeId, () -> RecipeType.simple(Robotica.id(machine.recipeId))));
            SERIALIZERS.put(machine, RECIPE_SERIALIZERS.register(machine.recipeId, () -> new ProcessingRecipe.Serializer(machine)));
        }
    }

    public static final DeferredBlock<RtgBlock> RTG = BLOCKS.registerBlock("rtg", RtgBlock::new, machine());
    public static final DeferredItem<BlockItem> RTG_ITEM = ITEMS.registerSimpleBlockItem(RTG);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RtgBlockEntity>> RTG_BE =
            BLOCK_ENTITIES.register("rtg", () -> BlockEntityType.Builder.of(RtgBlockEntity::new, RTG.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ProcessingMenu>> PROCESSING_MENU =
            MENUS.register("industry_machine", () -> IMenuTypeExtension.create(ProcessingMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<RtgMenu>> RTG_MENU =
            MENUS.register("rtg", () -> IMenuTypeExtension.create((id, inv, buf) -> new RtgMenu(id, inv, buf.readBlockPos())));

    public static DeferredBlock<ProcessingBlock> machineBlock(Machine machine, int tier) {
        return MACHINE_BLOCKS.get(machine).get(tier - 1);
    }

    public static DeferredItem<BlockItem> machineItem(Machine machine, int tier) {
        return MACHINE_ITEMS.get(machine).get(tier - 1);
    }

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingBlockEntity>> machineBlockEntity(Machine machine) {
        return MACHINE_BES.get(machine);
    }

    public static DeferredHolder<RecipeType<?>, RecipeType<ProcessingRecipe>> recipeType(Machine machine) {
        return TYPES.get(machine);
    }

    public static DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ProcessingRecipe>> serializer(Machine machine) {
        return SERIALIZERS.get(machine);
    }

    // ---------------------------------------------------------------- helpers

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().lightLevel(s -> s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? 8 : 0);
    }

    private static DeferredBlock<Block> ore(String name, IntProvider xp, BlockBehaviour.Properties props) {
        DeferredBlock<Block> block = BLOCKS.registerBlock(name, p -> new DropExperienceBlock(xp, p), props);
        TAB.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    private static DeferredBlock<Block> storage(String name, MapColor color, SoundType sound, int light) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(sound).lightLevel(s -> light));
        TAB.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    private static DeferredItem<PartItem> part(String name, int age) {
        return part(name, age, Rarity.COMMON);
    }

    private static DeferredItem<PartItem> part(String name, int age, Rarity rarity) {
        DeferredItem<PartItem> item = ITEMS.registerItem(name, p -> new PartItem(p.rarity(rarity), age));
        TAB.add(item);
        return item;
    }

    /** Registers everything and adds the items to the creative tab: materials, parts and fuel, then the machines. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);

        TAB.forEach(RoboticaTab::add);
        for (Machine machine : Machine.values()) MACHINE_ITEMS.get(machine).forEach(RoboticaTab::add);
        RoboticaTab.add(RTG_ITEM);
    }
}
