package com.arno.robotica.power.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

/**
 * Link rules of Tesla Relays and Tesla Emitters. An emitter links to one relay or machine; a relay links to machines and
 * to other relays, and a relay link works both ways. Energy flow: {@link TeslaGrid}.
 */
public final class TeslaNetwork {
    private TeslaNetwork() {}

    public enum LinkResult {
        LINKED, UNLINKED, FACE_CHANGED, FULL, OUT_OF_RANGE, NO_ENERGY, WRONG_FACE, SELF, OWN_SUPPORT, EMITTER, NOT_YOURS;

        public boolean changed() {
            return this == LINKED || this == UNLINKED || this == FACE_CHANGED;
        }
    }

    public static boolean inRange(TeslaCoilBlockEntity coil, BlockPos target) {
        int range = coil.tier().range();
        return coil.getBlockPos().distSqr(target) <= (long) range * range;
    }

    /**
     * Links {@code coil} to the block at {@code target}, or unlinks them when either already links the other. For a
     * machine, {@code face} is the side the energy goes in. Clicking a linked machine on another face moves the link to
     * that face. An emitter's one link moves to a new target instead of being full.
     */
    public static LinkResult toggle(TeslaCoilBlockEntity coil, BlockPos target, Direction face) {
        return toggle(coil, target, face, null);
    }

    /** As {@link #toggle(TeslaCoilBlockEntity, BlockPos, Direction)}; a relay link also needs {@code player} to own the other relay. */
    public static LinkResult toggle(TeslaCoilBlockEntity coil, BlockPos target, Direction face, @Nullable Player player) {
        Level level = coil.getLevel();
        if (level == null || target.equals(coil.getBlockPos())) return LinkResult.SELF;
        BlockPos self = coil.getBlockPos();
        TeslaCoilBlockEntity other = level.getBlockEntity(target) instanceof TeslaCoilBlockEntity be ? be : null;
        // A relay link joins two networks, so it takes both ends.
        if (other != null && player != null && !other.canEdit(player)) return LinkResult.NOT_YOURS;
        if (other != null) {
            int back = other.indexOf(self);
            if (back >= 0) {
                other.removeLink(back);
                coil.removeBacklink(target);
                return LinkResult.UNLINKED;
            }
        }
        int existing = coil.indexOf(target);
        if (existing >= 0) {
            TeslaLink link = coil.link(existing);
            if (link.coil() || link.face() == face) {
                coil.removeLink(existing);
                if (other != null) other.removeBacklink(self);
                return LinkResult.UNLINKED;
            }
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, target, face) == null) return LinkResult.WRONG_FACE;
            coil.setLink(existing, TeslaLink.toMachine(target, face));
            return LinkResult.FACE_CHANGED;
        }
        if (other != null && other.isEmitter()) return LinkResult.EMITTER;
        if (coil.isEmitter() && target.equals(coil.supportPos())) return LinkResult.OWN_SUPPORT;
        if (!inRange(coil, target)) return LinkResult.OUT_OF_RANGE;
        boolean replace = coil.maxLinks() == 1 && coil.linkCount() == 1;
        if (!replace && coil.linkCount() >= coil.maxLinks()) return LinkResult.FULL;
        TeslaLink link;
        if (other != null) {
            link = TeslaLink.toCoil(target);
        } else if (level.getCapability(Capabilities.EnergyStorage.BLOCK, target, face) == null) {
            return level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null) != null ? LinkResult.WRONG_FACE : LinkResult.NO_ENERGY;
        } else {
            link = TeslaLink.toMachine(target, face);
        }
        if (replace) {
            TeslaLink old = coil.link(0);
            if (old.coil() && level.isLoaded(old.pos()) && level.getBlockEntity(old.pos()) instanceof TeslaCoilBlockEntity was) {
                was.removeBacklink(self);
            }
            coil.setLink(0, link);
        } else {
            coil.addLink(link);
        }
        if (other != null) other.addBacklink(self);
        return LinkResult.LINKED;
    }
}
