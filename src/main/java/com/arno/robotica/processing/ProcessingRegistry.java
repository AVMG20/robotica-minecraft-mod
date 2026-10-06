package com.arno.robotica.processing;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.processing.block.ElectricFurnaceBlockEntity;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.arno.robotica.processing.block.ProcessingMachineItem;
import com.arno.robotica.processing.menu.ElectricFurnaceMenu;
import com.arno.robotica.processing.menu.GrinderMenu;
import com.arno.robotica.processing.recipe.GrindingRecipe;
import com.arno.robotica.processing.recipe.MachineUpgradeRecipe;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Every registry entry of the processing module. Recipes and data: scripts/data/processing_recipes.py. */
public final class ProcessingRegistry {
    private ProcessingRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Robotica.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /**
     * Legacy: wear that older versions kept on a media stack. Still registered so old stacks load; the Grinder loads a
     * worn item with what it had left and strips the component, so the rest stacks again.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MEDIA_WEAR = COMPONENTS.registerComponentType(
            "media_wear", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 9 : 0);
    }

    // ---- Machines: Mk1 (Age 1) to Mk4 (Age 4) ----
    public static final DeferredBlock<ProcessingMachineBlock> GRINDER_MK1 = machine("grinder_mk1", ProcessingMachineBlock.Kind.GRINDER, 1);
    public static final DeferredBlock<ProcessingMachineBlock> GRINDER_MK2 = machine("grinder_mk2", ProcessingMachineBlock.Kind.GRINDER, 2);
    public static final DeferredBlock<ProcessingMachineBlock> GRINDER_MK3 = machine("grinder_mk3", ProcessingMachineBlock.Kind.GRINDER, 3);
    public static final DeferredBlock<ProcessingMachineBlock> GRINDER_MK4 = machine("grinder_mk4", ProcessingMachineBlock.Kind.GRINDER, 4);
    public static final DeferredBlock<ProcessingMachineBlock> ELECTRIC_FURNACE_MK1 = machine("electric_furnace_mk1", ProcessingMachineBlock.Kind.ELECTRIC_FURNACE, 1);
    public static final DeferredBlock<ProcessingMachineBlock> ELECTRIC_FURNACE_MK2 = machine("electric_furnace_mk2", ProcessingMachineBlock.Kind.ELECTRIC_FURNACE, 2);
    public static final DeferredBlock<ProcessingMachineBlock> ELECTRIC_FURNACE_MK3 = machine("electric_furnace_mk3", ProcessingMachineBlock.Kind.ELECTRIC_FURNACE, 3);
    public static final DeferredBlock<ProcessingMachineBlock> ELECTRIC_FURNACE_MK4 = machine("electric_furnace_mk4", ProcessingMachineBlock.Kind.ELECTRIC_FURNACE, 4);

    public static final List<DeferredBlock<ProcessingMachineBlock>> GRINDERS = List.of(GRINDER_MK1, GRINDER_MK2, GRINDER_MK3, GRINDER_MK4);
    public static final List<DeferredBlock<ProcessingMachineBlock>> FURNACES = List.of(ELECTRIC_FURNACE_MK1, ELECTRIC_FURNACE_MK2,
            ELECTRIC_FURNACE_MK3, ELECTRIC_FURNACE_MK4);

    private static DeferredBlock<ProcessingMachineBlock> machine(String name, ProcessingMachineBlock.Kind kind, int tier) {
        DeferredBlock<ProcessingMachineBlock> block = BLOCKS.registerBlock(name, p -> new ProcessingMachineBlock(p, kind, tier), machine());
        Rarity rarity = tier >= 4 ? Rarity.RARE : tier >= 3 ? Rarity.UNCOMMON : Rarity.COMMON;
        ITEMS.registerItem(name, p -> new ProcessingMachineItem(block.get(), p.rarity(rarity)));
        return block;
    }

    public static DeferredItem<Item> item(DeferredBlock<ProcessingMachineBlock> block) {
        return DeferredItem.createItem(block.getId());
    }

    // ---- Items ----
    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust");
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust");
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust");
    public static final DeferredItem<Item> IRON_GRINDING_BALLS = ITEMS.registerSimpleItem("iron_grinding_balls");
    public static final DeferredItem<Item> FERROTHORIUM_GRINDING_BALLS = ITEMS.registerItem("ferrothorium_grinding_balls",
            p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> PYROSTEEL_GRINDING_BALLS = ITEMS.registerItem("pyrosteel_grinding_balls",
            p -> new Item(p.rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<Item> RESONANT_GRINDING_BALLS = ITEMS.registerItem("resonant_grinding_balls",
            p -> new Item(p.rarity(Rarity.RARE).fireResistant()));

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrinderBlockEntity>> GRINDER_BE =
            BLOCK_ENTITIES.register("grinder", () -> BlockEntityType.Builder.of(GrinderBlockEntity::new,
                    GRINDER_MK1.get(), GRINDER_MK2.get(), GRINDER_MK3.get(), GRINDER_MK4.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE_BE =
            BLOCK_ENTITIES.register("electric_furnace", () -> BlockEntityType.Builder.of(ElectricFurnaceBlockEntity::new,
                    ELECTRIC_FURNACE_MK1.get(), ELECTRIC_FURNACE_MK2.get(), ELECTRIC_FURNACE_MK3.get(), ELECTRIC_FURNACE_MK4.get()).build(null));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<GrinderMenu>> GRINDER_MENU =
            MENUS.register("grinder", () -> IMenuTypeExtension.create((id, inv, buf) -> new GrinderMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU =
            MENUS.register("electric_furnace", () -> IMenuTypeExtension.create((id, inv, buf) -> new ElectricFurnaceMenu(id, inv, buf.readBlockPos())));

    // ---- Recipes ----
    public static final DeferredHolder<RecipeType<?>, RecipeType<GrindingRecipe>> GRINDING_TYPE =
            RECIPE_TYPES.register("grinding", () -> RecipeType.simple(Robotica.id("grinding")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GrindingRecipe>> GRINDING_SERIALIZER =
            RECIPE_SERIALIZERS.register("grinding", GrindingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineUpgradeRecipe>> MACHINE_UPGRADE_SERIALIZER =
            RECIPE_SERIALIZERS.register("machine_upgrade", MachineUpgradeRecipe.Serializer::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        COMPONENTS.register(modBus);

        for (DeferredBlock<ProcessingMachineBlock> block : GRINDERS) RoboticaTab.add(item(block));
        for (DeferredBlock<ProcessingMachineBlock> block : FURNACES) RoboticaTab.add(item(block));
        RoboticaTab.add(IRON_DUST);
        RoboticaTab.add(GOLD_DUST);
        RoboticaTab.add(COPPER_DUST);
        RoboticaTab.add(IRON_GRINDING_BALLS);
        RoboticaTab.add(FERROTHORIUM_GRINDING_BALLS);
        RoboticaTab.add(PYROSTEEL_GRINDING_BALLS);
        RoboticaTab.add(RESONANT_GRINDING_BALLS);
    }
}
