package com.arno.robotica.warp.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.gate.GateControllerBlock;
import com.arno.robotica.warp.gate.GateControllerBlockEntity;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.gate.GatePortalBlock;
import com.arno.robotica.warp.gate.GateShape;
import com.arno.robotica.warp.pad.PadRecord;
import com.arno.robotica.warp.pad.WarpCosts;
import com.arno.robotica.warp.pad.WarpPadBlock;
import com.arno.robotica.warp.pad.WarpPadBlockEntity;
import com.arno.robotica.warp.pad.WarpPads;
import com.arno.robotica.warp.teleport.Teleporter;
import com.arno.robotica.warp.teleport.WarpCooldowns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Headless tests of the warp module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class WarpGameTests {

    private static int nextX;

    private static PadRecord pad(String name, UUID owner, String ownerName, boolean isPublic) {
        return new PadRecord(UUID.randomUUID(), new BlockPos(nextX++, 64, 0), Level.OVERWORLD, name, owner, ownerName, isPublic, false);
    }

    // ---- Registry ----

    @GameTest(template = "empty")
    public static void padRegistryAddRemoveLookup(GameTestHelper helper) {
        WarpPads pads = new WarpPads();
        UUID alice = UUID.randomUUID();
        PadRecord home = pad("Home", alice, "Alice", false);
        PadRecord mine = pad("Mine", alice, "Alice", true);
        pads.put(home);
        pads.put(mine);
        helper.assertTrue(pads.size() == 2, "two pads registered");
        helper.assertTrue(pads.get(home.id()) == home, "lookup by id");
        helper.assertTrue(pads.findAt(Level.OVERWORLD, mine.pos()) == mine, "lookup by position");
        helper.assertTrue(pads.findAt(Level.NETHER, mine.pos()) == null, "other dimension does not match");

        pads.put(home.withName("Base").withPublic(true).withRift(true));
        PadRecord changed = pads.get(home.id());
        helper.assertTrue(pads.size() == 2 && changed.name().equals("Base") && changed.isPublic() && changed.rift(), "update replaces the record");

        // round trip through NBT
        CompoundTag tag = pads.save(new CompoundTag(), helper.getLevel().registryAccess());
        WarpPads loaded = WarpPads.FACTORY.deserializer().apply(tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.size() == 2, "saved registry loads again");
        helper.assertTrue(loaded.get(home.id()).equals(changed), "record survives saving");

        helper.assertTrue(pads.remove(home.id()) != null && pads.get(home.id()) == null, "remove drops the pad");
        helper.assertTrue(pads.remove(home.id()) == null, "removing twice is harmless");
        pads.refreshOwnerName(alice, "Alicia");
        helper.assertTrue(pads.get(mine.id()).ownerName().equals("Alicia"), "owner name refresh");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void padRegistersOnPlaceAndCleansOnRemove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(pos, WarpRegistry.WARP_PAD.get().defaultBlockState());
        WarpPadBlockEntity be = (WarpPadBlockEntity) helper.getBlockEntity(pos);
        be.initPlacement(player);
        WarpPads pads = WarpPads.get(helper.getLevel().getServer());
        BlockPos abs = helper.absolutePos(pos);
        PadRecord rec = pads.findAt(helper.getLevel().dimension(), abs);
        helper.assertTrue(rec != null, "placed pad is in the registry");
        helper.assertTrue(player.getUUID().equals(rec.owner()) && !rec.isPublic() && !rec.rift(), "owner is the placing player, pad starts private");
        helper.assertTrue(rec.name().length() <= WarpPadBlockEntity.MAX_NAME, "default name fits");

        be.rename("Spawn");
        be.setPublic(true);
        helper.assertTrue(pads.get(rec.id()).name().equals("Spawn") && pads.get(rec.id()).isPublic(), "rename and visibility reach the registry");
        helper.assertTrue(pads.validated(helper.getLevel().getServer(), rec.id()) != null, "existing pad validates");

        helper.setBlock(pos, Blocks.AIR);
        helper.assertTrue(pads.get(rec.id()) == null, "breaking the pad removes it from the registry");

        // stale entry: the block is gone but the registry still lists it; a lookup in a loaded chunk cleans it
        pads.put(rec);
        helper.assertTrue(pads.validated(helper.getLevel().getServer(), rec.id()) == null, "missing block is detected");
        helper.assertTrue(pads.get(rec.id()) == null, "stale entry was cleaned");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void padPermissionFilter(GameTestHelper helper) {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        PadRecord own = pad("Own", me, "Me", false);
        PadRecord friendPublic = pad("FriendPublic", friend, "Friend", true);
        PadRecord friendPrivate = pad("FriendPrivate", friend, "Friend", false);
        PadRecord teamPrivate = pad("TeamPrivate", teammate, "Teammate", false);
        PadRecord strangerPrivate = pad("StrangerPrivate", stranger, "Stranger", false);
        PadRecord noOwner = new PadRecord(UUID.randomUUID(), new BlockPos(0, 70, 0), Level.OVERWORLD, "Nobody", null, "", false, false);

        WarpPads pads = new WarpPads();
        for (PadRecord rec : List.of(own, friendPublic, friendPrivate, teamPrivate, strangerPrivate, noOwner)) pads.put(rec);

        // me and Teammate are in team "red"
        List<PadRecord> usable = pads.usableBy(me, "red", name -> name.equals("Teammate") || name.equals("Me") ? "red" : null);
        Set<UUID> ids = new HashSet<>();
        usable.forEach(r -> ids.add(r.id()));
        helper.assertTrue(ids.contains(own.id()), "own pad is usable");
        helper.assertTrue(ids.contains(friendPublic.id()), "public pad is usable");
        helper.assertTrue(ids.contains(teamPrivate.id()), "private pad of a team mate is usable");
        helper.assertTrue(ids.contains(noOwner.id()), "pad without owner is open");
        helper.assertTrue(!ids.contains(friendPrivate.id()), "private pad of somebody else is hidden");
        helper.assertTrue(!ids.contains(strangerPrivate.id()), "private pad of a stranger is hidden");
        helper.assertTrue(usable.size() == 4, "exactly four pads, got " + usable.size());

        // without a team nothing but own, public and ownerless pads
        List<PadRecord> noTeam = pads.usableBy(me, null, name -> null);
        helper.assertTrue(noTeam.size() == 3, "no team gives own, public and ownerless pads, got " + noTeam.size());
        // different teams do not share
        helper.assertTrue(!WarpPads.canUse(teamPrivate, me, "blue", "red"), "different teams do not share pads");
        helper.assertTrue(WarpPads.canUse(teamPrivate, teammate, null, "red"), "owner always may use the pad");
        helper.succeed();
    }

    // ---- Costs and names ----

    @GameTest(template = "empty")
    public static void costFormula(GameTestHelper helper) {
        helper.assertTrue(WarpCosts.padTrip(0, false, 5_000, 20, 100_000) == 5_000, "same spot costs the base");
        helper.assertTrue(WarpCosts.padTrip(100, false, 5_000, 20, 100_000) == 7_000, "100 blocks cost 5000 + 2000");
        helper.assertTrue(WarpCosts.padTrip(1_000.4, false, 5_000, 20, 100_000) == 25_000, "distance rounds to blocks");
        helper.assertTrue(WarpCosts.padTrip(5, true, 5_000, 20, 100_000) == 100_000, "cross dimension is flat");
        helper.assertTrue(WarpCosts.padTrip(1_000_000, true, 5_000, 20, 100_000) == 100_000, "cross dimension ignores distance");
        helper.assertTrue(WarpCosts.padTrip(Double.MAX_VALUE / 2, false, 5_000, 20, 100_000) > 0, "huge distances do not overflow");
        helper.assertTrue(WarpCosts.padTrip(100, false) == 5_000 + 100 * 20, "defaults are 5000 + 20 per block");
        helper.assertTrue(WarpConfig.padRiftCost() == 100_000 && WarpConfig.remoteCost() == 20_000 && WarpConfig.riftRemoteCost() == 150_000, "default costs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nameValidation(GameTestHelper helper) {
        helper.assertTrue(WarpTravel.sanitizeName("  Home Base ").orElse("").equals("Home Base"), "names are trimmed");
        helper.assertTrue(WarpTravel.sanitizeName("a".repeat(24)).isPresent(), "24 characters are fine");
        helper.assertTrue(WarpTravel.sanitizeName("a".repeat(25)).isEmpty(), "25 characters are too long");
        helper.assertTrue(WarpTravel.sanitizeName("   ").isEmpty(), "blank names are refused");
        helper.assertTrue(WarpTravel.sanitizeName("bad\nname").isEmpty(), "control characters are refused");
        helper.assertTrue(WarpTravel.sanitizeName("§cRed").isEmpty(), "formatting codes are refused");
        helper.succeed();
    }

    // ---- Safe spot ----

    private static void clearColumn(GameTestHelper helper, int x, int z) {
        for (int y = 0; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    @GameTest(template = "empty")
    public static void safeSpotFinder(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                clearColumn(helper, x, z);
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));

        Vec3 spot = Teleporter.standingSpot(level, center);
        helper.assertTrue(spot != null && Math.abs(spot.y - (center.getY())) < 1e-6, "stone floor gives a spot on top of it");
        helper.assertTrue(Teleporter.findSafeSpot(level, center).get().equals(spot), "the wanted cell wins when it is safe");

        // the wanted cell is inside stone: the finder moves up
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        Optional<Vec3> above = Teleporter.findSafeSpot(level, center);
        helper.assertTrue(above.isPresent() && level.getBlockState(BlockPos.containing(above.get())).isAir()
                && above.get().distanceTo(Vec3.atCenterOf(center)) < 2.0, "a free spot next to the stone block is found");

        // head room blocked
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
        helper.assertTrue(Teleporter.standingSpot(level, center) == null, "a block over the head makes the cell unsafe");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);

        // bad ground and bad air
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.MAGMA_BLOCK);
        helper.assertTrue(Teleporter.standingSpot(level, center) == null, "magma is no ground");
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FIRE);
        helper.assertTrue(Teleporter.standingSpot(level, center) == null, "fire is no air");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
        helper.assertTrue(Teleporter.standingSpot(level, center) == null, "water is no air");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR);
        helper.assertTrue(Teleporter.standingSpot(level, center) == null, "no ground below, no spot");
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);

        // a Warp Pad (half height) is ground: feet on its top surface
        helper.setBlock(new BlockPos(1, 1, 1), WarpRegistry.WARP_PAD.get().defaultBlockState());
        Vec3 onPad = Teleporter.standingSpot(level, center.above());
        helper.assertTrue(onPad != null && Math.abs(onPad.y - (center.getY() + 0.5)) < 1e-6, "standing on a pad puts the feet at half a block");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);

        // search radius, high in the sky where nothing else is: one stone block is the only ground
        BlockPos sky = helper.absolutePos(new BlockPos(1, 150, 1));
        helper.assertTrue(Teleporter.findSafeSpot(level, sky).isEmpty(), "open air without ground has no safe spot");
        level.setBlockAndUpdate(sky.below(4), Blocks.STONE.defaultBlockState());
        Optional<Vec3> reach = Teleporter.findSafeSpot(level, sky);
        helper.assertTrue(reach.isPresent() && Math.abs(reach.get().y - (sky.getY() - 3)) < 1e-6, "ground 3 blocks away is reached");
        level.setBlockAndUpdate(sky.below(4), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(sky.below(5), Blocks.STONE.defaultBlockState());
        helper.assertTrue(Teleporter.findSafeSpot(level, sky).isEmpty(), "ground 4 blocks away is out of reach");
        level.setBlockAndUpdate(sky.below(5), Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    // ---- Gate shape ----

    private static Set<BlockPos> perfectFrame(Direction.Axis axis, BlockPos corner, int controllerIndex, Set<BlockPos> open) {
        Set<BlockPos> frame = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                BlockPos p = axis == Direction.Axis.X ? corner.offset(i, j, 0) : corner.offset(0, j, i);
                boolean edge = i == 0 || i == 3 || j == 0 || j == 4;
                if (edge && !(i == controllerIndex && j == 0)) frame.add(p);
                else if (!edge) open.add(p);
            }
        }
        return frame;
    }

    private static GateShape.Probe probe(Set<BlockPos> frame, Set<BlockPos> open) {
        return new GateShape.Probe() {
            @Override
            public boolean isFrame(BlockPos pos) {
                return frame.contains(pos);
            }

            @Override
            public boolean isOpen(BlockPos pos) {
                return open.contains(pos);
            }
        };
    }

    @GameTest(template = "empty")
    public static void gateShapeValidation(GameTestHelper helper) {
        BlockPos corner = new BlockPos(10, 64, 10);
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            for (int index = 1; index <= 2; index++) {
                Set<BlockPos> open = new HashSet<>();
                Set<BlockPos> frame = perfectFrame(axis, corner, index, open);
                BlockPos controller = axis == Direction.Axis.X ? corner.offset(index, 0, 0) : corner.offset(0, 0, index);
                helper.assertTrue(open.size() == 6 && frame.size() == 13, "4x5 frame has 13 frame blocks and a 2x3 opening");
                Optional<GateShape> found = GateShape.find(probe(frame, open), controller, null);
                helper.assertTrue(found.isPresent(), "valid frame along " + axis + " with controller " + index + " is found");
                helper.assertTrue(found.get().axis() == axis && found.get().controllerIndex() == index && found.get().corner().equals(corner),
                        "shape knows axis, controller index and corner");
                helper.assertTrue(new HashSet<>(found.get().innerPositions()).equals(open), "inner positions are the 2x3 opening");

                // one frame block missing
                for (BlockPos missing : new HashSet<>(frame)) {
                    Set<BlockPos> broken = new HashSet<>(frame);
                    broken.remove(missing);
                    helper.assertTrue(GateShape.find(probe(broken, open), controller, null).isEmpty(), "missing frame block " + missing + " is detected");
                }
                // blocked opening
                for (BlockPos blocked : new HashSet<>(open)) {
                    Set<BlockPos> smaller = new HashSet<>(open);
                    smaller.remove(blocked);
                    helper.assertTrue(GateShape.find(probe(frame, smaller), controller, null).isEmpty(), "blocked opening cell " + blocked + " is detected");
                }
            }
        }
        // controller in a corner or in the top row is not a gate
        Set<BlockPos> open = new HashSet<>();
        Set<BlockPos> frame = perfectFrame(Direction.Axis.X, corner, 1, open);
        frame.add(corner.offset(1, 0, 0));
        helper.assertTrue(GateShape.find(probe(frame, open), corner, null).isEmpty(), "controller in the corner is rejected");
        helper.assertTrue(GateShape.find(probe(frame, open), corner.offset(1, 4, 0), null).isEmpty(), "controller in the top row is rejected");
        // 3 wide frame is not valid
        Set<BlockPos> narrow = new HashSet<>();
        for (int i = 0; i < 3; i++) for (int j = 0; j < 5; j++) if (i == 0 || i == 2 || j == 0 || j == 4) narrow.add(corner.offset(i, j, 0));
        narrow.remove(corner.offset(1, 0, 0));
        Set<BlockPos> narrowOpen = new HashSet<>();
        for (int j = 1; j < 4; j++) narrowOpen.add(corner.offset(1, j, 0));
        helper.assertTrue(GateShape.find(probe(narrow, narrowOpen), corner.offset(1, 0, 0), null).isEmpty(), "a 3 wide frame is rejected");
        helper.succeed();
    }

    // ---- Real gate in a level ----

    /** Gate along X with its frame corner at the given test position. */
    private static GateControllerBlockEntity buildGate(GameTestHelper helper, BlockPos corner, int controllerIndex) {
        BlockState frame = WarpRegistry.GATE_FRAME.get().defaultBlockState();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                boolean edge = i == 0 || i == 3 || j == 0 || j == 4;
                if (!edge) continue;
                BlockPos p = corner.offset(i, j, 0);
                if (i == controllerIndex && j == 0) {
                    helper.setBlock(p, WarpRegistry.GATE_CONTROLLER.get().defaultBlockState().setValue(GateControllerBlock.FACING, Direction.SOUTH));
                } else {
                    helper.setBlock(p, frame);
                }
            }
        }
        GateControllerBlockEntity be = (GateControllerBlockEntity) helper.getBlockEntity(corner.offset(controllerIndex, 0, 0));
        be.energy.setEnergy(2_000_000);
        return be;
    }

    private static void clearArea(GameTestHelper helper, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    private static boolean portalFilled(GameTestHelper helper, BlockPos corner) {
        for (int i = 1; i <= 2; i++) {
            for (int j = 1; j <= 3; j++) {
                if (!helper.getBlockState(corner.offset(i, j, 0)).is(WarpRegistry.GATE_PORTAL.get())) return false;
            }
        }
        return true;
    }

    /** Gates are bigger than the 3x3x3 template, so the gate tests run alone in their own batches. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "warpGateOpen")
    public static void gateOpensAndClosesWithItsFrame(GameTestHelper helper) {
        clearArea(helper, -1, 4, 0, 7, 0, 2);
        BlockPos corner = new BlockPos(0, 1, 1);
        GateControllerBlockEntity gate = buildGate(helper, corner, 1);
        GlobalPos self = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(corner.offset(1, 0, 0)));
        GlobalPos far = GlobalPos.of(Level.NETHER, new BlockPos(5, 70, 5));
        GateLinks links = GateLinks.get(helper.getLevel().getServer());

        helper.startSequence()
                .thenExecuteAfter(25, () -> helper.assertTrue(!portalFilled(helper, corner), "an unlinked gate stays closed"))
                .thenExecute(() -> {
                    links.link(self, far);
                    helper.assertTrue(links.partner(self).equals(far) && links.partner(far).equals(self), "links work both ways");
                })
                .thenWaitUntil(() -> helper.assertTrue(portalFilled(helper, corner), "a linked, powered gate fills its opening"))
                .thenExecute(() -> {
                    BlockState portal = helper.getBlockState(corner.offset(1, 1, 0));
                    helper.assertTrue(portal.getValue(GatePortalBlock.AXIS) == Direction.Axis.X, "portal axis follows the frame");
                    helper.assertTrue(gate.isActive() && gate.shape() != null, "controller reports active");
                    helper.assertTrue(gate.energy.getEnergyStored() < 2_000_000, "an open gate uses energy");
                    gate.energy.setEnergy(0);
                })
                .thenWaitUntil(() -> helper.assertTrue(!portalFilled(helper, corner) && !gate.isActive(), "without energy the portal closes"))
                .thenExecute(() -> gate.energy.setEnergy(2_000_000))
                .thenWaitUntil(() -> helper.assertTrue(portalFilled(helper, corner), "power brings the portal back"))
                .thenExecute(() -> helper.setBlock(corner.offset(0, 2, 0), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(corner.offset(1, 2, 0)).isAir()
                        && helper.getBlockState(corner.offset(2, 3, 0)).isAir(), "breaking a frame block removes the portal"))
                .thenExecute(() -> helper.setBlock(corner.offset(1, 0, 0), Blocks.AIR))
                .thenExecute(() -> helper.assertTrue(links.partner(self) == null && links.partner(far) == null, "breaking the controller drops the link"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "warpGateTransit")
    public static void gateSendsEntitiesToItsPartner(GameTestHelper helper) {
        // floor under both gates and the space in front of them
        clearArea(helper, -2, 14, 1, 7, -3, 5);
        for (int x = -2; x <= 14; x++) {
            for (int z = -3; z <= 5; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos cornerA = new BlockPos(0, 1, 1);
        BlockPos cornerB = new BlockPos(9, 1, 1);
        GateControllerBlockEntity a = buildGate(helper, cornerA, 1);
        GateControllerBlockEntity b = buildGate(helper, cornerB, 2);
        GlobalPos posA = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(cornerA.offset(1, 0, 0)));
        GlobalPos posB = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(cornerB.offset(2, 0, 0)));
        GateLinks.get(helper.getLevel().getServer()).link(posA, posB);
        int cost = WarpConfig.gateEntityCost();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, -1));
        int[] energyBefore = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.isActive() && portalFilled(helper, cornerA), "gate A opens"))
                .thenExecute(() -> {
                    // touching the portal only queues the pig: nothing moves inside entityInside, the next tick does it
                    energyBefore[0] = a.energy.getEnergyStored();
                    a.queueEntity(pig);
                    helper.assertTrue(helper.relativeVec(pig.position()).x < 5, "queueing does not move the entity");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.relativeVec(pig.position()).x > 9, "the queued pig is sent on the next tick"))
                .thenExecute(() -> {
                    Vec3 rel = helper.relativeVec(pig.position());
                    // gate B faces SOUTH (controller facing on the portal axis), arrival is in front of its left column
                    helper.assertTrue(rel.x > 9 && rel.x < 13 && rel.z > 1.4 && rel.z < 2.6, "the pig left through gate B, it is at " + rel);
                    helper.assertTrue(Math.abs(rel.y - 1.0) < 0.6 || Math.abs(rel.y - 2.0) < 0.6, "the pig stands on the floor in front of gate B, y=" + rel.y);
                    helper.assertTrue(a.energy.getEnergyStored() <= energyBefore[0] - cost, "the departure gate paid " + cost + " FE");
                    helper.assertTrue(GateControllerBlockEntity.readyAt(pig, helper.getLevel().getGameTime()) > helper.getLevel().getGameTime(),
                            "the pig has a cooldown");
                    helper.assertTrue(pig.getYRot() == Direction.SOUTH.toYRot(), "the pig faces out of the gate");
                    // entering gate B right away does nothing (cooldown), and B has no partner energy trouble
                    b.onEntityEnter(pig);
                    helper.assertTrue(helper.relativeVec(pig.position()).x > 9, "the cooldown stops the pig from bouncing back");
                })
                .thenExecute(() -> {
                    helper.setBlock(cornerA.offset(1, 0, 0), Blocks.AIR);
                    helper.setBlock(cornerB.offset(2, 0, 0), Blocks.AIR);
                })
                .thenSucceed();
    }

    // ---- Gate ownership, status refresh, pushing ----

    private static GateControllerBlockEntity controllerAt(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, WarpRegistry.GATE_CONTROLLER.get().defaultBlockState());
        return (GateControllerBlockEntity) helper.getBlockEntity(rel);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty")
    public static void gateOwnershipAndLinking(GameTestHelper helper) {
        BlockPos relA = new BlockPos(0, 1, 0);
        BlockPos relB = new BlockPos(2, 1, 2);
        GateControllerBlockEntity a = controllerAt(helper, relA);
        GateControllerBlockEntity b = controllerAt(helper, relB);
        var owner = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!owner.getUUID().equals(stranger.getUUID()), "two different players");
        helper.assertTrue(a.canUse(stranger), "an unowned gate may be used by anybody (first linker claims it)");
        a.setOwner(owner);
        helper.assertTrue(a.canUse(owner) && !a.canUse(stranger), "only the owner uses an owned gate");
        helper.assertTrue(a.ownerName().equals(owner.getGameProfile().getName()), "owner name stored");

        var provider = helper.getLevel().registryAccess();
        GateControllerBlockEntity copy = (GateControllerBlockEntity) helper.getBlockEntity(relB);
        copy.loadWithComponents(a.saveWithoutMetadata(provider), provider);
        helper.assertTrue(owner.getUUID().equals(copy.owner()), "owner survives saving");
        copy.loadWithComponents(new CompoundTag(), provider);
        helper.assertTrue(copy.owner() == null, "old gates load without an owner");

        GlobalPos posA = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relA));
        GlobalPos posB = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relB));
        GateLinks links = GateLinks.get(helper.getLevel().getServer());
        var card = new net.minecraft.world.item.ItemStack(WarpRegistry.LINKING_CARD.get());
        stranger.setPos(Vec3.atCenterOf(helper.absolutePos(relA)));
        WarpTravel.linkingCardUsed(stranger, card, posA);
        helper.assertTrue(card.get(WarpComponents.LINK_SOURCE.get()) == null, "a stranger can not even store an owned gate");
        owner.setPos(Vec3.atCenterOf(helper.absolutePos(relA)).add(40, 0, 0));
        WarpTravel.linkingCardUsed(owner, card, posA);
        helper.assertTrue(card.get(WarpComponents.LINK_SOURCE.get()) == null, "linking from far away is refused");
        owner.setPos(Vec3.atCenterOf(helper.absolutePos(relA)));
        WarpTravel.linkingCardUsed(owner, card, posA);
        helper.assertTrue(posA.equals(card.get(WarpComponents.LINK_SOURCE.get())), "the owner stores the first gate");
        WarpTravel.linkingCardUsed(owner, card, posB);
        helper.assertTrue(posB.equals(links.partner(posA)) && posA.equals(links.partner(posB)), "the owner links both gates");
        helper.assertTrue(owner.getUUID().equals(b.owner()), "the unowned second gate was claimed by the linker");
        links.unlink(posA);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gateBlocksResistPistons(GameTestHelper helper) {
        for (var block : List.of(WarpRegistry.GATE_FRAME.get(), WarpRegistry.GATE_CONTROLLER.get(), WarpRegistry.GATE_PORTAL.get(), WarpRegistry.WARP_PAD.get())) {
            helper.assertTrue(block.defaultBlockState().getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK,
                    block + " must not be moved by pistons");
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty")
    public static void statusRefreshDoesNotSpendEnergy(GameTestHelper helper) {
        GateControllerBlockEntity gate = controllerAt(helper, new BlockPos(1, 1, 1));
        gate.energy.setEnergy(100_000);
        gate.refresh(helper.getLevel());
        helper.assertTrue(gate.energy.getEnergyStored() == 100_000, "a status refresh is free");
        helper.succeed();
    }

    // ---- Names and cooldowns ----

    @SuppressWarnings("removal")
    @GameTest(template = "empty")
    public static void ownerNameResolvesByUuid(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        String name = WarpPads.currentName(server, player.getUUID(), "OldName");
        helper.assertTrue(name.equals("OldName") || name.equals(player.getGameProfile().getName()), "a known owner resolves to a current name: " + name);
        helper.assertTrue(WarpPads.currentName(server, UUID.randomUUID(), "Stored").equals("Stored"), "unknown owner falls back to the stored name");
        helper.assertTrue(WarpPads.currentName(server, null, "Stored").equals("Stored"), "no owner falls back to the stored name");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cooldownsSurviveSavingAndOnlyExpireByTime(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        WarpCooldowns data = new WarpCooldowns();
        data.startPad(id, 1000, 40);
        data.startRemote(id, 1000, 600);
        helper.assertTrue(data.padRemaining(id, 1010) == 30, "pad cooldown counts down");
        var provider = helper.getLevel().registryAccess();
        WarpCooldowns loaded = WarpCooldowns.FACTORY.deserializer().apply(data.save(new CompoundTag(), provider), provider);
        helper.assertTrue(loaded.padRemaining(id, 1010) == 30, "pad cooldown survives a restart (and so a relog)");
        helper.assertTrue(loaded.remoteRemaining(id, 1100) == 500, "remote cooldown survives too");
        loaded.prune(1020);
        helper.assertTrue(loaded.padRemaining(id, 1020) == 20 || loaded.padRemaining(id, 1020) == 0, "pruning only drops expired entries");
        helper.assertTrue(loaded.remoteRemaining(id, 1020) == 580, "an unexpired entry is kept");
        loaded.prune(2000);
        helper.assertTrue(loaded.size() == 0, "expired entries are pruned by time");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedGateChunksAreNotGenerated(GameTestHelper helper) {
        // far away from anything the test world ever generates
        helper.assertTrue(!Teleporter.isGenerated(helper.getLevel(), 1_000_000, 1_000_000), "an ungenerated chunk is reported as such");
        BlockPos abs = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(Teleporter.isGenerated(helper.getLevel(), abs.getX() >> 4, abs.getZ() >> 4), "a loaded chunk is generated");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void padBlockHasHalfHeightAndRiftState(GameTestHelper helper) {
        BlockState state = WarpRegistry.WARP_PAD.get().defaultBlockState();
        helper.assertTrue(!state.getValue(WarpPadBlock.RIFT), "pads start without rift");
        helper.assertTrue(state.getShape(helper.getLevel(), BlockPos.ZERO).max(Direction.Axis.Y) == 0.5, "pad is slab height");
        helper.succeed();
    }
}
