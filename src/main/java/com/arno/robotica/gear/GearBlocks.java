package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.bench.TinkersBenchBlock;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks and menus of the gear module: the Tinker's Bench. */
public final class GearBlocks {
    private GearBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);

    public static final DeferredBlock<TinkersBenchBlock> TINKERS_BENCH = BLOCKS.registerBlock("tinkers_bench", TinkersBenchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> TINKERS_BENCH_ITEM = GearItems.ITEMS.registerSimpleBlockItem(TINKERS_BENCH);

    public static final DeferredHolder<MenuType<?>, MenuType<TinkersBenchMenu>> TINKERS_BENCH_MENU =
            MENUS.register("tinkers_bench", () -> new MenuType<>(TinkersBenchMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
