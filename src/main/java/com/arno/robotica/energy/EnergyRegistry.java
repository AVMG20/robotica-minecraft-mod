package com.arno.robotica.energy;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.energy.block.BankControllerBlockEntity;
import com.arno.robotica.energy.block.CapacitorBlock;
import com.arno.robotica.energy.block.ControllerBlock;
import com.arno.robotica.energy.block.AcceleratorSegmentBlock;
import com.arno.robotica.energy.block.ColliderBlockEntity;
import com.arno.robotica.energy.block.CoreReactorBlockEntity;
import com.arno.robotica.energy.block.PartBlock;
import com.arno.robotica.energy.block.PortBlock;
import com.arno.robotica.energy.block.PortBlockEntity;
import com.arno.robotica.energy.block.SpireBlockEntity;
import com.arno.robotica.energy.block.SpireCrownBlock;
import com.arno.robotica.energy.block.StructureGlassBlock;
import com.arno.robotica.energy.menu.BankMenu;
import com.arno.robotica.energy.menu.ColliderMenu;
import com.arno.robotica.energy.menu.CoreReactorMenu;
import com.arno.robotica.energy.menu.SpireMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Every registry entry of the energy module. Recipes: scripts/data/energy_recipes.py, models: scripts/data/energy_models.py. */
public final class EnergyRegistry {
    private EnergyRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    // ---- Data components ----
    /** Ticks a boss core has burned in a Core Reactor; it breaks at its life (data map robotica:reactor_core). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CORE_WEAR =
            COMPONENTS.registerComponentType("core_wear", b -> b.persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** FE a Capacitor Bank Controller keeps when picked up (a long: banks hold far more than an int). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> BANK_ENERGY =
            COMPONENTS.registerComponentType("bank_energy", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    // ---- Block properties ----
    private static BlockBehaviour.Properties casing(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(4.0F, 12.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties glass(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 12.0F).sound(SoundType.GLASS).requiresCorrectToolForDrops()
                .noOcclusion().isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    private static BlockBehaviour.Properties controller(MapColor color) {
        // Light stays at 7 or less: brighter light next to a Core Reactor would melt its ice stabilizers.
        return casing(color).lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 7 : s.getValue(ControllerBlock.FORMED) ? 3 : 0);
    }

    // ---- Capacitor Bank ----
    public static final DeferredBlock<PartBlock> BANK_CASING = block("bank_casing", PartBlock::new, casing(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<StructureGlassBlock> BANK_GLASS = block("bank_glass", StructureGlassBlock::new, glass(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<ControllerBlock> BANK_CONTROLLER = block("bank_controller",
            p -> new ControllerBlock(p, EnergyRegistry.BANK_BE), controller(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<PortBlock.Bank> BANK_PORT = block("bank_port", PortBlock.Bank::new, casing(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<CapacitorBlock> CAPACITOR_COPPER = capacitor("capacitor_copper", CapacitorBlock.Kind.CAPACITOR, 0);
    public static final DeferredBlock<CapacitorBlock> CAPACITOR_REDSTONE = capacitor("capacitor_redstone", CapacitorBlock.Kind.CAPACITOR, 1);
    public static final DeferredBlock<CapacitorBlock> CAPACITOR_ENDER = capacitor("capacitor_ender", CapacitorBlock.Kind.CAPACITOR, 2);
    public static final DeferredBlock<CapacitorBlock> CAPACITOR_RESONANT = capacitor("capacitor_resonant", CapacitorBlock.Kind.CAPACITOR, 3);
    public static final DeferredBlock<CapacitorBlock> TRANSFER_COIL_BASIC = capacitor("transfer_coil_basic", CapacitorBlock.Kind.COIL, 0);
    public static final DeferredBlock<CapacitorBlock> TRANSFER_COIL_ADVANCED = capacitor("transfer_coil_advanced", CapacitorBlock.Kind.COIL, 1);
    public static final DeferredBlock<CapacitorBlock> TRANSFER_COIL_ELITE = capacitor("transfer_coil_elite", CapacitorBlock.Kind.COIL, 2);

    // ---- Tesla Spire ----
    public static final DeferredBlock<ControllerBlock> SPIRE_BASE = block("spire_base",
            p -> new ControllerBlock(p, EnergyRegistry.SPIRE_BE), controller(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<SpireCrownBlock> SPIRE_CROWN = block("spire_crown", SpireCrownBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.0F, 12.0F).sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 12 : 0));

    // ---- Core Reactor (block ids of the 0.6 Fission Reactor); modulators are open cages, so the core shows through ----
    public static final DeferredBlock<PartBlock> REACTOR_CASING = block("reactor_casing", PartBlock::new, casing(MapColor.COLOR_GRAY));
    public static final DeferredBlock<StructureGlassBlock> REACTOR_GLASS = block("reactor_glass", StructureGlassBlock::new, glass(MapColor.COLOR_LIGHT_GREEN));
    public static final DeferredBlock<ControllerBlock> REACTOR_CONTROLLER = block("reactor_controller",
            p -> new ControllerBlock(p, EnergyRegistry.REACTOR_BE), controller(MapColor.COLOR_GRAY));
    public static final DeferredBlock<PortBlock> REACTOR_POWER_PORT = block("reactor_power_port",
            p -> new PortBlock(p, PortBlock.Kind.REACTOR_POWER), casing(MapColor.COLOR_GRAY));
    public static final DeferredBlock<PortBlock> REACTOR_ACCESS_PORT = block("reactor_access_port",
            p -> new PortBlock(p, PortBlock.Kind.REACTOR_ACCESS), casing(MapColor.COLOR_GRAY));
    public static final DeferredBlock<PartBlock> FLUX_AMPLIFIER = block("flux_amplifier", PartBlock::new,
            casing(MapColor.COLOR_RED).lightLevel(s -> 4).noOcclusion());
    public static final DeferredBlock<PartBlock> PYRO_AMPLIFIER = block("pyro_amplifier", PartBlock::new,
            casing(MapColor.COLOR_ORANGE).lightLevel(s -> 6).noOcclusion());
    public static final DeferredBlock<PartBlock> RESONANT_AMPLIFIER = block("resonant_amplifier", PartBlock::new,
            casing(MapColor.COLOR_CYAN).lightLevel(s -> 8).noOcclusion());
    public static final DeferredBlock<PartBlock> GRAPHITE_DAMPER = block("graphite_damper", PartBlock::new, casing(MapColor.COLOR_BLACK).noOcclusion());
    public static final DeferredBlock<PartBlock> CRYO_COOLANT = block("cryo_coolant", PartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(2.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F)
                    .requiresCorrectToolForDrops());

    // ---- Ring Collider ----
    public static final DeferredBlock<ControllerBlock> COLLIDER_CONTROLLER = block("collider_controller",
            p -> new ControllerBlock(p, EnergyRegistry.COLLIDER_BE), controller(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<AcceleratorSegmentBlock> ACCELERATOR_SEGMENT = block("accelerator_segment",
            p -> new AcceleratorSegmentBlock(p, 1), casing(MapColor.COLOR_BLUE).noOcclusion());
    public static final DeferredBlock<AcceleratorSegmentBlock> RESONANT_SEGMENT = block("resonant_segment",
            p -> new AcceleratorSegmentBlock(p, 2), casing(MapColor.COLOR_PURPLE).noOcclusion());
    public static final DeferredItem<com.arno.robotica.core.item.PartItem> STRANGE_MATTER = ITEMS.registerItem("strange_matter",
            p -> new com.arno.robotica.core.item.PartItem(p.rarity(Rarity.EPIC), 4));

    static {
        TAB_ORDER.add(STRANGE_MATTER);
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    private static DeferredBlock<CapacitorBlock> capacitor(String name, CapacitorBlock.Kind kind, int tier) {
        MapColor color = tier == 0 ? MapColor.COLOR_ORANGE : tier == 1 ? MapColor.COLOR_RED : MapColor.COLOR_CYAN;
        DeferredBlock<CapacitorBlock> block = BLOCKS.registerBlock(name, p -> new CapacitorBlock(p, kind, tier), casing(color));
        Rarity rarity = tier >= 2 ? Rarity.RARE : tier == 1 ? Rarity.UNCOMMON : Rarity.COMMON;
        TAB_ORDER.add(ITEMS.registerItem(name, p -> new BlockItem(block.get(), p.rarity(rarity))));
        return block;
    }

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoreReactorBlockEntity>> REACTOR_BE =
            BLOCK_ENTITIES.register("reactor_controller", () -> BlockEntityType.Builder.of(CoreReactorBlockEntity::new, REACTOR_CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankControllerBlockEntity>> BANK_BE =
            BLOCK_ENTITIES.register("bank_controller", () -> BlockEntityType.Builder.of(BankControllerBlockEntity::new, BANK_CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpireBlockEntity>> SPIRE_BE =
            BLOCK_ENTITIES.register("spire_base", () -> BlockEntityType.Builder.of(SpireBlockEntity::new, SPIRE_BASE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColliderBlockEntity>> COLLIDER_BE =
            BLOCK_ENTITIES.register("collider_controller", () -> BlockEntityType.Builder.of(ColliderBlockEntity::new, COLLIDER_CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortBlockEntity>> PORT_BE =
            BLOCK_ENTITIES.register("energy_port", () -> BlockEntityType.Builder.of(PortBlockEntity::new,
                    REACTOR_POWER_PORT.get(), REACTOR_ACCESS_PORT.get(), BANK_PORT.get()).build(null));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<CoreReactorMenu>> REACTOR_MENU =
            MENUS.register("reactor_controller", () -> IMenuTypeExtension.create((id, inv, buf) -> new CoreReactorMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<BankMenu>> BANK_MENU =
            MENUS.register("bank_controller", () -> IMenuTypeExtension.create((id, inv, buf) -> new BankMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<SpireMenu>> SPIRE_MENU =
            MENUS.register("spire_base", () -> IMenuTypeExtension.create((id, inv, buf) -> new SpireMenu(id, inv, buf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<ColliderMenu>> COLLIDER_MENU =
            MENUS.register("collider_controller", () -> IMenuTypeExtension.create((id, inv, buf) -> new ColliderMenu(id, inv, buf.readBlockPos())));

    /** 0.6 ids that are gone: worlds keep the blocks as their replacements. */
    private static void aliases() {
        alias("reactor_fuel_rod", "flux_amplifier", true);
        alias("fusion_controller", "collider_controller", true);
        alias("fusion_coil", "resonant_segment", true);
        alias("fusion_casing", "reactor_casing", true);
        BLOCK_ENTITIES.addAlias(Robotica.id("fusion_controller"), Robotica.id("collider_controller"));
    }

    private static void alias(String from, String to, boolean item) {
        BLOCKS.addAlias(Robotica.id(from), Robotica.id(to));
        if (item) ITEMS.addAlias(Robotica.id(from), Robotica.id(to));
    }

    /** Registers everything and adds the items to the creative tab in build order. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        aliases();
        TAB_ORDER.forEach(RoboticaTab::add);
    }
}
