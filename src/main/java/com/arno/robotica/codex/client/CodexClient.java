package com.arno.robotica.codex.client;

import com.arno.robotica.codex.CodexItem;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class CodexClient {
    private CodexClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        CodexItem.openScreen = () -> Minecraft.getInstance().setScreen(new CodexScreen());
    }
}
