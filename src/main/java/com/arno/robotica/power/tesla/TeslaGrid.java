package com.arno.robotica.power.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One Tesla network: the relays joined by relay links (either direction counts) and the emitters linked into them.
 * Its sources are the emitters and the relays that sit on a block giving out FE ({@link TeslaCoilBlockEntity#relaySource}).
 * <p>
 * The first source that ticks without a valid network walks the loaded blocks once and caches the network on every
 * member. A link change or a member going away marks only that network dead ({@link #invalidate}); a network that met
 * an unloaded link while walking is also rebuilt after any Tesla block loads.
 * <p>
 * Every tick the network runs once, as one pool: the machines say what they take, the sources what their blocks give,
 * and the smaller of the two moves. Each source gives a fair share of it and each relay hands out a fair share, both up
 * to their tier rate; inside a relay its machines get fair shares again. A machine linked from two relays is fed by the
 * first only. Energy is taken from the sources before it is handed out, so a source that gives less than it promised
 * never creates energy. Nothing is lost on the way. A run allocates nothing; chunks are never loaded.
 */
final class TeslaGrid {
    /** Bumped whenever a Tesla block loads; networks with gaps are rebuilt after it. Server thread only. */
    private static int loadVersion;
    private static int buildStamp;

    /** Built when this load version was current; whether the walk met an unloaded link; marked by {@link #invalidate}. */
    private final int builtAtLoad;
    private final boolean gaps;
    private boolean dead;
    private final TeslaCoilBlockEntity[] relays, emitters;
    /**
     * Per relay what its machines take and what it handed out; per source (the emitters, then the relays) what it can
     * give and what it gave.
     */
    private final int[] relayWant, relayGive, supply, take;
    private long lastRun = Long.MIN_VALUE;
    private int rotation;

    private TeslaGrid(List<TeslaCoilBlockEntity> relays, List<TeslaCoilBlockEntity> emitters, boolean gaps) {
        this.builtAtLoad = loadVersion;
        this.gaps = gaps;
        this.relays = relays.toArray(new TeslaCoilBlockEntity[0]);
        this.emitters = emitters.toArray(new TeslaCoilBlockEntity[0]);
        this.relayWant = new int[this.relays.length];
        this.relayGive = new int[this.relays.length];
        this.supply = new int[this.emitters.length + this.relays.length];
        this.take = new int[this.emitters.length + this.relays.length];
    }

    /** The links around {@code coil} changed or it went away: its network is rebuilt before the next run. */
    static void invalidate(TeslaCoilBlockEntity coil) {
        if (coil.grid != null) coil.grid.dead = true;
    }

    /** A Tesla block loaded: networks that met an unloaded link are rebuilt, it may be one of theirs. */
    static void loaded() {
        loadVersion++;
    }

    private boolean stale() {
        return dead || gaps && builtAtLoad != loadVersion;
    }

    /** Runs the network of {@code source} (an emitter or a relay on a source) for tick {@code now}, unless another source did. */
    static void tick(TeslaCoilBlockEntity source, long now) {
        TeslaGrid grid = source.grid;
        if (grid == null || grid.stale()) grid = build(source);
        if (grid == null || grid.lastRun == now) return;
        grid.lastRun = now;
        grid.run(now);
    }

    /** An emitter linked straight to a machine: moves what both sides allow, up to the tier rate. */
    static void sendDirect(TeslaCoilBlockEntity emitter) {
        Level level = emitter.getLevel();
        TeslaLink link = emitter.link(0);
        if (level == null || !TeslaNetwork.inRange(emitter, link.pos()) || !level.isLoaded(link.pos())) return;
        IEnergyStorage source = emitter.source(), target = emitter.targetCapability(0);
        if (source == null || target == null || !target.canReceive()) return;
        int offer = Math.max(0, source.extractEnergy(emitter.tier().rate(), true));
        int wanted = offer <= 0 ? 0 : Math.max(0, target.receiveEnergy(offer, true));
        if (wanted <= 0) return;
        int got = Math.max(0, source.extractEnergy(wanted, false));
        int sent = got <= 0 ? 0 : Math.max(0, target.receiveEnergy(got, false));
        if (sent < got) giveBack(emitter, got - sent);
        emitter.recordSent(sent);
        emitter.recordLinkSent(0, sent);
    }

    // ---- building ----

    /** The relay that link {@code index} of {@code coil} points at, if it is loaded, in range and still a relay. */
    @Nullable
    private static TeslaCoilBlockEntity relayAt(Level level, TeslaCoilBlockEntity coil, int index) {
        TeslaLink link = coil.link(index);
        if (!link.coil() || !TeslaNetwork.inRange(coil, link.pos()) || !level.isLoaded(link.pos())) return null;
        return level.getBlockEntity(link.pos()) instanceof TeslaCoilBlockEntity relay && !relay.isEmitter() && !relay.isRemoved() ? relay : null;
    }

    /** Walks the loaded network of {@code start} (an emitter or a relay) through links and backlinks, and caches it on every member. */
    @Nullable
    private static TeslaGrid build(TeslaCoilBlockEntity start) {
        Level level = start.getLevel();
        boolean emitter = start.isEmitter();
        TeslaCoilBlockEntity first = level == null ? null : !emitter ? start : start.linkCount() == 0 ? null : relayAt(level, start, 0);
        if (first == null) {
            start.grid = null;
            return null;
        }
        if (++buildStamp == 0) buildStamp = 1;
        int stamp = buildStamp;
        boolean gaps = false;
        List<TeslaCoilBlockEntity> relays = new ArrayList<>(), emitters = new ArrayList<>();
        if (emitter) {
            first.addBacklink(start.getBlockPos());
            start.buildMark = stamp;
            emitters.add(start);
        }
        first.buildMark = stamp;
        relays.add(first);
        for (int at = 0; at < relays.size(); at++) {
            TeslaCoilBlockEntity relay = relays.get(at);
            BlockPos pos = relay.getBlockPos();
            for (int i = 0; i < relay.linkCount(); i++) {
                TeslaLink link = relay.link(i);
                if (link.coil() && !level.isLoaded(link.pos())) gaps = true;
                TeslaCoilBlockEntity next = relayAt(level, relay, i);
                if (next == null) continue;
                next.addBacklink(pos);
                if (next.buildMark != stamp) {
                    next.buildMark = stamp;
                    relays.add(next);
                }
            }
            List<BlockPos> back = relay.backlinks();
            for (int i = back.size() - 1; i >= 0; i--) {
                BlockPos from = back.get(i);
                if (!level.isLoaded(from)) {
                    gaps = true;
                    continue;
                }
                int k;
                if (!(level.getBlockEntity(from) instanceof TeslaCoilBlockEntity other) || other.isRemoved()
                        || (k = other.indexOf(pos)) < 0 || !other.link(k).coil() || !TeslaNetwork.inRange(other, pos)) {
                    back.remove(i);
                    continue;
                }
                if (other.buildMark == stamp) continue;
                other.buildMark = stamp;
                (other.isEmitter() ? emitters : relays).add(other);
            }
        }
        TeslaGrid grid = new TeslaGrid(relays, emitters, gaps);
        // Each machine is fed by the first relay that links it, so two relays never count the same free space twice.
        Set<BlockPos> machines = new HashSet<>();
        for (TeslaCoilBlockEntity relay : grid.relays) {
            for (int i = 0; i < Math.min(relay.linkCount(), relay.linkShared.length); i++) {
                TeslaLink link = relay.link(i);
                relay.linkShared[i] = !link.coil() && (!TeslaNetwork.inRange(relay, link.pos()) || !machines.add(link.pos()));
            }
        }
        // A rebuild in the middle of a tick must not run that tick again.
        for (TeslaCoilBlockEntity member : relays) if (member.grid != null) grid.lastRun = Math.max(grid.lastRun, member.grid.lastRun);
        for (TeslaCoilBlockEntity member : emitters) if (member.grid != null) grid.lastRun = Math.max(grid.lastRun, member.grid.lastRun);
        for (TeslaCoilBlockEntity relay : grid.relays) relay.grid = grid;
        for (TeslaCoilBlockEntity e : grid.emitters) e.grid = grid;
        return grid;
    }

    // ---- running ----

    private void run(long now) {
        long wanted = 0;
        for (int j = 0; j < relays.length; j++) {
            relayWant[j] = relays[j].isRemoved() ? 0 : measure(relays[j]);
            wanted += relayWant[j];
        }
        if (wanted <= 0) return;
        long offered = 0;
        int sources = supply.length;
        for (int k = 0; k < sources; k++) {
            IEnergyStorage source = sourceOf(k);
            supply[k] = source == null ? 0 : Math.max(0, source.extractEnergy(sourceBlock(k).tier().rate(), true));
            offered += supply[k];
        }
        if (offered <= 0) return;
        rotation++;
        // Take first: what the sources really give is what goes out.
        fill(supply, take, sources, Math.min(wanted, offered), rotation);
        long got = 0;
        for (int k = 0; k < sources; k++) {
            if (take[k] <= 0) continue;
            IEnergyStorage source = sourceOf(k);
            int given = source == null ? 0 : Math.max(0, source.extractEnergy(take[k], false));
            take[k] = given;
            got += given;
            if (k < emitters.length) {
                // a relay's rate counts what it hands out, not what it takes in
                emitters[k].recordSent(given);
                emitters[k].recordLinkSent(0, given);
            }
        }
        if (got <= 0) return;
        fill(relayWant, relayGive, relays.length, got, rotation);
        long left = got;
        for (int j = 0; j < relays.length; j++) {
            relayGive[j] = relayGive[j] > 0 ? handOut(relays[j], relayGive[j]) : 0;
            left -= relayGive[j];
        }
        // A machine took less than it said (two relays feed it): offer the rest to the others, then back to the sources.
        for (int j = 0; j < relays.length && left > 0; j++) {
            int sent = topUp(relays[j], (int) Math.min(left, relays[j].tier().rate() - relayGive[j]));
            relayGive[j] += sent;
            left -= sent;
        }
        for (int k = 0; k < sources && left > 0; k++) {
            if (take[k] > 0) left -= giveBack(sourceBlock(k), (int) Math.min(left, take[k]));
        }
        for (int j = 0; j < relays.length; j++) {
            TeslaCoilBlockEntity relay = relays[j];
            relay.markActive(now);
            relay.recordSent(relayGive[j]);
            // Relay-to-relay links glow with what the whole network moves.
            for (int i = 0; i < relay.linkCount(); i++) {
                if (relay.link(i).coil()) relay.recordLinkSent(i, (int) Math.min(Integer.MAX_VALUE, got));
            }
        }
    }

    /** Source {@code k}: the emitters first, then the relays. */
    private TeslaCoilBlockEntity sourceBlock(int k) {
        return k < emitters.length ? emitters[k] : relays[k - emitters.length];
    }

    /** The FE storage source {@code k} feeds the network from, or null (removed, or a relay not on a source). */
    @Nullable
    private IEnergyStorage sourceOf(int k) {
        TeslaCoilBlockEntity block = sourceBlock(k);
        if (block.isRemoved()) return null;
        return k < emitters.length ? block.source() : block.relaySource();
    }

    /** Records in {@code relay.linkDemand} what each machine link takes this tick and returns the total, capped at the tier rate. */
    private static int measure(TeslaCoilBlockEntity relay) {
        Level level = relay.getLevel();
        int cap = relay.tier().rate(), n = Math.min(relay.linkCount(), relay.linkDemand.length);
        long total = 0;
        for (int i = 0; i < n; i++) {
            TeslaLink link = relay.link(i);
            int want = 0;
            if (level != null && !link.coil() && !relay.linkShared[i] && TeslaNetwork.inRange(relay, link.pos()) && level.isLoaded(link.pos())) {
                IEnergyStorage target = relay.targetCapability(i);
                if (target != null && target.canReceive()) want = Math.max(0, Math.min(cap, target.receiveEnergy(cap, true)));
            }
            relay.linkDemand[i] = want;
            total += want;
        }
        return (int) Math.min(cap, total);
    }

    /** Splits {@code amount} fairly over the machines of {@code relay}, up to what each takes. Returns what went in. */
    private static int handOut(TeslaCoilBlockEntity relay, int amount) {
        int n = Math.min(relay.linkCount(), relay.linkDemand.length);
        fill(relay.linkDemand, relay.linkGive, n, amount, relay.nextRotation(n));
        int sent = 0;
        for (int i = 0; i < n; i++) {
            if (relay.linkGive[i] > 0) sent += deliver(relay, i, relay.linkGive[i]);
        }
        return sent;
    }

    /** Offers up to {@code amount} more to the machines of {@code relay} that wanted energy this tick. Returns what went in. */
    private static int topUp(TeslaCoilBlockEntity relay, int amount) {
        int n = Math.min(relay.linkCount(), relay.linkDemand.length), sent = 0, start = relay.nextRotation(n);
        for (int i = 0; i < n && sent < amount; i++) {
            int k = (start + i) % n;
            if (relay.linkDemand[k] > 0) sent += deliver(relay, k, amount - sent);
        }
        return sent;
    }

    /** Puts {@code amount} back into the block under {@code source} or its intake. Returns what went back. */
    private static int giveBack(TeslaCoilBlockEntity source, int amount) {
        return source.giveBack(amount);
    }

    private static int deliver(TeslaCoilBlockEntity relay, int index, int amount) {
        IEnergyStorage target = relay.targetCapability(index);
        int sent = target == null ? 0 : Math.max(0, target.receiveEnergy(amount, false));
        relay.recordLinkSent(index, sent);
        return sent;
    }

    /**
     * Fair split of {@code amount} over {@code want[0..n)} into {@code give}: every round each entry that is still short
     * gets an equal share of what is left, so what one entry does not need goes to the others. {@code start} rotates who
     * gets the rounding remainder.
     */
    static void fill(int[] want, int[] give, int n, long amount, int start) {
        long left = amount;
        int active = 0;
        for (int i = 0; i < n; i++) {
            give[i] = 0;
            if (want[i] > 0) active++;
        }
        start = Math.floorMod(start, Math.max(1, n));
        while (left > 0 && active > 0) {
            long share = (left + active - 1) / active;
            for (int i = 0; i < n && left > 0; i++) {
                int k = (start + i) % n, need = want[k] - give[k];
                if (need <= 0) continue;
                int g = (int) Math.min(Math.min(need, share), left);
                give[k] += g;
                left -= g;
                if (give[k] == want[k]) active--;
            }
        }
    }
}
