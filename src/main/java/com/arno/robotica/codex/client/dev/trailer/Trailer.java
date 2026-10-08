package com.arno.robotica.codex.client.dev.trailer;

import com.arno.robotica.Robotica;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Development tool, inert unless the JVM runs with -Drobotica.trailer=true (./gradlew runTrailer, scripts/trailer/record.sh).
 * Creates a normal world, builds each scene on a flattened pad in a meadow, slows the game with {@code tick rate 5} and
 * records every rendered frame together with its scene time, then quits. {@code -Drobotica.trailer.scenes=a,b} picks
 * scenes, {@code -Drobotica.trailer.out=dir} the output folder (default run-trailer/footage).
 * scripts/trailer/retime.py turns the footage into real-speed 60 fps clips.
 */
public final class Trailer {
    private Trailer() {}

    private static final int SLOW_RATE = Integer.getInteger("robotica.trailer.rate", 5);
    /** Distance between scene sites along the line, in blocks. */
    private static final int SITE_SPACING = 64;
    private static final int MESH_TIMEOUT_TICKS = 1200;

    private enum Phase { TITLE, CREATING, JOINING, PREPARING, SETUP, WARMUP, SLOWING, RECORDING, DONE }

    private static final List<TrailerScene> ALL = new ArrayList<>();
    private static List<TrailerScene> scenes = List.of();
    private static Phase phase = Phase.TITLE;
    private static int idleTicks, sceneIndex, phaseTicks;

    private static final AtomicBoolean serverReady = new AtomicBoolean();
    private static volatile BlockPos baseSite;
    private static volatile BlockPos currentSite;
    private static final List<BlockPos> usedSites = new ArrayList<>();
    private static CameraPath path;
    private static TrailerScene scene;
    private static FrameRecorder recorder;
    private static long recordStartGameTime;
    private static double sceneTime;
    private static int nextAction;

    public static void init(IEventBus modBus) {
        if (!Boolean.getBoolean("robotica.trailer")) return;
        Robotica.LOGGER.info("Robotica trailer mode: will create a world, record scenes and quit.");
        ALL.addAll(TrailerScenesRobots.scenes());
        ALL.addAll(TrailerScenesMachines.scenes());
        ALL.addAll(TrailerScenesBosses.scenes());
        String filter = System.getProperty("robotica.trailer.scenes", "").trim();
        Set<String> wanted = new HashSet<>(Arrays.asList(filter.split("\\s*,\\s*")));
        List<TrailerScene> picked = new ArrayList<>();
        for (TrailerScene s : ALL) if (filter.isEmpty() || wanted.contains(s.id)) picked.add(s);
        scenes = picked;
        NeoForge.EVENT_BUS.addListener(Trailer::tick);
        NeoForge.EVENT_BUS.addListener(Trailer::renderPre);
        NeoForge.EVENT_BUS.addListener(Trailer::renderPost);
        NeoForge.EVENT_BUS.addListener(Trailer::fov);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ------------------------------------------------------------------ flow

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = mc();
        phaseTicks++;
        if (phase != Phase.TITLE && phase != Phase.CREATING && phase != Phase.DONE) {
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
        }
        switch (phase) {
            case TITLE -> {
                if (mc.getOverlay() == null && mc.screen != null && ++idleTicks > 40) {
                    createWorld();
                    enter(Phase.CREATING);
                }
            }
            case CREATING -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    mc.options.tutorialStep = TutorialSteps.NONE;
                    mc.getTutorial().setStep(TutorialSteps.NONE);
                    enter(Phase.JOINING);
                }
            }
            case JOINING -> {
                if (phaseTicks > 60) {
                    serverReady.set(false);
                    server(Trailer::prepareWorld);
                    enter(Phase.PREPARING);
                }
            }
            case PREPARING -> {
                if (serverReady.get()) startScene(0);
            }
            case SETUP -> {
                if (serverReady.get()) enter(Phase.WARMUP);
            }
            case WARMUP -> warmup();
            case SLOWING -> {
                follow(0);
                if (mc.level != null && mc.level.tickRateManager().tickrate() <= SLOW_RATE + 0.01F) {
                    recordStartGameTime = mc.level.getGameTime();
                    sceneTime = 0;
                    nextAction = 0;
                    try {
                        Path out = Path.of(System.getProperty("robotica.trailer.out", "footage"));
                        recorder = new FrameRecorder(out.isAbsolute() ? out : mc.gameDirectory.toPath().resolve(out), scene.id, mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
                    } catch (IOException e) {
                        throw new IllegalStateException("Trailer could not start ffmpeg", e);
                    }
                    Robotica.LOGGER.info("Trailer: recording {} ({}x{})", scene.id, mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
                    enter(Phase.RECORDING);
                }
            }
            case RECORDING -> recordTick();
            default -> {}
        }
    }

    private static void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private static void server(java.util.function.Consumer<MinecraftServer> action) {
        MinecraftServer server = mc().getSingleplayerServer();
        if (server != null) server.execute(() -> action.accept(server));
    }

    private static void command(MinecraftServer server, String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static void createWorld() {
        Minecraft mc = mc();
        var o = mc.options;
        o.tutorialStep = TutorialSteps.NONE;
        o.pauseOnLostFocus = false;
        o.hideGui = true;
        o.bobView().set(false);
        o.graphicsMode().set(GraphicsStatus.FANCY);
        o.ambientOcclusion().set(true);
        o.renderDistance().set(12);
        o.simulationDistance().set(10);
        o.entityShadows().set(true);
        o.cloudStatus().set(CloudStatus.FANCY);
        o.particles().set(ParticleStatus.ALL);
        o.enableVsync().set(false);
        o.framerateLimit().set(120);
        o.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.getTutorial().setStep(TutorialSteps.NONE);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        LevelSettings settings = new LevelSettings("trailer-" + System.currentTimeMillis(), GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(20240607L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
                mc.screen);
    }

    /** Server thread: a meadow near spawn, spectator player, no weather. */
    private static void prepareWorld(MinecraftServer server) {
        ServerLevel level = server.overworld();
        command(server, "weather clear");
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        sp.setGameMode(GameType.SPECTATOR);
        Pair<BlockPos, Holder<Biome>> found = findMeadow(level, BlockPos.ZERO, 2000);
        baseSite = found == null ? BlockPos.ZERO : found.getFirst();
        Robotica.LOGGER.info("Trailer: base site {}", baseSite);
        serverReady.set(true);
    }

    private static Pair<BlockPos, Holder<Biome>> findMeadow(ServerLevel level, BlockPos around, int radius) {
        return level.findClosestBiome3d(h -> h.is(Biomes.PLAINS) || h.is(Biomes.MEADOW) || h.is(Biomes.SUNFLOWER_PLAINS),
                around, radius, 16, 64);
    }

    private static void startScene(int index) {
        sceneIndex = index;
        scene = scenes.get(index);
        int slot = ALL.indexOf(scene);
        serverReady.set(false);
        path = null;
        enter(Phase.SETUP);
        server(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            BlockPos guess = baseSite.offset(slot * SITE_SPACING, 0, 0);
            BlockPos center = null;
            for (int attempt = 0; attempt < 6 && center == null; attempt++) {
                Pair<BlockPos, Holder<Biome>> found = findMeadow(level, guess, 160);
                BlockPos c = found == null ? guess : new BlockPos(found.getFirst().getX(), 0, found.getFirst().getZ());
                boolean clear = true;
                for (BlockPos used : usedSites) {
                    if (Math.abs(used.getX() - c.getX()) < SITE_SPACING - 8 && Math.abs(used.getZ() - c.getZ()) < SITE_SPACING - 8) clear = false;
                }
                if (clear) center = c;
                else guess = guess.offset(0, 0, SITE_SPACING);
            }
            if (center == null) center = guess;
            usedSites.add(center);
            // the camera needs the area loaded first; flatten also loads the chunks it touches
            sp.teleportTo(level, center.getX() + 0.5, 100, center.getZ() + 0.5, 0, 0);
            BlockPos origin = flatten(level, center.getX(), center.getZ(), scene.width, scene.depth);
            currentSite = origin;
            command(server, "time set " + scene.timeOfDay);
            command(server, "difficulty " + scene.difficulty.getKey());
            scene.setup.run(level, sp, origin);
            path = scene.camera.apply(origin);
            teleportTo(sp, path.at(0));
            serverReady.set(true);
            Robotica.LOGGER.info("Trailer: scene {} set up at {}", scene.id, origin);
        });
    }

    private static void warmup() {
        Minecraft mc = mc();
        follow(0);
        boolean meshed = mc.levelRenderer.hasRenderedAllSections();
        if (phaseTicks >= scene.warmupTicks && (meshed || phaseTicks > scene.warmupTicks + MESH_TIMEOUT_TICKS)) {
            Robotica.LOGGER.info("Trailer: warmup of {} done after {} ticks, meshed={}", scene.id, phaseTicks, meshed);
            server(server -> command(server, "tick rate " + SLOW_RATE));
            enter(Phase.SLOWING);
        }
    }

    private static void recordTick() {
        Minecraft mc = mc();
        if (mc.level == null) return;
        long tick = mc.level.getGameTime() - recordStartGameTime;
        while (nextAction < scene.actions.size() && scene.actions.get(nextAction).tick() <= tick) {
            TrailerScene.Action action = scene.actions.get(nextAction++).action();
            server(server -> action.run(server.overworld(), server.getPlayerList().getPlayers().get(0), currentSite));
        }
        follow(tick);
        if (tick >= scene.durationTicks) {
            recorder.close();
            Robotica.LOGGER.info("Trailer: {} done, {} frames", scene.id, recorder.frames());
            recorder = null;
            server(server -> command(server, "tick rate 20"));
            if (sceneIndex + 1 < scenes.size()) {
                startScene(sceneIndex + 1);
            } else {
                enter(Phase.DONE);
                Robotica.LOGGER.info("Robotica trailer finished.");
                mc.stop();
            }
        }
    }

    /** Moves the server player along the path (every tick) so chunks and entities around the camera stay loaded. */
    private static void follow(double tick) {
        CameraPath p = path;
        if (p == null) return;
        CameraPath.Pose pose = p.at(tick);
        server(server -> {
            if (!server.getPlayerList().getPlayers().isEmpty()) teleportTo(server.getPlayerList().getPlayers().get(0), pose);
        });
    }

    private static void teleportTo(ServerPlayer sp, CameraPath.Pose pose) {
        sp.teleportTo(sp.serverLevel(), pose.x(), pose.y() - sp.getEyeHeight(), pose.z(), pose.yaw(), pose.pitch());
    }

    // ------------------------------------------------------------------ rendering

    private static boolean rendering() {
        return path != null && (phase == Phase.WARMUP || phase == Phase.SLOWING || phase == Phase.RECORDING);
    }

    /** Before each frame: the scene time of this frame, and the player exactly on the path at that time. */
    private static void renderPre(RenderFrameEvent.Pre event) {
        Minecraft mc = mc();
        if (!rendering() || mc.player == null || mc.level == null) return;
        sceneTime = phase == Phase.RECORDING
                ? (mc.level.getGameTime() - recordStartGameTime) + event.getPartialTick().getGameTimeDeltaPartialTick(false)
                : 0;
        applyPose(mc.player, path.at(sceneTime));
    }

    private static void applyPose(LocalPlayer p, CameraPath.Pose pose) {
        double y = pose.y() - p.getEyeHeight();
        p.setPos(pose.x(), y, pose.z());
        p.xo = p.xOld = pose.x();
        p.yo = p.yOld = y;
        p.zo = p.zOld = pose.z();
        p.setYRot(pose.yaw());
        p.setXRot(pose.pitch());
        p.yRotO = pose.yaw();
        p.xRotO = pose.pitch();
        p.yHeadRot = pose.yaw();
        p.yHeadRotO = pose.yaw();
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        if (!rendering() || !event.usedConfiguredFov()) return;
        event.setFOV(path.at(sceneTime).fov());
    }

    private static void renderPost(RenderFrameEvent.Post event) {
        if (phase != Phase.RECORDING || recorder == null) return;
        if (sceneTime < 0 || sceneTime > scene.durationTicks) return;
        recorder.capture(mc().getMainRenderTarget(), sceneTime);
    }

    // ------------------------------------------------------------------ site and helpers for scenes

    /**
     * Flattens a pad of w x d blocks around (cx, cz) at the median surface height: grass on top, dirt below, air above
     * up to +32. Returns the centre one block above the grass. The terrain and trees around stay as backdrop.
     */
    private static BlockPos flatten(ServerLevel level, int cx, int cz, int w, int d) {
        int x0 = cx - w / 2, z0 = cz - d / 2;
        for (int x = x0 - 16; x <= x0 + w + 16; x += 16) {
            for (int z = z0 - 16; z <= z0 + d + 16; z += 16) level.getChunk(x >> 4, z >> 4);
        }
        List<Integer> heights = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4; j++) {
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + i * (w - 1) / 4, z0 + j * (d - 1) / 4));
            }
        }
        heights.sort(null);
        int y0 = heights.get(heights.size() / 2);
        BlockState air = Blocks.AIR.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int y = y0; y <= y0 + 32; y++) level.setBlock(pos.set(x, y, z), air, Block.UPDATE_CLIENTS);
                level.setBlock(pos.set(x, y0 - 1, z), grass, Block.UPDATE_CLIENTS);
                for (int y = y0 - 2; y >= y0 - 12; y--) {
                    pos.set(x, y, z);
                    if (y < y0 - 5 && level.getBlockState(pos).canOcclude()) break;
                    level.setBlock(pos, dirt, Block.UPDATE_CLIENTS);
                }
            }
        }
        for (int x = x0; x < x0 + w; x += 16) {
            for (int z = z0; z < z0 + d; z += 16) level.setChunkForced(x >> 4, z >> 4, true);
        }
        level.setChunkForced(new ChunkPos(new BlockPos(cx, 0, cz)).x, new ChunkPos(new BlockPos(cx, 0, cz)).z, true);
        return new BlockPos(cx, y0, cz);
    }

    /** A Robotica block by registry name. */
    public static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(Robotica.id(name));
    }

    /** Places a block facing {@code dir} (HORIZONTAL_FACING or FACING). */
    public static void place(ServerLevel level, BlockPos pos, Block block, Direction dir) {
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        else if (s.hasProperty(BlockStateProperties.FACING)) s = s.setValue(BlockStateProperties.FACING, dir);
        level.setBlock(pos, s, 3);
    }

    /** Fills a block's energy buffer (through the capability, like the Showcase). */
    public static void fillEnergy(ServerLevel level, BlockPos pos) {
        var cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        for (int i = 0; i < 64 && cap != null && cap.receiveEnergy(Integer.MAX_VALUE, false) > 0; i++) {}
    }

    /** Runs a server command without chat output. */
    public static void run(ServerLevel level, String cmd) {
        command(level.getServer(), cmd);
    }
}
