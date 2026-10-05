package com.arno.robotica.boss;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.block.ColossusAltarBlock;
import com.arno.robotica.boss.block.ColossusAltarBlockEntity;
import com.arno.robotica.boss.entity.ScrapChunk;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.arno.robotica.boss.item.SignalFlareItem;
import com.arno.robotica.boss.world.RustedFoundryPiece;
import com.arno.robotica.boss.world.RustedFoundryStructure;
import com.arno.robotica.core.RoboticaTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the boss module. Recipes, loot and worldgen data: scripts/data/boss_*.py. */
public final class BossRegistry {
    private BossRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Robotica.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Robotica.MODID);

    // ---- Blocks ----
    public static final DeferredBlock<ColossusAltarBlock> COLOSSUS_ALTAR = BLOCKS.registerBlock("colossus_altar", ColossusAltarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(6.0F, 1200.0F).sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(ColossusAltarBlock.READY) ? 10 : 3)
                    .pushReaction(PushReaction.BLOCK));

    // ---- Items ----
    public static final DeferredItem<BlockItem> COLOSSUS_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(COLOSSUS_ALTAR, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SignalFlareItem> SIGNAL_FLARE = ITEMS.registerItem("signal_flare",
            p -> new SignalFlareItem(p.stacksTo(16)));

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColossusAltarBlockEntity>> COLOSSUS_ALTAR_BE =
            BLOCK_ENTITIES.register("colossus_altar", () -> BlockEntityType.Builder.of(ColossusAltarBlockEntity::new, COLOSSUS_ALTAR.get()).build(null));

    // ---- Entities ----
    public static final DeferredHolder<EntityType<?>, EntityType<ScrapColossus>> SCRAP_COLOSSUS =
            ENTITIES.register("scrap_colossus", () -> EntityType.Builder.<ScrapColossus>of(ScrapColossus::new, MobCategory.MONSTER)
                    .sized(2.0F, 3.0F).eyeHeight(2.55F).fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":scrap_colossus"));
    public static final DeferredHolder<EntityType<?>, EntityType<ScrapDrone>> SCRAP_DRONE =
            ENTITIES.register("scrap_drone", () -> EntityType.Builder.<ScrapDrone>of(ScrapDrone::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.5F).eyeHeight(0.25F).fireImmune().clientTrackingRange(8).updateInterval(2)
                    .build(Robotica.MODID + ":scrap_drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<ScrapChunk>> SCRAP_CHUNK =
            ENTITIES.register("scrap_chunk", () -> EntityType.Builder.<ScrapChunk>of(ScrapChunk::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(6).updateInterval(2)
                    .build(Robotica.MODID + ":scrap_chunk"));

    // ---- World gen ----
    public static final DeferredHolder<StructureType<?>, StructureType<RustedFoundryStructure>> RUSTED_FOUNDRY =
            STRUCTURE_TYPES.register("rusted_foundry", () -> () -> RustedFoundryStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUSTED_FOUNDRY_PIECE =
            STRUCTURE_PIECES.register("rusted_foundry", () -> (StructurePieceType.StructureTemplateType) RustedFoundryPiece::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        RoboticaTab.add(SIGNAL_FLARE);
        RoboticaTab.add(COLOSSUS_ALTAR_ITEM);
    }
}
