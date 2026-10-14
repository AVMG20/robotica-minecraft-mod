package com.arno.robotica.automation;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.block.ChunkAnchorBlock;
import com.arno.robotica.automation.block.ExcavatorBlock;
import com.arno.robotica.automation.block.SproutBlock;
import com.arno.robotica.automation.block.StumpyBlock;
import com.arno.robotica.automation.block.SupplyCrateBlock;
import com.arno.robotica.automation.block.SurveyRigBlock;
import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.arno.robotica.automation.entity.FarmBotBlockEntity;
import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.automation.item.WorkerBlockItem;
import com.arno.robotica.automation.menu.AreaWorkerMenu;
import com.arno.robotica.automation.menu.ChunkAnchorMenu;
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

    private static DeferredBlock<ExcavatorBlock> excavator(String id, int tier) {
        return BLOCKS.registerBlock(id, p -> new ExcavatorBlock(p, tier), BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(3.5F, 8.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(ExcavatorBlock.LIT) ? 6 : 0));
    }

    private static DeferredBlock<SurveyRigBlock> surveyRig(String id, int tier) {
        return BLOCKS.registerBlock(id, p -> new SurveyRigBlock(p, tier), BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(4.0F, 10.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(SurveyRigBlock.LIT) ? 9 : 4));
    }

    private static DeferredBlock<SupplyCrateBlock> crate(String id, int tier) {
        return BLOCKS.registerBlock(id, p -> new SupplyCrateBlock(p, tier), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                .strength(2.0F + tier * 0.5F, 3.0F + tier * 3.0F).sound(SoundType.WOOD));
    }

    private static DeferredBlock<ChunkAnchorBlock> chunkAnchor(String id, int tier) {
        return BLOCKS.registerBlock(id, p -> new ChunkAnchorBlock(p, tier), BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(4.0F, 10.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> switch (s.getValue(ChunkAnchorBlock.MODE)) {
                    case ACTIVE -> 11;
                    case ALERT -> 5;
                    case IDLE -> 2;
                }));
    }

    // ---- blocks ----
    /** Stumpy and Sprout from before the robots walked: no item, they turn into the robot entity (farmbot package). */
    public static final DeferredBlock<StumpyBlock> STUMPY = BLOCKS.registerBlock("stumpy", StumpyBlock::new, robot(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<SproutBlock> SPROUT = BLOCKS.registerBlock("sprout", SproutBlock::new, robot(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<ExcavatorBlock> EXCAVATOR = excavator("excavator", 1);
    public static final DeferredBlock<ExcavatorBlock> EXCAVATOR_MK2 = excavator("excavator_mk2", 2);
    public static final DeferredBlock<ExcavatorBlock> EXCAVATOR_MK3 = excavator("excavator_mk3", 3);
    public static final DeferredBlock<ExcavatorBlock> EXCAVATOR_MK4 = excavator("excavator_mk4", 4);
    public static final DeferredBlock<SurveyRigBlock> SURVEY_RIG = surveyRig("survey_rig", 1);
    public static final DeferredBlock<SurveyRigBlock> SURVEY_RIG_MK2 = surveyRig("survey_rig_mk2", 2);
    public static final DeferredBlock<SurveyRigBlock> SURVEY_RIG_MK3 = surveyRig("survey_rig_mk3", 3);
    public static final DeferredBlock<SurveyRigBlock> SURVEY_RIG_MK4 = surveyRig("survey_rig_mk4", 4);
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE = crate("supply_crate", 1);
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE_MK2 = crate("supply_crate_mk2", 2);
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE_MK3 = crate("supply_crate_mk3", 3);
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE_MK4 = crate("supply_crate_mk4", 4);
    public static final DeferredBlock<ChunkAnchorBlock> CHUNK_ANCHOR = chunkAnchor("chunk_anchor", 1);
    public static final DeferredBlock<ChunkAnchorBlock> CHUNK_ANCHOR_MK2 = chunkAnchor("chunk_anchor_mk2", 2);
    public static final DeferredBlock<ChunkAnchorBlock> CHUNK_ANCHOR_MK3 = chunkAnchor("chunk_anchor_mk3", 3);

    // ---- items ----
    public static final DeferredItem<BlockItem> EXCAVATOR_ITEM = ITEMS.registerItem("excavator",
            p -> new WorkerBlockItem(EXCAVATOR.get(), p, 2, "tooltip.robotica.excavator"));
    public static final DeferredItem<BlockItem> EXCAVATOR_MK2_ITEM = ITEMS.registerItem("excavator_mk2",
            p -> new WorkerBlockItem(EXCAVATOR_MK2.get(), p, 2, "tooltip.robotica.excavator"));
    public static final DeferredItem<BlockItem> EXCAVATOR_MK3_ITEM = ITEMS.registerItem("excavator_mk3",
            p -> new WorkerBlockItem(EXCAVATOR_MK3.get(), p.rarity(Rarity.UNCOMMON), 3, "tooltip.robotica.excavator"));
    public static final DeferredItem<BlockItem> EXCAVATOR_MK4_ITEM = ITEMS.registerItem("excavator_mk4",
            p -> new WorkerBlockItem(EXCAVATOR_MK4.get(), p.rarity(Rarity.RARE), 4, "tooltip.robotica.excavator"));
    public static final DeferredItem<BlockItem> SURVEY_RIG_ITEM = ITEMS.registerItem("survey_rig",
            p -> new WorkerBlockItem(SURVEY_RIG.get(), p.rarity(Rarity.UNCOMMON), 2, "tooltip.robotica.survey_rig"));
    public static final DeferredItem<BlockItem> SURVEY_RIG_MK2_ITEM = ITEMS.registerItem("survey_rig_mk2",
            p -> new WorkerBlockItem(SURVEY_RIG_MK2.get(), p.rarity(Rarity.UNCOMMON), 2, "tooltip.robotica.survey_rig"));
    public static final DeferredItem<BlockItem> SURVEY_RIG_MK3_ITEM = ITEMS.registerItem("survey_rig_mk3",
            p -> new WorkerBlockItem(SURVEY_RIG_MK3.get(), p.rarity(Rarity.RARE), 3, "tooltip.robotica.survey_rig"));
    public static final DeferredItem<BlockItem> SURVEY_RIG_MK4_ITEM = ITEMS.registerItem("survey_rig_mk4",
            p -> new WorkerBlockItem(SURVEY_RIG_MK4.get(), p.rarity(Rarity.EPIC), 4, "tooltip.robotica.survey_rig"));
    public static final DeferredItem<BlockItem> CHUNK_ANCHOR_ITEM = ITEMS.registerItem("chunk_anchor",
            p -> new WorkerBlockItem(CHUNK_ANCHOR.get(), p, 1, "tooltip.robotica.chunk_anchor"));
    public static final DeferredItem<BlockItem> CHUNK_ANCHOR_MK2_ITEM = ITEMS.registerItem("chunk_anchor_mk2",
            p -> new WorkerBlockItem(CHUNK_ANCHOR_MK2.get(), p.rarity(Rarity.UNCOMMON), 2, "tooltip.robotica.chunk_anchor"));
    public static final DeferredItem<BlockItem> CHUNK_ANCHOR_MK3_ITEM = ITEMS.registerItem("chunk_anchor_mk3",
            p -> new WorkerBlockItem(CHUNK_ANCHOR_MK3.get(), p.rarity(Rarity.RARE), 3, "tooltip.robotica.chunk_anchor"));
    public static final DeferredItem<BlockItem> SUPPLY_CRATE_ITEM = ITEMS.registerItem("supply_crate",
            p -> new WorkerBlockItem(SUPPLY_CRATE.get(), p, 0, "tooltip.robotica.supply_crate"));
    public static final DeferredItem<BlockItem> SUPPLY_CRATE_MK2_ITEM = ITEMS.registerItem("supply_crate_mk2",
            p -> new WorkerBlockItem(SUPPLY_CRATE_MK2.get(), p, 0, "tooltip.robotica.supply_crate"));
    public static final DeferredItem<BlockItem> SUPPLY_CRATE_MK3_ITEM = ITEMS.registerItem("supply_crate_mk3",
            p -> new WorkerBlockItem(SUPPLY_CRATE_MK3.get(), p, 0, "tooltip.robotica.supply_crate"));
    public static final DeferredItem<BlockItem> SUPPLY_CRATE_MK4_ITEM = ITEMS.registerItem("supply_crate_mk4",
            p -> new WorkerBlockItem(SUPPLY_CRATE_MK4.get(), p.rarity(Rarity.UNCOMMON), 0, "tooltip.robotica.supply_crate"));


    // ---- block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmBotBlockEntity>> STUMPY_BE = BLOCK_ENTITIES.register("stumpy",
            () -> BlockEntityType.Builder.of(FarmBotBlockEntity::new, STUMPY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmBotBlockEntity>> SPROUT_BE = BLOCK_ENTITIES.register("sprout",
            () -> BlockEntityType.Builder.of(FarmBotBlockEntity::new, SPROUT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExcavatorBlockEntity>> EXCAVATOR_BE = BLOCK_ENTITIES.register("excavator",
            () -> BlockEntityType.Builder.of(ExcavatorBlockEntity::new, EXCAVATOR.get(), EXCAVATOR_MK2.get(), EXCAVATOR_MK3.get(),
                    EXCAVATOR_MK4.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurveyRigBlockEntity>> SURVEY_RIG_BE = BLOCK_ENTITIES.register("survey_rig",
            () -> BlockEntityType.Builder.of(SurveyRigBlockEntity::new, SURVEY_RIG.get(), SURVEY_RIG_MK2.get(), SURVEY_RIG_MK3.get(),
                    SURVEY_RIG_MK4.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE_BE = BLOCK_ENTITIES.register("supply_crate",
            () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new, SUPPLY_CRATE.get(), SUPPLY_CRATE_MK2.get(),
                    SUPPLY_CRATE_MK3.get(), SUPPLY_CRATE_MK4.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkAnchorBlockEntity>> CHUNK_ANCHOR_BE = BLOCK_ENTITIES.register("chunk_anchor",
            () -> BlockEntityType.Builder.of(ChunkAnchorBlockEntity::new, CHUNK_ANCHOR.get(), CHUNK_ANCHOR_MK2.get(), CHUNK_ANCHOR_MK3.get()).build(null));

    // ---- menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<AreaWorkerMenu>> WORKER_MENU = MENUS.register("area_worker",
            () -> IMenuTypeExtension.create(AreaWorkerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SurveyRigMenu>> SURVEY_RIG_MENU = MENUS.register("survey_rig",
            () -> IMenuTypeExtension.create(SurveyRigMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SupplyCrateMenu>> CRATE_MENU = MENUS.register("supply_crate",
            () -> IMenuTypeExtension.create(SupplyCrateMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ChunkAnchorMenu>> CHUNK_ANCHOR_MENU = MENUS.register("chunk_anchor",
            () -> IMenuTypeExtension.create(ChunkAnchorMenu::new));
}
