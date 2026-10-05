package com.arno.robotica.architect;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.block.ArchitectTableBlock;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.block.BuildingBlock;
import com.arno.robotica.architect.block.StyleBlock;
import com.arno.robotica.architect.block.StylePillarBlock;
import com.arno.robotica.architect.block.StyleWindowBlock;
import com.arno.robotica.architect.entity.BuilderDrone;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.menu.ArchitectMenu;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import com.arno.robotica.core.RoboticaTab;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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

/** Every registry entry of the architect module. Recipes and models are written by scripts/data/architect_*.py. */
public final class ArchitectRegistry {
    private ArchitectRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Matter stored in the table, kept when the table is picked up. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Matter>> MATTER_COMPONENT =
            COMPONENTS.registerComponentType("architect_matter", b -> b.persistent(Matter.CODEC).networkSynchronized(Matter.STREAM_CODEC));

    /** Queue, plot records, build cursor and settings of the table, kept when the table is picked up (energy is not). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> BUILD_STATE_COMPONENT =
            COMPONENTS.registerComponentType("architect_build", b -> b.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredBlock<ArchitectTableBlock> ARCHITECT_TABLE = BLOCKS.registerBlock("architect_table", ArchitectTableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> ARCHITECT_TABLE_ITEM = ITEMS.registerSimpleBlockItem(ARCHITECT_TABLE);

    private static final Map<BuildStyle, Map<Role, DeferredBlock<Block>>> STYLE_BLOCKS = new EnumMap<>(BuildStyle.class);
    private static final Map<BuildStyle, Map<Role, DeferredItem<BlockItem>>> STYLE_ITEMS = new EnumMap<>(BuildStyle.class);

    static {
        for (BuildStyle style : BuildStyle.values()) {
            Map<Role, DeferredBlock<Block>> blocks = new EnumMap<>(Role.class);
            Map<Role, DeferredItem<BlockItem>> items = new EnumMap<>(Role.class);
            for (Role role : Role.values()) {
                DeferredBlock<Block> block = BLOCKS.registerBlock(style.blockName(role), p -> create(role, p), properties(style, role));
                blocks.put(role, block);
                items.put(role, ITEMS.registerSimpleBlockItem(block));
            }
            STYLE_BLOCKS.put(style, blocks);
            STYLE_ITEMS.put(style, items);
        }
    }

    private static Block create(Role role, BlockBehaviour.Properties props) {
        return switch (role) {
            case PILLAR -> new StylePillarBlock(props);
            case WINDOW -> new StyleWindowBlock(props);
            default -> new StyleBlock(props);
        };
    }

    private static BlockBehaviour.Properties properties(BuildStyle style, Role role) {
        MapColor color = switch (style) {
            case TIMBERFRAME, STEEL_LAB -> role == Role.ROOF ? MapColor.STONE : MapColor.SAND;
            case COPPER_WORKS -> role == Role.ROOF ? MapColor.STONE : MapColor.QUARTZ;
            case NULL_SPIRE -> role == Role.ROOF ? MapColor.STONE : MapColor.SNOW;
        };
        SoundType sound = switch (style) {
            case TIMBERFRAME -> SoundType.STONE;
            case COPPER_WORKS -> SoundType.CALCITE;
            case STEEL_LAB -> SoundType.STONE;
            case NULL_SPIRE -> role == Role.LIGHT ? SoundType.AMETHYST : SoundType.POLISHED_DEEPSLATE;
        };
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(sound);
        if (role == Role.WINDOW) {
            p = BlockBehaviour.Properties.of().mapColor(color).strength(0.6F, 3.0F).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, pos, t) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false);
            if (style == BuildStyle.NULL_SPIRE) p = p.lightLevel(s -> 6);
        } else if (role == Role.LIGHT) {
            int level = style.lightLevel;
            p = p.strength(1.0F, 3.0F).lightLevel(s -> level);
        }
        return p;
    }

    public static DeferredBlock<Block> styleBlock(BuildStyle style, Role role) {
        return STYLE_BLOCKS.get(style).get(role);
    }

    public static DeferredItem<BlockItem> styleItem(BuildStyle style, Role role) {
        return STYLE_ITEMS.get(style).get(role);
    }

    /** All 24 style block names, for scripts and tests. */
    public static List<DeferredBlock<Block>> allStyleBlocks() {
        List<DeferredBlock<Block>> list = new ArrayList<>();
        for (BuildStyle style : BuildStyle.values()) list.addAll(STYLE_BLOCKS.get(style).values());
        return list;
    }

    public static boolean isBuildingBlock(BlockState state) {
        return state.getBlock() instanceof BuildingBlock;
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArchitectTableBlockEntity>> ARCHITECT_TABLE_BE =
            BLOCK_ENTITIES.register("architect_table", () -> BlockEntityType.Builder.of(ArchitectTableBlockEntity::new, ARCHITECT_TABLE.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ArchitectMenu>> ARCHITECT_MENU =
            MENUS.register("architect_table", () -> IMenuTypeExtension.create((id, inv, buf) -> new ArchitectMenu(id, inv, buf.readBlockPos())));

    public static final DeferredHolder<EntityType<?>, EntityType<BuilderDrone>> BUILDER_DRONE =
            ENTITIES.register("builder_drone", () -> EntityType.Builder.<BuilderDrone>of(BuilderDrone::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build(Robotica.MODID + ":builder_drone"));

    /** Registers everything and adds the items to the creative tab in progression order. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);

        RoboticaTab.add(ARCHITECT_TABLE_ITEM);
        for (BuildStyle style : BuildStyle.values()) {
            for (Role role : Role.values()) RoboticaTab.add(styleItem(style, role));
        }
    }
}
