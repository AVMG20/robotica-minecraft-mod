package com.arno.robotica.warp.item;

import com.arno.robotica.warp.WarpComponents;
import com.arno.robotica.warp.WarpComponents.BoundPad;
import com.arno.robotica.warp.pad.PadRecord;
import com.arno.robotica.warp.pad.WarpPads;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The pad list of a Warp Remote ({@link WarpComponents#BOUND_PADS}); the first pad is home. Pure item-stack logic. */
public final class RemoteTargets {
    private RemoteTargets() {}

    public enum AddResult { ADDED, ALREADY_STORED, FULL }

    /** The stored pads, home first. Never null. */
    public static List<BoundPad> list(ItemStack stack) {
        List<BoundPad> stored = stack.get(WarpComponents.BOUND_PADS.get());
        if (stored == null) return new ArrayList<>();
        List<BoundPad> out = new ArrayList<>(stored);
        return out.size() > WarpComponents.MAX_PADS ? new ArrayList<>(out.subList(0, WarpComponents.MAX_PADS)) : out;
    }

    /** The home pad, or null when nothing is stored. */
    @Nullable
    public static BoundPad home(ItemStack stack) {
        List<BoundPad> stored = stack.get(WarpComponents.BOUND_PADS.get());
        return stored == null || stored.isEmpty() ? null : stored.get(0);
    }

    private static void store(ItemStack stack, List<BoundPad> list) {
        if (list.isEmpty()) stack.remove(WarpComponents.BOUND_PADS.get());
        else stack.set(WarpComponents.BOUND_PADS.get(), List.copyOf(list));
    }

    /** Adds the pad at the end (the first pad stored becomes home). A known pad only gets its snapshot refreshed. */
    public static AddResult add(ItemStack stack, PadRecord rec) {
        List<BoundPad> list = list(stack);
        BoundPad entry = new BoundPad(rec.id(), rec.name(), Optional.of(GlobalPos.of(rec.dimension(), rec.pos())));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(rec.id())) {
                list.set(i, entry);
                store(stack, list);
                return AddResult.ALREADY_STORED;
            }
        }
        if (list.size() >= WarpComponents.MAX_PADS) return AddResult.FULL;
        list.add(entry);
        store(stack, list);
        return AddResult.ADDED;
    }

    /** Returns true when the pad was stored. */
    public static boolean remove(ItemStack stack, UUID id) {
        List<BoundPad> list = list(stack);
        if (!list.removeIf(b -> b.id().equals(id))) return false;
        store(stack, list);
        return true;
    }

    /** Moves a stored pad to the front. Returns true when the pad was stored. */
    public static boolean setHome(ItemStack stack, UUID id) {
        List<BoundPad> list = list(stack);
        BoundPad found = find(list, id);
        if (found == null) return false;
        list.remove(found);
        list.add(0, found);
        store(stack, list);
        return true;
    }

    @Nullable
    public static BoundPad find(List<BoundPad> list, UUID id) {
        for (BoundPad b : list) if (b.id().equals(id)) return b;
        return null;
    }

    /** Copies current names and positions from the registry into the snapshots (pads that are gone keep theirs). */
    public static void refresh(ItemStack stack, WarpPads pads) {
        List<BoundPad> list = list(stack);
        if (list.isEmpty()) return;
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            BoundPad b = list.get(i);
            PadRecord rec = pads.get(b.id());
            if (rec == null) continue;
            BoundPad fresh = new BoundPad(b.id(), rec.name(), Optional.of(GlobalPos.of(rec.dimension(), rec.pos())));
            if (!fresh.equals(b)) {
                list.set(i, fresh);
                changed = true;
            }
        }
        if (changed) store(stack, list);
    }

    /** Current name of a stored pad: the registry's when the pad is known, else the snapshot. */
    public static String currentName(WarpPads pads, BoundPad bound) {
        PadRecord rec = pads.get(bound.id());
        return rec != null ? rec.name() : bound.name();
    }
}
