package com.arno.robotica.gear.client;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Client-only entry point of the gear module. Called from RoboticaClient. */
public final class GearClient {
    private GearClient() {}

    /** The Spark Lamp pops out with its own sparks (SparkWispRenderer), not with block crumbs. */
    private static final IClientBlockExtensions NO_BREAK_PARTICLES = new IClientBlockExtensions() {
        @Override
        public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
            return true;
        }

        @Override
        public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
            return true;
        }
    };

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, com.arno.robotica.gear.GearClientConfig.SPEC, "robotica-gear-client.toml");
        NeoForge.EVENT_BUS.addListener(AddSectionGeometryEvent.class, SparkWisps::onSectionGeometry);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, SparkWisps::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, SparkWisps::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, SparkWispRenderer::onRenderLevel);
        modBus.addListener(RegisterClientExtensionsEvent.class, e -> e.registerBlock(NO_BREAK_PARTICLES, com.arno.robotica.gear.GearBlocks.SPARK_LAMP.get()));
        modBus.addListener(RegisterKeyMappingsEvent.class, GearKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, GearHud::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, GearKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, GearKeys::onScroll);
        NeoForge.EVENT_BUS.addListener(RenderHighlightEvent.Block.class, AreaOutline::onHighlight);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, AreaOutline::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, AreaOutline::onRenderLevel);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(com.arno.robotica.gear.GearEntities.RIVET.get(), RivetRenderer::new));
        modBus.addListener(RegisterMenuScreensEvent.class, e -> e.register(com.arno.robotica.gear.GearBlocks.TINKERS_BENCH_MENU.get(), TinkersBenchScreen::new));
    }
}
