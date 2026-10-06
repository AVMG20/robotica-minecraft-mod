package com.arno.robotica.processing;

import com.arno.robotica.processing.media.GrindingByproducts;
import com.arno.robotica.processing.media.GrindingMedia;
import com.arno.robotica.processing.recipe.GrindingLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Common (both sides) entry point of the ore processing module: tiered Grinder (ore doubling by c: tags, grinding
 * media) and Electric Furnace (parallel lanes). See the "Ore processing" section of docs/DESIGN.md.
 */
public final class ProcessingModule {
    private ProcessingModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        ProcessingRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ProcessingConfig.SPEC, "robotica-processing-server.toml");
        modBus.addListener(ProcessingModule::registerCapabilities);
        modBus.addListener(ProcessingModule::registerDataMaps);
        NeoForge.EVENT_BUS.addListener(ProcessingModule::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(ProcessingModule::mediaTooltip);
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(GrindingMedia.TYPE);
        event.register(GrindingByproducts.TYPE);
    }

    /** Tags (and with them the tag grinding rules) changed: drop every cached plan. */
    private static void onTagsUpdated(TagsUpdatedEvent event) {
        GrindingLogic.invalidate();
    }

    /** Any grinding media item (flint, grinding balls, other mods' entries) says what it does in a Grinder. */
    private static void mediaTooltip(ItemTooltipEvent event) {
        GrindingMedia media = GrindingMedia.of(event.getItemStack());
        if (media == null) return;
        event.getToolTip().add(Component.translatable("tooltip.robotica.grinding_media",
                Math.round(media.bonus() * 100), Math.round(media.secondary() * 100), media.uses(), Math.max(1, media.tier()))
                .withStyle(ChatFormatting.DARK_GRAY));
        int wear = event.getItemStack().getOrDefault(ProcessingRegistry.MEDIA_WEAR.get(), 0);
        if (wear > 0) {
            event.getToolTip().add(Component.translatable("tooltip.robotica.grinding_media_worn", Math.max(0, media.uses() - wear), media.uses())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ProcessingRegistry.GRINDER_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ProcessingRegistry.GRINDER_BE.get(), (be, side) -> be.automation(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ProcessingRegistry.ELECTRIC_FURNACE_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ProcessingRegistry.ELECTRIC_FURNACE_BE.get(), (be, side) -> be.automation(side));
    }
}
