package com.arno.robotica.architect.plan;

import com.arno.robotica.architect.style.BuildStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The plan of one table: which plots hold a building, in which style, where the doors are and what was built last.
 * Pure data, no world access, so the rules are testable on their own.
 * <ul>
 *   <li>Every plot that is queued or built counts as a neighbour; shared walls are left out of both shells.</li>
 *   <li>Doors only exist in outside walls. Every group of joined plots keeps at least one: when a change leaves a
 *       group without a door, the plot nearest the table gets one on its side facing the table (or any outside side).</li>
 *   <li>A built plot whose shape changed (a neighbour came or went, a door moved) needs a re-pass; the table re-walks
 *       its shell and only touches the blocks that differ.</li>
 * </ul>
 */
public final class Layout {
    public static final int EMPTY = 0, QUEUED = 1, BUILT = 2;

    private final int[] state = new int[Plots.COUNT];
    private final BuildStyle[] style = new BuildStyle[Plots.COUNT];
    private final int[] doors = new int[Plots.COUNT];
    /** Signature of the shape a built plot was last finished with (0 = none). */
    private final int[] builtSig = new int[Plots.COUNT];
    /** Queued plots in the order they were clicked. */
    private final List<Integer> order = new ArrayList<>();

    public Layout() {
        Arrays.fill(style, BuildStyle.TIMBERFRAME);
    }

    // ---------------------------------------------------------------- queries

    public int state(int plot) {
        return state[plot];
    }

    public boolean planned(int plot) {
        return Plots.valid(plot) && state[plot] != EMPTY;
    }

    public BuildStyle style(int plot) {
        return style[plot];
    }

    /** Door bits as stored (only outside sides are ever set). */
    public int doors(int plot) {
        return doors[plot];
    }

    /** Sides with a planned neighbour. */
    public int sides(int plot) {
        int m = 0;
        for (int side = 0; side < 4; side++) if (planned(Plots.neighbour(plot, side))) m |= Plots.bit(side);
        return m;
    }

    /** Corners whose diagonal neighbour is planned. */
    public int diagonals(int plot) {
        int m = 0;
        for (int c = 0; c < 4; c++) if (planned(Plots.diagonal(plot, c))) m |= 1 << c;
        return m;
    }

    public Shell.Shape shape(int plot) {
        return new Shell.Shape(sides(plot), diagonals(plot), doors[plot], ((Plots.px(plot) + Plots.pz(plot)) & 1) != 0);
    }

    /** Shape plus style: what a finished plot must look like. */
    public int signature(int plot) {
        return shape(plot).signature() | style[plot].ordinal() << 14;
    }

    public boolean needsWork(int plot) {
        return state[plot] == QUEUED || (state[plot] == BUILT && builtSig[plot] != signature(plot));
    }

    public int queuedCount() {
        return order.size();
    }

    public boolean hasWork() {
        return nextWork() >= 0;
    }

    /** The next plot to build: queued plots in click order first, then re-passes of built plots nearest the table. */
    public int nextWork() {
        for (int plot : order) if (state[plot] == QUEUED) return plot;
        int best = -1;
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            if (state[plot] == BUILT && needsWork(plot) && (best < 0 || Plots.distance(plot) < Plots.distance(best))) best = plot;
        }
        return best;
    }

    public boolean isEmpty() {
        for (int s : state) if (s != EMPTY) return false;
        return true;
    }

    // ---------------------------------------------------------------- edits

    /** Plans a building on an empty plot. False if the plot is not empty. */
    public boolean queue(int plot, BuildStyle buildStyle) {
        if (!Plots.valid(plot) || state[plot] != EMPTY) return false;
        state[plot] = QUEUED;
        style[plot] = buildStyle;
        doors[plot] = 0;
        builtSig[plot] = 0;
        order.add(plot);
        normalize();
        return true;
    }

    /** Takes a queued plot off the plan (blocks it already placed stay). False if it was not queued. */
    public boolean unqueue(int plot) {
        if (!Plots.valid(plot) || state[plot] != QUEUED) return false;
        clear(plot);
        normalize();
        return true;
    }

    /** Forgets a built plot: its blocks stay, the neighbours close their walls toward it. */
    public boolean forget(int plot) {
        if (!Plots.valid(plot) || state[plot] != BUILT) return false;
        clear(plot);
        normalize();
        return true;
    }

    public void unqueueAll() {
        for (int plot = 0; plot < Plots.COUNT; plot++) if (state[plot] == QUEUED) clear(plot);
        normalize();
    }

    private void clear(int plot) {
        state[plot] = EMPTY;
        doors[plot] = 0;
        builtSig[plot] = 0;
        order.remove(Integer.valueOf(plot));
    }

    public static final int DOOR_OK = 0, DOOR_NOT_PLANNED = 1, DOOR_SHARED = 2, DOOR_LAST = 3;

    /** Toggles the door of an outside side. A group's last door cannot be removed. */
    public int toggleDoor(int plot, int side) {
        if (!planned(plot) || side < 0 || side > 3) return DOOR_NOT_PLANNED;
        if ((sides(plot) & Plots.bit(side)) != 0) return DOOR_SHARED;
        int bit = Plots.bit(side);
        if ((doors[plot] & bit) != 0) {
            doors[plot] &= ~bit;
            if (groupDoors(group(plot)) == 0) {
                doors[plot] |= bit;
                return DOOR_LAST;
            }
        } else {
            doors[plot] |= bit;
        }
        return DOOR_OK;
    }

    /** Called when the table finished walking a plot's shell for this signature. */
    public void markBuilt(int plot, int signature) {
        if (state[plot] == EMPTY) return;
        if (state[plot] == QUEUED) order.remove(Integer.valueOf(plot));
        state[plot] = BUILT;
        builtSig[plot] = signature;
    }

    /** After moving the table elsewhere: nothing that was built stands around it, queued plots stay queued. */
    public void forgetBuilt() {
        for (int plot = 0; plot < Plots.COUNT; plot++) if (state[plot] == BUILT) clear(plot);
        normalize();
    }

    /** Drops door bits on shared sides and gives every group without a door one. */
    private void normalize() {
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            if (state[plot] == EMPTY) doors[plot] = 0;
            else doors[plot] &= ~sides(plot);
        }
        boolean[] seen = new boolean[Plots.COUNT];
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            if (state[plot] == EMPTY || seen[plot]) continue;
            List<Integer> group = group(plot);
            for (int p : group) seen[p] = true;
            if (groupDoors(group) == 0) addEntrance(group);
        }
    }

    /** Plots joined to this one through shared sides, nearest the table first. */
    public List<Integer> group(int start) {
        List<Integer> group = new ArrayList<>();
        if (!planned(start)) return group;
        boolean[] in = new boolean[Plots.COUNT];
        ArrayDeque<Integer> todo = new ArrayDeque<>();
        todo.add(start);
        in[start] = true;
        while (!todo.isEmpty()) {
            int p = todo.poll();
            group.add(p);
            for (int side = 0; side < 4; side++) {
                int n = Plots.neighbour(p, side);
                if (planned(n) && !in[n]) {
                    in[n] = true;
                    todo.add(n);
                }
            }
        }
        group.sort(Comparator.comparingInt(Plots::distance).thenComparingInt(p -> p));
        return group;
    }

    private int groupDoors(List<Integer> group) {
        int count = 0;
        for (int p : group) count += Integer.bitCount(doors[p] & ~sides(p));
        return count;
    }

    private static final int[] FALLBACK_SIDES = {Plots.S, Plots.W, Plots.E, Plots.N};

    private void addEntrance(List<Integer> group) {
        for (int p : group) {
            int toward = Plots.sideTowardTable(p);
            if ((sides(p) & Plots.bit(toward)) == 0) {
                doors[p] |= Plots.bit(toward);
                return;
            }
        }
        for (int p : group) {
            for (int side : FALLBACK_SIDES) {
                if ((sides(p) & Plots.bit(side)) == 0) {
                    doors[p] |= Plots.bit(side);
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- persistence and sync

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        int[] packed = new int[Plots.COUNT];
        for (int plot = 0; plot < Plots.COUNT; plot++) packed[plot] = state[plot] | style[plot].ordinal() << 2 | doors[plot] << 4;
        tag.putIntArray("plots", packed);
        tag.putIntArray("built_sig", builtSig.clone());
        tag.putIntArray("order", order.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public void load(CompoundTag tag) {
        Arrays.fill(state, EMPTY);
        Arrays.fill(style, BuildStyle.TIMBERFRAME);
        Arrays.fill(doors, 0);
        Arrays.fill(builtSig, 0);
        order.clear();
        if (tag.contains("plots", Tag.TAG_INT_ARRAY)) {
            int[] packed = tag.getIntArray("plots");
            for (int plot = 0; plot < Math.min(packed.length, Plots.COUNT); plot++) {
                int s = packed[plot] & 3;
                state[plot] = s <= BUILT ? s : EMPTY;
                style[plot] = BuildStyle.byOrdinal((packed[plot] >> 2) & 3);
                doors[plot] = (packed[plot] >> 4) & 15;
            }
        }
        if (tag.get("built_sig") instanceof IntArrayTag sigs) {
            int[] v = sigs.getAsIntArray();
            for (int plot = 0; plot < Math.min(v.length, Plots.COUNT); plot++) builtSig[plot] = v[plot];
        }
        for (int plot : tag.getIntArray("order")) {
            if (Plots.valid(plot) && state[plot] == QUEUED && !order.contains(plot)) order.add(plot);
        }
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            if (state[plot] == QUEUED && !order.contains(plot)) order.add(plot);
        }
        normalize();
    }

    /** 9 bits per plot for the GUI: state (2), style (2), doors (4), needs work (1). */
    public int packed(int plot) {
        return state[plot] | style[plot].ordinal() << 2 | doors[plot] << 4 | (needsWork(plot) ? 1 << 8 : 0);
    }
}
