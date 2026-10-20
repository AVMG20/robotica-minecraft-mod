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
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.AccumulatorBlockEntity;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
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

    private static LinkNetwork network(GameTestHelper helper, String name, Visibility visibility) {
        return LinkNetworks.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "Tester", name, 9, visibility);
    }

    private static LinkNetwork network(GameTestHelper helper, String name) {
        return network(helper, name, Visibility.PUBLIC);
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

    /** A Tesla Relay on top of a receiving Link hands the network's FE to the machine it links. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void relayOnReceiverSharesNetworkPower(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Relay Feed");
        QuantumLinkBlockEntity sender = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.SEND, TransferMode.OFF);
        sender.energy.setEnergy(64_000);
        link(helper, new BlockPos(2, 1, 2), LinkTier.MK1, net, TransferMode.RECEIVE, TransferMode.OFF);
        BlockPos relayPos = new BlockPos(2, 2, 2);
        helper.setBlock(relayPos, PowerRegistry.TESLA_COIL_1.get().defaultBlockState().setValue(TeslaCoilBlock.FACING, Direction.UP));
        TeslaCoilBlockEntity relay = (TeslaCoilBlockEntity) helper.getBlockEntity(relayPos);
        BlockPos sinkPos = new BlockPos(4, 1, 4);
        helper.setBlock(sinkPos, PowerRegistry.ACCUMULATOR_1.get().defaultBlockState());
        AccumulatorBlockEntity sink = (AccumulatorBlockEntity) helper.getBlockEntity(sinkPos);
        helper.assertTrue(TeslaNetwork.toggle(relay, helper.absolutePos(sinkPos), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "link relay -> sink");
        helper.succeedWhen(() -> {
            helper.assertTrue(relay.isRoot(), "the relay draws from the Link under it");
            helper.assertTrue(sink.energy.getEnergyStored() > 10_000, "the sink fills, has " + sink.energy.getEnergyStored());
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
                    // unload: save, then take the block entity and its ticker away like a chunk unload does
                    saved[0] = receiver.saveWithFullMetadata(level.registryAccess());
                    receiver.onChunkUnloaded();
                    level.removeBlockEntity(receiverAbs);
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

    /**
     * A receiver whose chest is full sits out the hand-out instead of scanning the chest every time; the chest getting
     * room (a neighbour change) ends that at once and the items arrive.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullReceiverBacksOff(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Backoff");
        QuantumLinkBlockEntity sender = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.SEND);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 2, 0));
        source.setItem(0, new ItemStack(Items.DIAMOND, 8));
        QuantumLinkBlockEntity receiver = link(helper, new BlockPos(3, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.RECEIVE);
        // beside the receiver: a container change there reaches it as a neighbour change
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 0));
        for (int i = 0; i < target.getContainerSize(); i++) target.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        for (QuantumLinkBlockEntity be : List.of(sender, receiver)) be.energy.setEnergy(64_000);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(source.isEmpty(), "the sender pulled the diamonds");
                    helper.assertTrue(receiver.itemBackedOff(), "a receiver whose chest took nothing sits out");
                })
                .thenExecute(() -> {
                    helper.assertTrue(target.countItem(Items.DIAMOND) == 0, "the full chest got nothing");
                    target.setItem(0, ItemStack.EMPTY);
                    helper.assertFalse(receiver.itemBackedOff(), "room in the chest ends the wait");
                })
                .thenWaitUntil(() -> helper.assertTrue(target.countItem(Items.DIAMOND) == 8,
                        "the diamonds arrive, has " + target.countItem(Items.DIAMOND)))
                .thenExecute(() -> delete(helper, net))
                .thenSucceed();
    }

    // ---- Access ----

    /** Someone else's private network refuses a player and their Link; a public one lets them in. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void privateNetworkRefusesOthers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LinkNetworks data = LinkNetworks.get(server);
        LinkNetwork vault = network(helper, "Vault", Visibility.PRIVATE);
        LinkNetwork square = network(helper, "Square", Visibility.PUBLIC);
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

    // ---- Upkeep ----

    /**
     * Three Links on an item network, only one with FE: the network pays all three upkeeps (3 x 20 FE/t) from that one
     * buffer and every Link works. Once it runs dry nothing is taken and all of them wait.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void upkeepIsSharedByTheNetwork(GameTestHelper helper) {
        LinkNetwork net = network(helper, "Shared");
        QuantumLinkBlockEntity powered = link(helper, new BlockPos(0, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.SEND);
        QuantumLinkBlockEntity a = link(helper, new BlockPos(2, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.RECEIVE);
        QuantumLinkBlockEntity b = link(helper, new BlockPos(4, 1, 0), LinkTier.MK1, net, TransferMode.OFF, TransferMode.RECEIVE);
        powered.energy.setEnergy(50_000);
        int upkeep = LinkTier.MK1.upkeep();
        int[] mark = new int[1];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    for (QuantumLinkBlockEntity be : List.of(powered, a, b)) {
                        helper.assertTrue(be.status().connected(), "every Link runs off the one powered Link, one is " + be.status());
                    }
                    helper.assertTrue(net.upkeep() == 3L * upkeep, "the network owes 3 x " + upkeep + ", owes " + net.upkeep());
                    helper.assertTrue(a.energy.getEnergyStored() == 0 && b.energy.getEnergyStored() == 0, "the others need no FE of their own");
                    mark[0] = powered.energy.getEnergyStored();
                })
                .thenExecuteAfter(10, () -> helper.assertTrue(powered.energy.getEnergyStored() == mark[0] - 10 * 3 * upkeep,
                        "10 ticks of 3 upkeeps, used " + (mark[0] - powered.energy.getEnergyStored())))
                .thenExecute(() -> powered.energy.setEnergy(3 * upkeep - 1))
                .thenExecuteAfter(3, () -> {
                    for (QuantumLinkBlockEntity be : List.of(powered, a, b)) {
                        helper.assertTrue(be.status() == Status.NO_POWER, "short of the whole upkeep, all wait, one is " + be.status());
                    }
                    helper.assertTrue(powered.energy.getEnergyStored() == 3 * upkeep - 1, "an unpaid upkeep takes nothing");
                })
                .thenExecute(() -> a.energy.setEnergy(1_000))
                .thenExecuteAfter(3, () -> helper.assertTrue(powered.status().connected() && b.status().connected(),
                        "FE in any Link pays for all of them"))
                .thenExecute(() -> delete(helper, net))
                .thenSucceed();
    }

    // ---- Dimensions ----

    /** A Mk1 in the Nether sends FE to a Mk1 in the Overworld on the same network. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mk1WorksAcrossDimensions(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the test server has a Nether");
        LinkNetwork net = network(helper, "Nether Line");
        BlockPos far = new BlockPos(8, 100, 8);
        ChunkPos chunk = new ChunkPos(far);
        nether.setChunkForced(chunk.x, chunk.z, true);
        nether.setBlockAndUpdate(far, LinkRegistry.LINK_MK1.get().defaultBlockState());
        QuantumLinkBlockEntity sender = (QuantumLinkBlockEntity) nether.getBlockEntity(far);
        sender.joinNetwork(net);
        sender.setEnergyMode(TransferMode.SEND);
        sender.energy.setEnergy(64_000);
        QuantumLinkBlockEntity receiver = link(helper, new BlockPos(1, 1, 1), LinkTier.MK1, net, TransferMode.RECEIVE, TransferMode.OFF);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(sender.status().connected(), "the Nether Mk1 is connected, is " + sender.status());
                    helper.assertTrue(receiver.energy.getEnergyStored() > 50_000, "the Overworld Mk1 gets its FE, has " + receiver.energy.getEnergyStored());
                })
                .thenExecute(() -> {
                    sender.onBroken();
                    nether.removeBlock(far, false);
                    nether.setChunkForced(chunk.x, chunk.z, false);
                    delete(helper, net);
                })
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
