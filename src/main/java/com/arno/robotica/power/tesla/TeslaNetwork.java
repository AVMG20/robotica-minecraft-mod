package com.arno.robotica.power.tesla;

import com.arno.robotica.power.PowerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Energy flow and link rules of Tesla Coils.
 * <p>
 * Flow: a root coil (one sitting on an FE source) asks the source what it could give, up to its own rate, and pushes it
 * through its links. First a demand pass walks the links and records what each link could take this tick: a machine by a
 * simulated insert, a coil link by what that coil could pass on, plus the hop loss. Then each coil splits what it has
 * over its links fairly but never more than a link takes, so what one link does not need goes to the others, coil links
 * included. A coil-to-coil hop forwards the share minus the hop loss and the receiving coil splits it again, never more
 * than its tier rate per tick. Stamps per push stop loops and visit each coil once, so one push is O(links) of the
 * reachable coils and allocates nothing. Only what was actually delivered is taken out of the source. Unloaded targets
 * are skipped, chunks are never loaded.
 */
public final class TeslaNetwork {
    private TeslaNetwork() {}

    /** Hard cap on a chain, on top of the stamps. */
    public static final int MAX_DEPTH = 64;

    /** Stamp of the current push (server thread only); 0 is never used, so a new coil is never marked. */
    private static int stamp;

    /** Pushes energy from {@code source} through {@code root}. Returns the FE taken from the source. */
    public static int pushFromSource(TeslaCoilBlockEntity root, IEnergyStorage source, long now) {
        int budget = root.remainingRate(now);
        if (budget <= 0) return 0;
        int available = source.extractEnergy(budget, true);
        if (available <= 0) return 0;
        if (++stamp == 0) stamp = 1;
        if (demand(root, stamp, now, 0) <= 0) return 0;
        root.pushMark = stamp;
        int used = push(root, available, stamp, now, 0);
        if (used > 0) source.extractEnergy(used, false);
        return used;
    }

    /**
     * Records in {@code coil.linkDemand} what each link could take this tick and returns what could leave the coil
     * (delivered plus hop losses), capped at its remaining rate. Each coil is measured once per stamp; a link to a coil
     * measured before (a loop, or a second path to it) wants nothing, the same as in {@link #push}.
     */
    static int demand(TeslaCoilBlockEntity coil, int stamp, long now, int depth) {
        coil.demandMark = stamp;
        Level level = coil.getLevel();
        List<TeslaLink> links = coil.links();
        int n = Math.min(links.size(), coil.linkDemand.length);
        int cap = coil.remainingRate(now);
        long rangeSq = (long) coil.tier().range() * coil.tier().range();
        int keep = 100 - PowerConfig.teslaHopLoss();
        long total = 0;
        for (int i = 0; i < n; i++) {
            TeslaLink link = links.get(i);
            int want = 0;
            if (level != null && cap > 0 && reachable(level, coil, link, rangeSq)) {
                if (link.coil()) {
                    TeslaCoilBlockEntity child = childCoil(level, link, depth);
                    if (child != null && child.demandMark != stamp) {
                        int passOn = demand(child, stamp, now, depth + 1);
                        if (passOn > 0) want = (int) Math.min(cap, ((long) passOn * 100 + keep - 1) / keep);
                    }
                } else {
                    IEnergyStorage target = coil.targetCapability(i);
                    if (target != null && target.canReceive()) want = Math.max(0, Math.min(cap, target.receiveEnergy(cap, true)));
                }
            }
            coil.linkDemand[i] = want;
            total += want;
        }
        return (int) Math.min(cap, total);
    }

    /** Sends up to {@code amount} FE from {@code coil} to its links. Returns what left the coil (delivered plus hop losses). */
    static int push(TeslaCoilBlockEntity coil, int amount, int stamp, long now, int depth) {
        Level level = coil.getLevel();
        List<TeslaLink> links = coil.links();
        int n = Math.min(links.size(), coil.linkDemand.length);
        int budget = Math.min(amount, coil.remainingRate(now));
        if (level == null || n == 0 || budget <= 0 || coil.demandMark != stamp) return 0;
        long rangeSq = (long) coil.tier().range() * coil.tier().range();
        int keep = 100 - PowerConfig.teslaHopLoss();
        int start = coil.nextRotation(n);
        int[] want = coil.linkDemand, give = coil.linkGive;
        // Fair split capped by demand: every round each link still short gets an equal share of what is left.
        int left = budget, active = 0;
        for (int i = 0; i < n; i++) {
            give[i] = 0;
            if (want[i] > 0) active++;
        }
        while (left > 0 && active > 0) {
            int share = (left + active - 1) / active;
            for (int i = 0; i < n && left > 0; i++) {
                int k = (start + i) % n, need = want[k] - give[k];
                if (need <= 0) continue;
                int g = Math.min(Math.min(need, share), left);
                give[k] += g;
                left -= g;
                if (give[k] == want[k]) active--;
            }
        }
        int remaining = budget;
        boolean shortfall = false;
        for (int i = 0; i < n && remaining > 0; i++) {
            int k = (start + i) % n;
            if (give[k] <= 0) continue;
            int sent = send(level, coil, k, Math.min(give[k], remaining), stamp, now, depth, rangeSq, keep);
            coil.recordLinkSent(k, sent);
            remaining -= sent;
            if (sent < give[k]) shortfall = true;
        }
        // A link took less than it said (another coil filled the same machine first): offer the rest to the machines.
        for (int i = 0; shortfall && i < n && remaining > 0; i++) {
            int k = (start + i) % n;
            if (links.get(k).coil()) continue;
            int sent = send(level, coil, k, remaining, stamp, now, depth, rangeSq, keep);
            coil.recordLinkSent(k, sent);
            remaining -= sent;
        }
        int used = budget - remaining;
        coil.recordSent(now, used);
        return used;
    }

    private static boolean reachable(Level level, TeslaCoilBlockEntity coil, TeslaLink link, long rangeSq) {
        return coil.getBlockPos().distSqr(link.pos()) <= rangeSq && level.isLoaded(link.pos());
    }

    /** The relay coil a coil link points at, or null (too deep, gone, or an emitter). */
    private static TeslaCoilBlockEntity childCoil(Level level, TeslaLink link, int depth) {
        if (depth >= MAX_DEPTH || !(level.getBlockEntity(link.pos()) instanceof TeslaCoilBlockEntity child) || child.isEmitter()) return null;
        return child;
    }

    private static int send(Level level, TeslaCoilBlockEntity coil, int index, int amount, int stamp, long now,
                            int depth, long rangeSq, int keep) {
        TeslaLink link = coil.links().get(index);
        if (amount <= 0 || !reachable(level, coil, link, rangeSq)) return 0;
        if (link.coil()) {
            TeslaCoilBlockEntity child = childCoil(level, link, depth);
            if (child == null || child.pushMark == stamp) return 0;
            child.pushMark = stamp;
            int offer = (int) ((long) amount * keep / 100);
            if (offer <= 0) return 0;
            int delivered = push(child, offer, stamp, now, depth + 1);
            if (delivered <= 0) return 0;
            return (int) Math.min(amount, ((long) delivered * 100 + keep - 1) / keep);
        }
        IEnergyStorage target = coil.targetCapability(index);
        if (target == null || !target.canReceive()) return 0;
        return Math.max(0, target.receiveEnergy(amount, false));
    }

    // ---- linking ----

    public enum LinkResult {
        LINKED, UNLINKED, FACE_CHANGED, FULL, OUT_OF_RANGE, NO_ENERGY, WRONG_FACE, SELF, OWN_SUPPORT, REVERSE, EMITTER;

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
     * A coil with a single link (Tesla Emitter) moves that link to the new target instead of being full.
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
        boolean replace = coil.maxLinks() == 1 && coil.linkCount() == 1;
        if (!replace && coil.linkCount() >= coil.maxLinks()) return LinkResult.FULL;
        TeslaLink link;
        if (level.getBlockEntity(target) instanceof TeslaCoilBlockEntity other) {
            if (other.isEmitter()) return LinkResult.EMITTER;
            if (other.indexOf(coil.getBlockPos()) >= 0) return LinkResult.REVERSE;
            link = TeslaLink.toCoil(target);
        } else if (level.getCapability(Capabilities.EnergyStorage.BLOCK, target, face) == null) {
            return level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null) != null ? LinkResult.WRONG_FACE : LinkResult.NO_ENERGY;
        } else {
            link = TeslaLink.toMachine(target, face);
        }
        if (replace) coil.setLink(0, link);
        else coil.addLink(link);
        return LinkResult.LINKED;
    }
}
