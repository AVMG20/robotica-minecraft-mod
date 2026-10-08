package com.arno.robotica.power;

import com.arno.robotica.power.block.WindingCrankBlock;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Common (both sides) entry point of the power module. See docs/DESIGN.md.
 * Winding Crank, Combustion Generator, Solar Panels, Accumulators, Tesla Coils, Charger, Metal Press.
 */
public final class PowerModule {
    private PowerModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        PowerRegistry.register(modBus);
        com.arno.robotica.core.upgrade.UpgradeText.register(com.arno.robotica.core.upgrade.UpgradeText.WIRELESS_RANGE_STEP,
                PowerConfig::wirelessRangePerCard);
        com.arno.robotica.core.upgrade.UpgradeText.register(com.arno.robotica.core.upgrade.UpgradeText.GENERATOR_EFFICIENCY,
                () -> (int) Math.round(PowerConfig.generatorEfficiencyPerCard() * 100));
        container.registerConfig(ModConfig.Type.SERVER, PowerConfig.SPEC, "robotica-power-server.toml");
        modBus.addListener(PowerModule::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(WindingCrankBlock::onLogout);
        NeoForge.EVENT_BUS.addListener(PowerModule::mainspringTooltip);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TagsUpdatedEvent.class,
                e -> com.arno.robotica.power.recipe.PressingLogic.invalidate());
    }

    /** Mainspring tooltip: how far it is wound, in percent (the core tooltip shows the FE). */
    private static void mainspringTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(CoreItems.MAINSPRING.get())) return;
        int capacity = ItemEnergy.capacity(stack);
        int percent = capacity <= 0 ? 0 : (int) (100L * ItemEnergy.get(stack) / capacity);
        List<Component> tooltip = event.getToolTip();
        int at = Math.min(1, tooltip.size());
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getContents() instanceof TranslatableContents tc && tc.getKey().equals("tooltip.robotica.energy")) {
                at = i + 1;
                break;
            }
        }
        tooltip.add(at, Component.translatable("tooltip.robotica.mainspring_wound", percent).withStyle(ChatFormatting.AQUA));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.WINDING_CRANK_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.WINDING_CRANK_BE.get(), (be, side) -> be.automation());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.COMBUSTION_GENERATOR_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.COMBUSTION_GENERATOR_BE.get(), (be, side) -> be.sides.access(side));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.SOLAR_MK1_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.SOLAR_MK2_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.SOLAR_MK3_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.SOLAR_MK4_BE.get(), (be, side) -> be.energy);

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.ACCUMULATOR_BE.get(), (be, side) -> be.energyFor(side));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.CHARGER_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.CHARGER_BE.get(), (be, side) -> be.sides.access(side));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.WIRELESS_CHARGER_BE.get(), (be, side) -> be.energy);

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.METAL_PRESS_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.METAL_PRESS_BE.get(), (be, side) -> be.automation(side));
    }
}
