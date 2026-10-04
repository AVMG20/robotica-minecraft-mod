package com.arno.robotica.core;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The single Robotica creative tab. Modules call {@link #add} during init; entries show in call order.
 * Energy items are listed twice: empty and fully charged.
 */
public final class RoboticaTab {
    private RoboticaTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Robotica.MODID);
    private static final List<Supplier<? extends ItemLike>> ENTRIES = new ArrayList<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.robotica"))
            .icon(() -> new ItemStack(CoreItems.COPPER_GEAR.get()))
            .displayItems((params, output) -> {
                for (Supplier<? extends ItemLike> entry : ENTRIES) {
                    ItemStack stack = new ItemStack(entry.get());
                    output.accept(stack);
                    if (stack.getItem() instanceof EnergyItem) {
                        ItemStack full = stack.copy();
                        ItemEnergy.fill(full);
                        output.accept(full);
                    }
                }
            })
            .build());

    public static void add(Supplier<? extends ItemLike> entry) {
        ENTRIES.add(entry);
    }
}
