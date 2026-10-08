package com.arno.robotica.compat.jade;

import com.arno.robotica.drones.entity.DroneBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration (optional). Jade finds this class through the {@link WailaPlugin} annotation, so it is never loaded
 * when Jade is missing. Energy bars come from Jade's own FE support (every Robotica machine exposes the capability);
 * this adds status, progress, tier, owner, spring charge and the Survey Rig's last ore, plus an energy bar and mode for drones.
 */
@WailaPlugin
public class RoboticaJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineProvider.INSTANCE, BlockEntity.class);
        registration.registerEntityDataProvider(DroneProvider.INSTANCE, DroneBase.class);
        registration.registerEntityDataProvider(RancherProvider.INSTANCE, com.arno.robotica.automation.rancher.Rancher.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(DroneProvider.INSTANCE, DroneBase.class);
        registration.registerEntityComponent(RancherProvider.INSTANCE, com.arno.robotica.automation.rancher.Rancher.class);
    }
}
