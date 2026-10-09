package com.arno.robotica.codex.client.dev;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.plan.Layout;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.plan.ShellPlacer;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.codex.client.CodexScreen;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import com.arno.robotica.gear.client.ToggleScreen;
import com.arno.robotica.gear.lamp.SparkLampBlock;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.logistics.pipe.PipeMode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.CameraType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.logic.Essence;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Development tool, inert unless the JVM runs with -Drobotica.showcase=true (./gradlew runShowcase).
 * Creates a flat creative world, builds scenes with every Robotica block, opens every machine GUI,
 * the Codex and an item sheet, saves a screenshot of each into run-showcase/screenshots, then quits.
 * With -Drobotica.showcase.only=a,b (scripts/showcase.sh a b) it only builds and shoots the scenes and GUIs whose shot
 * name contains a or b.
 */
public final class Showcase {
    private Showcase() {}

    private record Step(int delay, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    /** Words a shot name must contain one of (-Drobotica.showcase.only=a,b); empty takes every shot. */
    private static final List<String> ONLY = java.util.Arrays.stream(System.getProperty("robotica.showcase.only", "").split(","))
            .map(String::trim).filter(w -> !w.isEmpty()).toList();
    private static int stepIndex = 0, wait = 0, idleTicks = 0;
    private static boolean worldRequested = false, inWorld = false;

    public static void init(IEventBus modBus) {
        if (!Boolean.getBoolean("robotica.showcase")) return;
        Robotica.LOGGER.info("Robotica showcase mode: will create a world, take screenshots and quit.");
        NeoForge.EVENT_BUS.addListener(Showcase::tick);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = mc();
        if (!worldRequested) {
            if (mc.getOverlay() == null && mc.screen != null && ++idleTicks > 40) {
                worldRequested = true;
                createWorld();
            }
            return;
        }
        if (!inWorld) {
            if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                inWorld = true;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                buildPlan();
                wait = 60;
            }
            return;
        }
        // no advancement toasts and no hover tooltips from wherever the real mouse is
        mc.getToasts().clear();
        mc.gui.getChat().clearMessages(false);
        parkMouse(mc);
        if (wait > 0) {
            wait--;
            return;
        }
        if (stepIndex >= STEPS.size()) return;
        Step step = STEPS.get(stepIndex++);
        try {
            step.action().run();
        } catch (RuntimeException e) {
            Robotica.LOGGER.error("Showcase step {} failed", stepIndex - 1, e);
        }
        wait = step.delay();
    }

    private static java.lang.reflect.Field mouseX, mouseY;
    private static boolean mouseFailed;

    /** Puts the GUI mouse in the top-left corner, away from every slot and button. */
    private static void parkMouse(Minecraft mc) {
        if (mouseFailed) return;
        try {
            if (mouseX == null) {
                mouseX = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                mouseY = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                mouseX.setAccessible(true);
                mouseY.setAccessible(true);
            }
            mouseX.setDouble(mc.mouseHandler, 0);
            mouseY.setDouble(mc.mouseHandler, 0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            mouseFailed = true;
            Robotica.LOGGER.warn("Showcase could not move the mouse; tooltips may show in GUI shots", e);
        }
    }

    private static void createWorld() {
        Minecraft mc = mc();
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.pauseOnLostFocus = false;
        mc.getTutorial().setStep(TutorialSteps.NONE);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("showcase-" + System.currentTimeMillis(), GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(1L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    // ------------------------------------------------------------------ plan

    private static final int Y = -60; // superflat surface + 1

    private static void server(Consumer<ServerPlayer> action) {
        MinecraftServer server = mc().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            action.accept(sp);
        });
    }

    private static void step(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    /** Whether any of these shots is wanted: builds a scene only when one of its shots will be taken. */
    private static boolean wants(String... shots) {
        if (ONLY.isEmpty()) return true;
        for (String shot : shots) for (String word : ONLY) if (shot.contains(word)) return true;
        return false;
    }

    private static void shot(String name) {
        grab(10, name);
    }

    /** A screenshot named {@code name}.png after {@code delay} ticks, if it is wanted. */
    private static void grab(int delay, String name) {
        if (wants(name)) step(delay, () -> Screenshot.grab(mc().gameDirectory, name + ".png", mc().getMainRenderTarget(), msg -> {}));
    }

    private static void camera(double x, double y, double z, float yaw, float pitch) {
        step(40, () -> server(sp -> {
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(sp.serverLevel(), x, y, z, yaw, pitch);
        }));
    }

    private static void hud(boolean visible) {
        step(2, () -> mc().options.hideGui = !visible);
    }

    private static List<Block> roboticaBlocks() {
        List<Block> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(Robotica.MODID) && b.asItem() != Items.AIR) out.add(b);
        }
        return out;
    }

    /** Dev only: links a Tesla Coil without the Linker (TeslaCoilBlockEntity.addLink is package-private). */
    private static void devLink(ServerLevel level, BlockPos coil, com.arno.robotica.power.tesla.TeslaLink link) {
        try {
            var be = level.getBlockEntity(coil);
            var m = com.arno.robotica.power.tesla.TeslaCoilBlockEntity.class.getDeclaredMethod("addLink", com.arno.robotica.power.tesla.TeslaLink.class);
            m.setAccessible(true);
            m.invoke(be, link);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Robotica.LOGGER.warn("Showcase could not link Tesla Coil at {}", coil, e);
        }
    }

    private static void setFacing(ServerLevel level, BlockPos pos, Block block, Direction dir) {
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        else if (s.hasProperty(BlockStateProperties.FACING)) s = s.setValue(BlockStateProperties.FACING, dir);
        level.setBlock(pos, s, 3);
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(Robotica.id(name));
    }

    /** Puts some state into a machine before its GUI is opened, so the screenshot shows something. */
    private static void prepare(String name, BlockPos pos, ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        switch (name) {
            case "combustion_generator" -> {
                if (items != null) items.insertItem(0, new ItemStack(Items.COAL, 16), false);
                if (energy != null) energy.receiveEnergy(30_000, false);
            }
            case "charger" -> {
                if (energy != null) energy.receiveEnergy(200_000, false);
                if (items != null) items.insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("magma_drill"))), false);
            }
            case "metal_press" -> {
                if (energy != null) energy.receiveEnergy(20_000, false);
                if (items != null) items.insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false);
            }
            case "architect_table" -> {
                if (level.getBlockEntity(pos) instanceof ArchitectTableBlockEntity table) {
                    table.setMatter(new Matter(320, 140, 36));
                    for (int plot : new int[]{Plots.index(0, -1), Plots.index(1, -1), Plots.index(1, 0), Plots.index(-1, 1)}) {
                        table.handleAction(sp, ArchitectTableBlockEntity.ACTION_TOGGLE, plot, 0);
                    }
                }
            }
            case "stumpy", "sprout", "excavator" -> {
                if (energy != null) energy.receiveEnergy(40_000, false);
            }
            default -> {
                if (energy != null) energy.receiveEnergy(20_000, false);
            }
        }
    }

    private static void buildPlan() {
        mc().options.renderDistance().set(8);
        step(20, () -> server(sp -> {
            MinecraftServer s = sp.server;
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "time set 6000");
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "weather clear");
            sp.getInventory().clearContent();
        }));

        // Scene 1: every block on a grid, facing the camera (south). The machine GUIs below open these blocks.
        List<Block> gridBlocks = roboticaBlocks();
        boolean gridGuis = false;
        for (int i = 0; i < gridBlocks.size(); i++) {
            gridGuis |= wants(String.format("gui_%02d_%s", i, BuiltInRegistries.BLOCK.getKey(gridBlocks.get(i)).getPath()));
        }
        if (gridGuis || wants("01_all_blocks", "02_blocks_close_a", "03_blocks_close_b")) {
            step(40, () -> server(sp -> {
                ServerLevel level = sp.serverLevel();
                List<Block> blocks = roboticaBlocks();
                for (int i = 0; i < blocks.size(); i++) {
                    BlockPos pos = new BlockPos((i % 12) * 2, Y, (i / 12) * 2);
                    setFacing(level, pos, blocks.get(i), Direction.SOUTH);
                }
            }));
        }
        hud(false);
        if (wants("01_all_blocks")) {
            camera(11, Y + 9, 22, 180, 32);
            shot("01_all_blocks");
        }
        if (wants("02_blocks_close_a")) {
            camera(5, Y + 3, 7, 180, 20);
            shot("02_blocks_close_a");
        }
        if (wants("03_blocks_close_b")) {
            camera(17, Y + 3, 7, 180, 20);
            shot("03_blocks_close_b");
        }

        // Scene 2: a joined pair of buildings in each style, side by side, then a 2x2 hall from the inside.
        if (wants("04_styles_halls", "05_style_timberframe_door")) {
            step(40, () -> server(sp -> {
                ServerLevel level = sp.serverLevel();
                int i = 0;
                for (BuildStyle style : BuildStyle.values()) {
                    Layout layout = new Layout();
                    layout.queue(Plots.CENTER, style);
                    layout.queue(Plots.index(1, 0), style);
                    ShellPlacer.placeAll(level, new BlockPos(64 + i * 20, Y, 4), layout);
                    i++;
                }
            }));
            camera(98, Y + 10, 44, 180, 14);
            shot("04_styles_halls");
            camera(64, Y + 2, 16, 180, 5);
            shot("05_style_timberframe_door");
        }
        if (wants("06_workshop_inside")) {
            step(20, () -> server(sp -> {
                Layout layout = new Layout();
                for (int plot : new int[]{Plots.CENTER, Plots.index(1, 0), Plots.index(0, 1), Plots.index(1, 1)}) layout.queue(plot, BuildStyle.STEEL_LAB);
                ShellPlacer.placeAll(sp.serverLevel(), new BlockPos(64, Y, 44), layout);
            }));
            camera(62, Y + 3, 42, -45, 12);
            shot("06_workshop_inside");
        }

        // Scene 3: robots at work.
        if (wants("07_robots")) step(40, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            MinecraftServer s = sp.server;
            for (int dx = 0; dx < 3; dx++) {
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                        "place feature minecraft:oak " + (-30 + dx * 4) + " " + Y + " 2");
            }
            setFacing(level, new BlockPos(-27, Y, 6), block("stumpy"), Direction.SOUTH);
            setFacing(level, new BlockPos(-23, Y, 6), block("sprout"), Direction.SOUTH);
            setFacing(level, new BlockPos(-19, Y, 6), block("excavator"), Direction.SOUTH);
            setFacing(level, new BlockPos(-31, Y, 6), block("winding_crank"), Direction.SOUTH);
            setFacing(level, new BlockPos(-33, Y, 6), block("supply_crate"), Direction.SOUTH);
            for (int x = -24; x <= -22; x++) for (int z = 8; z <= 9; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, Y, z), Blocks.WHEAT.defaultBlockState(), 3);
            }
        }));
        if (wants("07_robots")) {
            camera(-25, Y + 3, 14, 180, 15);
            shot("07_robots");
        }

        // Scene 4: replicator, formed, with a zombie vial and energy.
        boolean replicator = wants("08_replicator", "gui_92_replicator_formed");
        if (replicator) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            BlockPos base = new BlockPos(0, Y, 40);
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) {
                BlockPos p = base.offset(x, y, z);
                boolean centre = x == 1 && y == 1 && z == 1;
                boolean glass = (x == 1 && z == 1 && y == 2) || (x == 0 && y == 1 && z == 1) || (x == 2 && y == 1 && z == 1);
                if (centre) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                else level.setBlock(p, block(glass ? "replicator_glass" : "replicator_frame").defaultBlockState(), 3);
            }
            BlockPos ctrl = base.offset(1, 1, 2);
            setFacing(level, ctrl, block("replicator_controller"), Direction.SOUTH);
        }));
        if (replicator) step(60, () -> server(sp -> {
            if (sp.serverLevel().getBlockEntity(new BlockPos(1, Y + 1, 42)) instanceof ReplicatorControllerBlockEntity be) {
                be.vial.setStackInSlot(0, Essence.completeVial(EntityType.ZOMBIE));
                be.energy().setEnergy(1_000_000);
            }
        }));
        if (wants("08_replicator")) {
            camera(1.5, Y + 2.5, 47, 180, 12);
            step(100, () -> {});
            shot("08_replicator");
        }

        // Scene 5: two linked, powered Portal Projectors (the portal is drawn by the block entity renderer) and a warp pad.
        if (wants("09_gate_and_pad")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            BlockPos a = new BlockPos(20, Y, 40), b = new BlockPos(25, Y, 40);
            setFacing(level, a, block("gate_controller"), Direction.SOUTH);
            setFacing(level, b, block("gate_controller"), Direction.SOUTH);
            GlobalPos ga = GlobalPos.of(level.dimension(), a), gb = GlobalPos.of(level.dimension(), b);
            GateLinks.get(level.getServer()).link(ga, gb);
            for (BlockPos pos : new BlockPos[]{a, b}) {
                if (level.getBlockEntity(pos) instanceof PortalProjectorBlockEntity be) {
                    be.setLinked(pos.equals(a) ? gb : ga);
                    be.energy.setEnergy(be.energy.getMaxEnergyStored());
                    be.evaluate(level);
                }
            }
            setFacing(level, new BlockPos(29, Y, 40), block("warp_pad"), Direction.SOUTH);
        }));
        if (wants("09_gate_and_pad")) {
            camera(22.5, Y + 3, 47, 180, 8);
            step(60, () -> {});
            shot("09_gate_and_pad");
        }

        // Scene 6: drill HUD and area outline.
        if (wants("10_drill_hud_outline")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            for (int x = -6; x <= 6; x++) for (int y = 0; y < 6; y++) for (int z = -24; z <= -20; z++) {
                level.setBlock(new BlockPos(x, Y + y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            ItemStack drill = new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("magma_drill")));
            ItemEnergy.fill(drill);
            drill.set(GearComponents.MODE.get(), AreaMode.CUBE_3);
            sp.setItemInHand(InteractionHand.MAIN_HAND, drill);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(sp.serverLevel(), 0.5, Y + 2, -16.5, 180, 0);
        }));
        hud(true);
        if (wants("10_drill_hud_outline")) {
            step(40, () -> {});
            shot("10_drill_hud_outline");
            // Dev only: the outline wrapping the top corner of the wall, at an angle.
            step(5, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 8.5, Y + 6, -16.5, 146.3F, 30.5F)));
            step(30, () -> {});
            shot("dev_area_outline_corner");
        }

        // Scene 7: a Tesla network on an Accumulator, Linker in hand so the arcs show at full strength.
        if (wants("11_tesla_network")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            BlockPos acc = new BlockPos(40, Y, -20);
            level.setBlock(acc, block("accumulator_3").defaultBlockState(), 3);
            var cap = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, acc, null);
            for (int i = 0; i < 64 && cap != null && cap.receiveEnergy(Integer.MAX_VALUE, false) > 0; i++) {}
            BlockPos root = acc.above();
            level.setBlock(root, block("tesla_coil_5").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
            BlockPos pillar = new BlockPos(46, Y, -26);
            level.setBlock(pillar, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            level.setBlock(pillar.above(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            BlockPos relay = pillar.above(2);
            level.setBlock(relay, block("tesla_coil_3").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
            BlockPos press = new BlockPos(35, Y, -24), charger = new BlockPos(36, Y, -16), terminal = new BlockPos(50, Y, -30), excav = new BlockPos(44, Y, -16);
            setFacing(level, press, block("metal_press"), Direction.SOUTH);
            setFacing(level, charger, block("charger"), Direction.SOUTH);
            setFacing(level, terminal, block("storage_terminal"), Direction.SOUTH);
            setFacing(level, excav, block("combustion_generator"), Direction.SOUTH);
            devLink(level, root, new com.arno.robotica.power.tesla.TeslaLink(press, Direction.UP, false));
            devLink(level, root, new com.arno.robotica.power.tesla.TeslaLink(charger, Direction.UP, false));
            devLink(level, root, new com.arno.robotica.power.tesla.TeslaLink(excav, Direction.UP, false));
            devLink(level, root, new com.arno.robotica.power.tesla.TeslaLink(relay, null, true));
            devLink(level, relay, new com.arno.robotica.power.tesla.TeslaLink(terminal, Direction.UP, false));
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("tesla_linker"))));
        }));
        hud(false);
        if (wants("11_tesla_network")) {
            camera(41.5, Y + 4, -8, 180, 22);
            step(60, () -> {});
            shot("11_tesla_network");
        }

        // Dev only (not in shots.py): Tesla links up close without the Linker, by day and night, idle and flowing.
        // Left a tier I coil runs flat out into a relay and an Accumulator, middle idle tier III and IV coils, right a
        // tier V coil trickles into a small Accumulator. The dev_ frames are a few ticks apart to check the motes move.
        if (wants("tesla_links")) {
            step(20, () -> server(sp -> {
                ServerLevel level = sp.serverLevel();
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                BlockPos src = new BlockPos(145, Y, -46), sink = new BlockPos(152, Y, -46), relay = new BlockPos(149, Y + 2, -50);
                BlockPos idleA = new BlockPos(155, Y + 2, -49), idleB = new BlockPos(159, Y + 1, -46);
                BlockPos src2 = new BlockPos(161, Y, -50), sink2 = new BlockPos(164, Y, -45);
                for (BlockPos acc : new BlockPos[]{src, src2}) {
                    level.setBlock(acc, block("accumulator_3").defaultBlockState(), 3);
                    var cap = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, acc, null);
                    for (int i = 0; i < 64 && cap != null && cap.receiveEnergy(Integer.MAX_VALUE, false) > 0; i++) {}
                }
                level.setBlock(sink, block("accumulator_2").defaultBlockState(), 3);
                level.setBlock(sink2, block("accumulator_1").defaultBlockState(), 3);
                for (BlockPos coil : new BlockPos[]{relay, idleA, idleB}) {
                    for (BlockPos p = coil.below(); p.getY() >= Y; p = p.below()) level.setBlock(p, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                }
                BlockPos[] coils = {src.above(), relay, idleA, idleB, src2.above()};
                String[] tiers = {"tesla_coil_1", "tesla_coil_2", "tesla_coil_3", "tesla_coil_4", "tesla_coil_5"};
                for (int i = 0; i < coils.length; i++) {
                    level.setBlock(coils[i], block(tiers[i]).defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
                }
                devLink(level, src.above(), new com.arno.robotica.power.tesla.TeslaLink(relay, null, true));
                devLink(level, relay, new com.arno.robotica.power.tesla.TeslaLink(sink, Direction.UP, false));
                devLink(level, idleA, new com.arno.robotica.power.tesla.TeslaLink(idleB, null, true));
                devLink(level, src2.above(), new com.arno.robotica.power.tesla.TeslaLink(sink2, Direction.UP, false));
                devLink(level, idleB, new com.arno.robotica.power.tesla.TeslaLink(src2, Direction.WEST, false));
            }));
            camera(154.5, Y + 3.5, -37, 180, 14);
            step(60, () -> {});
            grab(10, "tesla_links_day");
            camera(148.5, Y + 3, -42.5, 180, 10);
            step(20, () -> {});
            for (String f : new String[]{"a", "b", "c", "d"}) grab(3, "tesla_links_dev_day_" + f);
            camera(158.5, Y + 3, -40.5, 180, 12);
            step(20, () -> {});
            grab(10, "tesla_links_dev_day_tiers");
            step(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack(), "time set 18000")));
            camera(154.5, Y + 3.5, -37, 180, 14);
            step(20, () -> {});
            grab(10, "tesla_links_night");
            camera(148.5, Y + 3, -42.5, 180, 10);
            step(20, () -> {});
            for (String f : new String[]{"a", "b", "c", "d"}) grab(3, "tesla_links_dev_night_" + f);
            camera(158.5, Y + 3, -40.5, 180, 12);
            step(20, () -> {});
            grab(10, "tesla_links_dev_night_tiers");
            step(10, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("tesla_linker"))))));
            camera(154.5, Y + 3.5, -37, 180, 14);
            step(20, () -> {});
            grab(10, "tesla_links_dev_linker_night");
            step(10, () -> server(sp -> {
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack(), "time set 6000");
            }));
        }

        // Scene 8: the Scrap Colossus on its altar (no AI, for the photo).
        if (wants("12_scrap_colossus")) step(20, () -> server(sp -> {
            MinecraftServer s = sp.server;
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "difficulty normal");
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerLevel level = sp.serverLevel();
            level.setBlock(new BlockPos(70, Y, -20), block("colossus_altar").defaultBlockState(), 3);
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                    "summon robotica:scrap_colossus 70.5 " + (Y + 1) + " -19.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        }));
        if (wants("12_scrap_colossus")) {
            camera(70.5, Y + 3, -11, 180, 8);
            step(60, () -> {});
            shot("12_scrap_colossus");
            step(5, () -> server(sp -> command(sp, "kill @e[type=robotica:scrap_colossus]")));
        }

        // Scene 9: a Rusted Foundry.
        if (wants("13_rusted_foundry")) {
            step(20, () -> server(sp -> command(sp, "place structure robotica:rusted_foundry 120 " + Y + " -20")));
            camera(120, Y + 22, 6, 180, 42);
            step(80, () -> {});
            shot("13_rusted_foundry");
        }
        step(5, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));

        newScenes();

        // Scene 10: a Storage Terminal with things in it.
        boolean terminal = wants("gui_95_storage_terminal_full");
        if (terminal) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            BlockPos t = new BlockPos(0, Y, -40);
            setFacing(level, t, block("storage_terminal"), Direction.SOUTH);
            var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, t, null);
            var energy = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, t, null);
            if (energy != null) energy.receiveEnergy(Integer.MAX_VALUE, false);
            if (items != null) {
                net.minecraft.world.item.Item[] fill = {Items.COBBLESTONE, Items.OAK_LOG, Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE,
                        Items.COAL, Items.DIAMOND, Items.OAK_PLANKS, Items.GOLD_INGOT, Items.GLASS, Items.STICK, Items.TORCH, Items.WHEAT,
                        Items.BREAD, Items.BONE_MEAL, Items.LAPIS_LAZULI, Items.QUARTZ, Items.OBSIDIAN, Items.SAND, Items.GRAVEL};
                for (int i = 0; i < fill.length; i++) {
                    for (int k = 0; k <= i % 4; k++) net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(items, new ItemStack(fill[i], 64), false);
                }
                for (String id : new String[]{"copper_gear", "iron_plate", "basic_circuit", "electric_motor"}) {
                    net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(items, new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id(id)), 32), false);
                }
            }
            sp.teleportTo(level, 0.5, Y, -37.5, 180, 30);
            sp.gameMode.useItemOn(sp, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(t), Direction.SOUTH, t, false));
        }));
        hud(true);
        if (terminal) {
            step(30, () -> {});
            shot("gui_95_storage_terminal_full");
            step(5, () -> { if (mc().player != null && mc().screen != null) mc().player.closeContainer(); });
        }

        newGuis();

        // GUIs: every block entity that is a menu provider.
        step(10, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
        List<Block> blocks = roboticaBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            final int idx = i;
            final BlockPos pos = new BlockPos((i % 12) * 2, Y, (i / 12) * 2);
            final String name = BuiltInRegistries.BLOCK.getKey(blocks.get(i)).getPath();
            if (!wants(String.format("gui_%02d_%s", idx, name))) continue;
            step(10, () -> server(sp -> {
                if (!(sp.serverLevel().getBlockEntity(pos) instanceof MenuProvider)) return;
                prepare(name, pos, sp);
                sp.teleportTo(sp.serverLevel(), pos.getX() + 0.5, Y, pos.getZ() + 2.5, 180, 30);
                sp.setShiftKeyDown(false);
                sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
            }));
            step(25, () -> {
                if (mc().screen != null) Screenshot.grab(mc().gameDirectory, String.format("gui_%02d_%s.png", idx, name), mc().getMainRenderTarget(), m -> {});
            });
            step(5, () -> {
                if (mc().player != null && mc().screen != null) mc().player.closeContainer();
            });
        }

        // Warp pads: owner GUI (sneak, empty hand) and the destination list (standing on a pad, another pad exists).
        final BlockPos padA = new BlockPos(0, Y, 60);
        final BlockPos padB = new BlockPos(10, Y, 60);
        if (wants("gui_90_warp_pad_owner", "gui_91_warp_destinations")) step(10, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            for (BlockPos p : new BlockPos[]{padA, padB}) {
                setFacing(level, p, block("warp_pad"), Direction.SOUTH);
                if (level.getBlockEntity(p) instanceof WarpPadBlockEntity pad) {
                    pad.initPlacement(sp);
                    pad.ensureRegistered();
                    pad.energy.receiveEnergy(40_000, false);
                }
            }
            if (level.getBlockEntity(padB) instanceof WarpPadBlockEntity pad) pad.rename("Mining Outpost");
        }));
        if (wants("gui_90_warp_pad_owner")) {
            step(20, () -> server(sp -> {
                sp.teleportTo(sp.serverLevel(), padA.getX() + 0.5, Y, padA.getZ() + 2.5, 180, 30);
                sp.setShiftKeyDown(true);
                sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(padA), Direction.SOUTH, padA, false));
            }));
            grab(25, "gui_90_warp_pad_owner");
            step(5, () -> {
                if (mc().player != null && mc().screen != null) mc().player.closeContainer();
            });
        }
        if (wants("gui_91_warp_destinations")) {
            step(10, () -> server(sp -> {
                sp.setShiftKeyDown(false);
                sp.teleportTo(sp.serverLevel(), padA.getX() + 0.5, Y + 0.5, padA.getZ() + 0.5, 180, 30);
            }));
            step(10, () -> server(sp -> sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(padA), Direction.UP, padA, false))));
            grab(25, "gui_91_warp_destinations");
            step(5, () -> {
                if (mc().player != null && mc().screen != null) mc().player.closeContainer();
            });
        }
        step(5, () -> server(sp -> sp.setShiftKeyDown(false)));

        // The formed replicator from scene 4.
        if (wants("gui_92_replicator_formed")) step(10, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), 1.5, Y, 45.5, 180, 30);
            sp.setShiftKeyDown(false);
            BlockPos ctrl = new BlockPos(1, Y + 1, 42);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(ctrl), Direction.SOUTH, ctrl, false));
        }));
        if (wants("gui_92_replicator_formed")) {
            grab(25, "gui_92_replicator_formed");
            step(5, () -> {
                if (mc().player != null && mc().screen != null) mc().player.closeContainer();
            });
        }

        // Gear tool settings screen (opened client side, like the G key does).
        if (wants("gui_93_gear_settings")) {
            step(10, () -> server(sp -> {
                ItemStack drill = new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("magma_drill")));
                ItemEnergy.fill(drill);
                sp.setItemInHand(InteractionHand.MAIN_HAND, drill);
            }));
            step(20, () -> mc().setScreen(new ToggleScreen()));
            grab(15, "gui_93_gear_settings");
            step(5, () -> mc().setScreen(null));
            step(10, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
        }

        // Codex pages and lab.
        if (wants("codex_01_start", "codex_02_robots", "codex_05_big_energy", "codex_03_recipe", "codex_04_lab")) {
            step(20, () -> mc().setScreen(new CodexScreen()));
            shot("codex_01_start");
            step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devSelect(7); });
            shot("codex_02_robots");
            step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devSelect(5); });
            shot("codex_05_big_energy");
            step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devShowRecipe(new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("stumpy")))); });
            shot("codex_03_recipe");
            step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devLab(); });
            shot("codex_04_lab");
        }

        // Item sheet.
        int sheetPages = IconSheetScreen.pageCount(mc().getWindow().getGuiScaledWidth(), mc().getWindow().getGuiScaledHeight());
        for (int p = 0; p < sheetPages; p++) {
            final int pageIndex = p;
            if (!wants("items_" + (p + 1))) continue;
            step(20, () -> mc().setScreen(new IconSheetScreen(pageIndex)));
            shot("items_" + (p + 1));
        }
        step(20, () -> mc().setScreen(null));
        step(40, () -> {
            Robotica.LOGGER.info("Robotica showcase finished.");
            mc().stop();
        });
    }

    // ------------------------------------------------------------------ 0.3 / 0.4 content

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(Robotica.id(name));
    }

    /** Dev: sends an Exo-Frame effect for the armor stand tagged exo_fx to the showcase player. */
    private static void exoFx(byte kind) {
        step(1, () -> server(sp -> {
            for (var e : sp.serverLevel().getEntities(EntityType.ARMOR_STAND, e -> e.getTags().contains("exo_fx"))) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.arno.robotica.exo.net.ExoFxPayload(e.getId(), kind));
            }
        }));
    }

    private static void command(ServerPlayer sp, String cmd) {
        MinecraftServer s = sp.server;
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static void fillEnergy(ServerLevel level, BlockPos pos) {
        var cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        for (int i = 0; i < 64 && cap != null && cap.receiveEnergy(Integer.MAX_VALUE, false) > 0; i++) {}
    }

    /** Recomputes a block's shape from its neighbours (pipes placed with setBlock). */
    private static void reshape(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), 3);
    }

    private record Multiblock(List<String[]> layers, Map<Character, String> legend) {
        int h() { return layers.size(); }
        int d() { return layers.get(0).length; }
        int w() { return layers.get(0)[0].length(); }
    }

    /** A structure from scripts/wiki/multiblocks.json (the showcase runs in run-showcase, next to scripts). */
    private static Multiblock multiblock(String id) {
        Path file = mc().gameDirectory.toPath().resolve("../scripts/wiki/multiblocks.json");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject(id);
            List<String[]> layers = new ArrayList<>();
            for (JsonElement layer : root.getAsJsonArray("layers")) {
                JsonArray rows = layer.getAsJsonArray();
                String[] out = new String[rows.size()];
                for (int z = 0; z < out.length; z++) out[z] = rows.get(z).getAsString();
                layers.add(out);
            }
            Map<Character, String> legend = new HashMap<>();
            for (var e : root.getAsJsonObject("legend").entrySet()) legend.put(e.getKey().charAt(0), e.getValue().getAsString());
            return new Multiblock(layers, legend);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Showcase could not read multiblock " + id + " from " + file, e);
        }
    }

    /**
     * Places a multiblock with its north-west-bottom corner at {@code origin}, mirrored north-south so the controller
     * (in the north wall in multiblocks.json) sits in the south wall and faces south, towards the cameras.
     */
    private static void placeMultiblock(ServerLevel level, Multiblock mb, BlockPos origin) {
        for (int y = 0; y < mb.h(); y++) for (int z = 0; z < mb.d(); z++) for (int x = 0; x < mb.w(); x++) {
            String id = mb.legend().get(mb.layers().get(y)[z].charAt(x));
            BlockPos pos = origin.offset(x, y, mb.d() - 1 - z);
            if (id == null || id.equals("air")) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                continue;
            }
            setFacing(level, pos, BuiltInRegistries.BLOCK.get(id.contains(":") ? ResourceLocation.parse(id) : Robotica.id(id)), Direction.SOUTH);
        }
        // connect blocks that shape to their neighbours (collider segments) to the ones placed before them
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(mb.w() - 1, mb.h() - 1, mb.d() - 1))) {
            BlockState state = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (shaped != state) level.setBlock(pos, shaped, 2);
        }
    }

    private static final String[] MACHINES = {"assembler", "centrifuge", "alloy_smelter", "electric_furnace", "grinder"};
    private static final BlockPos CORE_REACTOR = new BlockPos(42, Y + 2, 104), BANK = new BlockPos(50, Y + 2, 104);
    private static final BlockPos SPIRE = new BlockPos(57, Y, 103), COLLIDER = new BlockPos(67, Y, 104);
    private static final BlockPos CHARGER = new BlockPos(82, Y, 100);
    private static final BlockPos TABLE = new BlockPos(170, Y, 160);
    private static final BlockPos BENCH = new BlockPos(200, Y, 100);
    private static final BlockPos TYRANT = new BlockPos(230, Y, 100);
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final String[] ARMOR_NAMES = {"boots", "leggings", "chestplate", "helmet"};

    private static void newScenes() {
        // Exo-Frame Mk1 to Mk4 on armor stands.
        if (wants("14_exo_frames")) step(20, () -> server(sp -> {
            for (int mk = 1; mk <= 4; mk++) {
                StringBuilder armor = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    if (i > 0) armor.append(',');
                    armor.append("{id:\"robotica:exo_").append(ARMOR_NAMES[i]).append("_mk").append(mk).append("\",count:1}");
                }
                sp.serverLevel().setBlock(new BlockPos(mk * 2 - 2, Y - 1, 100), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                command(sp, "summon minecraft:armor_stand " + (mk * 2 - 1.5) + " " + Y + " 100.5 {Rotation:[0f,0f],NoBasePlate:1b,ShowArms:1b,ArmorItems:[" + armor + "]}");
            }
        }));
        if (wants("14_exo_frames")) {
            camera(3, Y + 1.2, 104.6, 180, 6);
            step(20, () -> {});
            shot("14_exo_frames");
        }

        // Dev only (not in shots.py): Exo-Frame effects on an armor stand in Mk4 armor, then the Sonar front ring.
        if (wants("exo_fx")) {
            step(20, () -> server(sp -> {
                StringBuilder armor = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    if (i > 0) armor.append(',');
                    armor.append("{id:\"robotica:exo_").append(ARMOR_NAMES[i]).append("_mk4\",count:1}");
                }
                sp.serverLevel().setBlock(new BlockPos(20, Y - 1, 100), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                command(sp, "summon minecraft:armor_stand 20.5 " + Y + " 100.5 {Tags:[\"exo_fx\"],Rotation:[0f,0f],NoBasePlate:1b,ShowArms:1b,ArmorItems:[" + armor + "]}");
            }));
            camera(20.5, Y + 0.6, 103.4, 180, 26);
            // a step runs its action, then waits its delay
            step(20, () -> {});
            exoFx(com.arno.robotica.exo.net.ExoFxPayload.MED);
            step(5, () -> {});
            grab(40, "dev_exo_fx_med");
            exoFx(com.arno.robotica.exo.net.ExoFxPayload.SHIELD_FULL);
            step(2, () -> {});
            grab(30, "dev_exo_fx_shield");
            exoFx(com.arno.robotica.exo.net.ExoFxPayload.SPRING);
            exoFx(com.arno.robotica.exo.net.ExoFxPayload.LANDED);
            step(3, () -> {});
            grab(30, "dev_exo_fx_land");
            step(8, () -> server(sp -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new com.arno.robotica.exo.net.ExoSonarPayload(24, 200))));
            grab(5, "dev_exo_fx_sonar_a");
            grab(30, "dev_exo_fx_sonar_b");
        }

        // Industry machines Mk1 to Mk4, one row per machine, each working on something.
        if (wants("15_industry_machines", "16_grinder_furnace")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            for (int row = 0; row < MACHINES.length; row++) for (int mk = 1; mk <= 4; mk++) {
                BlockPos pos = new BlockPos(18 + mk * 2, Y, 100 + row * 2);
                setFacing(level, pos, block(MACHINES[row] + "_mk" + mk), Direction.SOUTH);
                fillEnergy(level, pos);
                IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                Item input = switch (MACHINES[row]) {
                    case "grinder", "electric_furnace" -> Items.RAW_IRON;
                    case "alloy_smelter" -> Items.COPPER_INGOT;
                    case "centrifuge" -> Items.GRAVEL;
                    default -> Items.IRON_INGOT;
                };
                if (items != null) ItemHandlerHelper.insertItem(items, new ItemStack(input, 32), false);
            }
        }));
        if (wants("15_industry_machines", "16_grinder_furnace")) {
            camera(23.5, Y + 5, 113.5, 180, 32);
            step(40, () -> {});
            shot("15_industry_machines");
            camera(23.5, Y + 2.2, 112.4, 180, 16);
            step(10, () -> {});
            shot("16_grinder_furnace");
        }

        // Item pipes: chest, Grinder, Electric Furnace, chest.
        if (wants("17_item_pipes", "gui_101_item_pipe")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            int z = 124;
            setFacing(level, new BlockPos(20, Y, z), Blocks.CHEST, Direction.SOUTH);
            setFacing(level, new BlockPos(24, Y, z), block("grinder_mk2"), Direction.SOUTH);
            setFacing(level, new BlockPos(29, Y, z), block("electric_furnace_mk2"), Direction.SOUTH);
            setFacing(level, new BlockPos(33, Y, z), Blocks.CHEST, Direction.SOUTH);
            List<BlockPos> pipes = new ArrayList<>();
            for (int x : new int[]{21, 22, 23}) pipes.add(new BlockPos(x, Y, z));
            pipes.add(new BlockPos(24, Y + 1, z));
            for (int x : new int[]{24, 25, 26, 27, 28, 29}) pipes.add(new BlockPos(x, Y + 2, z));
            pipes.add(new BlockPos(29, Y + 1, z));
            for (int x : new int[]{30, 31, 32}) pipes.add(new BlockPos(x, Y, z));
            for (int i = 0; i < pipes.size(); i++) level.setBlock(pipes.get(i), block(i >= 4 && i < 10 ? "item_pipe_mk2" : "item_pipe").defaultBlockState(), 3);
            for (BlockPos p : pipes) reshape(level, p);
            // extract from the chest, the Grinder's top and the Furnace's east side
            BlockPos[] extract = {new BlockPos(21, Y, z), new BlockPos(24, Y + 1, z), new BlockPos(30, Y, z)};
            Direction[] from = {Direction.WEST, Direction.DOWN, Direction.WEST};
            for (int i = 0; i < extract.length; i++) {
                if (level.getBlockEntity(extract[i]) instanceof ItemPipeBlockEntity pipe) pipe.setMode(from[i], PipeMode.EXTRACT);
                reshape(level, extract[i]);
            }
            fillEnergy(level, new BlockPos(24, Y, z));
            fillEnergy(level, new BlockPos(29, Y, z));
        }));
        if (wants("17_item_pipes")) {
            camera(26.5, Y + 3.2, 130, 180, 16);
            step(20, () -> {});
            shot("17_item_pipes");
        }

        // Core Reactor, Capacitor Bank, Tesla Spire and Ring Collider, built from the wiki's multiblock examples.
        boolean energy = wants("18_energy_multiblocks", "19_core_reactor", "20_capacitor_bank", "21_tesla_spire", "28_ring_collider",
                "collider_burst", "gui_98_core_reactor_formed", "gui_99_bank_formed", "gui_100_spire_formed", "gui_102_collider_formed");
        if (energy) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            placeMultiblock(level, multiblock("core_reactor"), new BlockPos(40, Y, 100));
            placeMultiblock(level, multiblock("capacitor_bank_5"), new BlockPos(48, Y, 100));
            placeMultiblock(level, multiblock("tesla_spire"), SPIRE);
            placeMultiblock(level, multiblock("ring_collider"), new BlockPos(64, Y, 98));
        }));
        if (energy) step(40, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            // fuel and a Servo Core through the access ports, energy into the bank through its ports
            for (BlockPos p : BlockPos.betweenClosed(40, Y, 100, 54, Y + 4, 106)) {
                BlockState state = level.getBlockState(p);
                if (state.is(block("bank_port"))) fillEnergy(level, p.immutable());
                if (!state.is(block("reactor_access_port"))) continue;
                IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, p.immutable(), null);
                if (items != null) {
                    ItemHandlerHelper.insertItem(items, new ItemStack(item("thorium_fuel_pellet"), 16), false);
                    ItemHandlerHelper.insertItem(items, new ItemStack(item("servo_core")), false);
                }
            }
            IItemHandler spireItems = level.getCapability(Capabilities.ItemHandler.BLOCK, SPIRE, Direction.NORTH);
            if (spireItems != null) ItemHandlerHelper.insertItem(spireItems, new ItemStack(item("thorium_fuel_pellet"), 16), false);
            IItemHandler colliderItems = level.getCapability(Capabilities.ItemHandler.BLOCK, COLLIDER, Direction.NORTH);
            if (colliderItems != null) ItemHandlerHelper.insertItem(colliderItems, new ItemStack(item("fusion_fuel_pellet"), 16), false);
            if (level.getBlockEntity(COLLIDER) instanceof com.arno.robotica.energy.block.ColliderBlockEntity collider) {
                collider.setCharge(collider.chargeNeeded());
            }
        }));
        if (energy) step(240, () -> {});
        if (wants("18_energy_multiblocks")) {
            camera(55.5, Y + 9, 124, 180, 22);
            shot("18_energy_multiblocks");
        }
        if (wants("19_core_reactor")) {
            camera(42.5, Y + 3, 108.6, 180, 10);
            shot("19_core_reactor");
        }
        if (wants("20_capacitor_bank")) {
            camera(50.5, Y + 3, 108.6, 180, 10);
            shot("20_capacitor_bank");
        }
        if (wants("21_tesla_spire")) {
            camera(57.5, Y + 5, 112, 180, -25);
            step(1, () -> server(sp -> {
                if (sp.serverLevel().getBlockEntity(SPIRE) instanceof com.arno.robotica.energy.block.SpireBlockEntity spire) {
                    spire.strike(sp.serverLevel(), sp.serverLevel().getGameTime());
                }
            }));
            grab(3, "21_tesla_spire");
        }
        if (wants("28_ring_collider", "collider_burst")) {
            camera(67.5, Y + 7, 109, 180, 45);
            shot("28_ring_collider");
            // a burst a tick apart, for checking the collision flash at the controller (not used on the site)
            for (int i = 0; i < 12; i++) grab(1, "collider_burst_" + i);
        }

        // Wireless Charger feeding a player in an empty Exo-Frame Mk4 (third person, from the front).
        if (wants("22_wireless_charger")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            setFacing(level, CHARGER, block("wireless_charger"), Direction.SOUTH);
            setFacing(level, CHARGER.north(), block("accumulator_3"), Direction.SOUTH);
            fillEnergy(level, CHARGER.north());
            fillEnergy(level, CHARGER);
            for (int i = 0; i < 4; i++) {
                ItemStack piece = new ItemStack(item("exo_" + ARMOR_NAMES[i] + "_mk4"));
                ItemEnergy.set(piece, 0);
                sp.setItemSlot(ARMOR[i], piece);
            }
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.teleportTo(level, CHARGER.getX() - 2.5, Y, CHARGER.getZ() + 0.5, 20, -12);
        }));
        if (wants("22_wireless_charger")) {
            step(5, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            step(80, () -> {});
            shot("22_wireless_charger");
            step(5, () -> mc().options.setCameraType(CameraType.FIRST_PERSON));
            step(5, () -> server(sp -> {
                for (EquipmentSlot slot : ARMOR) sp.setItemSlot(slot, ItemStack.EMPTY);
            }));
        }

        // Excavator and Survey Rig, Mk1 to Mk4.
        if (wants("23_excavators_survey_rigs")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            String[] tiers = {"", "_mk2", "_mk3", "_mk4"};
            for (int i = 0; i < 4; i++) {
                setFacing(level, new BlockPos(20 + i * 2, Y, 140), block("excavator" + tiers[i]), Direction.SOUTH);
                setFacing(level, new BlockPos(29 + i * 2, Y, 140), block("survey_rig" + tiers[i]), Direction.SOUTH);
            }
        }));
        if (wants("23_excavators_survey_rigs")) {
            camera(28, Y + 3, 147.5, 180, 15);
            step(20, () -> {});
            shot("23_excavators_survey_rigs");
        }

        // Spark Lamps lighting a closed stone cave; a close-up of one, and frames a tick apart with a lamp zapping in.
        boolean lamps = wants("24_spark_lamp_cave", "29_spark_lamp_close", "spark_lamp_frame");
        if (lamps) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            BlockState[] rock = {Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
                    Blocks.TUFF.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.IRON_ORE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
            int x0 = 100, z0 = 120, size = 16, height = 7;
            for (int x = -1; x <= size; x++) for (int y = -1; y <= height; y++) for (int z = -1; z <= size; z++) {
                boolean shell = x < 0 || x >= size || y < 0 || y >= height || z < 0 || z >= size;
                boolean edge = x == 0 || x == size - 1 || z == 0 || z == size - 1;
                // a few bulges so the walls and roof look less flat
                boolean bulge = !shell && (edge && (x * 5 + y * 11 + z * 3) % 4 == 0 || y == height - 1 && (x * 3 + z * 5) % 3 == 0);
                BlockState state = shell || bulge ? rock[Math.floorMod(x * 31 + y * 17 + z * 7, rock.length)] : Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(x0 + x, Y + y, z0 + z), state, 3);
            }
            SparkLampBlock lamp = (SparkLampBlock) block("spark_lamp");
            for (int[] l : new int[][]{{5, 5}, {12, 11}, {3, 12}}) {
                level.setBlock(new BlockPos(x0 + l[0], Y, z0 + l[1]), lamp.facing(Direction.UP), 3);
            }
            for (int[] c : new int[][]{{9, 4}}) {
                BlockPos pos = new BlockPos(x0 + c[0], Y + height - 2, z0 + c[1]);
                level.setBlock(pos.above(), rock[0], 3);
                level.setBlock(pos, lamp.facing(Direction.DOWN), 3);
            }
            level.setBlock(new BlockPos(x0, Y + 2, z0 + 8), lamp.facing(Direction.EAST), 3);
        }));
        if (wants("24_spark_lamp_cave")) {
            camera(101.5, Y + 2.6, 134.5, -135, 8);
            step(60, () -> {});
            shot("24_spark_lamp_cave");
        }
        if (wants("29_spark_lamp_close")) {
            camera(101.9, Y + 0.9, 129.3, 117, 0);
            step(60, () -> {});
            shot("29_spark_lamp_close");
        }
        if (wants("spark_lamp_frame")) {
            camera(105.5, Y, 127.3, 180, 34);
            step(40, () -> {});
            // frames a tick apart (not used on the site): arcs, flicker, a new lamp zapping in at frame 4 and popping out at 11
            for (int i = 0; i < 16; i++) {
                if (i == 4) step(0, () -> server(sp -> {
                    BlockPos pos = new BlockPos(104, Y, 125);
                    sp.serverLevel().setBlock(pos, ((SparkLampBlock) block("spark_lamp")).facing(Direction.UP), 3);
                    com.arno.robotica.gear.lamp.SparkLamps.zap(sp.serverLevel(), pos, true);
                }));
                if (i == 11) step(0, () -> server(sp -> {
                    BlockPos pos = new BlockPos(104, Y, 125);
                    sp.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    com.arno.robotica.gear.lamp.SparkLamps.zap(sp.serverLevel(), pos, false);
                }));
                grab(1, "spark_lamp_frame_" + i);
            }
            // the wall lamp from a few blocks away, arcs sped up, a frame every other tick
            camera(104.5, Y, 128.5, 90, -12);
            step(20, () -> com.arno.robotica.gear.client.SparkWisps.demo = true);
            for (int i = 0; i < 24; i++) grab(2, "spark_lamp_frame_wall_" + i);
            step(1, () -> com.arno.robotica.gear.client.SparkWisps.demo = false);
        }

        structureFxFrames();

        // Architect Table: four separate buildings, one per style, around the table plot (queued), one joined pair.
        if (wants("25_architect_build", "gui_96_architect_demolish")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            setFacing(level, TABLE, block("architect_table"), Direction.SOUTH);
            if (!(level.getBlockEntity(TABLE) instanceof ArchitectTableBlockEntity table)) return;
            Layout layout = table.layout();
            int[][] plots = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {2, 1}};
            BuildStyle[] styles = {BuildStyle.TIMBERFRAME, BuildStyle.COPPER_WORKS, BuildStyle.STEEL_LAB, BuildStyle.NULL_SPIRE,
                    BuildStyle.NULL_SPIRE};
            for (int i = 0; i < plots.length; i++) layout.queue(Plots.index(plots[i][0], plots[i][1]), styles[i]);
            // an inner wall with a doorway between the joined pair
            layout.cycleWall(Plots.index(1, 1), Plots.E);
            ShellPlacer.placeAll(level, TABLE, layout);
            for (int[] p : plots) {
                int plot = Plots.index(p[0], p[1]);
                layout.markBuilt(plot, layout.signature(plot));
            }
            layout.queue(Plots.CENTER, BuildStyle.COPPER_WORKS);
            table.setMatter(new Matter(820, 360, 140));
            table.setChanged();
        }));
        if (wants("25_architect_build")) {
            camera(TABLE.getX() - 24, Y + 17, TABLE.getZ() - 26, -40, 30);
            step(40, () -> {});
            shot("25_architect_build");
        }

        // Solar Panels Mk1 to Mk4.
        if (wants("26_solar_panels")) step(20, () -> server(sp -> {
            for (int mk = 1; mk <= 4; mk++) setFacing(sp.serverLevel(), new BlockPos(58 + mk * 2, Y, 140), block("solar_panel_mk" + mk), Direction.SOUTH);
        }));
        if (wants("26_solar_panels")) {
            camera(63.5, Y + 2.2, 143.8, 180, 26);
            step(20, () -> {});
            shot("26_solar_panels");
        }

        // The Forge Tyrant by its Forge Altar on a scorched floor, venting with its furnace doors open (no AI, for the photo).
        if (wants("27_forge_tyrant")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            BlockState[] floor = {Blocks.NETHERRACK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
                    Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState()};
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                BlockPos p = TYRANT.offset(x, -1, z);
                boolean lava = (x == -5 || x == 5) && (z == -3 || z == -4) || x == 4 && z == 3;
                level.setBlock(p, lava ? Blocks.LAVA.defaultBlockState() : floor[Math.floorMod((x * 73856093) ^ (z * 19349663), floor.length)], 3);
            }
            setFacing(level, TYRANT.offset(-4, 0, -2), block("forge_altar"), Direction.SOUTH);
            command(sp, "summon robotica:forge_tyrant " + (TYRANT.getX() + 0.5) + " " + Y + " " + (TYRANT.getZ() + 0.5)
                    + " {NoAI:1b,PersistenceRequired:1b,Rotation:[20f,0f]}");
            for (var e : level.getEntitiesOfClass(com.arno.robotica.boss.entity.ForgeTyrant.class, new net.minecraft.world.phys.AABB(TYRANT).inflate(4))) {
                e.setYHeadRot(20);
                e.setYBodyRot(20);
                e.startVent();
            }
        }));
        if (wants("27_forge_tyrant")) {
            camera(TYRANT.getX() - 1.5, Y + 2.4, TYRANT.getZ() + 6.5, 198, 6);
            step(40, () -> {});
            shot("27_forge_tyrant");
            step(10, () -> server(sp -> command(sp, "kill @e[type=robotica:forge_tyrant]")));
        }
        robotScenes();
    }

    private static final BlockPos PEN = new BlockPos(-70, Y, 40);
    private static final BlockPos HAUL = new BlockPos(-70, Y, 80);

    /** Turns a mob (no AI) to look at a point. */
    /**
     * Dev only (not in shots.py): frames a few ticks apart of a Capacitor Bank and a Replicator forming (a corner put
     * back) and breaking (a corner taken out), by day and by night, and the Replicator's spawn shimmer.
     */
    private static void structureFxFrames() {
        if (!wants("structure_fx_frame")) return;
        BlockPos bank = new BlockPos(140, Y, 20), corner = new BlockPos(144, Y + 4, 24);
        BlockPos rep = new BlockPos(150, Y, 20), repCorner = new BlockPos(152, Y + 2, 22), repCtrl = new BlockPos(151, Y + 1, 22);
        camera(147.5, Y + 5.5, 30, 146, 18);
        step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            placeMultiblock(level, multiblock("capacitor_bank_5"), bank);
            level.setBlock(corner, Blocks.AIR.defaultBlockState(), 3);
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) {
                BlockPos p = rep.offset(x, y, z);
                boolean glass = (x == 1 && z == 1 && y == 2) || (x == 0 && y == 1 && z == 1) || (x == 2 && y == 1 && z == 1);
                if (x == 1 && y == 1 && z == 1) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                else level.setBlock(p, block(glass ? "replicator_glass" : "replicator_frame").defaultBlockState(), 3);
            }
            setFacing(level, repCtrl, block("replicator_controller"), Direction.SOUTH);
            level.setBlock(repCorner, Blocks.AIR.defaultBlockState(), 3);
        }));
        step(80, () -> {});
        for (String time : new String[]{"day", "night"}) {
            if (time.equals("night")) step(20, () -> server(sp -> command(sp, "time set 18000")));
            camera(147.5, Y + 5.5, 30, 146, 18);
            step(1, () -> server(sp -> sp.serverLevel().setBlock(corner, block("bank_casing").defaultBlockState(), 3)));
            for (int i = 0; i < 16; i++) grab(3, "structure_fx_frame_bank_form_" + time + "_" + i);
            step(50, () -> {});
            step(1, () -> server(sp -> sp.serverLevel().setBlock(corner, Blocks.AIR.defaultBlockState(), 3)));
            for (int i = 0; i < 12; i++) grab(2, "structure_fx_frame_bank_break_" + time + "_" + i);
        }
        camera(154.5, Y + 3.5, 26, 146, 20);
        step(1, () -> server(sp -> sp.serverLevel().setBlock(repCorner, block("replicator_frame").defaultBlockState(), 3)));
        for (int i = 0; i < 14; i++) grab(3, "structure_fx_frame_replicator_form_" + i);
        step(10, () -> {});
        for (int k = 0; k < 2; k++) {
            step(1, () -> server(sp -> com.arno.robotica.energy.net.StructureFxPayload.send(sp.serverLevel(),
                    com.arno.robotica.energy.net.StructureFxPayload.SPAWN, repCtrl,
                    new net.minecraft.world.level.levelgen.structure.BoundingBox(151, Y, 23, 151, Y + 1, 23), 0x5CFFC8)));
            for (int i = 0; i < 8; i++) grab(2, "structure_fx_frame_spawn_" + k + "_" + i);
            step(20, () -> {});
        }
        step(1, () -> server(sp -> sp.serverLevel().setBlock(repCorner, Blocks.AIR.defaultBlockState(), 3)));
        for (int i = 0; i < 10; i++) grab(2, "structure_fx_frame_replicator_break_" + i);
        step(10, () -> server(sp -> command(sp, "time set 6000")));
    }

    private static void face(net.minecraft.world.entity.Mob mob, double x, double z) {
        float yaw = (float) (Math.toDegrees(Math.atan2(z - mob.getZ(), x - mob.getX())) - 90.0);
        mob.setYRot(yaw);
        mob.yRotO = yaw;
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
    }

    private static <T extends net.minecraft.world.entity.Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double z, double y) {
        T mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(x, y, z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    /** Ranchers at work in a fenced pen with a chest, and Hauler Drones carrying a cow and a villager. */
    private static void robotScenes() {
        if (wants("rancher_pen", "rancher_close")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                BlockPos p = PEN.offset(x, 0, z);
                boolean edge = Math.abs(x) == 6 || Math.abs(z) == 6;
                level.setBlock(p.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                if (edge) level.setBlock(p, (x == 0 && z == 6 ? Blocks.OAK_FENCE_GATE : Blocks.OAK_FENCE).defaultBlockState(), 3);
            }
            BlockPos chest = PEN.offset(-4, 0, -4);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(chest) instanceof net.minecraft.world.Container c) {
                c.setItem(0, new ItemStack(Items.WHEAT, 48));
                c.setItem(1, new ItemStack(Items.BUCKET, 8));
            }
            level.setBlock(PEN.offset(-3, 0, -5), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            level.setBlock(PEN.offset(-5, 0, -2), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            level.setBlock(PEN.offset(-5, 1, -2), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            level.setBlock(PEN.offset(4, 0, -5), Blocks.COMPOSTER.defaultBlockState(), 3);
            level.setBlock(PEN.offset(5, 0, -5), Blocks.WATER_CAULDRON.defaultBlockState(), 3);

            var cow = spawn(level, EntityType.COW, PEN.getX() - 0.5, PEN.getZ() + 1.5, Y);
            var cow2 = spawn(level, EntityType.COW, PEN.getX() + 3.5, PEN.getZ() - 2.5, Y);
            var sheep = spawn(level, EntityType.SHEEP, PEN.getX() + 4.6, PEN.getZ() + 3.2, Y);
            var sheep2 = spawn(level, EntityType.SHEEP, PEN.getX() + 0.5, PEN.getZ() - 3.5, Y);
            var pig = spawn(level, EntityType.PIG, PEN.getX() - 3.5, PEN.getZ() + 3.5, Y);
            var chick = spawn(level, EntityType.CHICKEN, PEN.getX() + 1.5, PEN.getZ() + 4.5, Y);
            var chick2 = spawn(level, EntityType.CHICKEN, PEN.getX() - 1.5, PEN.getZ() - 1.5, Y);
            if (sheep2 != null) sheep2.setColor(net.minecraft.world.item.DyeColor.BROWN);
            if (cow != null) face(cow, PEN.getX() + 3, PEN.getZ() + 4);
            if (cow2 != null) face(cow2, PEN.getX() - 3, PEN.getZ() + 2);
            if (sheep != null) face(sheep, PEN.getX(), PEN.getZ() + 8);
            if (sheep2 != null) face(sheep2, PEN.getX() + 5, PEN.getZ());
            if (pig != null) face(pig, PEN.getX() + 2, PEN.getZ() + 9);
            if (chick != null) face(chick, PEN.getX() - 4, PEN.getZ() + 9);
            if (chick2 != null) face(chick2, PEN.getX() + 4, PEN.getZ() + 2);

            var mk1 = spawn(level, com.arno.robotica.automation.rancher.RancherContent.RANCHER_ENTITY.get(), PEN.getX() - 1.9, PEN.getZ() + 3.0, Y);
            var mk2 = spawn(level, com.arno.robotica.automation.rancher.RancherContent.RANCHER_ENTITY.get(), PEN.getX() + 2.5, PEN.getZ() + 3.4, Y);
            if (mk1 != null) {
                mk1.setOwner(sp);
                mk1.setHome(PEN);
                mk1.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT));
                if (cow != null) face(mk1, cow.getX(), cow.getZ());
            }
            if (mk2 != null) {
                mk2.setTier(2);
                mk2.setOwner(sp);
                mk2.setHome(PEN);
                mk2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
                if (sheep != null) face(mk2, sheep.getX(), sheep.getZ());
            }
        }));
        hud(false);
        if (wants("rancher_pen", "rancher_close")) {
            camera(PEN.getX() + 0.5, Y + 5.5, PEN.getZ() + 12.5, 180, 24);
            rancherPoses();
            shot("rancher_pen");
            camera(PEN.getX() + 1.6, Y + 1.6, PEN.getZ() + 7.6, 180, 12);
            rancherPoses();
            shot("rancher_close");
        }

        // Hauler Drones: Mk1 with a cow, Mk2 with a villager; an empty Mk1 with its claw open.
        if (wants("hauler_drone")) step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            for (int x = -5; x <= 5; x++) for (int z = -3; z <= 3; z++) {
                level.setBlock(HAUL.offset(x, -1, z), (Math.floorMod(x * 7 + z * 3, 5) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState(), 3);
            }
            level.setBlock(HAUL.offset(3, 0, -2), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            level.setBlock(HAUL.offset(-4, 0, -2), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(HAUL.offset(-4, 0, -1), Blocks.OAK_FENCE.defaultBlockState(), 3);
            haul(sp, level, EntityType.COW, 1, HAUL.getX() - 1.5, HAUL.getZ() + 0.5, Y + 0.8, 25.0F);
            haul(sp, level, EntityType.VILLAGER, 2, HAUL.getX() + 2.0, HAUL.getZ() - 0.5, Y + 0.7, -20.0F);
            var empty = spawn(level, com.arno.robotica.drones.DronesRegistry.HAULER_DRONE_ENTITY.get(), HAUL.getX() - 4.0, HAUL.getZ() + 1.5, Y + 2.0);
            if (empty != null) {
                empty.setOwner(sp);
                face(empty, HAUL.getX(), HAUL.getZ() + 9);
            }
        }));
        if (wants("hauler_drone")) {
            camera(HAUL.getX() - 0.5, Y + 0.4, HAUL.getZ() + 6.8, 180, -9);
            step(20, () -> {});
            shot("hauler_drone");
        }
    }

    /** Restarts the job poses so the next shot catches them mid-move. */
    private static void rancherPoses() {
        step(6, () -> server(sp -> {
            for (var r : sp.serverLevel().getEntitiesOfClass(com.arno.robotica.automation.rancher.Rancher.class, new net.minecraft.world.phys.AABB(PEN).inflate(8))) {
                r.showAction(r.tier() >= 2 ? com.arno.robotica.automation.rancher.Rancher.Kind.SHEAR : com.arno.robotica.automation.rancher.Rancher.Kind.FEED,
                        com.arno.robotica.automation.rancher.Rancher.Status.WORKING);
            }
        }));
    }

    /** A Hauler Drone (no AI) holding a mob with its feet at {@code feetY}. */
    private static void haul(ServerPlayer sp, ServerLevel level, EntityType<? extends net.minecraft.world.entity.Mob> type, int tier, double x, double z,
                             double feetY, float yaw) {
        var mob = spawn(level, type, x, z, feetY);
        var drone = spawn(level, com.arno.robotica.drones.DronesRegistry.HAULER_DRONE_ENTITY.get(), x, z, feetY + (mob != null ? mob.getBbHeight() : 1.0) + com.arno.robotica.drones.entity.HaulerDrone.HANG_GAP);
        if (mob == null || drone == null) return;
        drone.setTier(tier);
        drone.setOwner(sp);
        drone.setYRot(yaw);
        drone.yRotO = yaw;
        drone.setYBodyRot(yaw);
        drone.setYHeadRot(yaw);
        mob.setYRot(yaw + 30.0F);
        mob.setYBodyRot(yaw + 30.0F);
        mob.setYHeadRot(yaw + 30.0F);
        drone.grab(mob);
    }

    private static void openGui(BlockPos pos, String file) {
        if (!wants(file)) return;
        step(10, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), pos.getX() + 0.5, Math.max(Y, pos.getY() - 1), pos.getZ() + 2.5, 180, 20);
            sp.setShiftKeyDown(false);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        }));
        grab(30, file);
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });
    }

    private static void newGuis() {
        // An Item Pipe arm into the chest of the pipe scene: Extract, a whitelist and Closest first.
        final BlockPos pipePos = new BlockPos(21, Y, 124);
        if (wants("gui_101_item_pipe")) step(10, () -> server(sp -> {
            if (!(sp.serverLevel().getBlockEntity(pipePos) instanceof ItemPipeBlockEntity pipe)) return;
            pipe.filter(Direction.WEST).setStackInSlot(0, new ItemStack(Items.RAW_IRON));
            pipe.filter(Direction.WEST).setStackInSlot(1, new ItemStack(Items.RAW_COPPER));
            pipe.filter(Direction.WEST).setStackInSlot(2, new ItemStack(Items.RAW_GOLD));
            pipe.setWhitelist(Direction.WEST, true);
            pipe.setOrder(Direction.WEST, com.arno.robotica.logistics.pipe.PipeOrder.CLOSEST_FIRST);
            sp.teleportTo(sp.serverLevel(), pipePos.getX() + 0.5, Y, pipePos.getZ() + 2.5, 180, 20);
            sp.setShiftKeyDown(false);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pipePos).add(-0.4, 0, 0), Direction.SOUTH, pipePos, false));
        }));
        grab(30, "gui_101_item_pipe");
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });

        // The formed energy multiblocks.
        openGui(CORE_REACTOR, "gui_98_core_reactor_formed");
        openGui(BANK, "gui_99_bank_formed");
        openGui(SPIRE, "gui_100_spire_formed");
        openGui(COLLIDER, "gui_102_collider_formed");

        // The Architect Table with built plots: the Demolish button shows.
        if (wants("gui_96_architect_demolish")) step(10, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), TABLE.getX() + 0.5, Y, TABLE.getZ() + 2.5, 180, 30);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.SOUTH, TABLE, false));
        }));
        grab(25, "gui_96_architect_demolish");
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });

        // Tinker's Bench with a Null Drill full of modules, a few more modules in the inventory.
        if (wants("gui_97_tinkers_bench")) step(10, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            setFacing(level, BENCH, block("tinkers_bench"), Direction.SOUTH);
            sp.teleportTo(level, BENCH.getX() + 0.5, Y, BENCH.getZ() + 2.5, 180, 30);
            String[] spare = {"silk_touch_module", "night_vision_module_2", "jet_assist_module_2", "kinetic_shield_module", "step_assist_module"};
            for (int i = 0; i < spare.length; i++) sp.getInventory().setItem(9 + i, new ItemStack(item(spare[i])));
            sp.gameMode.useItemOn(sp, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(BENCH), Direction.SOUTH, BENCH, false));
        }));
        if (wants("gui_97_tinkers_bench")) step(10, () -> server(sp -> {
            if (!(sp.containerMenu instanceof TinkersBenchMenu menu)) return;
            ItemStack drill = new ItemStack(item("null_drill"));
            ItemEnergy.fill(drill);
            String[] candidates = {"fortune_module_3", "overclock_module_3", "power_regulator_module_3", "magnet_module_3", "auto_pickup_module",
                    "lamp_placer_module", "void_filter_module", "sonar_pulse_module_3"};
            List<String> used = new ArrayList<>();
            for (int slot = 0; slot < Modules.slots(drill); slot++) {
                for (String c : candidates) {
                    ItemStack module = new ItemStack(item(c));
                    if (used.contains(c) || module.isEmpty() || Modules.refusal(drill, slot, module, List.of()) != null) continue;
                    Modules.setModule(drill, slot, module);
                    used.add(c);
                    break;
                }
            }
            menu.getSlot(TinkersBenchMenu.TOOL_SLOT).set(drill);
            menu.broadcastChanges();
        }));
        grab(25, "gui_97_tinkers_bench");
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });
        step(5, () -> server(sp -> sp.getInventory().clearContent()));
    }
}
