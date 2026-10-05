package com.arno.robotica.storage;

import com.arno.robotica.Robotica;
import com.arno.robotica.storage.block.StorageTerminalBlock;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.item.StorageExpansionItem;
import com.arno.robotica.storage.menu.StorageMenu;
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

/** Registered blocks, items, block entity type and menu type of the storage module. */
public final class StorageContent {
    private StorageContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);

    public static final DeferredBlock<StorageTerminalBlock> TERMINAL = BLOCKS.registerBlock("storage_terminal", StorageTerminalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F).sound(SoundType.METAL)
                    .lightLevel(s -> s.getValue(StorageTerminalBlock.LIT) ? 5 : 0));

    public static final DeferredItem<BlockItem> TERMINAL_ITEM = ITEMS.registerItem("storage_terminal",
            p -> new StorageTerminalBlock.TerminalItem(TERMINAL.get(), p));

    public static final DeferredItem<StorageExpansionItem> EXPANSION_MK1 = ITEMS.registerItem("storage_expansion_mk1",
            p -> new StorageExpansionItem(p, 1, StorageTerminalBlockEntity.EXPANSION_SLOTS[0]));
    public static final DeferredItem<StorageExpansionItem> EXPANSION_MK2 = ITEMS.registerItem("storage_expansion_mk2",
            p -> new StorageExpansionItem(p.rarity(Rarity.UNCOMMON), 2, StorageTerminalBlockEntity.EXPANSION_SLOTS[1]));
    public static final DeferredItem<StorageExpansionItem> EXPANSION_MK3 = ITEMS.registerItem("storage_expansion_mk3",
            p -> new StorageExpansionItem(p.rarity(Rarity.RARE), 3, StorageTerminalBlockEntity.EXPANSION_SLOTS[2]));
    public static final List<DeferredItem<StorageExpansionItem>> EXPANSIONS = List.of(EXPANSION_MK1, EXPANSION_MK2, EXPANSION_MK3);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageTerminalBlockEntity>> TERMINAL_BE = BLOCK_ENTITIES.register("storage_terminal",
            () -> BlockEntityType.Builder.of(StorageTerminalBlockEntity::new, TERMINAL.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<StorageMenu>> MENU = MENUS.register("storage_terminal",
            () -> IMenuTypeExtension.create(StorageMenu::new));
}
