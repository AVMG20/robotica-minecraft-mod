package com.arno.robotica.storage.remote;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.chunk.ChunkTickets;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.item.TerminalLinkItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A Storage Terminal screen opened through a Terminal Link. Replaces the menu's distance check: the terminal must still
 * stand and the player must still hold the link bound to it (in its dimension for a Mk1). Drains the link's FE once a
 * second, closes after {@link #IDLE_TICKS} without a click, and releases the player's chunk ticket when the screen closes.
 */
public final class RemoteAccess {
    /** 60 seconds without a click, scroll or search closes the screen. */
    public static final int IDLE_TICKS = 1_200;

    /** Why the screen closed, each with a {@code message.robotica.terminal_link.<key>} message. */
    public enum Reason {
        LOST, DIMENSION, MISSING, EMPTY, IDLE;

        public Component message() {
            return Component.translatable("message.robotica.terminal_link." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final GlobalPos target;
    private long lastActive;
    private long nextDrain;
    @Nullable
    private Reason closeReason;

    public RemoteAccess(GlobalPos target, long now) {
        this.target = target;
        this.lastActive = now;
        this.nextDrain = now + TerminalLinks.OPEN_TICKS;
    }

    public GlobalPos target() {
        return target;
    }

    @Nullable
    public Reason closeReason() {
        return closeReason;
    }

    /** A click, scroll, search or recipe fill: resets the idle timer. */
    public void touch(Player player) {
        lastActive = player.level().getGameTime();
    }

    private boolean fail(Reason reason) {
        if (closeReason == null) closeReason = reason;
        return false;
    }

    /** Server check every tick, instead of the distance check. */
    public boolean stillValid(Player player, StorageTerminalBlockEntity be) {
        if (closeReason != null) return false;
        if (be.isRemoved() || be.getLevel() == null || be.getLevel().getBlockEntity(be.getBlockPos()) != be) return fail(Reason.MISSING);
        ItemStack held = TerminalLinks.heldLink(player, target);
        if (held == null) return fail(Reason.LOST);
        if (!((TerminalLinkItem) held.getItem()).crossDimension() && !player.level().dimension().equals(target.dimension())) {
            return fail(Reason.DIMENSION);
        }
        return true;
    }

    /** Once per server tick while open: idle timer and FE. */
    public void tick(ServerPlayer player) {
        if (closeReason != null) return;
        long now = player.level().getGameTime();
        if (now - lastActive > IDLE_TICKS) {
            closeReason = Reason.IDLE;
            return;
        }
        if (now < nextDrain) return;
        nextDrain = now + TerminalLinks.OPEN_TICKS;
        ItemStack held = TerminalLinks.heldLink(player, target);
        if (held == null || player.getAbilities().instabuild) return;
        int cost = ((TerminalLinkItem) held.getItem()).fePerTick() * TerminalLinks.OPEN_TICKS;
        if (!ItemEnergy.tryUse(held, cost)) {
            ItemEnergy.set(held, 0);
            closeReason = Reason.EMPTY;
        }
    }

    /** The screen closed (any way): the chunk goes, a chirp, and the reason when it was not the player's choice. */
    public void closed(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        ChunkTickets.releasePlayer(sp.server, sp.getUUID());
        CoreSounds.play(sp, CoreSounds.TERMINAL_LINK_CLOSE, SoundSource.PLAYERS, 0.6F, 1.0F);
        if (closeReason != null) sp.displayClientMessage(closeReason.message(), true);
    }
}
