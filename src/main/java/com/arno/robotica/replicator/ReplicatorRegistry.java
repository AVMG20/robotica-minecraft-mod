package com.arno.robotica.replicator;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.replicator.block.ReplicatorControllerBlock;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.energy.block.ControllerBlock;
import com.arno.robotica.energy.block.FramedPartBlock;
import com.arno.robotica.energy.block.PartBlock;
import com.arno.robotica.energy.block.PortBlock;
import com.arno.robotica.energy.block.StructureGlassBlock;
import com.arno.robotica.replicator.farm.FarmControllerBlockEntity;
import com.arno.robotica.replicator.farm.FarmMenu;
import com.arno.robotica.replicator.item.EssenceVialItem;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the replicator module (Mob Replicator and the mob farms). Recipes: scripts/data/replicator_recipes.py. */
public final class ReplicatorRegistry {
    private ReplicatorRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    // ---- Data components of the Essence Vial ----
    /** Registry id of the entity type the vial is bound to. Absent on an empty vial. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> ESSENCE_TYPE =
            COMPONENTS.registerComponentType("essence_type", b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    /** Samples taken so far, 0 to 8. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ESSENCE_SAMPLES =
            COMPONENTS.registerComponentType("essence_samples", b -> b.persistent(Codec.intRange(0, 64)).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---- Blocks ----
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.5F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<ReplicatorControllerBlock> REPLICATOR_CONTROLLER = BLOCKS.registerBlock("replicator_controller",
            ReplicatorControllerBlock::new,
            metal().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 12 : s.getValue(ReplicatorControllerBlock.FORMED) ? 5 : 0));
    // frame and glass carry the formed look in their state: pistons may not move them out of the cube
    public static final DeferredBlock<FramedPartBlock> REPLICATOR_FRAME = BLOCKS.registerBlock("replicator_frame", FramedPartBlock::new,
            metal().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<StructureGlassBlock> REPLICATOR_GLASS = BLOCKS.registerBlock("replicator_glass", StructureGlassBlock::new,
            BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK).mapColor(MapColor.COLOR_CYAN).strength(2.5F, 8.0F).sound(SoundType.GLASS).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> 3)
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

    // ---- Mob farms (5x5x5): shared shell, rams and ports, a matrix and a controller per farm ----
    private static BlockBehaviour.Properties farmCasing() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(4.0F, 12.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<FramedPartBlock> FARM_CASING = BLOCKS.registerBlock("farm_casing", FramedPartBlock::new,
            farmCasing().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<StructureGlassBlock> FARM_GLASS = BLOCKS.registerBlock("farm_glass", StructureGlassBlock::new,
            BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK).mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3.0F, 12.0F)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final DeferredBlock<PortBlock> FARM_PORT = BLOCKS.registerBlock("farm_port",
            p -> new PortBlock(p, PortBlock.Kind.FARM), farmCasing());
    public static final DeferredBlock<PartBlock> IMPACT_RAM = BLOCKS.registerBlock("impact_ram", PartBlock::new,
            farmCasing().mapColor(MapColor.COLOR_RED).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<PartBlock> GOLEM_MATRIX = BLOCKS.registerBlock("golem_matrix", PartBlock::new,
            farmCasing().mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 5).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<PartBlock> WITHER_MATRIX = BLOCKS.registerBlock("wither_matrix", PartBlock::new,
            farmCasing().mapColor(MapColor.COLOR_BLACK).lightLevel(s -> 5).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<ControllerBlock> IRON_GOLEM_FARMER = BLOCKS.registerBlock("iron_golem_farmer",
            p -> new ControllerBlock(p, ReplicatorRegistry.FARM_BE), farmController(MapColor.COLOR_GRAY));
    public static final DeferredBlock<ControllerBlock> WITHER_FARMER = BLOCKS.registerBlock("wither_farmer",
            p -> new ControllerBlock(p, ReplicatorRegistry.FARM_BE), farmController(MapColor.COLOR_BLACK));

    private static BlockBehaviour.Properties farmController(MapColor color) {
        return farmCasing().mapColor(color).lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 7 : s.getValue(ControllerBlock.FORMED) ? 3 : 0);
    }

    // ---- Items ----
    public static final DeferredItem<EssenceVialItem> ESSENCE_VIAL = ITEMS.registerItem("essence_vial",
            p -> new EssenceVialItem(p.stacksTo(16)));
    public static final DeferredItem<BlockItem> REPLICATOR_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(REPLICATOR_CONTROLLER);
    public static final DeferredItem<BlockItem> REPLICATOR_FRAME_ITEM = ITEMS.registerSimpleBlockItem(REPLICATOR_FRAME);
    public static final DeferredItem<BlockItem> REPLICATOR_GLASS_ITEM = ITEMS.registerSimpleBlockItem(REPLICATOR_GLASS);
    public static final DeferredItem<BlockItem> FARM_CASING_ITEM = ITEMS.registerSimpleBlockItem(FARM_CASING);
    public static final DeferredItem<BlockItem> FARM_GLASS_ITEM = ITEMS.registerSimpleBlockItem(FARM_GLASS);
    public static final DeferredItem<BlockItem> FARM_PORT_ITEM = ITEMS.registerSimpleBlockItem(FARM_PORT);
    public static final DeferredItem<BlockItem> IMPACT_RAM_ITEM = ITEMS.registerSimpleBlockItem(IMPACT_RAM);
    public static final DeferredItem<BlockItem> GOLEM_MATRIX_ITEM = ITEMS.registerSimpleBlockItem(GOLEM_MATRIX);
    public static final DeferredItem<BlockItem> IRON_GOLEM_FARMER_ITEM = ITEMS.registerSimpleBlockItem(IRON_GOLEM_FARMER);
    public static final DeferredItem<BlockItem> WITHER_MATRIX_ITEM = ITEMS.registerItem("wither_matrix",
            p -> new BlockItem(WITHER_MATRIX.get(), p.rarity(Rarity.EPIC)));
    public static final DeferredItem<BlockItem> WITHER_FARMER_ITEM = ITEMS.registerItem("wither_farmer",
            p -> new BlockItem(WITHER_FARMER.get(), p.rarity(Rarity.EPIC)));

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReplicatorControllerBlockEntity>> CONTROLLER_BE =
            BLOCK_ENTITIES.register("replicator_controller",
                    () -> BlockEntityType.Builder.of(ReplicatorControllerBlockEntity::new, REPLICATOR_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmControllerBlockEntity>> FARM_BE =
            BLOCK_ENTITIES.register("mob_farm_controller",
                    () -> BlockEntityType.Builder.of(FarmControllerBlockEntity::new, IRON_GOLEM_FARMER.get(), WITHER_FARMER.get()).build(null));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<ReplicatorMenu>> REPLICATOR_MENU =
            MENUS.register("replicator", () -> IMenuTypeExtension.create((id, inv, buf) -> new ReplicatorMenu(id, inv, buf.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<FarmMenu>> FARM_MENU =
            MENUS.register("mob_farm", () -> IMenuTypeExtension.create((id, inv, buf) -> new FarmMenu(id, inv, buf.readBlockPos())));

    /** Registers everything and adds the items to the creative tab in progression order. */
    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);

        RoboticaTab.add(ESSENCE_VIAL);
        RoboticaTab.add(REPLICATOR_FRAME_ITEM);
        RoboticaTab.add(REPLICATOR_GLASS_ITEM);
        RoboticaTab.add(REPLICATOR_CONTROLLER_ITEM);
        RoboticaTab.add(FARM_CASING_ITEM);
        RoboticaTab.add(FARM_GLASS_ITEM);
        RoboticaTab.add(FARM_PORT_ITEM);
        RoboticaTab.add(IMPACT_RAM_ITEM);
        RoboticaTab.add(GOLEM_MATRIX_ITEM);
        RoboticaTab.add(IRON_GOLEM_FARMER_ITEM);
        RoboticaTab.add(WITHER_MATRIX_ITEM);
        RoboticaTab.add(WITHER_FARMER_ITEM);
    }
}
