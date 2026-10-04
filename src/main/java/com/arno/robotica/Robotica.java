package com.arno.robotica;

import com.arno.robotica.automation.AutomationModule;
import com.arno.robotica.core.RoboticaCore;
import com.arno.robotica.gear.GearModule;
import com.arno.robotica.power.PowerModule;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Common entry point. Runs on both the client and the dedicated server.
 * Each feature module owns its own registries and is initialised here in a fixed order
 * (the order also decides the creative tab order).
 */
@Mod(Robotica.MODID)
public class Robotica {
    public static final String MODID = "robotica";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Robotica(IEventBus modBus, ModContainer container) {
        RoboticaCore.init(modBus, container);
        PowerModule.init(modBus, container);
        AutomationModule.init(modBus, container);
        GearModule.init(modBus, container);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
