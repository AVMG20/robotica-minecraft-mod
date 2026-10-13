package com.arno.robotica.storage.remote;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.chunk.ChunkTickets;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.storage.StorageConfig;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.item.TerminalLinkItem;
import com.arno.robotica.storage.menu.StorageMenu;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Opening a Storage Terminal through a Terminal Link. The terminal's chunk is ticket-loaded for that player only
 * ({@link ChunkTickets#holdForPlayer}) while the screen is open; the menu's {@link RemoteAccess} checks every tick that
 * the terminal still stands and the player still holds the bound link, drains its FE and closes after 60 s idle.
 */
public final class TerminalLinks {
    private TerminalLinks() {}

    /** Why a link does not open, each with a {@code message.robotica.terminal_link.<key>} message. */
    public enum Refusal {
        UNBOUND, DIMENSION, MISSING, EMPTY, UNLOADED;

        public Component message() {
            return Component.translatable("message.robotica.terminal_link." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Ticks of FE a link must hold to open: one second. */
    public static final int OPEN_TICKS = 20;

    /** The terminal a link is bound to, or null. */
    @Nullable
    public static GlobalPos target(ItemStack stack) {
        return stack.get(StorageContent.LINKED_TERMINAL.get());
    }

    /** The checks that need no chunk: bound, reachable dimension, energy. Null when they pass. */
    @Nullable
    public static Refusal check(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof TerminalLinkItem link)) return Refusal.UNBOUND;
        GlobalPos at = target(stack);
        if (at == null) return Refusal.UNBOUND;
        if (!link.crossDimension() && !at.dimension().equals(player.level().dimension())) return Refusal.DIMENSION;
        if (player.server.getLevel(at.dimension()) == null) return Refusal.DIMENSION;
        if (!player.getAbilities().instabuild && !ItemEnergy.has(stack, link.fePerTick() * OPEN_TICKS)) return Refusal.EMPTY;
        return null;
    }

    /** Opens the bound terminal's screen. Tells the player why when it can not. */
    public static boolean open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Refusal refusal = check(player, stack);
        if (refusal != null) return refuse(player, refusal);
        GlobalPos at = target(stack);
        ServerLevel level = player.server.getLevel(at.dimension());
        ChunkPos chunk = new ChunkPos(at.pos());
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        if (!level.isLoaded(at.pos())) {
            if (!StorageConfig.linkLoadsChunks()) return refuse(player, Refusal.UNLOADED);
        }
        if (StorageConfig.linkLoadsChunks()) {
            ChunkTickets.holdForPlayer(level, player.getUUID(), chunk);
            level.getChunk(chunk.x, chunk.z);    // load it now, not a few ticks later
        }
        if (!(level.getBlockEntity(at.pos()) instanceof StorageTerminalBlockEntity terminal)) {
            ChunkTickets.releasePlayer(player.server, player.getUUID());
            return refuse(player, Refusal.MISSING);
        }
        RemoteAccess access = new RemoteAccess(at, player.serverLevel().getGameTime());
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StorageMenu(id, inv, terminal, access), terminal.getDisplayName()),
                buf -> buf.writeBlockPos(at.pos()));
        if (!(player.containerMenu instanceof StorageMenu)) {
            ChunkTickets.releasePlayer(player.server, player.getUUID());
            return false;
        }
        CoreSounds.play(player, CoreSounds.TERMINAL_LINK_OPEN, SoundSource.PLAYERS, 0.7F, 1.0F);
        player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.5, 0.3, 0.4);
        return true;
    }

    private static boolean refuse(ServerPlayer player, Refusal refusal) {
        player.displayClientMessage(refusal.message(), true);
        CoreSounds.play(player, CoreSounds.ROBOT_ERROR, SoundSource.PLAYERS, 0.5F, 1.4F);
        return false;
    }

    /** The stack in either hand that is a Terminal Link bound to {@code at}, or null. */
    @Nullable
    public static ItemStack heldLink(Player player, GlobalPos at) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof TerminalLinkItem && at.equals(target(main))) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof TerminalLinkItem && at.equals(target(off))) return off;
        return null;
    }
}
