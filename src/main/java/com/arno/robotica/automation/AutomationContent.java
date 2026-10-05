package com.arno.robotica.automation;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.block.ExcavatorBlock;
import com.arno.robotica.automation.block.SproutBlock;
import com.arno.robotica.automation.block.StumpyBlock;
import com.arno.robotica.automation.block.SupplyCrateBlock;
import com.arno.robotica.automation.block.SurveyRigBlock;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.arno.robotica.automation.entity.SproutBlockEntity;
import com.arno.robotica.automation.entity.StumpyBlockEntity;
import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.automation.item.FarmKitItem;
import com.arno.robotica.automation.item.WorkerBlockItem;
import com.arno.robotica.automation.menu.AreaWorkerMenu;
import com.arno.robotica.automation.menu.SupplyCrateMenu;
import com.arno.robotica.automation.menu.SurveyRigMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Registered blocks, items, block entity types and menu types of the automation module. */
public final class AutomationContent {
    private AutomationContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);

    private static BlockBehaviour.Properties robot(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(SoundType.COPPER).noOcclusion();
    }

    // ---- blocks ----
    public static final DeferredBlock<StumpyBlock> STUMPY = BLOCKS.registerBlock("stumpy", StumpyBlock::new, robot(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<SproutBlock> SPROUT = BLOCKS.registerBlock("sprout", SproutBlock::new, robot(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<ExcavatorBlock> EXCAVATOR = BLOCKS.registerBlock("excavator", ExcavatorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<SurveyRigBlock> SURVEY_RIG = BLOCKS.registerBlock("survey_rig", SurveyRigBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> 4));
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE = BLOCKS.registerBlock("supply_crate", SupplyCrateBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD));

    // ---- items ----
    public static final DeferredItem<BlockItem> STUMPY_ITEM = ITEMS.registerItem("stumpy",
            p -> new WorkerBlockItem(STUMPY.get(), p, 0, "tooltip.robotica.stumpy"));
    public static final DeferredItem<BlockItem> SPROUT_ITEM = ITEMS.registerItem("sprout",
            p -> new WorkerBlockItem(SPROUT.get(), p, 0, "tooltip.robotica.sprout"));
    public static final DeferredItem<BlockItem> EXCAVATOR_ITEM = ITEMS.registerItem("excavator",
            p -> new WorkerBlockItem(EXCAVATOR.get(), p, 2, "tooltip.robotica.excavator"));
    public static final DeferredItem<BlockItem> SURVEY_RIG_ITEM = ITEMS.registerItem("survey_rig",
            p -> new WorkerBlockItem(SURVEY_RIG.get(), p.rarity(Rarity.UNCOMMON), 2, "tooltip.robotica.survey_rig"));
    public static final DeferredItem<BlockItem> SUPPLY_CRATE_ITEM = ITEMS.registerItem("supply_crate",
            p -> new WorkerBlockItem(SUPPLY_CRATE.get(), p, 0, "tooltip.robotica.supply_crate"));

    public static final DeferredItem<FarmKitItem> FARM_KIT_MK2 = ITEMS.registerItem("farm_kit_mk2",
            p -> new FarmKitItem(p.rarity(Rarity.COMMON), 2, 1));
    public static final DeferredItem<FarmKitItem> FARM_KIT_MK3 = ITEMS.registerItem("farm_kit_mk3",
            p -> new FarmKitItem(p.rarity(Rarity.UNCOMMON), 3, 2));
    public static final DeferredItem<FarmKitItem> FARM_KIT_MK4 = ITEMS.registerItem("farm_kit_mk4",
            p -> new FarmKitItem(p.rarity(Rarity.RARE), 4, 4));
    /** Index 0 = Mk2 kit. */
    public static final List<DeferredItem<FarmKitItem>> FARM_KITS = List.of(FARM_KIT_MK2, FARM_KIT_MK3, FARM_KIT_MK4);

    // ---- block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StumpyBlockEntity>> STUMPY_BE = BLOCK_ENTITIES.register("stumpy",
            () -> BlockEntityType.Builder.of(StumpyBlockEntity::new, STUMPY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SproutBlockEntity>> SPROUT_BE = BLOCK_ENTITIES.register("sprout",
            () -> BlockEntityType.Builder.of(SproutBlockEntity::new, SPROUT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExcavatorBlockEntity>> EXCAVATOR_BE = BLOCK_ENTITIES.register("excavator",
            () -> BlockEntityType.Builder.of(ExcavatorBlockEntity::new, EXCAVATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurveyRigBlockEntity>> SURVEY_RIG_BE = BLOCK_ENTITIES.register("survey_rig",
            () -> BlockEntityType.Builder.of(SurveyRigBlockEntity::new, SURVEY_RIG.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE_BE = BLOCK_ENTITIES.register("supply_crate",
            () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new, SUPPLY_CRATE.get()).build(null));

    // ---- menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<AreaWorkerMenu>> WORKER_MENU = MENUS.register("area_worker",
            () -> IMenuTypeExtension.create(AreaWorkerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SurveyRigMenu>> SURVEY_RIG_MENU = MENUS.register("survey_rig",
            () -> IMenuTypeExtension.create(SurveyRigMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SupplyCrateMenu>> CRATE_MENU = MENUS.register("supply_crate",
            () -> IMenuTypeExtension.create(SupplyCrateMenu::new));
}
