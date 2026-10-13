package com.arno.robotica.link.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.link.LinkPayloads;
import com.arno.robotica.link.LinkRegistry;
import com.arno.robotica.link.block.LinkPriority;
import com.arno.robotica.link.block.LinkTier;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.block.QuantumLinkBlockEntity.Status;
import com.arno.robotica.link.block.TransferMode;
import com.arno.robotica.link.menu.NetworkEntry;
import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.LinkNetworks;
import com.arno.robotica.link.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Headless tests of the link module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class LinkGameTests {

    private static LinkNetwork network(GameTestHelper helper, String name, Visibility visibility, ResourceKey<Level> home) {
        return LinkNetworks.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "Tester", name, 9, visibility, home);
    }

    private static LinkNetwork network(GameTestHelper helper, String name) {
        return network(helper, name, Visibility.PUBLIC, helper.getLevel().dimension());
    }

    private static void delete(GameTestHelper helper, LinkNetwork net) {
        LinkNetworks.get(helper.getLevel().getServer()).delete(net);
    }

    private static Block block(LinkTier tier) {
        return switch (tier) {
            case MK1 -> LinkRegistry.LINK_MK1.get();
            case MK2 -> LinkRegistry.LINK_MK2.get();
            case MK3 -> LinkRegistry.LINK_MK3.get();
        };
    }

    /** A Link placed by the test (no owner: it may use any network), on {@code net} with the given modes and a full buffer. */
    private static QuantumLinkBlockEntity link(GameTestHelper helper, BlockPos pos, LinkTier tier, LinkNetwork net, TransferMode energy,
                                               TransferMode items) {
        helper.setBlock(pos, block(tier).defaultBlockState());
        QuantumLinkBlockEntity be = (QuantumLinkBlockEntity) helper.getBlockEntity(pos);
        be.joinNetwork(net);
        be.setEnergyMode(energy);
        be.setItemMode(items);
        return be;
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST.defaultBlockState());
        return (ChestBlockEntity) helper.getBlockEntity(pos);
    }

    // ---- Energy ----

    /** One Mk1 sends its buffer into the network, another one on the same network receives it; only upkeep is spent. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void energyMovesBetweenLinks(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Power");
        QuantumLinkBlockEntity sender = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.SEND, TransferMode.OFF);
        QuantumLinkBlockEntity receiver = link(helper, new BlockPos(2, 1, 2), LinkTier.MK1, net, TransferMode.RECEIVE, TransferMode.OFF);
        sender.energy.setEnergy(64_000);
        long start = helper.getLevel().getGameTime();
        int upkeep = LinkTier.MK1.upkeep();
        helper.succeedWhen(() -> {
            long elapsed = helper.getLevel().getGameTime() - start + 2;
            long total = sender.energy.getEnergyStored() + receiver.energy.getEnergyStored() + net.energy();
            helper.assertTrue(receiver.energy.getEnergyStored() > 50_000, "receiver should fill, has " + receiver.energy.getEnergyStored());
            helper.assertTrue(net.senders().size() == 1 && net.energyCapacity() == 64_000L * LinkNetwork.BUFFER_TICKS,
                    "the sender sizes the buffer: 5 s of its rate");
            helper.assertTrue(total <= 64_000 && total >= 64_000 - 2L * upkeep * elapsed, "nothing but upkeep is lost, total " + total);
            delete(helper, net);
        });
    }

    /**
     * The receiver's chunk "unloads" (its block entity is removed and stops ticking, as on a real unload): the buffer
     * fills up to its size and the sender keeps the rest. When the receiver loads again it drains the buffer.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void unloadedReceiverLetsBufferFill(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Far Base");
        QuantumLinkBlockEntity sender = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.SEND, TransferMode.OFF);
        BlockPos receiverRel = new BlockPos(2, 1, 2);
        QuantumLinkBlockEntity receiver = link(helper, receiverRel, LinkTier.MK1, net, TransferMode.RECEIVE, TransferMode.OFF);
        receiver.energy.setEnergy(64_000);
        ServerLevel level = helper.getLevel();
        BlockPos receiverAbs = helper.absolutePos(receiverRel);
        CompoundTag[] saved = new CompoundTag[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    // unload: save, then remove the block entity like a chunk unload does (it no longer ticks)
                    saved[0] = receiver.saveWithFullMetadata(level.registryAccess());
                    receiver.onChunkUnloaded();
                    receiver.setRemoved();
                    helper.assertTrue(net.energyCapacity() == 64_000L * LinkNetwork.BUFFER_TICKS, "one Mk1 sender: 5 s of 64k FE/t");
                    net.setEnergy(net.energyCapacity() - 10_000);
                    sender.energy.setEnergy(64_000);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(net.energy() == net.energyCapacity(), "the buffer fills to its size, has " + net.energy());
                    helper.assertTrue(sender.energy.getEnergyStored() <= 54_000 && sender.energy.getEnergyStored() > 40_000,
                            "the sender put in what fit and keeps the rest");
                })
                .thenExecuteAfter(10, () -> {
                    int kept = sender.energy.getEnergyStored();
                    helper.assertTrue(kept > 0 && net.energy() == net.energyCapacity(), "a full buffer makes the sender wait");
                    // load again: the chunk makes a fresh block entity from the saved tag
                    BlockEntity fresh = level.getBlockEntity(receiverAbs);
                    if (fresh == null || fresh.isRemoved()) fresh = level.getBlockEntity(receiverAbs);
                    helper.assertTrue(fresh instanceof QuantumLinkBlockEntity && fresh != receiver, "a new block entity after the reload");
                    fresh.loadWithComponents(saved[0], level.registryAccess());
                    ((QuantumLinkBlockEntity) fresh).energy.setEnergy(0);
                })
                .thenWaitUntil(() -> {
                    QuantumLinkBlockEntity back = (QuantumLinkBlockEntity) level.getBlockEntity(receiverAbs);
                    helper.assertTrue(back != null && back.energy.getEnergyStored() > 30_000, "the reloaded receiver drains the buffer");
                    helper.assertTrue(net.energy() < net.energyCapacity(), "the buffer has room again");
                })
                .thenExecute(() -> delete(helper, net))
                .thenSucceed();
    }

    // ---- Items ----

    /** Diamonds go to the Highest receiver, whose filter allows only diamonds; cobblestone goes on to the Lowest one. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void itemsFollowPriorityAndFilter(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Sorter");
        QuantumLinkBlockEntity sender = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.SEND);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 2, 0));
        source.setItem(0, new ItemStack(Items.DIAMOND, 16));
        source.setItem(1, new ItemStack(Items.COBBLESTONE, 16));

        QuantumLinkBlockEntity high = link(helper, new BlockPos(2, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.RECEIVE);
        high.setPriority(LinkPriority.HIGHEST);
        high.filter().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        high.setWhitelist(true);
        ChestBlockEntity highChest = chest(helper, new BlockPos(2, 2, 0));

        QuantumLinkBlockEntity low = link(helper, new BlockPos(2, 1, 2), LinkTier.MK1, net, TransferMode.OFF, TransferMode.RECEIVE);
        low.setPriority(LinkPriority.LOWEST);
        ChestBlockEntity lowChest = chest(helper, new BlockPos(2, 2, 2));

        // upkeep for each Link
        for (QuantumLinkBlockEntity be : List.of(sender, high, low)) be.energy.setEnergy(64_000);
        helper.succeedWhen(() -> {
            helper.assertTrue(source.isEmpty(), "the sender pulled everything from its chest");
            helper.assertTrue(highChest.countItem(Items.DIAMOND) == 16, "Highest receiver gets the diamonds, has " + highChest.countItem(Items.DIAMOND));
            helper.assertTrue(highChest.countItem(Items.COBBLESTONE) == 0, "its filter blocks cobblestone");
            helper.assertTrue(lowChest.countItem(Items.COBBLESTONE) == 16 && lowChest.countItem(Items.DIAMOND) == 0,
                    "cobblestone goes on to the Lowest receiver");
            delete(helper, net);
        });
    }

    // ---- Access ----

    /** Someone else's private network refuses a player and their Link; a public one lets them in. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void privateNetworkRefusesOthers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LinkNetworks data = LinkNetworks.get(server);
        LinkNetwork vault = network(helper, "Vault", Visibility.PRIVATE, helper.getLevel().dimension());
        LinkNetwork square = network(helper, "Square", Visibility.PUBLIC, helper.getLevel().dimension());
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, LinkRegistry.LINK_MK1.get().defaultBlockState());
        QuantumLinkBlockEntity be = (QuantumLinkBlockEntity) helper.getBlockEntity(pos);
        be.setOwner(stranger);

        helper.assertTrue(!LinkPayloads.join(server, be, vault, stranger) && be.networkId() == null, "a private network refuses other players");
        List<NetworkEntry> visible = data.entriesFor(server, stranger);
        helper.assertTrue(visible.stream().noneMatch(e -> e.id().equals(vault.id())), "others do not see a private network");
        helper.assertTrue(visible.stream().anyMatch(e -> e.id().equals(square.id()) && !e.manage()), "a public one is listed, not editable");
        helper.assertTrue(LinkPayloads.join(server, be, square, stranger) && square.id().equals(be.networkId()), "a public network lets them join");

        // a Link that still names the private network (an item from its owner) gets no access and sends nothing
        be.joinNetwork(vault);
        be.setEnergyMode(TransferMode.SEND);
        be.energy.setEnergy(64_000);
        helper.succeedWhen(() -> {
            helper.assertTrue(be.status() == Status.NO_ACCESS, "status should be no access, is " + be.status());
            helper.assertTrue(vault.energy() == 0 && vault.senders().isEmpty(), "nothing reaches the private network");
            delete(helper, vault);
            delete(helper, square);
        });
    }

    // ---- Dimensions ----

    /** A network made in the Nether: a Mk1 in the Overworld does nothing (not even upkeep), a Mk2 works. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mk1OutsideHomeDimensionIdles(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Nether Line", Visibility.PUBLIC, Level.NETHER);
        QuantumLinkBlockEntity mk1 = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.SEND, TransferMode.SEND);
        QuantumLinkBlockEntity mk2 = link(helper, new BlockPos(2, 1, 2), LinkTier.MK2, net, TransferMode.SEND, TransferMode.OFF);
        mk1.energy.setEnergy(64_000);
        mk2.energy.setEnergy(64_000);
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(mk1.status() == Status.WRONG_DIMENSION, "Mk1 away from home, is " + mk1.status());
                    helper.assertTrue(mk1.energy.getEnergyStored() == 64_000, "an idle Mk1 uses nothing");
                    helper.assertTrue(mk2.status().connected() && mk2.energy.getEnergyStored() < 64_000, "Mk2 works in any dimension");
                    helper.assertTrue(net.senders().size() == 1 && net.energy() > 0, "only the Mk2 sends");
                    helper.assertTrue(net.itemSlots() == 0, "the Mk1 adds no item slots");
                })
                .thenExecute(() -> delete(helper, net))
                .thenSucceed();
    }

    // ---- Deleting ----

    /** Deleting a network idles its Links: they forget it and stop moving or paying upkeep. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void deletingNetworkIdlesLinks(GameTestHelper helper) {
        LinkNetworks data = LinkNetworks.get(helper.getLevel().getServer());
        LinkNetwork net = network(helper, "Old");
        QuantumLinkBlockEntity be = link(helper, new BlockPos(1, 1, 1), LinkTier.MK1, net, TransferMode.SEND, TransferMode.OFF);
        be.energy.setEnergy(64_000);
        int[] kept = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.status().connected() && net.energy() > 0, "the Link sends first"))
                .thenExecute(() -> {
                    data.delete(net);
                    helper.assertTrue(data.get(net.id()) == null && net.removed(), "the network is gone");
                    be.energy.setEnergy(10_000);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.status() == Status.NO_NETWORK && be.networkId() == null, "the Link forgets it"))
                .thenExecute(() -> kept[0] = be.energy.getEnergyStored())
                .thenExecuteAfter(10, () -> helper.assertTrue(be.energy.getEnergyStored() == kept[0], "an idle Link moves and pays nothing"))
                .thenSucceed();
    }
}
