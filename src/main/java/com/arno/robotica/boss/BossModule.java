package com.arno.robotica.boss;

import com.arno.robotica.boss.entity.ForgeTyrant;
import com.arno.robotica.boss.entity.RoboticaBoss;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Common (both sides) entry point of the boss module: the Scrap Colossus (Colossus Altar, Signal Flare, Rusted Foundry) and
 * the Forge Tyrant (Forge Altar, Ignition Charge, Cinder Forge).
 */
public final class BossModule {
    private BossModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        BossRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, BossConfig.SPEC, "robotica-boss-server.toml");
        modBus.addListener(BossModule::registerAttributes);
        NeoForge.EVENT_BUS.addListener(BossModule::onDrops);
        BossLoot.init();
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BossRegistry.SCRAP_COLOSSUS.get(), ScrapColossus.createAttributes().build());
        event.put(BossRegistry.SCRAP_DRONE.get(), ScrapDrone.createAttributes().build());
        event.put(BossRegistry.FORGE_TYRANT.get(), ForgeTyrant.createAttributes().build());
    }

    /**
     * Boss loot belongs to whoever landed the kill: every drop moves to the killer, only they can pick it up for two minutes
     * (config; or until they log out, see {@link BossLoot}) and it lasts longer than normal items. Kills without a player
     * (/kill, a pet) leave the loot where the boss fell, free for anyone.
     */
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof RoboticaBoss) || !(event.getEntity() instanceof LivingEntity boss)) return;
        ServerPlayer killer = killer(event.getSource().getEntity(), boss);
        for (ItemEntity drop : event.getDrops()) {
            drop.setExtendedLifetime();
            if (killer != null) {
                BossLoot.lock(drop, killer.getUUID(), boss.level().getGameTime());
                drop.setPos(killer.getX(), killer.getY() + 0.5, killer.getZ());
                drop.setNoPickUpDelay();
            }
        }
    }

    private static ServerPlayer killer(Entity direct, LivingEntity boss) {
        if (direct instanceof ServerPlayer player && player.level() == boss.level() && player.isAlive()) return player;
        if (boss.getKillCredit() instanceof ServerPlayer player && player.level() == boss.level() && player.isAlive()) return player;
        return null;
    }
}
