package com.arno.robotica.exo;

import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Reads and writes the module data (installed modules, their on/off bits and the core) of an Exo armor piece. Safe on both sides. */
public final class ExoData {
    private ExoData() {}

    /** The boss cores of the chestplate socket and their set bonus. */
    public enum Core {
        NONE, SERVO, MAGMA, ANTIGRAV
    }

    public static int slotCount(ItemStack piece) {
        return piece.getItem() instanceof ExoArmorItem a ? a.moduleSlots() : 0;
    }

    public static int mark(ItemStack piece) {
        return piece.getItem() instanceof ExoArmorItem a ? a.mk : 0;
    }

    /** The installed module stack of a slot (never modify the result), or EMPTY. */
    public static ItemStack module(ItemStack piece, int slot) {
        ItemContainerContents contents = piece.get(ExoRegistry.MODULES.get());
        if (contents == null || slot < 0 || slot >= contents.getSlots()) return ItemStack.EMPTY;
        return contents.getStackInSlot(slot);
    }

    @Nullable
    public static ExoModuleKind kind(ItemStack piece, int slot) {
        return module(piece, slot).getItem() instanceof ExoModuleItem m ? m.kind : null;
    }

    /** Level of the module in a slot, 0 when empty. */
    public static int level(ItemStack piece, int slot) {
        return module(piece, slot).getItem() instanceof ExoModuleItem m ? m.level : 0;
    }

    /** Copies of the installed modules, one entry per module slot of the piece. */
    public static NonNullList<ItemStack> modules(ItemStack piece) {
        int n = slotCount(piece);
        NonNullList<ItemStack> list = NonNullList.withSize(n, ItemStack.EMPTY);
        for (int i = 0; i < n; i++) list.set(i, module(piece, i).copy());
        return list;
    }

    public static void setModules(ItemStack piece, List<ItemStack> modules) {
        boolean any = false;
        for (ItemStack s : modules) any |= !s.isEmpty();
        if (any) piece.set(ExoRegistry.MODULES.get(), ItemContainerContents.fromItems(modules));
        else piece.remove(ExoRegistry.MODULES.get());
    }

    public static int offMask(ItemStack piece) {
        return piece.getOrDefault(ExoRegistry.MODULES_OFF.get(), 0);
    }

    public static boolean isEnabled(ItemStack piece, int slot) {
        return (offMask(piece) & (1 << slot)) == 0;
    }

    public static void setEnabled(ItemStack piece, int slot, boolean on) {
        int mask = offMask(piece);
        mask = on ? mask & ~(1 << slot) : mask | (1 << slot);
        if (mask == 0) piece.remove(ExoRegistry.MODULES_OFF.get());
        else piece.set(ExoRegistry.MODULES_OFF.get(), mask);
    }

    /** Highest installed level (on or off) of a kind in the piece, 0 when absent. */
    public static int levelIn(ItemStack piece, ExoModuleKind kind) {
        int best = 0;
        int n = slotCount(piece);
        for (int i = 0; i < n; i++) {
            if (module(piece, i).getItem() instanceof ExoModuleItem m && m.kind == kind) best = Math.max(best, m.level);
        }
        return best;
    }

    /**
     * True when the module in this slot may work where it sits: it fits the piece type and the piece's mark is high
     * enough. A module that got in anyway (old data, commands) is shown dimmed and does nothing.
     */
    public static boolean fitsHere(ItemStack piece, int slot) {
        return piece.getItem() instanceof ExoArmorItem a && module(piece, slot).getItem() instanceof ExoModuleItem m
                && m.kind.fits(a.getEquipmentSlot()) && a.mk >= m.minMark();
    }

    /** Highest installed level of a kind that may work in this piece ({@link #fitsHere}), 0 when absent. */
    public static int workingLevelIn(ItemStack piece, ExoModuleKind kind) {
        int best = 0;
        int n = slotCount(piece);
        for (int i = 0; i < n; i++) {
            if (module(piece, i).getItem() instanceof ExoModuleItem m && m.kind == kind && fitsHere(piece, i)) best = Math.max(best, m.level);
        }
        return best;
    }

    /** Slot index of a module kind in the piece, or -1. */
    public static int slotOf(ItemStack piece, ExoModuleKind kind) {
        int n = slotCount(piece);
        for (int i = 0; i < n; i++) {
            if (kind(piece, i) == kind) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- core socket

    public static ItemStack core(ItemStack piece) {
        ItemContainerContents contents = piece.get(ExoRegistry.CORE.get());
        return contents == null || contents.getSlots() == 0 ? ItemStack.EMPTY : contents.getStackInSlot(0);
    }

    public static void setCore(ItemStack piece, ItemStack core) {
        if (core.isEmpty()) piece.remove(ExoRegistry.CORE.get());
        else piece.set(ExoRegistry.CORE.get(), ItemContainerContents.fromItems(List.of(core.copyWithCount(1))));
    }

    public static Core coreKind(ItemStack core) {
        if (core.is(CoreItems.SERVO_CORE.get())) return Core.SERVO;
        if (core.is(CoreItems.MAGMA_CORE.get())) return Core.MAGMA;
        if (core.is(CoreItems.ANTIGRAV_CORE.get())) return Core.ANTIGRAV;
        return Core.NONE;
    }

    public static boolean isCore(ItemStack stack) {
        return coreKind(stack) != Core.NONE;
    }
}
