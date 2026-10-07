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
 */
public final class Showcase {
    private Showcase() {}

    private record Step(int delay, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
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

    private static void shot(String name) {
        step(10, () -> Screenshot.grab(mc().gameDirectory, name + ".png", mc().getMainRenderTarget(), msg -> {}));
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

        // Scene 1: every block on a grid, facing the camera (south).
        step(40, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            List<Block> blocks = roboticaBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos pos = new BlockPos((i % 12) * 2, Y, (i / 12) * 2);
                setFacing(level, pos, blocks.get(i), Direction.SOUTH);
            }
        }));
        hud(false);
        camera(11, Y + 9, 22, 180, 32);
        shot("01_all_blocks");
        camera(5, Y + 3, 7, 180, 20);
        shot("02_blocks_close_a");
        camera(17, Y + 3, 7, 180, 20);
        shot("03_blocks_close_b");

        // Scene 2: a joined pair of buildings in each style, side by side, then a 2x2 hall from the inside.
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
        step(20, () -> server(sp -> {
            Layout layout = new Layout();
            for (int plot : new int[]{Plots.CENTER, Plots.index(1, 0), Plots.index(0, 1), Plots.index(1, 1)}) layout.queue(plot, BuildStyle.STEEL_LAB);
            ShellPlacer.placeAll(sp.serverLevel(), new BlockPos(64, Y, 44), layout);
        }));
        camera(62, Y + 3, 42, -45, 12);
        shot("06_workshop_inside");

        // Scene 3: robots at work.
        step(40, () -> server(sp -> {
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
        camera(-25, Y + 3, 14, 180, 15);
        shot("07_robots");

        // Scene 4: replicator, formed, with a zombie vial and energy.
        step(20, () -> server(sp -> {
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
        step(60, () -> server(sp -> {
            if (sp.serverLevel().getBlockEntity(new BlockPos(1, Y + 1, 42)) instanceof ReplicatorControllerBlockEntity be) {
                be.vial.setStackInSlot(0, Essence.completeVial(EntityType.ZOMBIE));
                be.energy().setEnergy(1_000_000);
            }
        }));
        camera(1.5, Y + 2.5, 47, 180, 12);
        step(100, () -> {});
        shot("08_replicator");

        // Scene 5: two linked, powered Portal Projectors (the portal is drawn by the block entity renderer) and a warp pad.
        step(20, () -> server(sp -> {
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
        camera(22.5, Y + 3, 47, 180, 8);
        step(60, () -> {});
        shot("09_gate_and_pad");

        // Scene 6: drill HUD and area outline.
        step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            for (int x = -6; x <= 6; x++) for (int y = 0; y < 6; y++) level.setBlock(new BlockPos(x, Y + y, -20), Blocks.STONE.defaultBlockState(), 3);
            ItemStack drill = new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("magma_drill")));
            ItemEnergy.fill(drill);
            drill.set(GearComponents.MODE.get(), AreaMode.CUBE_3);
            sp.setItemInHand(InteractionHand.MAIN_HAND, drill);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(sp.serverLevel(), 0.5, Y + 2, -16.5, 180, 0);
        }));
        hud(true);
        step(40, () -> {});
        shot("10_drill_hud_outline");

        // Scene 7: a Tesla network on an Accumulator, Linker in hand so the arcs show at full strength.
        step(20, () -> server(sp -> {
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
        camera(41.5, Y + 4, -8, 180, 22);
        step(60, () -> {});
        shot("11_tesla_network");

        // Scene 8: the Scrap Colossus on its altar (no AI, for the photo).
        step(20, () -> server(sp -> {
            MinecraftServer s = sp.server;
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "difficulty normal");
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerLevel level = sp.serverLevel();
            level.setBlock(new BlockPos(70, Y, -20), block("colossus_altar").defaultBlockState(), 3);
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                    "summon robotica:scrap_colossus 70.5 " + (Y + 1) + " -19.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        }));
        camera(70.5, Y + 3, -11, 180, 8);
        step(60, () -> {});
        shot("12_scrap_colossus");

        // Scene 9: a Rusted Foundry.
        step(20, () -> server(sp -> {
            MinecraftServer s = sp.server;
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=robotica:scrap_colossus]");
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "place structure robotica:rusted_foundry 120 " + Y + " -20");
        }));
        camera(120, Y + 22, 6, 180, 42);
        step(80, () -> {});
        shot("13_rusted_foundry");

        newScenes();

        // Scene 10: a Storage Terminal with things in it.
        step(20, () -> server(sp -> {
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
        step(30, () -> {});
        shot("gui_95_storage_terminal_full");
        step(5, () -> { if (mc().player != null && mc().screen != null) mc().player.closeContainer(); });

        newGuis();

        // GUIs: every block entity that is a menu provider.
        step(10, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
        List<Block> blocks = roboticaBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            final int idx = i;
            final BlockPos pos = new BlockPos((i % 12) * 2, Y, (i / 12) * 2);
            final String name = BuiltInRegistries.BLOCK.getKey(blocks.get(i)).getPath();
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
        step(10, () -> server(sp -> {
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
        step(20, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), padA.getX() + 0.5, Y, padA.getZ() + 2.5, 180, 30);
            sp.setShiftKeyDown(true);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(padA), Direction.SOUTH, padA, false));
        }));
        step(25, () -> Screenshot.grab(mc().gameDirectory, "gui_90_warp_pad_owner.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });
        step(10, () -> server(sp -> {
            sp.setShiftKeyDown(false);
            sp.teleportTo(sp.serverLevel(), padA.getX() + 0.5, Y + 0.5, padA.getZ() + 0.5, 180, 30);
        }));
        step(10, () -> server(sp -> sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(padA), Direction.UP, padA, false))));
        step(25, () -> Screenshot.grab(mc().gameDirectory, "gui_91_warp_destinations.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });

        // The formed replicator from scene 4.
        step(10, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), 1.5, Y, 45.5, 180, 30);
            sp.setShiftKeyDown(false);
            BlockPos ctrl = new BlockPos(1, Y + 1, 42);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(ctrl), Direction.SOUTH, ctrl, false));
        }));
        step(25, () -> Screenshot.grab(mc().gameDirectory, "gui_92_replicator_formed.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });

        // Gear tool settings screen (opened client side, like the G key does).
        step(10, () -> server(sp -> {
            ItemStack drill = new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("magma_drill")));
            ItemEnergy.fill(drill);
            sp.setItemInHand(InteractionHand.MAIN_HAND, drill);
        }));
        step(20, () -> mc().setScreen(new ToggleScreen()));
        step(15, () -> Screenshot.grab(mc().gameDirectory, "gui_93_gear_settings.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> mc().setScreen(null));
        step(10, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));

        // Codex pages and lab.
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

        // Item sheet.
        int sheetPages = IconSheetScreen.pageCount(mc().getWindow().getGuiScaledWidth(), mc().getWindow().getGuiScaledHeight());
        for (int p = 0; p < sheetPages; p++) {
            final int pageIndex = p;
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
    }

    private static final String[] MACHINES = {"assembler", "centrifuge", "alloy_smelter", "electric_furnace", "grinder"};
    private static final BlockPos FISSION = new BlockPos(42, Y + 2, 104), BANK = new BlockPos(50, Y + 2, 104), FUSION = new BlockPos(59, Y + 1, 106);
    private static final BlockPos CHARGER = new BlockPos(82, Y, 100);
    private static final BlockPos TABLE = new BlockPos(170, Y, 160);
    private static final BlockPos BENCH = new BlockPos(200, Y, 100);
    private static final BlockPos TYRANT = new BlockPos(230, Y, 100);
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final String[] ARMOR_NAMES = {"boots", "leggings", "chestplate", "helmet"};

    private static void newScenes() {
        // Exo-Frame Mk1 to Mk4 on armor stands.
        step(20, () -> server(sp -> {
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
        camera(3, Y + 1.2, 104.6, 180, 6);
        step(20, () -> {});
        shot("14_exo_frames");

        // Industry machines Mk1 to Mk4, one row per machine, each working on something.
        step(20, () -> server(sp -> {
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
        camera(23.5, Y + 5, 113.5, 180, 32);
        step(40, () -> {});
        shot("15_industry_machines");
        camera(23.5, Y + 2.2, 112.4, 180, 16);
        step(10, () -> {});
        shot("16_grinder_furnace");

        // Item pipes: chest, Grinder, Electric Furnace, chest.
        step(20, () -> server(sp -> {
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
        camera(26.5, Y + 3.2, 130, 180, 16);
        step(20, () -> {});
        shot("17_item_pipes");

        // Fission Reactor, Capacitor Bank and Fusion Reactor, built from the wiki's multiblock examples.
        step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            placeMultiblock(level, multiblock("fission_reactor_5"), new BlockPos(40, Y, 100));
            placeMultiblock(level, multiblock("capacitor_bank_5"), new BlockPos(48, Y, 100));
            placeMultiblock(level, multiblock("fusion_reactor"), new BlockPos(56, Y, 100));
        }));
        step(100, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            // fuel through the access ports, energy into the bank through its ports
            for (BlockPos p : BlockPos.betweenClosed(40, Y, 100, 62, Y + 4, 106)) {
                BlockState state = level.getBlockState(p);
                if (state.is(block("bank_port"))) fillEnergy(level, p.immutable());
                if (!state.is(block("reactor_access_port"))) continue;
                IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, p.immutable(), null);
                if (items != null) ItemHandlerHelper.insertItem(items, new ItemStack(item(p.getX() < 48 ? "thorium_fuel_pellet" : "fusion_fuel_pellet"), 16), false);
            }
        }));
        camera(51.5, Y + 6, 119, 180, 16);
        shot("18_energy_multiblocks");
        camera(42.5, Y + 3, 108.6, 180, 10);
        shot("19_fission_reactor");
        camera(50.5, Y + 3, 108.6, 180, 10);
        shot("20_capacitor_bank");
        camera(59.5, Y + 4.5, 113, 180, 22);
        shot("21_fusion_reactor");

        // Wireless Charger feeding a player in an empty Exo-Frame Mk4 (third person, from the front).
        step(20, () -> server(sp -> {
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
        step(5, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        step(80, () -> {});
        shot("22_wireless_charger");
        step(5, () -> mc().options.setCameraType(CameraType.FIRST_PERSON));
        step(5, () -> server(sp -> {
            for (EquipmentSlot slot : ARMOR) sp.setItemSlot(slot, ItemStack.EMPTY);
        }));

        // Excavator and Survey Rig, Mk1 to Mk4.
        step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            String[] tiers = {"", "_mk2", "_mk3", "_mk4"};
            for (int i = 0; i < 4; i++) {
                setFacing(level, new BlockPos(20 + i * 2, Y, 140), block("excavator" + tiers[i]), Direction.SOUTH);
                setFacing(level, new BlockPos(29 + i * 2, Y, 140), block("survey_rig" + tiers[i]), Direction.SOUTH);
            }
        }));
        camera(28, Y + 3, 147.5, 180, 15);
        step(20, () -> {});
        shot("23_excavators_survey_rigs");

        // Spark Lamps lighting a closed stone cave.
        step(20, () -> server(sp -> {
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
        }));
        camera(101.5, Y + 2.6, 134.5, -135, 8);
        step(60, () -> {});
        shot("24_spark_lamp_cave");

        // Architect Table: four separate buildings, one per style, around the table plot (queued), one joined pair.
        step(20, () -> server(sp -> {
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
        camera(TABLE.getX() - 24, Y + 17, TABLE.getZ() - 26, -40, 30);
        step(40, () -> {});
        shot("25_architect_build");

        // Solar Panels Mk1 to Mk4.
        step(20, () -> server(sp -> {
            for (int mk = 1; mk <= 4; mk++) setFacing(sp.serverLevel(), new BlockPos(58 + mk * 2, Y, 140), block("solar_panel_mk" + mk), Direction.SOUTH);
        }));
        camera(63.5, Y + 2.2, 143.8, 180, 26);
        step(20, () -> {});
        shot("26_solar_panels");

        // The Forge Tyrant by its Forge Altar on a scorched floor, venting with its furnace doors open (no AI, for the photo).
        step(20, () -> server(sp -> {
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
        camera(TYRANT.getX() - 1.5, Y + 2.4, TYRANT.getZ() + 6.5, 198, 6);
        step(40, () -> {});
        shot("27_forge_tyrant");
        step(10, () -> server(sp -> command(sp, "kill @e[type=robotica:forge_tyrant]")));
    }

    private static void openGui(BlockPos pos, String file) {
        step(10, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), pos.getX() + 0.5, pos.getY() - 1, pos.getZ() + 2.5, 180, 20);
            sp.setShiftKeyDown(false);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        }));
        step(30, () -> Screenshot.grab(mc().gameDirectory, file + ".png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });
    }

    private static void newGuis() {
        // An Item Pipe arm into the chest of the pipe scene: Extract, a whitelist and Closest first.
        final BlockPos pipePos = new BlockPos(21, Y, 124);
        step(10, () -> server(sp -> {
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
        step(30, () -> Screenshot.grab(mc().gameDirectory, "gui_101_item_pipe.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });

        // The formed energy multiblocks.
        openGui(FISSION, "gui_98_fission_formed");
        openGui(BANK, "gui_99_bank_formed");
        openGui(FUSION, "gui_100_fusion_formed");

        // The Architect Table with built plots: the Demolish button shows.
        step(10, () -> server(sp -> {
            sp.teleportTo(sp.serverLevel(), TABLE.getX() + 0.5, Y, TABLE.getZ() + 2.5, 180, 30);
            sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.SOUTH, TABLE, false));
        }));
        step(25, () -> Screenshot.grab(mc().gameDirectory, "gui_96_architect_demolish.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });

        // Tinker's Bench with a Null Drill full of modules, a few more modules in the inventory.
        step(10, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            setFacing(level, BENCH, block("tinkers_bench"), Direction.SOUTH);
            sp.teleportTo(level, BENCH.getX() + 0.5, Y, BENCH.getZ() + 2.5, 180, 30);
            String[] spare = {"silk_touch_module", "night_vision_module_2", "jet_assist_module_2", "kinetic_shield_module", "step_assist_module"};
            for (int i = 0; i < spare.length; i++) sp.getInventory().setItem(9 + i, new ItemStack(item(spare[i])));
            sp.gameMode.useItemOn(sp, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(BENCH), Direction.SOUTH, BENCH, false));
        }));
        step(10, () -> server(sp -> {
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
        step(25, () -> Screenshot.grab(mc().gameDirectory, "gui_97_tinkers_bench.png", mc().getMainRenderTarget(), m -> {}));
        step(5, () -> {
            if (mc().player != null && mc().screen != null) mc().player.closeContainer();
        });
        step(5, () -> server(sp -> sp.getInventory().clearContent()));
    }
}
