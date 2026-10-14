package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.ChunkAnchorBlock;
import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity;
import com.arno.robotica.automation.entity.ChunkAnchorBlockEntity.State;
import com.arno.robotica.automation.menu.ChunkAnchorMenu;
import com.arno.robotica.core.chunk.ChunkClaims;
import com.arno.robotica.core.chunk.ChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Chunk Anchor: tickets taken and released, the per-owner cap across anchors and dimensions, offline pause, Mk upgrade, stale claims. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class ChunkAnchorGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos POS_B = new BlockPos(3, 2, 3);

    private static ChunkAnchorBlockEntity place(GameTestHelper helper, BlockPos rel, net.minecraft.world.level.block.Block block, UUID owner) {
        helper.setBlock(rel, block);
        ChunkAnchorBlockEntity be = helper.getBlockEntity(rel);
        be.setOwner(owner);
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        return be;
    }

    /** Every chunk of the anchor's square is (or is not) forced by it. */
    private static boolean allForced(GameTestHelper helper, BlockPos rel, int radius, boolean forced) {
        BlockPos abs = helper.absolutePos(rel);
        ChunkPos c = new ChunkPos(abs);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (ChunkTickets.isForced(helper.getLevel().dimension(), abs, new ChunkPos(c.x + dx, c.z + dz)) != forced) return false;
            }
        }
        return true;
    }

    private static ChunkClaims claims(GameTestHelper helper) {
        return ChunkClaims.get(helper.getLevel().getServer());
    }

    /** Powered it forces its chunk; dry it lets go (and red); powered again it takes it back; broken it lets go for good. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anchorForcesItsChunkAndReleasesIt(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ChunkAnchorBlockEntity be = place(helper, POS, AutomationContent.CHUNK_ANCHOR.get(), owner);
        helper.assertTrue(be.upkeep() == 10 && be.energy.getMaxEnergyStored() == 120_000, "Mk1: 10 FE/t, 10 minutes of buffer");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.isLoading() && be.state() == State.LOADING, "a powered anchor loads"))
                .thenExecute(() -> {
                    helper.assertTrue(allForced(helper, POS, 0, true), "its own chunk is forced");
                    helper.assertTrue(claims(helper).used(owner) == 1, "the owner uses one chunk");
                    helper.assertTrue(helper.getBlockState(POS).getValue(ChunkAnchorBlock.MODE) == ChunkAnchorBlock.Mode.ACTIVE, "the core glows");
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(be.energy.getEnergyStored() < be.energy.getMaxEnergyStored(), "it pays its upkeep"))
                .thenExecute(() -> be.energy.setEnergy(0))
                .thenWaitUntil(() -> helper.assertTrue(!be.isLoading() && be.state() == State.NO_POWER, "dry, it lets the chunk go"))
                .thenExecute(() -> {
                    helper.assertTrue(allForced(helper, POS, 0, false), "no ticket while dry");
                    helper.assertTrue(claims(helper).used(owner) == 0, "nothing counted while dry");
                    helper.assertTrue(helper.getBlockState(POS).getValue(ChunkAnchorBlock.MODE) == ChunkAnchorBlock.Mode.ALERT, "red while dry");
                    be.energy.setEnergy(be.energy.getMaxEnergyStored());
                })
                .thenWaitUntil(() -> helper.assertTrue(be.isLoading(), "power brings the chunk back"))
                .thenExecute(() -> {
                    BlockPos abs = helper.absolutePos(POS);
                    helper.setBlock(POS, Blocks.AIR);
                    helper.assertTrue(allForced(helper, POS, 0, false), "breaking it releases the ticket");
                    helper.assertTrue(claims(helper).find(helper.getLevel().dimension(), abs) == null, "and forgets the claim");
                })
                .thenSucceed();
    }

    /** The cap counts every anchor of the owner, in every dimension; over it an anchor loads nothing until room frees up. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void anchorCapCountsAllAnchorsOfTheOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the Nether exists");
        helper.assertTrue(ChunkClaims.cap() == 25, "default cap of 25 chunks, is " + ChunkClaims.cap());
        BlockPos far = new BlockPos(8, 64, 8);
        ChunkClaims claims = claims(helper);
        // one chunk held in the Nether
        ChunkClaims.Claim netherClaim = claims.claim(nether, far, owner, 0);
        claims.activate(nether, netherClaim);
        helper.assertTrue(claims.used(owner) == 1, "the Nether anchor counts");
        ChunkAnchorBlockEntity big = place(helper, POS, AutomationContent.CHUNK_ANCHOR_MK3.get(), owner);
        helper.assertTrue(big.chunkCount() == 25 && big.upkeep() == 250, "Mk3: 5x5 chunks, 250 FE/t");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(big.state() == State.OVER_CAP, "1 + 25 is over the cap of 25"))
                .thenExecute(() -> {
                    helper.assertTrue(!big.isLoading() && allForced(helper, POS, 2, false), "over the cap it loads nothing");
                    helper.assertTrue(helper.getBlockState(POS).getValue(ChunkAnchorBlock.MODE) == ChunkAnchorBlock.Mode.ALERT, "red over the cap");
                    claims.remove(nether, far);
                })
                .thenWaitUntil(() -> helper.assertTrue(big.isLoading(), "room in the cap: the Mk3 loads"))
                .thenExecute(() -> {
                    helper.assertTrue(allForced(helper, POS, 2, true), "all 25 chunks forced");
                    helper.assertTrue(claims.used(owner) == 25, "25 chunks in use");
                })
                .thenExecute(() -> place(helper, POS_B, AutomationContent.CHUNK_ANCHOR.get(), owner))
                .thenWaitUntil(() -> helper.assertTrue(((ChunkAnchorBlockEntity) helper.getBlockEntity(POS_B)).state() == State.OVER_CAP,
                        "a second anchor of the same owner is over the cap"))
                .thenExecute(() -> {
                    ChunkAnchorBlockEntity small = helper.getBlockEntity(POS_B);
                    helper.assertTrue(!small.isLoading() && claims.used(owner) == 25, "it loads nothing");
                    UUID other = UUID.randomUUID();
                    small.setOwner(other);
                })
                .thenWaitUntil(() -> helper.assertTrue(((ChunkAnchorBlockEntity) helper.getBlockEntity(POS_B)).isLoading(),
                        "another player's anchor has its own cap"))
                .thenExecute(() -> {
                    helper.setBlock(POS, Blocks.AIR);
                    helper.setBlock(POS_B, Blocks.AIR);
                    helper.assertTrue(claims.used(owner) == 0, "broken anchors free the cap");
                })
                .thenSucceed();
    }

    /** Offline loading off: logout lets the owner's chunks go and pauses the claim, login takes them back. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anchorPausesWhileItsOwnerIsOffline(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ChunkAnchorBlockEntity be = place(helper, POS, AutomationContent.CHUNK_ANCHOR.get(), owner);
        var server = helper.getLevel().getServer();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.isLoading(), "the anchor loads"))
                .thenExecute(() -> {
                    ChunkClaims claims = claims(helper);
                    ChunkClaims.Claim claim = claims.find(helper.getLevel().dimension(), helper.absolutePos(POS));
                    claims.ownerLeft(server, owner, true);
                    helper.assertTrue(claim.active() && allForced(helper, POS, 0, true), "offline loading on: logout changes nothing");
                    claims.ownerLeft(server, owner, false);
                    helper.assertTrue(!claim.active() && claim.paused(), "offline loading off: the claim pauses");
                    helper.assertTrue(allForced(helper, POS, 0, false) && claims.used(owner) == 0, "and its chunk goes");
                    claims.ownerJoined(server, owner);
                    helper.assertTrue(claim.active() && !claim.paused() && allForced(helper, POS, 0, true), "login takes the chunk back");
                    helper.setBlock(POS, Blocks.AIR);
                })
                .thenSucceed();
    }

    /** Right-click with the next Mk: owner, energy and the area toggle stay, the old block comes back, the bigger area loads. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anchorUpgradesInPlace(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChunkAnchorBlockEntity old = place(helper, POS, AutomationContent.CHUNK_ANCHOR.get(), player.getUUID());
        old.energy.setEnergy(100_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(old.isLoading(), "Mk1 loads"))
                .thenExecute(() -> {
                    ChunkAnchorMenu menu = new ChunkAnchorMenu(0, player.getInventory(), old);
                    helper.assertTrue(menu.clickMenuButton(player, ChunkAnchorMenu.BUTTON_SHOW_AREA) && old.showArea()
                            && old.outlineVisible(), "the button shows the area");
                    menu.clickMenuButton(player, ChunkAnchorMenu.BUTTON_SHOW_AREA);
                    helper.assertTrue(!old.showArea(), "and hides it again");
                    menu.clickMenuButton(player, ChunkAnchorMenu.BUTTON_SHOW_AREA);
                    ItemStack mk3 = new ItemStack(AutomationContent.CHUNK_ANCHOR_MK3_ITEM.get());
                    use(helper, player, mk3);
                    helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.CHUNK_ANCHOR.get()) && mk3.getCount() == 1, "Mk3 can not skip Mk2");
                    ItemStack mk2 = new ItemStack(AutomationContent.CHUNK_ANCHOR_MK2_ITEM.get());
                    use(helper, player, mk2);
                    helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.CHUNK_ANCHOR_MK2.get()) && mk2.isEmpty(), "the block is now Mk2");
                    helper.assertTrue(player.getInventory().contains(new ItemStack(AutomationContent.CHUNK_ANCHOR_ITEM.get())), "the Mk1 comes back");
                    ChunkAnchorBlockEntity fresh = helper.getBlockEntity(POS);
                    helper.assertTrue(fresh != old && fresh.tier() == 2 && fresh.chunkCount() == 9, "a fresh Mk2: 3x3 chunks");
                    helper.assertTrue(player.getUUID().equals(fresh.owner()), "owner kept");
                    helper.assertTrue(fresh.showArea(), "area toggle kept");
                    int energy = fresh.energy.getEnergyStored();
                    helper.assertTrue(energy > 99_000 && energy <= 100_000, "energy kept, is " + energy);
                    helper.assertTrue(fresh.energy.getMaxEnergyStored() == 1_080_000, "Mk2 buffer: 10 minutes of 90 FE/t");
                })
                .thenWaitUntil(() -> helper.assertTrue(((ChunkAnchorBlockEntity) helper.getBlockEntity(POS)).isLoading()
                        && allForced(helper, POS, 1, true), "the Mk2 loads all 9 chunks"))
                .thenExecute(() -> helper.setBlock(POS, Blocks.AIR))
                .thenExecute(() -> helper.assertTrue(allForced(helper, POS, 1, false), "broken, all 9 go"))
                .thenSucceed();
    }

    /** A claim left behind by an anchor that vanished without breaking lets its chunk go once nobody checks in. */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void anchorClaimWithoutAnchorIsSwept(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(POS);
        ChunkClaims claims = claims(helper);
        claims.activate(level, claims.claim(level, abs, owner, 0));
        helper.assertTrue(claims.used(owner) == 1 && allForced(helper, POS, 0, true), "the claim holds its chunk with no anchor");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(claims.find(level.dimension(), abs) == null, "the stale claim is dropped"))
                .thenExecute(() -> helper.assertTrue(claims.used(owner) == 0 && allForced(helper, POS, 0, false), "and its chunk let go"))
                .thenSucceed();
    }

    private static void use(GameTestHelper helper, Player player, ItemStack stack) {
        BlockPos abs = helper.absolutePos(POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(POS).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }
}
