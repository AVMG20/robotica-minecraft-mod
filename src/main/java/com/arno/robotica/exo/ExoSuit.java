package com.arno.robotica.exo;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What a living entity wears: the worn Exo pieces, their active modules and the shared energy pool.
 * Safe on both sides (reads item components only). Slot index 0 head, 1 chest, 2 legs, 3 feet.
 *
 * <p>Energy rule: with the full set on, every module draws from all four batteries (most charged piece first);
 * with a partial set a module draws only from the piece it sits in.
 *
 * <p>Module rule: a kind counts once per suit at the highest switched-on level, so two of a kind never stack.
 */
public final class ExoSuit {
    private ExoSuit() {}

    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ExoModuleKind[] KINDS = ExoModuleKind.values();

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

    /** The switched-on modules of the worn suit: highest level per kind and the piece it sits in. */
    public static final class Active {
        public final int[] level = new int[KINDS.length];
        public final int[] piece = new int[KINDS.length];

        public int level(ExoModuleKind kind) {
            return level[kind.ordinal()];
        }

        public boolean has(ExoModuleKind kind) {
            return level[kind.ordinal()] > 0;
        }

        /** Piece index (0-3) of the active module of this kind. */
        public int piece(ExoModuleKind kind) {
            return piece[kind.ordinal()];
        }
    }

    /**
     * Collects the switched-on modules of every worn piece. Duplicates count once, at their highest level. A module in
     * the wrong piece or below its mark ({@link ExoData#fitsHere}) does nothing.
     */
    public static Active active(LivingEntity entity) {
        Active a = new Active();
        for (int p = 0; p < 4; p++) {
            ItemStack piece = piece(entity, SLOTS[p]);
            if (piece.isEmpty()) continue;
            int n = ExoData.slotCount(piece);
            for (int i = 0; i < n; i++) {
                if (!(ExoData.module(piece, i).getItem() instanceof ExoModuleItem m) || !ExoData.isEnabled(piece, i)
                        || !ExoData.fitsHere(piece, i)) continue;
                int k = m.kind.ordinal();
                if (m.level > a.level[k]) {
                    a.level[k] = m.level;
                    a.piece[k] = p;
                }
            }
        }
        return a;
    }

    /** Ticks between two Jet Assist air jumps. */
    public static final int AIR_JUMP_GAP = 6;

    /** What the Power Regulator leaves of every cost (1 = no regulator). */
    public static double costFactor(Active a) {
        return 1.0 - ExoConfig.regulatorSaving(a.level(ExoModuleKind.POWER_REGULATOR));
    }

    /**
     * True when the suit's Jet Assist can pay an air jump right now. The client checks exactly this before it predicts
     * the jump, and the server repeats it, so both sides agree.
     */
    public static boolean canPayAirJump(LivingEntity entity, Active a) {
        int level = a.level(ExoModuleKind.JET_ASSIST);
        if (level <= 0) return false;
        return energyFor(entity, SLOTS[a.piece(ExoModuleKind.JET_ASSIST)]) >= Math.max(ExoConfig.doubleJumpCost(level) * costFactor(a), 1);
    }

    /** Level of a switched-on module kind in the worn suit, 0 when absent. */
    public static int level(LivingEntity entity, ExoModuleKind kind) {
        return active(entity).level(kind);
    }

    /** True when the module is installed, switched on and its energy source holds any energy. Used by client prediction too. */
    public static boolean isActive(LivingEntity entity, ExoModuleKind kind) {
        Active a = active(entity);
        return a.has(kind) && energyFor(entity, SLOTS[a.piece(kind)]) > 0;
    }

    /**
     * The core of the set bonus: the chestplate's core when all four pieces are worn at {@link ExoConfig#setBonusMinMark}
     * or better, otherwise NONE.
     */
    public static ExoData.Core setBonus(LivingEntity entity) {
        int min = ExoConfig.setBonusMinMark();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack piece = piece(entity, slot);
            if (piece.isEmpty() || ExoData.mark(piece) < min) return ExoData.Core.NONE;
        }
        return ExoData.coreKind(ExoData.core(piece(entity, EquipmentSlot.CHEST)));
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

    /**
     * Charges the suit with {@code amount} FE made by a module in {@code slot} (Solar Weave, Kinetic Generator): the piece
     * itself first, with the full set the emptiest piece gets the rest. Returns what was stored. Server side.
     */
    public static int charge(LivingEntity entity, EquipmentSlot slot, int amount) {
        if (amount <= 0) return 0;
        int left = amount - ItemEnergy.addInternal(piece(entity, slot), amount);
        if (left > 0 && fullSet(entity)) {
            for (int pass = 0; pass < 4 && left > 0; pass++) {
                ItemStack best = ItemStack.EMPTY;
                int bestSpace = 0;
                for (EquipmentSlot s : SLOTS) {
                    ItemStack p = piece(entity, s);
                    int space = ItemEnergy.capacity(p) - ItemEnergy.get(p);
                    if (space > bestSpace) {
                        best = p;
                        bestSpace = space;
                    }
                }
                if (best.isEmpty()) break;
                left -= ItemEnergy.addInternal(best, left);
            }
        }
        return amount - left;
    }
}
