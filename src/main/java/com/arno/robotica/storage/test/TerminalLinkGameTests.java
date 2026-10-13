package com.arno.robotica.storage.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.chunk.ChunkTickets;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.menu.StorageMenu;
import com.arno.robotica.storage.remote.RemoteAccess;
import com.arno.robotica.storage.remote.TerminalLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Terminal Link: dimension rule, refusals, the remote screen's chunk ticket and its checks. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class TerminalLinkGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 3));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static ItemStack link(boolean mk2, GlobalPos target) {
        ItemStack stack = new ItemStack(mk2 ? StorageContent.TERMINAL_LINK_MK2.get() : StorageContent.TERMINAL_LINK.get());
        ItemEnergy.fill(stack);
        if (target != null) stack.set(StorageContent.LINKED_TERMINAL.get(), target);
        return stack;
    }

    /** What {@link TerminalLinks#open} does, minus the packet. */
    private static boolean openMenu(ServerPlayer player, ItemStack stack, int id) {
        StorageTerminalBlockEntity terminal = TerminalLinks.reach(player, stack);
        if (terminal == null) return false;
        GlobalPos at = stack.get(StorageContent.LINKED_TERMINAL.get());
        player.containerMenu = new StorageMenu(id, player.getInventory(), terminal, new RemoteAccess(at, player.serverLevel().getGameTime()));
        return true;
    }

    /** A Mk1 refuses a terminal in another dimension, a Mk2 does not; unbound and empty links refuse too. */
    @GameTest(template = "empty")
    public static void terminalLinkMk1StaysInItsDimension(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        GlobalPos nether = GlobalPos.of(Level.NETHER, new BlockPos(8, 64, 8));
        helper.assertTrue(TerminalLinks.check(player, link(false, nether)) == TerminalLinks.Refusal.DIMENSION, "Mk1: other dimension refused");
        helper.assertTrue(TerminalLinks.check(player, link(true, nether)) == null, "Mk2: any dimension");
        helper.assertTrue(TerminalLinks.check(player, link(false, null)) == TerminalLinks.Refusal.UNBOUND, "unbound refused");
        ItemStack empty = link(false, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(POS)));
        ItemEnergy.set(empty, 0);
        helper.assertTrue(TerminalLinks.check(player, empty) == TerminalLinks.Refusal.EMPTY, "empty refused");

        player.setItemInHand(InteractionHand.MAIN_HAND, link(false, nether));
        helper.assertTrue(!TerminalLinks.open(player, InteractionHand.MAIN_HAND), "Mk1 does not open the Nether terminal");
        helper.assertTrue(!ChunkTickets.holdsAny(player.getUUID()), "and loads nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, link(false, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(POS))));
        helper.assertTrue(!TerminalLinks.open(player, InteractionHand.MAIN_HAND), "no terminal there: refused");
        helper.assertTrue(!ChunkTickets.holdsAny(player.getUUID()), "and the chunk is let go again");
        helper.succeed();
    }

    /** Open: the terminal's chunk is held for the player, the screen works from far away; close: the ticket goes. */
    @GameTest(template = "empty")
    public static void terminalLinkHoldsTheChunkOnlyWhileOpen(GameTestHelper helper) {
        helper.setBlock(POS, StorageContent.TERMINAL.get());
        BlockPos abs = helper.absolutePos(POS);
        GlobalPos target = GlobalPos.of(helper.getLevel().dimension(), abs);
        ChunkPos chunk = new ChunkPos(abs);
        ServerPlayer player = survivalPlayer(helper);
        player.moveTo(abs.getX() + 200.5, abs.getY(), abs.getZ() + 0.5);
        ItemStack stack = link(false, target);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        // A mock player can not receive the open-screen packet, so the menu is built here the way open() builds it.
        helper.assertTrue(openMenu(player, stack, 1), "the bound terminal opens from 200 blocks");
        helper.assertTrue(player.containerMenu instanceof StorageMenu menu && menu.isRemote(), "its normal screen, opened remotely");
        helper.assertTrue(ChunkTickets.holds(player.getUUID(), target.dimension(), chunk), "the terminal's chunk is held for the player");
        StorageMenu menu = (StorageMenu) player.containerMenu;
        helper.assertTrue(menu.stillValid(player), "far away but valid: the link replaces the distance check");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(!menu.stillValid(player), "without the bound link in hand the screen is not valid");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.closeContainer();
        helper.assertTrue(!ChunkTickets.holdsAny(player.getUUID()), "closing releases the ticket");

        helper.assertTrue(openMenu(player, stack, 2), "opens again");
        helper.assertTrue(ChunkTickets.holdsAny(player.getUUID()), "held again");
        StorageMenu again = (StorageMenu) player.containerMenu;
        helper.setBlock(POS, Blocks.AIR);
        helper.assertTrue(!again.stillValid(player), "a broken terminal closes the screen");
        player.closeContainer();
        helper.assertTrue(!ChunkTickets.holdsAny(player.getUUID()), "and the ticket goes");
        helper.assertTrue(RemoteAccess.IDLE_TICKS == 1_200, "60 s idle limit");
        helper.succeed();
    }
}
