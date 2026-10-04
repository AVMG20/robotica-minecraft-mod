package com.arno.robotica.codex.client.dev;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.plan.BlockOp;
import com.arno.robotica.architect.plan.ModuleType;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.codex.client.CodexScreen;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.logic.Essence;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
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
                buildPlan();
                wait = 60;
            }
            return;
        }
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

    private static void createWorld() {
        Minecraft mc = mc();
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

    private static void setFacing(ServerLevel level, BlockPos pos, Block block, Direction dir) {
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        else if (s.hasProperty(BlockStateProperties.FACING)) s = s.setValue(BlockStateProperties.FACING, dir);
        level.setBlock(pos, s, 3);
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(Robotica.id(name));
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

        // Scene 2: a Hall in each building style, side by side.
        step(40, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            int i = 0;
            for (BuildStyle style : BuildStyle.values()) {
                BlockPos origin = new BlockPos(60 + i * 11, Y, 0);
                for (BlockOp op : ModuleType.HALL.generate(0b0100)) {
                    BlockState st = op.piece().resolve(style);
                    if (st != null) level.setBlock(origin.offset(op.x(), op.y(), op.z()), st, 2);
                }
                i++;
            }
        }));
        camera(81, Y + 8, 26, 180, 18);
        shot("04_styles_halls");
        camera(64, Y + 2, 12, 180, 5);
        shot("05_style_timberframe_door");
        step(20, () -> server(sp -> {
            ServerLevel level = sp.serverLevel();
            int i = 0;
            for (BuildStyle style : BuildStyle.values()) {
                BlockPos origin = new BlockPos(60 + i * 11, Y, 30);
                for (BlockOp op : ModuleType.WORKSHOP.generate(0b0101)) {
                    BlockState st = op.piece().resolve(style);
                    if (st != null) level.setBlock(origin.offset(op.x(), op.y(), op.z()), st, 2);
                }
                i++;
            }
        }));
        camera(64, Y + 3, 32, 135, 20);
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

        // GUIs: every block entity that is a menu provider.
        step(10, () -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
        List<Block> blocks = roboticaBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            final int idx = i;
            final BlockPos pos = new BlockPos((i % 12) * 2, Y, (i / 12) * 2);
            final String name = BuiltInRegistries.BLOCK.getKey(blocks.get(i)).getPath();
            step(10, () -> server(sp -> {
                if (!(sp.serverLevel().getBlockEntity(pos) instanceof MenuProvider)) return;
                sp.teleportTo(sp.serverLevel(), pos.getX() + 0.5, Y, pos.getZ() + 2.5, 180, 30);
                sp.setShiftKeyDown(false);
                sp.gameMode.useItemOn(sp, sp.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
            }));
            step(15, () -> {
                if (mc().screen != null) Screenshot.grab(mc().gameDirectory, String.format("gui_%02d_%s.png", idx, name), mc().getMainRenderTarget(), m -> {});
            });
            step(5, () -> {
                if (mc().player != null && mc().screen != null) mc().player.closeContainer();
            });
        }

        // Codex pages and lab.
        step(20, () -> mc().setScreen(new CodexScreen()));
        shot("codex_01_start");
        step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devSelect(3); });
        shot("codex_02_robots");
        step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devShowRecipe(new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("stumpy")))); });
        shot("codex_03_recipe");
        step(10, () -> { if (mc().screen instanceof CodexScreen c) c.devLab(); });
        shot("codex_04_lab");

        // Item sheet.
        step(20, () -> mc().setScreen(new IconSheetScreen(0)));
        shot("items_1");
        step(20, () -> mc().setScreen(new IconSheetScreen(1)));
        shot("items_2");
        step(20, () -> mc().setScreen(null));
        step(40, () -> {
            Robotica.LOGGER.info("Robotica showcase finished.");
            mc().stop();
        });
    }
}
