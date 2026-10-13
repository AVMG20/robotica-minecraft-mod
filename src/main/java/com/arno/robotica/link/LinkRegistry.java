package com.arno.robotica.link;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.link.block.LinkTier;
import com.arno.robotica.link.block.QuantumLinkBlock;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.menu.LinkMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the link module. Models, loot and recipes: scripts/data/link_data.py. */
public final class LinkRegistry {
    private LinkRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);

    /** Network, modes, priority, filter and sides of a mined Quantum Link. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> LINK_SETTINGS =
            COMPONENTS.registerComponentType("link_settings", b -> b.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(4.0F, 8.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 9).pushReaction(PushReaction.BLOCK);
    }

    // ---- Blocks ----
    public static final DeferredBlock<QuantumLinkBlock> LINK_MK1 = BLOCKS.registerBlock("quantum_link_mk1",
            p -> new QuantumLinkBlock(p, LinkTier.MK1), props(MapColor.COLOR_CYAN));
    public static final DeferredBlock<QuantumLinkBlock> LINK_MK2 = BLOCKS.registerBlock("quantum_link_mk2",
            p -> new QuantumLinkBlock(p, LinkTier.MK2), props(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<QuantumLinkBlock> LINK_MK3 = BLOCKS.registerBlock("quantum_link_mk3",
            p -> new QuantumLinkBlock(p, LinkTier.MK3), props(MapColor.COLOR_PURPLE));

    // ---- Items ----
    public static final DeferredItem<BlockItem> LINK_MK1_ITEM = ITEMS.registerSimpleBlockItem(LINK_MK1, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> LINK_MK2_ITEM = ITEMS.registerSimpleBlockItem(LINK_MK2, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> LINK_MK3_ITEM = ITEMS.registerSimpleBlockItem(LINK_MK3, new Item.Properties().rarity(Rarity.EPIC));

    // ---- Block entity: one type for every Mk (the Mk comes from the block) ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumLinkBlockEntity>> LINK_BE =
            BLOCK_ENTITIES.register("quantum_link", () -> BlockEntityType.Builder.of(QuantumLinkBlockEntity::new,
                    LINK_MK1.get(), LINK_MK2.get(), LINK_MK3.get()).build(null));

    // ---- Menu ----
    public static final DeferredHolder<MenuType<?>, MenuType<LinkMenu>> LINK_MENU =
            MENUS.register("quantum_link", () -> IMenuTypeExtension.create(LinkMenu::client));

    /** Registers everything and adds the items to the creative tab in Mk order. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);

        RoboticaTab.add(LINK_MK1_ITEM);
        RoboticaTab.add(LINK_MK2_ITEM);
        RoboticaTab.add(LINK_MK3_ITEM);
    }
}
