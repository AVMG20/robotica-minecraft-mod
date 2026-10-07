package com.arno.robotica.boss;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.block.BossAltarBlock;
import com.arno.robotica.boss.block.BossAltarBlockEntity;
import com.arno.robotica.boss.entity.ForgeTyrant;
import com.arno.robotica.boss.entity.MagmaGlob;
import com.arno.robotica.boss.entity.ScrapChunk;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.arno.robotica.boss.item.BossSummonItem;
import com.arno.robotica.boss.world.CinderForgePiece;
import com.arno.robotica.boss.world.CinderForgeStructure;
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

    // ---- Items (summons; registered first so the altar kinds can point at them) ----
    public static final DeferredItem<BossSummonItem> SIGNAL_FLARE = ITEMS.registerItem("signal_flare",
            p -> new BossSummonItem("signal_flare", p.stacksTo(16)));
    public static final DeferredItem<BossSummonItem> IGNITION_CHARGE = ITEMS.registerItem("ignition_charge",
            p -> new BossSummonItem("ignition_charge", p.stacksTo(16).fireResistant()));

    // ---- Blocks ----
    public static final DeferredBlock<BossAltarBlock> COLOSSUS_ALTAR = BLOCKS.registerBlock("colossus_altar",
            p -> new BossAltarBlock(new BossAltarBlock.Kind("colossus", BossRegistry.SCRAP_COLOSSUS, SIGNAL_FLARE,
                    new int[]{0xE8742A, 0xC9302A}, 0xFFD080), p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(6.0F, 1200.0F).sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(BossAltarBlock.READY) ? 10 : 3)
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<BossAltarBlock> FORGE_ALTAR = BLOCKS.registerBlock("forge_altar",
            p -> new BossAltarBlock(new BossAltarBlock.Kind("tyrant", BossRegistry.FORGE_TYRANT, IGNITION_CHARGE,
                    new int[]{0xFF7A1E, 0xFFC23A}, 0xFFF2B0), p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(8.0F, 1200.0F).sound(SoundType.GILDED_BLACKSTONE)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(BossAltarBlock.READY) ? 12 : 4)
                    .pushReaction(PushReaction.BLOCK));

    // ---- Block items ----
    public static final DeferredItem<BlockItem> COLOSSUS_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(COLOSSUS_ALTAR, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> FORGE_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(FORGE_ALTAR, new Item.Properties().rarity(Rarity.RARE).fireResistant());

    // ---- Block entities ----
    /** One block entity type for every boss altar (kept under the old id so placed Colossus Altars load). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BossAltarBlockEntity>> BOSS_ALTAR_BE =
            BLOCK_ENTITIES.register("colossus_altar", () -> BlockEntityType.Builder.of(BossAltarBlockEntity::new, COLOSSUS_ALTAR.get(), FORGE_ALTAR.get()).build(null));

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

    public static final DeferredHolder<EntityType<?>, EntityType<ForgeTyrant>> FORGE_TYRANT =
            ENTITIES.register("forge_tyrant", () -> EntityType.Builder.<ForgeTyrant>of(ForgeTyrant::new, MobCategory.MONSTER)
                    .sized(2.2F, 2.9F).eyeHeight(2.2F).fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":forge_tyrant"));
    public static final DeferredHolder<EntityType<?>, EntityType<MagmaGlob>> MAGMA_GLOB =
            ENTITIES.register("magma_glob", () -> EntityType.Builder.<MagmaGlob>of(MagmaGlob::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).fireImmune().clientTrackingRange(6).updateInterval(2)
                    .build(Robotica.MODID + ":magma_glob"));

    // ---- World gen ----
    public static final DeferredHolder<StructureType<?>, StructureType<RustedFoundryStructure>> RUSTED_FOUNDRY =
            STRUCTURE_TYPES.register("rusted_foundry", () -> () -> RustedFoundryStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUSTED_FOUNDRY_PIECE =
            STRUCTURE_PIECES.register("rusted_foundry", () -> (StructurePieceType.StructureTemplateType) RustedFoundryPiece::new);
    public static final DeferredHolder<StructureType<?>, StructureType<CinderForgeStructure>> CINDER_FORGE =
            STRUCTURE_TYPES.register("cinder_forge", () -> () -> CinderForgeStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> CINDER_FORGE_PIECE =
            STRUCTURE_PIECES.register("cinder_forge", () -> (StructurePieceType.StructureTemplateType) CinderForgePiece::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        RoboticaTab.add(SIGNAL_FLARE);
        RoboticaTab.add(COLOSSUS_ALTAR_ITEM);
        RoboticaTab.add(IGNITION_CHARGE);
        RoboticaTab.add(FORGE_ALTAR_ITEM);
    }
}
