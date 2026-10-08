package com.arno.robotica.energy.client.jei;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Locale;

/**
 * JEI info pages of the energy module (optional; JEI finds it through {@link JeiPlugin}, so it is never loaded without
 * JEI): reactor fuel, fusion fuel and reactor coolant, read from the synced data maps so datapack changes show up.
 */
@JeiPlugin
public class EnergyJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Robotica.id("jei_energy");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        BuiltInRegistries.ITEM.getDataMap(EnergyDataMaps.REACTOR_FUEL).forEach((key, fuel) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) return;
            Component line = Component.translatable("jei.robotica.info.reactor_fuel", fuel.heat(), seconds(fuel.ticks()));
            if (fuel.waste().isPresent()) {
                Component waste = Component.translatable("jei.robotica.info.reactor_fuel_waste",
                        Component.translatable(fuel.waste().get().getDescriptionId()));
                registration.addItemStackInfo(new ItemStack(item), line, waste);
            } else {
                registration.addItemStackInfo(new ItemStack(item), line);
            }
        });
        BuiltInRegistries.ITEM.getDataMap(EnergyDataMaps.FUSION_FUEL).forEach((key, fuel) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) return;
            long power = Math.round(fuel.power() * CoreConfig.generation());
            registration.addItemStackInfo(new ItemStack(item),
                    Component.translatable("jei.robotica.info.fusion_fuel", String.format(Locale.ROOT, "%,d", power), seconds(fuel.ticks())));
        });
        BuiltInRegistries.BLOCK.getDataMap(EnergyDataMaps.REACTOR_COOLANT).forEach((key, coolant) -> {
            Block block = BuiltInRegistries.BLOCK.get(key);
            Component line = Component.translatable("jei.robotica.info.reactor_coolant", String.format(Locale.ROOT, "%.1f", coolant.cooling()));
            if (block instanceof LiquidBlock liquid) {
                registration.addIngredientInfo(new FluidStack(liquid.fluid, 1000), NeoForgeTypes.FLUID_STACK, line);
                Item bucket = liquid.fluid.getBucket();
                if (bucket != Items.AIR) registration.addItemStackInfo(new ItemStack(bucket), line);
            } else if (block.asItem() != Items.AIR) {
                registration.addItemStackInfo(new ItemStack(block), line);
            }
        });
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? String.valueOf(ticks / 20) : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
