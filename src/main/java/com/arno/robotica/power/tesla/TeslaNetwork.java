package com.arno.robotica.power.tesla;

import com.arno.robotica.power.PowerConfig;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;
import java.util.Set;

/**
 * Energy flow and link rules of Tesla Coils.
 * <p>
 * Flow: a root coil (one sitting on an FE source) asks the source what it could give, up to its own rate, and pushes it
 * through its links. Each coil splits what it has fairly over its links (then offers the rest to machines that still
 * take energy), never more than its tier rate per tick. A coil-to-coil hop forwards the share minus the hop loss and the
 * receiving coil splits it again. A visited set per push stops loops and visits each coil once, so one push is O(links)
 * of the reachable coils. Only what was actually delivered is taken out of the source. Unloaded targets are skipped,
 * chunks are never loaded.
 */
public final class TeslaNetwork {
    private TeslaNetwork() {}

    /** Hard cap on a chain, on top of the visited set. */
    public static final int MAX_DEPTH = 64;

    /** Pushes energy from {@code source} through {@code root}. Returns the FE taken from the source. */
    public static int pushFromSource(TeslaCoilBlockEntity root, IEnergyStorage source, long now) {
        int budget = root.remainingRate(now);
        if (budget <= 0) return 0;
        int available = source.extractEnergy(budget, true);
        if (available <= 0) return 0;
        Set<TeslaCoilBlockEntity> visited = new ReferenceOpenHashSet<>(4);
        visited.add(root);
        int used = push(root, available, visited, now, 0);
        if (used > 0) source.extractEnergy(used, false);
        return used;
    }

    /** Sends up to {@code amount} FE from {@code coil} to its links. Returns what left the coil (delivered plus hop losses). */
    static int push(TeslaCoilBlockEntity coil, int amount, Set<TeslaCoilBlockEntity> visited, long now, int depth) {
        Level level = coil.getLevel();
        List<TeslaLink> links = coil.links();
        int n = links.size();
        int budget = Math.min(amount, coil.remainingRate(now));
        if (level == null || n == 0 || budget <= 0) return 0;
        long rangeSq = (long) coil.tier().range() * coil.tier().range();
        int keep = 100 - PowerConfig.teslaHopLoss();
        int start = coil.nextRotation(n);
        int remaining = budget;
        for (int i = 0; i < n && remaining > 0; i++) {
            int share = (remaining + (n - i) - 1) / (n - i);
            remaining -= send(level, coil, (start + i) % n, share, visited, now, depth, rangeSq, keep);
        }
        for (int i = 0; i < n && remaining > 0; i++) {
            int k = (start + i) % n;
            if (!links.get(k).coil()) remaining -= send(level, coil, k, remaining, visited, now, depth, rangeSq, keep);
        }
        int used = budget - remaining;
        coil.recordSent(now, used);
        return used;
    }

    private static int send(Level level, TeslaCoilBlockEntity coil, int index, int amount, Set<TeslaCoilBlockEntity> visited, long now,
                            int depth, long rangeSq, int keep) {
        TeslaLink link = coil.links().get(index);
        if (amount <= 0 || coil.getBlockPos().distSqr(link.pos()) > rangeSq || !level.isLoaded(link.pos())) return 0;
        if (link.coil()) {
            if (depth >= MAX_DEPTH || !(level.getBlockEntity(link.pos()) instanceof TeslaCoilBlockEntity child) || !visited.add(child)) return 0;
            int offer = (int) ((long) amount * keep / 100);
            if (offer <= 0) return 0;
            int delivered = push(child, offer, visited, now, depth + 1);
            if (delivered <= 0) return 0;
            return (int) Math.min(amount, ((long) delivered * 100 + keep - 1) / keep);
        }
        IEnergyStorage target = coil.targetCapability(index);
        if (target == null || !target.canReceive()) return 0;
        return Math.max(0, target.receiveEnergy(amount, false));
    }

    // ---- linking ----

    public enum LinkResult {
        LINKED, UNLINKED, FACE_CHANGED, FULL, OUT_OF_RANGE, NO_ENERGY, WRONG_FACE, SELF, OWN_SUPPORT, REVERSE;

        public boolean changed() {
            return this == LINKED || this == UNLINKED || this == FACE_CHANGED;
        }
    }

    public static boolean inRange(TeslaCoilBlockEntity coil, BlockPos target) {
        int range = coil.tier().range();
        return coil.getBlockPos().distSqr(target) <= (long) range * range;
    }

    /**
     * Links {@code coil} to the block at {@code target}, or unlinks it when it is already a target. For a machine,
     * {@code face} is the side the energy goes in. Clicking a linked machine on another face moves the link to that face.
     */
    public static LinkResult toggle(TeslaCoilBlockEntity coil, BlockPos target, Direction face) {
        Level level = coil.getLevel();
        if (level == null || target.equals(coil.getBlockPos())) return LinkResult.SELF;
        int existing = coil.indexOf(target);
        if (existing >= 0) {
            TeslaLink link = coil.links().get(existing);
            if (link.coil() || link.face() == face) {
                coil.removeLink(existing);
                return LinkResult.UNLINKED;
            }
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, target, face) == null) return LinkResult.WRONG_FACE;
            coil.setLink(existing, TeslaLink.toMachine(target, face));
            return LinkResult.FACE_CHANGED;
        }
        if (target.equals(coil.supportPos())) return LinkResult.OWN_SUPPORT;
        if (!inRange(coil, target)) return LinkResult.OUT_OF_RANGE;
        if (coil.linkCount() >= coil.maxLinks()) return LinkResult.FULL;
        if (level.getBlockEntity(target) instanceof TeslaCoilBlockEntity other) {
            if (other.indexOf(coil.getBlockPos()) >= 0) return LinkResult.REVERSE;
            coil.addLink(TeslaLink.toCoil(target));
            return LinkResult.LINKED;
        }
        if (level.getCapability(Capabilities.EnergyStorage.BLOCK, target, face) == null) {
            return level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null) != null ? LinkResult.WRONG_FACE : LinkResult.NO_ENERGY;
        }
        coil.addLink(TeslaLink.toMachine(target, face));
        return LinkResult.LINKED;
    }
}
