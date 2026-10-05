package com.arno.robotica.exo;

import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import org.jetbrains.annotations.Nullable;

/** Reads and writes the module data (installed modules and their on/off bits) of an Exo armor piece. Safe on both sides. */
public final class ExoData {
    private ExoData() {}

    public static int slotCount(ItemStack piece) {
        return piece.getItem() instanceof ExoArmorItem a ? a.moduleSlots() : 0;
    }

    /** The installed module stack of a slot (never modify the result), or EMPTY. */
    public static ItemStack module(ItemStack piece, int slot) {
        ItemContainerContents contents = piece.get(ExoRegistry.MODULES.get());
        if (contents == null || slot < 0 || slot >= contents.getSlots()) return ItemStack.EMPTY;
        return contents.getStackInSlot(slot);
    }

    @Nullable
    public static ExoModuleKind kind(ItemStack piece, int slot) {
        ItemStack stack = module(piece, slot);
        return stack.getItem() instanceof ExoModuleItem m ? m.kind : null;
    }

    /** Copies of the installed modules, one entry per module slot of the piece. */
    public static NonNullList<ItemStack> modules(ItemStack piece) {
        int n = slotCount(piece);
        NonNullList<ItemStack> list = NonNullList.withSize(n, ItemStack.EMPTY);
        for (int i = 0; i < n; i++) list.set(i, module(piece, i).copy());
        return list;
    }

    public static void setModules(ItemStack piece, java.util.List<ItemStack> modules) {
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

    /** Bit set of {@link ExoModuleKind#bit()} of every installed module (on or off). */
    public static int installedMask(ItemStack piece) {
        int mask = 0;
        int n = slotCount(piece);
        for (int i = 0; i < n; i++) {
            ExoModuleKind kind = kind(piece, i);
            if (kind != null) mask |= kind.bit();
        }
        return mask;
    }

    /** Bit set of every installed module that is switched on. */
    public static int activeMask(ItemStack piece) {
        int mask = 0;
        int n = slotCount(piece);
        for (int i = 0; i < n; i++) {
            ExoModuleKind kind = kind(piece, i);
            if (kind != null && isEnabled(piece, i)) mask |= kind.bit();
        }
        return mask;
    }

    /** Slot index of a module kind in the piece, or -1. */
    public static int slotOf(ItemStack piece, ExoModuleKind kind) {
        int n = slotCount(piece);
        for (int i = 0; i < n; i++) {
            if (kind(piece, i) == kind) return i;
        }
        return -1;
    }
}
