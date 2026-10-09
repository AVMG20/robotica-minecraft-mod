package com.arno.robotica.codex.client;

import com.arno.robotica.codex.CodexItem;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class CodexClient {
    private CodexClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.arno.robotica.codex.CodexClientConfig.SPEC, "robotica-codex-client.toml");
        CodexItem.openScreen = () -> Minecraft.getInstance().setScreen(new CodexScreen());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, CodexSounds::onClientTick);
        com.arno.robotica.codex.client.dev.Showcase.init(modBus);
        com.arno.robotica.codex.client.dev.trailer.Trailer.init(modBus);
    }
}
