package com.arno.robotica.compat;

import net.minecraft.server.level.ServerLevel;

/**
 * Optional interface for a block entity that wants to describe itself to overlay mods such as Jade.
 * Fill in only what applies. Called on the server. Block entities that do not implement it are described by
 * {@link MachineInfoCollector}, which knows the current Robotica modules.
 */
public interface InfoSource {
    void collectInfo(ServerLevel level, MachineInfo info);
}
