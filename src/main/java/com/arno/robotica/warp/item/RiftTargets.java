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

/** The pad list of a Rift Remote ({@link WarpComponents#BOUND_PADS}). Pure item-stack logic, no level access. */
public final class RiftTargets {
    private RiftTargets() {}

    public enum AddResult { ADDED, ALREADY_STORED, FULL }

    /** The stored pads, including a single pad of an older remote that has not been migrated yet. Never null. */
    public static List<BoundPad> list(ItemStack stack) {
        List<BoundPad> stored = stack.get(WarpComponents.BOUND_PADS.get());
        List<BoundPad> out = stored == null ? new ArrayList<>() : new ArrayList<>(stored);
        BoundPad legacy = stack.get(WarpComponents.BOUND_PAD.get());
        if (legacy != null && find(out, legacy.id()) == null) out.add(0, legacy);
        if (out.size() > WarpComponents.MAX_RIFT_PADS) out = new ArrayList<>(out.subList(0, WarpComponents.MAX_RIFT_PADS));
        return out;
    }

    /** Moves the single pad of an older Rift Remote (or a smithed Recall Remote) into the list. */
    public static void migrate(ItemStack stack) {
        if (!stack.has(WarpComponents.BOUND_PAD.get())) return;
        List<BoundPad> merged = list(stack);
        stack.remove(WarpComponents.BOUND_PAD.get());
        stack.set(WarpComponents.BOUND_PADS.get(), List.copyOf(merged));
    }

    public static AddResult add(ItemStack stack, PadRecord rec) {
        migrate(stack);
        List<BoundPad> list = list(stack);
        BoundPad entry = new BoundPad(rec.id(), rec.name(), Optional.of(GlobalPos.of(rec.dimension(), rec.pos())));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(rec.id())) {
                list.set(i, entry); // refresh the snapshot
                stack.set(WarpComponents.BOUND_PADS.get(), List.copyOf(list));
                return AddResult.ALREADY_STORED;
            }
        }
        if (list.size() >= WarpComponents.MAX_RIFT_PADS) return AddResult.FULL;
        list.add(entry);
        stack.set(WarpComponents.BOUND_PADS.get(), List.copyOf(list));
        return AddResult.ADDED;
    }

    /** Returns true when the pad was stored. */
    public static boolean remove(ItemStack stack, UUID id) {
        migrate(stack);
        List<BoundPad> list = list(stack);
        if (!list.removeIf(b -> b.id().equals(id))) return false;
        if (list.isEmpty()) stack.remove(WarpComponents.BOUND_PADS.get());
        else stack.set(WarpComponents.BOUND_PADS.get(), List.copyOf(list));
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
        if (changed || stack.has(WarpComponents.BOUND_PAD.get())) {
            stack.remove(WarpComponents.BOUND_PAD.get());
            stack.set(WarpComponents.BOUND_PADS.get(), List.copyOf(list));
        }
    }

    /** Current name of a stored pad: the registry's when the pad is known, else the snapshot. */
    public static String currentName(WarpPads pads, BoundPad bound) {
        PadRecord rec = pads.get(bound.id());
        return rec != null ? rec.name() : bound.name();
    }
}
