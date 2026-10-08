package com.arno.robotica.codex.client.dev.trailer;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.CourierRoute;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.entity.HaulerDrone;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.logistics.pipe.PipeMode;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaLink;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.gate.GateTransit;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Trailer scenes about the machines: drones, Tesla Coils, reactors, the factory line and the warp gate. Dev-only. */
final class TrailerScenesMachines {
    private TrailerScenesMachines() {}

    static List<TrailerScene> scenes() {
        return List.of(haulerCow(), dronesMining(), dronesFleet(), sentryDefense(), teslaArcs(), reactorPower(), factoryLine(), warpGate());
    }

    // ------------------------------------------------------------------ helpers

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(Robotica.id(name));
    }

    private static Vec3 v(BlockPos site, double x, double y, double z) {
        return new Vec3(site.getX() + x, site.getY() + y, site.getZ() + z);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
    }

    /** Spawns a drone of the given tier, fully charged, owned by the player, facing {@code yaw}. */
    private static <T extends DroneBase> T drone(ServerLevel level, ServerPlayer owner, EntityType<T> type, int tier, double x, double y, double z, float yaw) {
        T d = type.create(level);
        d.moveTo(x, y, z, yaw, 0.0F);
        d.setYBodyRot(yaw);
        d.setYHeadRot(yaw);
        d.setTier(tier);
        d.setHealth(d.getMaxHealth());
        d.setOwner(owner);
        d.setEnergy(d.getEnergyCapacity());
        level.addFreshEntity(d);
        return d;
    }

    /** Dev only: links a Tesla Coil without the Linker (TeslaCoilBlockEntity.addLink is package-private). */
    private static void teslaLink(ServerLevel level, BlockPos coil, TeslaLink link) {
        try {
            var be = level.getBlockEntity(coil);
            var m = TeslaCoilBlockEntity.class.getDeclaredMethod("addLink", TeslaLink.class);
            m.setAccessible(true);
            m.invoke(be, link);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Robotica.LOGGER.warn("Trailer could not link Tesla Coil at {}", coil, e);
        }
    }

    private static void coil(ServerLevel level, BlockPos pos, int tier, Direction facing) {
        level.setBlock(pos, Trailer.block("tesla_coil_" + tier).defaultBlockState().setValue(BlockStateProperties.FACING, facing), 3);
    }

    /** The Tesla Linker in the camera player's hand makes the client draw the arcs at full strength. */
    private static void holdLinker(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("tesla_linker")));
    }

    private static void clearHand(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Fills a block entity's energy with a full buffer through the capability (several passes for big buffers). */
    private static void charge(ServerLevel level, BlockPos pos) {
        Trailer.fillEnergy(level, pos);
    }

    private static void insert(ServerLevel level, BlockPos pos, ItemStack stack) {
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (items != null) ItemHandlerHelper.insertItem(items, stack, false);
    }

    private static void fillChest(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < stacks.length; i++) chest.setItem(i, stacks[i]);
        }
    }

    /** Places a layered multiblock (as in scripts/wiki/multiblocks.json) with its north-west-bottom corner at {@code origin}, mirrored so the controller faces south. */
    private static void multiblock(ServerLevel level, BlockPos origin, String[][] layers, Map<Character, String> legend) {
        int depth = layers[0].length;
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < layers[y][z].length(); x++) {
                    String id = legend.get(layers[y][z].charAt(x));
                    BlockPos pos = origin.offset(x, y, depth - 1 - z);
                    if (id == null || id.equals("air")) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    } else if (id.equals("water")) {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                    } else {
                        Trailer.place(level, pos, Trailer.block(id), Direction.SOUTH);
                    }
                }
            }
        }
    }

    private static CameraPath.Builder path() {
        return CameraPath.builder();
    }

    // ------------------------------------------------------------------ hauler_cow

    /** Hauler position at scene tick t (x from pen A to pen B, lifting off and coming down again). */
    private static Vec3 haulerAt(BlockPos site, double t) {
        double s = t / 150.0;
        double up = smooth(t / 35.0) - smooth((t - 118.0) / 32.0);
        return new Vec3(site.getX() + 0.5 - 24 + 48 * s, site.getY() + 2.4 + 4.0 * up + 0.35 * Math.sin(t / 11.0), site.getZ() + 0.5 + 1.8 * Math.sin(s * Math.PI * 2));
    }

    private static double smooth(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    /** A Hauler Drone lifts a cow out of one pen and flies it across the field to another; the camera tracks alongside. */
    private static TrailerScene haulerCow() {
        TrailerScene.Builder b = TrailerScene.builder("hauler_cow")
                .site(84, 40)
                .time(2500)
                .warmup(40)
                .duration(150)
                .setup((level, player, site) -> {
                    clearHand(player);
                    for (int pen = 0; pen < 2; pen++) {
                        int cx = pen == 0 ? -24 : 24;
                        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                            if (Math.abs(x) == 5 || Math.abs(z) == 5) set(level, site.offset(cx + x, 0, z), Blocks.OAK_FENCE);
                        }
                        set(level, site.offset(cx + 3, 0, -3), Blocks.HAY_BLOCK);
                        set(level, site.offset(cx + 3, 1, -3), Blocks.HAY_BLOCK);
                        set(level, site.offset(cx - 3, 0, 3), Blocks.HAY_BLOCK);
                    }
                    for (int[] t : new int[][]{{-34, -14}, {-14, -17}, {2, -15}, {18, -18}, {34, -13}, {-6, 14}, {10, 15}, {30, 16}}) {
                        Trailer.run(level, "place feature minecraft:oak " + (site.getX() + t[0]) + " " + site.getY() + " " + (site.getZ() + t[1]));
                    }
                    // other cows stay in pen A
                    for (int[] c : new int[][]{{-26, 2}, {-22, -2}, {-27, -2}}) {
                        var cow = EntityType.COW.create(level);
                        cow.moveTo(site.getX() + c[0] + 0.5, site.getY(), site.getZ() + c[1] + 0.5, 40 * c[0], 0);
                        cow.setNoAi(true);
                        level.addFreshEntity(cow);
                    }
                    var cow = EntityType.COW.create(level);
                    Vec3 p = haulerAt(site, 0);
                    cow.moveTo(p.x, site.getY(), p.z, 90, 0);
                    level.addFreshEntity(cow);
                    HaulerDrone h = drone(level, player, DronesRegistry.HAULER_DRONE_ENTITY.get(), 1, p.x, site.getY() + cow.getBbHeight() + 0.85, p.z, 270);
                    h.setOwnerUUID(null);
                    h.grab(cow);
                })
                .camera(site -> {
                    CameraPath.Builder cb = path();
                    for (int t = 0; t <= 150; t += 10) {
                        Vec3 d = haulerAt(site, t);
                        double s = t / 150.0;
                        Vec3 cam = new Vec3(d.x - 2.5 + 5.0 * s, Math.max(site.getY() + 1.7, d.y - 1.8), d.z + 5.6 - 1.0 * s);
                        cb.keyLookAt(t, cam, d.add(0, -0.9, 0), 58);
                    }
                    return cb.build();
                });
        for (int t = 0; t <= 150; t++) {
            final int tick = t;
            b.at(t, (level, player, site) -> {
                Vec3 d = haulerAt(site, tick + 2), prev = haulerAt(site, tick + 1);
                for (HaulerDrone h : level.getEntitiesOfClass(HaulerDrone.class, new AABB(site).inflate(70, 20, 30))) {
                    h.setPos(d.x, d.y, d.z);
                    h.setDeltaMovement(d.subtract(prev));
                    h.setYRot(270);
                    h.yBodyRot = 270;
                    h.yHeadRot = 270;
                    h.setEnergy(h.getEnergyCapacity());
                }
            });
        }
        // actions must be added in tick order
        b.at(145, (level, player, site) -> {
            for (HaulerDrone h : level.getEntitiesOfClass(HaulerDrone.class, new AABB(site).inflate(70, 20, 30))) h.releaseCargo();
        });
        return b.build();
    }

    // ------------------------------------------------------------------ drones_mining

    /** A Mk3 Mining Drone eating a 5x5 tunnel through a beam of ore-flecked rock. */
    private static TrailerScene dronesMining() {
        return TrailerScene.builder("drones_mining")
                .site(48, 36)
                .time(3500)
                .warmup(30)
                .duration(130)
                .setup((level, player, site) -> {
                    clearHand(player);
                    // a 5x5 beam of rock, x +1 .. +18 east of the drone, z -2 .. +2, y 0 .. +4
                    Random rnd = new Random(7);
                    Block[] ores = {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
                            Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.EMERALD_ORE};
                    for (int x = 1; x <= 18; x++) {
                        for (int y = 0; y <= 4; y++) {
                            for (int z = -2; z <= 2; z++) {
                                Block b = rnd.nextInt(4) == 0 ? ores[rnd.nextInt(ores.length)]
                                        : rnd.nextInt(9) == 0 ? Blocks.ANDESITE : rnd.nextInt(14) == 0 ? Blocks.TUFF : Blocks.STONE;
                                set(level, site.offset(x, y, z), b);
                            }
                        }
                    }
                    MiningDrone d = drone(level, player, DronesRegistry.MINING_DRONE_ENTITY.get(), 3,
                            site.getX() + 0.5, site.getY() + 2.0, site.getZ() + 0.5, Direction.EAST.toYRot());
                    d.digTicksOverride = 1;
                    d.setHeading(Direction.EAST);
                })
                .at(0, (level, player, site) -> {
                    for (MiningDrone d : level.getEntitiesOfClass(MiningDrone.class, new AABB(site).inflate(6))) {
                        d.startTunnel(Direction.EAST, 18, site);
                    }
                })
                .camera(site -> path()
                        .keyLookAt(0, v(site, -1.5, 2.5, 5.0), v(site, 1.0, 2.2, 0), 55)
                        .keyLookAt(65, v(site, 0.5, 2.8, 4.8), v(site, 2.0, 2.2, 0), 55)
                        .keyLookAt(130, v(site, 2.5, 3.0, 4.4), v(site, 3.8, 2.2, 0), 55)
                        .build())
                .build();
    }

    // ------------------------------------------------------------------ drones_fleet

    /** Courier Drones shuttling stacks between chests on stone towers, passing each other in front of the camera. */
    private static TrailerScene dronesFleet() {
        return TrailerScene.builder("drones_fleet")
                .site(64, 44)
                .time(5000)
                .warmup(60)
                .duration(140)
                .setup((level, player, site) -> {
                    clearHand(player);
                    String dim = level.dimension().location().toString();
                    // three lines of chests on 4 high towers; on each line one courier goes each way
                    int[] lineZ = {-3, -6, -10};
                    int[] half = {13, 14, 15};
                    for (int r = 0; r < lineZ.length; r++) {
                        BlockPos a = site.offset(-half[r], 4, lineZ[r]);
                        BlockPos b = site.offset(half[r], 4, lineZ[r]);
                        for (BlockPos p : new BlockPos[]{a, b}) {
                            for (int y = 0; y < 4; y++) set(level, p.below(4 - y), y == 3 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
                            fillChest(level, p, new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.COPPER_INGOT, 64),
                                    new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.DIAMOND, 64),
                                    new ItemStack(Items.COAL, 64), new ItemStack(Items.QUARTZ, 64), new ItemStack(Items.LAPIS_LAZULI, 64));
                        }
                        int tier = r == 1 ? 1 : 2;
                        CourierDrone fwd = drone(level, player, DronesRegistry.COURIER_DRONE_ENTITY.get(), tier,
                                a.getX() + 0.5 + r * 3, a.getY() + 1.3, a.getZ() + 0.5, 270);
                        fwd.addRoute(new CourierRoute(dim, a, Direction.UP, b, Direction.UP));
                        CourierDrone back = drone(level, player, DronesRegistry.COURIER_DRONE_ENTITY.get(), tier,
                                b.getX() + 0.5 - r * 4, b.getY() + 1.3, b.getZ() + 0.5, 90);
                        back.addRoute(new CourierRoute(dim, b, Direction.UP, a, Direction.UP));
                    }
                })
                .camera(site -> path()
                        .keyLookAt(0, v(site, -9.0, 1.8, 3.0), v(site, -3, 5.4, -6), 62)
                        .keyLookAt(70, v(site, 0.0, 2.0, 3.0), v(site, 2, 5.8, -6), 62)
                        .keyLookAt(140, v(site, 9.0, 1.8, 3.0), v(site, 3, 5.6, -6), 62)
                        .build())
                .build();
    }

    // ------------------------------------------------------------------ sentry_defense

    /** Three Sentry Drones holding the line against a wave of zombies and husks at dusk. */
    private static TrailerScene sentryDefense() {
        return TrailerScene.builder("sentry_defense")
                .site(64, 64)
                .time(12300)
                .difficulty(Difficulty.NORMAL)
                .warmup(40)
                .duration(130)
                .setup((level, player, site) -> {
                    clearHand(player);
                    // low sandbag wall and torches in front of the camera
                    for (int x = -6; x <= 6; x++) {
                        if (x % 3 == 0) {
                            set(level, site.offset(x, 0, 6), Blocks.OAK_FENCE);
                            set(level, site.offset(x, 1, 6), Blocks.TORCH);
                        }
                    }
                    double[][] spots = {{-3.5, 2.4, -1.0}, {3.5, 2.0, -2.0}, {0.0, 2.9, -3.5}};
                    for (double[] s : spots) {
                        SentryDrone d = drone(level, player, DronesRegistry.SENTRY_DRONE_ENTITY.get(), 2,
                                site.getX() + 0.5 + s[0], site.getY() + s[1], site.getZ() + 0.5 + s[2], 180);
                        d.setMode(SentryDrone.Mode.STAY);
                        d.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 1));
                    }
                })
                .at(0, (level, player, site) -> { wave(level, site, 0); march(level, site); })
                .at(8, (level, player, site) -> march(level, site))
                .at(16, (level, player, site) -> march(level, site))
                .at(28, (level, player, site) -> march(level, site))
                .at(40, (level, player, site) -> march(level, site))
                .at(50, (level, player, site) -> { wave(level, site, 1); march(level, site); })
                .at(55, (level, player, site) -> march(level, site))
                .at(70, (level, player, site) -> march(level, site))
                .at(90, (level, player, site) -> march(level, site))
                .at(110, (level, player, site) -> march(level, site))
                .camera(site -> path()
                        .keyLookAt(0, v(site, -2.5, 1.3, 3.5), v(site, 0, 2.6, -14), 56)
                        .keyLookAt(65, v(site, 0.5, 1.6, 2.5), v(site, 0, 2.4, -14), 56)
                        .keyLookAt(130, v(site, 3.5, 2.0, 1.5), v(site, 0, 2.2, -13), 56)
                        .build())
                .build();
    }

    /** Spawns a row of zombies and husks 17 to 23 blocks north of the site (inside the sentries' range from the start). */
    private static void wave(ServerLevel level, BlockPos site, int index) {
        Random rnd = new Random(3 + index);
        for (int i = 0; i < 7; i++) {
            EntityType<? extends Mob> type = i % 3 == 0 ? EntityType.HUSK : EntityType.ZOMBIE;
            Mob m = type.create(level);
            m.moveTo(site.getX() + 0.5 + (i - 3) * 3.0 + rnd.nextDouble(), site.getY(), site.getZ() + 0.5 - 18 - rnd.nextInt(6), 0, 0);
            m.setPersistenceRequired();
            m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            level.addFreshEntity(m);
        }
    }

    /** Walks the undead at the sentries. */
    private static void march(ServerLevel level, BlockPos site) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(site).inflate(60, 12, 60), e -> e.getType() == EntityType.ZOMBIE || e.getType() == EntityType.HUSK)) {
            m.getNavigation().moveTo(site.getX() + 0.5 + (m.getId() % 7 - 3), site.getY(), site.getZ() + 3.5, 1.0);
        }
    }

    // ------------------------------------------------------------------ tesla_arcs

    /** A Tesla Coil network at night: a root coil on an Accumulator, relay pylons around it, accumulators as sinks. */
    private static TrailerScene teslaArcs() {
        return TrailerScene.builder("tesla_arcs")
                .site(60, 60)
                .time(13500)
                .warmup(60)
                .duration(140)
                .setup((level, player, site) -> {
                    holdLinker(player);
                    BlockPos acc = site.offset(0, 0, 0);
                    set(level, acc, Trailer.block("accumulator_3"));
                    fullAccumulator(level, acc);
                    BlockPos root = acc.above();
                    coil(level, root, 5, Direction.UP);
                    int n = 6;
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n + 0.3;
                        BlockPos base = site.offset((int) Math.round(Math.cos(a) * 9), 0, (int) Math.round(Math.sin(a) * 9));
                        set(level, base, Blocks.POLISHED_DEEPSLATE);
                        set(level, base.above(), Blocks.POLISHED_DEEPSLATE);
                        set(level, base.above(2), Blocks.POLISHED_DEEPSLATE);
                        BlockPos relay = base.above(3);
                        coil(level, relay, 3, Direction.UP);
                        teslaLink(level, root, TeslaLink.toCoil(relay));
                        // two sinks behind each relay
                        for (int k = -1; k <= 1; k += 2) {
                            double b = a + k * 0.28;
                            BlockPos sink = site.offset((int) Math.round(Math.cos(b) * 15), 0, (int) Math.round(Math.sin(b) * 15));
                            set(level, sink, Trailer.block("accumulator_3"));
                            teslaLink(level, relay, TeslaLink.toMachine(sink, Direction.UP));
                        }
                    }
                    // a chain of relays across one side as well
                    BlockPos tower = site.offset(0, 0, -16);
                    for (int y = 0; y < 5; y++) set(level, tower.above(y), Blocks.POLISHED_DEEPSLATE);
                    coil(level, tower.above(5), 4, Direction.UP);
                    teslaLink(level, root, TeslaLink.toCoil(tower.above(5)));
                })
                .camera(site -> {
                    Vec3 c = Vec3.atCenterOf(site).add(0, 3.0, 0);
                    return CameraPath.orbit(c, 11, 0.4, 0.4, 200, 300, 140, 62);
                })
                .build();
    }

    private static void fullAccumulator(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof com.arno.robotica.power.block.AccumulatorBlockEntity a) {
            a.energy.setEnergy(a.energy.getMaxEnergyStored());
        }
    }

    // ------------------------------------------------------------------ reactor_power

    private static final Map<Character, String> FISSION_LEGEND = Map.of('C', "reactor_casing", 'G', "reactor_glass", 'K', "reactor_controller",
            'P', "reactor_power_port", 'A', "reactor_access_port", 'R', "reactor_fuel_rod", 'W', "water", '.', "air");
    private static final String[][] FISSION = {
            {"CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"},
            {"CCCCC", "C.W.C", "AWRWP", "C.W.C", "CCCCC"},
            {"CGKGC", "C.W.C", "CWRWC", "C.W.C", "CCCCC"},
            {"CCGCC", "C.W.C", "CWRWC", "C.W.C", "CCCCC"},
            {"CCCCC", "CCCCC", "CCGCC", "CCCCC", "CCCCC"}};
    private static final Map<Character, String> BANK_LEGEND = Map.of('B', "bank_casing", 'G', "bank_glass", 'K', "bank_controller",
            'I', "bank_port", 'O', "bank_port", 'R', "capacitor_redstone", 'T', "transfer_coil_advanced", '.', "air");
    private static final String[][] BANK = {
            {"BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB"},
            {"BGGGB", "BRRRB", "BRRRB", "BRRRB", "BBBBB"},
            {"BGKGB", "BRTRB", "ITTTO", "BRTRB", "BBBBB"},
            {"BGGGB", "BRRRB", "BRRRB", "BRRRB", "BBBBB"},
            {"BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB"}};

    /** A running Fission Reactor feeding a Capacitor Bank through Tesla Coils, which in turn power furnaces. */
    private static TrailerScene reactorPower() {
        return TrailerScene.builder("reactor_power")
                .site(64, 44)
                .time(12600)
                .warmup(60)
                .duration(140)
                .setup((level, player, site) -> {
                    holdLinker(player);
                    BlockPos reactor = site.offset(-20, 0, -5);
                    BlockPos bank = site.offset(-8, 0, -5);
                    multiblock(level, reactor, FISSION, FISSION_LEGEND);
                    multiblock(level, bank, BANK, BANK_LEGEND);
                    for (BlockPos p : new BlockPos[]{reactor.offset(2, 2, 4), bank.offset(2, 2, 4)}) {
                        if (level.getBlockEntity(p) instanceof com.arno.robotica.energy.block.StructureControllerBlockEntity c) c.scanNow();
                    }
                    BlockPos access = reactor.offset(0, 1, 2);
                    insert(level, access, new ItemStack(item("thorium_fuel_pellet"), 32));
                    if (level.getBlockEntity(reactor.offset(2, 2, 4)) instanceof com.arno.robotica.energy.block.ReactorControllerBlockEntity r) {
                        r.energy.setEnergy(r.energy.getMaxEnergyStored());
                    }
                    // reactor power port (east wall) -> coil -> bank input (west wall)
                    BlockPos coilA = reactor.offset(5, 1, 2);
                    coil(level, coilA, 3, Direction.EAST);
                    teslaLink(level, coilA, TeslaLink.toMachine(bank.offset(0, 2, 2), Direction.WEST));
                    // bank output (east wall) -> coil -> three furnaces and an accumulator
                    BlockPos coilB = bank.offset(5, 2, 2);
                    coil(level, coilB, 3, Direction.EAST);
                    for (int i = 0; i < 3; i++) {
                        BlockPos f = site.offset(6, 0, -8 + i * 4);
                        Trailer.place(level, f, Trailer.block("electric_furnace_mk3"), Direction.SOUTH);
                        insert(level, f, new ItemStack(Items.RAW_IRON, 64));
                        teslaLink(level, coilB, TeslaLink.toMachine(f, Direction.UP));
                    }
                    BlockPos acc = site.offset(10, 0, -1);
                    set(level, acc, Trailer.block("accumulator_3"));
                    teslaLink(level, coilB, TeslaLink.toMachine(acc, Direction.UP));
                    // keep the bank's output port primed so the second hop runs from the first second
                    if (level.getBlockEntity(bank.offset(2, 2, 4)) instanceof com.arno.robotica.energy.block.BankControllerBlockEntity b) {
                        Robotica.LOGGER.info("Trailer: bank controller ready {}", b.isFormed());
                    }
                })
                .camera(site -> path()
                        .keyLookAt(0, v(site, -26, 2.4, 6), v(site, -17, 3.0, -3), 62)
                        .keyLookAt(70, v(site, -12, 2.8, 5), v(site, -8, 3.0, -3), 62)
                        .keyLookAt(140, v(site, 3, 3.4, 8), v(site, 6, 2.0, -5), 62)
                        .build())
                .build();
    }

    // ------------------------------------------------------------------ factory_line

    /** Chest, Grinder, Electric Furnace and Alloy Smelter lines joined by item pipes, all running. */
    private static TrailerScene factoryLine() {
        return TrailerScene.builder("factory_line")
                .site(56, 36)
                .time(5000)
                .warmup(50)
                .duration(130)
                .setup((level, player, site) -> {
                    clearHand(player);
                    for (int row = 0; row < 2; row++) {
                        int z = row * -7;
                        int o = row * 3;
                        BlockPos in = site.offset(-14 + o, 0, z);
                        BlockPos grinder = site.offset(-9 + o, 0, z);
                        BlockPos furnace = site.offset(-4 + o, 0, z);
                        BlockPos smelter = site.offset(1 + o, 0, z);
                        BlockPos out = site.offset(6 + o, 0, z);
                        fillChest(level, in, new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_COPPER, 64), new ItemStack(Items.RAW_GOLD, 64),
                                new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_COPPER, 64), new ItemStack(Items.RAW_GOLD, 64));
                        Trailer.place(level, grinder, Trailer.block("grinder_mk4"), Direction.SOUTH);
                        Trailer.place(level, furnace, Trailer.block("electric_furnace_mk4"), Direction.SOUTH);
                        Trailer.place(level, smelter, Trailer.block("metal_press"), Direction.SOUTH);
                        fillChest(level, out);
                        // pipes: chest -> grinder (over the top) -> furnace -> smelter -> chest
                        List<BlockPos> pipes = new ArrayList<>();
                        for (int x = -13; x <= -10; x++) pipes.add(site.offset(x + o, 0, z));
                        for (int x = -8; x <= -5; x++) pipes.add(site.offset(x + o, 0, z));
                        for (int x = -3; x <= 0; x++) pipes.add(site.offset(x + o, 0, z));
                        for (int x = 2; x <= 5; x++) pipes.add(site.offset(x + o, 0, z));
                        for (BlockPos p : pipes) set(level, p, Trailer.block("item_pipe_mk3"));
                        for (BlockPos p : pipes) level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 3);
                        extract(level, site.offset(-13 + o, 0, z), Direction.WEST);
                        extract(level, site.offset(-8 + o, 0, z), Direction.WEST);
                        extract(level, site.offset(-3 + o, 0, z), Direction.WEST);
                        extract(level, site.offset(2 + o, 0, z), Direction.WEST);
                        for (BlockPos m : new BlockPos[]{grinder, furnace, smelter}) charge(level, m);
                    }
                })
                .at(30, (level, player, site) -> recharge(level, site))
                .at(70, (level, player, site) -> recharge(level, site))
                .at(110, (level, player, site) -> recharge(level, site))
                .camera(site -> path()
                        .keyLookAt(0, v(site, -14, 1.9, 4.5), v(site, -9, 1.3, -1), 62)
                        .keyLookAt(65, v(site, -6, 2.2, 4.0), v(site, -2, 1.3, -1), 62)
                        .keyLookAt(130, v(site, 3, 2.6, 4.5), v(site, 6, 1.3, -1), 62)
                        .build())
                .build();
    }

    private static void extract(ServerLevel level, BlockPos pipe, Direction from) {
        if (level.getBlockEntity(pipe) instanceof ItemPipeBlockEntity be) be.changeMode(from, PipeMode.EXTRACT);
    }

    private static void recharge(ServerLevel level, BlockPos site) {
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-14, 0, -8), site.offset(12, 0, 2))) {
            if (level.getBlockState(p).is(Trailer.block("grinder_mk4")) || level.getBlockState(p).is(Trailer.block("electric_furnace_mk4"))
                    || level.getBlockState(p).is(Trailer.block("metal_press"))) charge(level, p.immutable());
        }
    }

    // ------------------------------------------------------------------ warp_gate

    /** Two linked Portal Projectors; the portal opens, a cow and a sheep go through, the camera pushes in. */
    private static TrailerScene warpGate() {
        return TrailerScene.builder("warp_gate")
                .site(48, 40)
                .time(13200)
                .warmup(40)
                .duration(130)
                .setup((level, player, site) -> {
                    clearHand(player);
                    BlockPos a = site.offset(-7, 0, 0), b = site.offset(7, 0, 0);
                    for (BlockPos p : new BlockPos[]{a, b}) {
                        Trailer.place(level, p, Trailer.block("gate_controller"), Direction.SOUTH);
                        // a small stone pad around each projector
                        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                            set(level, p.offset(x, -1, z), (x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
                        }
                    }
                    GlobalPos ga = GlobalPos.of(level.dimension(), a), gb = GlobalPos.of(level.dimension(), b);
                    GateLinks.get(level.getServer()).link(ga, gb);
                    for (BlockPos p : new BlockPos[]{a, b}) {
                        if (level.getBlockEntity(p) instanceof PortalProjectorBlockEntity be) be.setLinked(p.equals(a) ? gb : ga);
                    }
                    var cow = EntityType.COW.create(level);
                    cow.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 4.5, 180, 0);
                    cow.setNoAi(true);
                    level.addFreshEntity(cow);
                    var sheep = EntityType.SHEEP.create(level);
                    sheep.moveTo(a.getX() - 1.0, a.getY(), a.getZ() + 5.5, 180, 0);
                    sheep.setNoAi(true);
                    level.addFreshEntity(sheep);
                })
                .at(4, (level, player, site) -> power(level, site))
                .at(60, (level, player, site) -> send(level, site, EntityType.COW))
                .at(80, (level, player, site) -> send(level, site, EntityType.SHEEP))
                .camera(site -> path()
                        .keyLookAt(0, v(site, 0, 3.6, 11), v(site, 0, 3.6, 0), 70)
                        .keyLookAt(55, v(site, -3, 3.8, 7), v(site, -7, 4.0, 0), 66)
                        .keyLookAt(100, v(site, -6.5, 4.0, 3.5), v(site, -7, 4.0, 0), 60)
                        .keyLookAt(130, v(site, -7, 4.0, 0.2), v(site, -7, 4.0, -6), 74)
                        .build())
                .build();
    }

    /** Charges both projectors and lets them open, so the opening burst falls inside the recording. */
    private static void power(ServerLevel level, BlockPos site) {
        for (int dx : new int[]{-7, 7}) {
            BlockPos p = site.offset(dx, 0, 0);
            if (level.getBlockEntity(p) instanceof PortalProjectorBlockEntity be) {
                be.energy.setEnergy(be.energy.getMaxEnergyStored());
                be.evaluate(level);
            }
        }
    }

    private static void send(ServerLevel level, BlockPos site, EntityType<?> type) {
        BlockPos a = site.offset(-7, 0, 0);
        if (!(level.getBlockEntity(a) instanceof PortalProjectorBlockEntity from)) return;
        for (var e : level.getEntitiesOfClass(Mob.class, new AABB(a).inflate(12), m -> m.getType() == type)) {
            e.setNoAi(false);
            GateTransit.send(from, e);
            return;
        }
    }
}
