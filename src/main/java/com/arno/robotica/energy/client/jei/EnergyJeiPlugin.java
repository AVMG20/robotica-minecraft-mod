package com.arno.robotica.energy.client.jei;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.EnergyDataMaps;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JEI info pages of the energy module (optional; JEI finds it through {@link JeiPlugin}, so it is never loaded without
 * JEI): Tesla Spire conductors and fuel, Core Reactor cores, fuel and modulators, Ring Collider fuel. Read from the synced
 * data maps so datapack changes show up.
 */
@JeiPlugin
public class EnergyJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Robotica.id("jei_energy");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        BuiltInRegistries.BLOCK.getDataMap(EnergyDataMaps.SPIRE_CONDUCTOR).forEach((key, c) -> {
            Block block = BuiltInRegistries.BLOCK.get(key);
            if (block.asItem() == Items.AIR) return;
            registration.addItemStackInfo(new ItemStack(block), Component.translatable("jei.robotica.info.spire_conductor",
                    Math.round(c.power() * CoreConfig.generation()), percent(c.efficiency())));
        });
        BuiltInRegistries.ITEM.getDataMap(EnergyDataMaps.SPIRE_FUEL).forEach((key, fuel) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) return;
            info(registration, item, Component.translatable("jei.robotica.info.spire_fuel", Fmt.energy(fuel.energy())), fuel.waste());
        });
        BuiltInRegistries.ITEM.getDataMap(EnergyDataMaps.REACTOR_CORE).forEach((key, core) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) return;
            registration.addItemStackInfo(new ItemStack(item), Component.translatable("jei.robotica.info.reactor_core",
                    String.format(Locale.ROOT, "%.2f", core.power()).replaceAll("\\.?0+$", ""), Fmt.duration(core.life())));
        });
        BuiltInRegistries.ITEM.getDataMap(EnergyDataMaps.REACTOR_FUEL).forEach((key, fuel) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) return;
            info(registration, item, Component.translatable("jei.robotica.info.reactor_fuel",
                    Math.round(fuel.power() * CoreConfig.generation()), Fmt.duration(fuel.ticks())), fuel.waste());
        });
        BuiltInRegistries.BLOCK.getDataMap(EnergyDataMaps.CORE_MODULATOR).forEach((key, mod) -> {
            Block block = BuiltInRegistries.BLOCK.get(key);
            if (block.asItem() == Items.AIR) return;
            registration.addItemStackInfo(new ItemStack(block), Component.translatable("jei.robotica.info.core_modulator",
                    signed(mod.power()), signed(mod.burn())));
        });
        BuiltInRegistries.ITEM.getDataMap(EnergyDataMaps.COLLIDER_FUEL).forEach((key, fuel) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) return;
            registration.addItemStackInfo(new ItemStack(item), Component.translatable("jei.robotica.info.collider_fuel", Fmt.duration(fuel.ticks())));
        });
    }

    private static void info(IRecipeRegistration registration, Item item, Component line, Optional<Item> waste) {
        List<Component> lines = new ArrayList<>();
        lines.add(line);
        waste.ifPresent(w -> lines.add(Component.translatable("jei.robotica.info.fuel_waste", Component.translatable(w.getDescriptionId()))));
        registration.addItemStackInfo(new ItemStack(item), lines.toArray(Component[]::new));
    }

    private static String percent(float v) {
        return String.format(Locale.ROOT, "%.0f", v * 100);
    }

    private static String signed(float v) {
        return String.format(Locale.ROOT, "%+.0f", v * 100);
    }
}
