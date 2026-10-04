package com.arno.robotica.architect.client;

import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.client.screen.ArchitectScreen;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.matter.MatterTable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only entry point of the architect module. Called from RoboticaClient. */
public final class ArchitectClient {
    private ArchitectClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(ArchitectClient::registerScreens);
        modBus.addListener(ArchitectClient::registerRenderers);
        modBus.addListener(ArchitectClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(ArchitectClient::onTooltip);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ArchitectRegistry.ARCHITECT_MENU.get(), ArchitectScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArchitectRegistry.BUILDER_DRONE.get(), BuilderDroneRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BuilderDroneModel.LAYER, BuilderDroneModel::createLayer);
    }

    /** Shows what an item is worth as Architect Table matter. */
    private static void onTooltip(ItemTooltipEvent event) {
        Matter m = MatterTable.valueOf(event.getItemStack());
        if (m.isZero()) return;
        StringBuilder sb = new StringBuilder();
        for (Matter.Grade grade : Matter.Grade.values()) {
            int v = m.get(grade);
            if (v <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(v).append(' ').append(Component.translatable(grade.langKey()).getString().toLowerCase(java.util.Locale.ROOT));
        }
        event.getToolTip().add(Component.translatable("tooltip.robotica.matter_value", sb.toString()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
