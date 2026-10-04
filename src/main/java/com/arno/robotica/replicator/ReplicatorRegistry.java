package com.arno.robotica.replicator;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.replicator.block.ReplicatorControllerBlock;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.block.ReplicatorGlassBlock;
import com.arno.robotica.replicator.item.EssenceVialItem;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
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

/** Every registry entry of the replicator module. Recipes: scripts/data/replicator_recipes.py. */
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
    public static final DeferredBlock<Block> REPLICATOR_FRAME = BLOCKS.registerBlock("replicator_frame", Block::new, metal());
    public static final DeferredBlock<ReplicatorGlassBlock> REPLICATOR_GLASS = BLOCKS.registerBlock("replicator_glass", ReplicatorGlassBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2.5F, 8.0F).sound(SoundType.GLASS).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> 3)
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

    // ---- Items ----
    public static final DeferredItem<EssenceVialItem> ESSENCE_VIAL = ITEMS.registerItem("essence_vial",
            p -> new EssenceVialItem(p.stacksTo(16)));
    public static final DeferredItem<BlockItem> REPLICATOR_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(REPLICATOR_CONTROLLER);
    public static final DeferredItem<BlockItem> REPLICATOR_FRAME_ITEM = ITEMS.registerSimpleBlockItem(REPLICATOR_FRAME);
    public static final DeferredItem<BlockItem> REPLICATOR_GLASS_ITEM = ITEMS.registerSimpleBlockItem(REPLICATOR_GLASS);

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReplicatorControllerBlockEntity>> CONTROLLER_BE =
            BLOCK_ENTITIES.register("replicator_controller",
                    () -> BlockEntityType.Builder.of(ReplicatorControllerBlockEntity::new, REPLICATOR_CONTROLLER.get()).build(null));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<ReplicatorMenu>> REPLICATOR_MENU =
            MENUS.register("replicator", () -> IMenuTypeExtension.create((id, inv, buf) -> new ReplicatorMenu(id, inv, buf.readBlockPos())));

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
    }
}
