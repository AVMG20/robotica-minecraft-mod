package com.arno.robotica.logistics;

import com.arno.robotica.Robotica;
import com.arno.robotica.logistics.pipe.ItemPipeBlock;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered blocks, items and block entity type of the logistics module. */
public final class LogisticsContent {
    private LogisticsContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Robotica.MODID);

    public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE = pipe("item_pipe", 1);
    public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE_MK2 = pipe("item_pipe_mk2", 2);

    public static final DeferredItem<BlockItem> ITEM_PIPE_ITEM = ITEMS.registerSimpleBlockItem(ITEM_PIPE);
    public static final DeferredItem<BlockItem> ITEM_PIPE_MK2_ITEM = ITEMS.registerSimpleBlockItem(ITEM_PIPE_MK2);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE_BE = BLOCK_ENTITIES.register("item_pipe",
            () -> BlockEntityType.Builder.of(ItemPipeBlockEntity::new, ITEM_PIPE.get(), ITEM_PIPE_MK2.get()).build(null));

    private static DeferredBlock<ItemPipeBlock> pipe(String name, int tier) {
        return BLOCKS.registerBlock(name, p -> new ItemPipeBlock(p, tier),
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 6.0F).sound(SoundType.COPPER).noOcclusion());
    }
}
