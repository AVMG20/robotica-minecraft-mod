package com.arno.robotica.warp.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpConfig;
import com.arno.robotica.warp.WarpRegistry;
import com.arno.robotica.warp.WarpTravel;
import com.arno.robotica.warp.gate.PortalProjectorBlock;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
import com.arno.robotica.warp.gate.GateLinks;
import com.arno.robotica.warp.gate.PortalGeometry;
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
import net.minecraft.world.phys.AABB;
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

    // ---- Portal geometry ----

    @GameTest(template = "empty")
    public static void portalVolumeMatchesTheProjectedEllipse(GameTestHelper helper) {
        BlockPos pos = new BlockPos(100, 64, 100);
        Vec3 c = PortalGeometry.center(pos);
        helper.assertTrue(Math.abs(c.y - (64 + PortalGeometry.TOP + 2.5)) < 1e-9, "the portal centre is 2.5 blocks above the projector top");
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            AABB box = PortalGeometry.triggerBox(pos, facing);
            double wide = facing.getAxis() == Direction.Axis.Z ? box.getXsize() : box.getZsize();
            double thin = facing.getAxis() == Direction.Axis.Z ? box.getZsize() : box.getXsize();
            helper.assertTrue(Math.abs(wide - 2.0) < 1e-9, facing + ": the portal is 2 wide along its plane, " + wide);
            helper.assertTrue(thin < 1.5, facing + ": the portal is thin along the facing, " + thin);
            helper.assertTrue(box.getYsize() >= 2.7 && box.getYsize() <= 3.6, facing + ": the portal is about 3 tall");
            helper.assertTrue(box.contains(c), facing + ": the centre is inside");
            helper.assertTrue(box.minY > 64 + PortalGeometry.TOP, facing + ": the volume floats above the projector top");
            // a pig standing on the ground beside the projector is under the volume
            AABB pig = new AABB(c.x - 0.45, 64, c.z - 0.45, c.x + 0.45, 64.9, c.z + 0.45).move(facing.getStepZ() != 0 ? 1.0 : 0.0, 0, facing.getStepX() != 0 ? 1.0 : 0.0);
            helper.assertTrue(!box.intersects(pig), facing + ": a pig on the ground does not touch the portal");
            helper.assertTrue(PortalGeometry.inColumn(pos, facing, new Vec3(c.x, 64, c.z)), facing + ": the projector cell is in the column");
            helper.assertTrue(!PortalGeometry.inColumn(pos, facing, new Vec3(c.x + facing.getStepX() * 2.0, 64, c.z + facing.getStepZ() * 2.0)),
                    facing + ": two blocks in front is outside the column (arrival spot)");
        }
        helper.succeed();
    }

    // ---- Real projectors in a level ----

    /** Places a projector at the test position, powers it and returns its block entity. */
    private static PortalProjectorBlockEntity buildProjector(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, WarpRegistry.GATE_CONTROLLER.get().defaultBlockState().setValue(PortalProjectorBlock.FACING, facing));
        PortalProjectorBlockEntity be = (PortalProjectorBlockEntity) helper.getBlockEntity(rel);
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

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void projectorActivatesWithLinkAndPower(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        PortalProjectorBlockEntity gate = buildProjector(helper, rel, Direction.SOUTH);
        GlobalPos self = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(rel));
        GlobalPos far = GlobalPos.of(Level.NETHER, new BlockPos(5, 70, 5));
        GateLinks links = GateLinks.get(helper.getLevel().getServer());
        helper.assertTrue(helper.getBlockState(rel).getLightEmission() == 6, "an idle projector glows at level 6");

        helper.startSequence()
                .thenExecuteAfter(25, () -> helper.assertTrue(!gate.isActive(), "an unlinked projector stays idle"))
                .thenExecute(() -> {
                    links.link(self, far);
                    helper.assertTrue(links.partner(self).equals(far) && links.partner(far).equals(self), "links work both ways");
                })
                .thenWaitUntil(() -> helper.assertTrue(gate.isActive(), "a linked, powered projector projects its portal"))
                .thenExecute(() -> {
                    BlockState state = helper.getBlockState(rel);
                    helper.assertTrue(state.getValue(PortalProjectorBlock.ACTIVE) && state.getLightEmission() == 15, "active projectors emit light level 15");
                    helper.assertTrue(state.getValue(PortalProjectorBlock.FACING) == Direction.SOUTH, "facing is kept");
                    helper.assertTrue(gate.energy.getEnergyStored() < 2_000_000, "an active projector uses energy");
                    gate.energy.setEnergy(0);
                })
                .thenWaitUntil(() -> helper.assertTrue(!gate.isActive(), "without energy the portal goes out"))
                .thenExecute(() -> gate.energy.setEnergy(2_000_000))
                .thenWaitUntil(() -> helper.assertTrue(gate.isActive(), "power brings the portal back"))
                .thenExecute(() -> helper.setBlock(rel, Blocks.AIR))
                .thenExecute(() -> helper.assertTrue(links.partner(self) == null && links.partner(far) == null, "breaking the projector drops the link"))
                .thenSucceed();
    }

    /** Two projectors 9 blocks apart on a stone floor; a pig floating in the first portal arrives in front of the second. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void projectorSendsEntitiesInItsPortalToItsPartner(GameTestHelper helper) {
        clearArea(helper, -2, 14, 1, 8, -3, 6);
        for (int x = -2; x <= 14; x++) {
            for (int z = -3; z <= 6; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos relA = new BlockPos(1, 1, 1);
        BlockPos relB = new BlockPos(10, 1, 1);
        PortalProjectorBlockEntity a = buildProjector(helper, relA, Direction.SOUTH);
        PortalProjectorBlockEntity b = buildProjector(helper, relB, Direction.SOUTH);
        GlobalPos posA = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relA));
        GlobalPos posB = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relB));
        GateLinks.get(helper.getLevel().getServer()).link(posA, posB);
        int cost = WarpConfig.gateEntityCost();
        Pig floor = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, -1));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, -1));
        pig.setNoGravity(true);
        int[] energyBefore = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.isActive() && b.isActive(), "both projectors project their portals"))
                .thenExecute(() -> {
                    helper.assertTrue(!a.entitiesInPortal(helper.getLevel()).contains(pig), "nothing is inside the portal yet");
                    // lift the pig into the portal: its centre is 2.5 blocks above the projector top
                    Vec3 centre = PortalGeometry.center(helper.absolutePos(relA));
                    pig.setPos(centre.x, centre.y - 0.45, centre.z);
                    helper.assertTrue(a.entitiesInPortal(helper.getLevel()).contains(pig), "the portal AABB query finds the pig");
                    helper.assertTrue(!a.entitiesInPortal(helper.getLevel()).contains(floor), "a pig on the floor is not in the portal");
                    energyBefore[0] = a.energy.getEnergyStored();
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.relativeVec(pig.position()).x > 8, "the pig in the portal is sent by the server tick"))
                .thenExecute(() -> {
                    Vec3 rel = helper.relativeVec(pig.position());
                    // projector B faces SOUTH: arrival is two or more blocks in front of it, on the floor
                    helper.assertTrue(rel.x > 9.9 && rel.x < 11.1 && rel.z > 3.4 && rel.z < 5.6, "the pig arrived in front of projector B, it is at " + rel);
                    helper.assertTrue(Math.abs(rel.y - 1.0) < 0.6, "the pig stands on the floor in front of projector B, y=" + rel.y);
                    helper.assertTrue(a.energy.getEnergyStored() <= energyBefore[0] - cost, "the departure projector paid " + cost + " FE");
                    helper.assertTrue(PortalProjectorBlockEntity.readyAt(pig, helper.getLevel().getGameTime()) > helper.getLevel().getGameTime(),
                            "the pig has a cooldown");
                    helper.assertTrue(pig.getYRot() == Direction.SOUTH.toYRot(), "the pig faces out of the portal");
                    b.onEntityEnter(pig);
                    helper.assertTrue(helper.relativeVec(pig.position()).x > 9, "the cooldown stops the pig from bouncing back");
                    helper.assertTrue(b.entitiesInPortal(helper.getLevel()).isEmpty(), "the arrival spot is outside the portal volume");
                })
                .thenExecute(() -> {
                    helper.setBlock(relA, Blocks.AIR);
                    helper.setBlock(relB, Blocks.AIR);
                })
                .thenSucceed();
    }

    // ---- Gate ownership, status refresh, pushing ----

    private static PortalProjectorBlockEntity controllerAt(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, WarpRegistry.GATE_CONTROLLER.get().defaultBlockState());
        return (PortalProjectorBlockEntity) helper.getBlockEntity(rel);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty")
    public static void projectorOwnershipAndLinking(GameTestHelper helper) {
        BlockPos relA = new BlockPos(0, 1, 0);
        BlockPos relB = new BlockPos(2, 1, 2);
        PortalProjectorBlockEntity a = controllerAt(helper, relA);
        PortalProjectorBlockEntity b = controllerAt(helper, relB);
        var owner = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!owner.getUUID().equals(stranger.getUUID()), "two different players");
        helper.assertTrue(a.canUse(stranger), "an unowned gate may be used by anybody (first linker claims it)");
        a.setOwner(owner);
        helper.assertTrue(a.canUse(owner) && !a.canUse(stranger), "only the owner uses an owned gate");
        helper.assertTrue(a.ownerName().equals(owner.getGameProfile().getName()), "owner name stored");

        var provider = helper.getLevel().registryAccess();
        PortalProjectorBlockEntity copy = (PortalProjectorBlockEntity) helper.getBlockEntity(relB);
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
    public static void projectorAndPadResistPistons(GameTestHelper helper) {
        for (var block : List.of(WarpRegistry.GATE_CONTROLLER.get(), WarpRegistry.WARP_PAD.get())) {
            helper.assertTrue(block.defaultBlockState().getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK,
                    block + " must not be moved by pistons");
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty")
    public static void statusRefreshDoesNotSpendEnergy(GameTestHelper helper) {
        PortalProjectorBlockEntity gate = controllerAt(helper, new BlockPos(1, 1, 1));
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
