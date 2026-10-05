package com.arno.robotica.exo;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.exo.item.ExoArmorItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What a living entity wears: the worn Exo pieces, their active modules and the shared energy pool.
 * Safe on both sides (reads item components only). Slot index 0 head, 1 chest, 2 legs, 3 feet.
 *
 * <p>Energy rule: with the full set on, every module draws from all four batteries (most charged piece first);
 * with a partial set a module draws only from the piece it sits in.
 */
public final class ExoSuit {
    private ExoSuit() {}

    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** The worn Exo piece in a slot, or EMPTY. */
    public static ItemStack piece(LivingEntity entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        return stack.getItem() instanceof ExoArmorItem item && item.getEquipmentSlot() == slot ? stack : ItemStack.EMPTY;
    }

    public static boolean wearingAny(LivingEntity entity) {
        for (EquipmentSlot slot : SLOTS) {
            if (!piece(entity, slot).isEmpty()) return true;
        }
        return false;
    }

    public static boolean fullSet(LivingEntity entity) {
        for (EquipmentSlot slot : SLOTS) {
            if (piece(entity, slot).isEmpty()) return false;
        }
        return true;
    }

    /** Bit set of {@link ExoModuleKind#bit()} for every module that is installed on a worn piece and switched on. */
    public static int activeMask(LivingEntity entity) {
        int mask = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack piece = piece(entity, slot);
            if (!piece.isEmpty()) mask |= ExoData.activeMask(piece);
        }
        return mask;
    }

    /** True when the module is installed, switched on and its piece holds any energy. Used by client prediction too. */
    public static boolean isActive(LivingEntity entity, ExoModuleKind kind) {
        ItemStack piece = piece(entity, kind.slot);
        return !piece.isEmpty() && (ExoData.activeMask(piece) & kind.bit()) != 0 && energyFor(entity, kind.slot) > 0;
    }

    /** Energy a module in this slot may use: all four batteries with the full set, otherwise the piece itself. */
    public static int energyFor(LivingEntity entity, EquipmentSlot slot) {
        if (fullSet(entity)) return totalEnergy(entity);
        return ItemEnergy.get(piece(entity, slot));
    }

    public static int totalEnergy(LivingEntity entity) {
        long sum = 0;
        for (EquipmentSlot slot : SLOTS) sum += ItemEnergy.get(piece(entity, slot));
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    public static int totalCapacity(LivingEntity entity) {
        long sum = 0;
        for (EquipmentSlot slot : SLOTS) sum += ItemEnergy.capacity(piece(entity, slot));
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /**
     * Takes up to {@code amount} FE for a module in {@code slot} (all pieces with the full set, most charged first,
     * otherwise the piece itself). Returns what was taken. Server side.
     */
    public static int drain(LivingEntity entity, EquipmentSlot slot, int amount) {
        if (amount <= 0) return 0;
        if (!fullSet(entity)) return ItemEnergy.drain(piece(entity, slot), amount);
        int left = amount;
        while (left > 0) {
            ItemStack best = ItemStack.EMPTY;
            int bestEnergy = 0;
            for (EquipmentSlot s : SLOTS) {
                ItemStack p = piece(entity, s);
                int e = ItemEnergy.get(p);
                if (e > bestEnergy) {
                    best = p;
                    bestEnergy = e;
                }
            }
            if (best.isEmpty()) break;
            left -= ItemEnergy.drain(best, left);
        }
        return amount - left;
    }
}
