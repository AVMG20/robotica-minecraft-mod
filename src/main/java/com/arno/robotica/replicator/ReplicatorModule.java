package com.arno.robotica.replicator;

import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.logic.Essence;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Common (both sides) entry point of the replicator module.
 * Essence Vial, Replicator Controller / Frame / Glass (3x3x3 multiblock), harvest and spawn modes.
 */
public final class ReplicatorModule {
    private ReplicatorModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        ReplicatorRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ReplicatorConfig.SPEC, "robotica-replicator-server.toml");
        modBus.addListener(RegisterCapabilitiesEvent.class, ReplicatorModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ReplicatorModePayload::register);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ReplicatorMutePayload::register);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, PlayerInteractEvent.EntityInteract.class, ReplicatorModule::onInteract);
    }

    /** An Essence Vial samples a villager before the villager's own right-click (trades) runs. */
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!Essence.isVial(event.getItemStack()) || !(event.getTarget() instanceof LivingEntity target)
                || !Essence.isFarmOnly(target.getType())) return;
        InteractionResult result = event.getItemStack().interactLivingEntity(event.getEntity(), target, event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // FE on every side, items extract-only (the output) on the faces the side config allows.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ReplicatorRegistry.CONTROLLER_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ReplicatorRegistry.CONTROLLER_BE.get(), (be, side) -> be.automation(side));
        // the frames and glass of a formed Replicator act as the controller: FE in, the output out
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> {
            ReplicatorControllerBlockEntity controller = ReplicatorControllerBlockEntity.ofPart(level, pos, state);
            return controller == null ? null : controller.energy();
        }, ReplicatorRegistry.REPLICATOR_FRAME.get(), ReplicatorRegistry.REPLICATOR_GLASS.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            ReplicatorControllerBlockEntity controller = ReplicatorControllerBlockEntity.ofPart(level, pos, state);
            return controller == null ? null : controller.automation();
        }, ReplicatorRegistry.REPLICATOR_FRAME.get(), ReplicatorRegistry.REPLICATOR_GLASS.get());
    }
}
