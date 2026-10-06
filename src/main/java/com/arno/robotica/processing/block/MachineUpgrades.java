package com.arno.robotica.processing.block;

import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * Upgrade slots of a processing machine: always {@link #MAX_SLOTS} slots (so a Mk upgrade keeps every card), of which
 * only the first {@code active} take cards (Mk1 2, Mk2 3, Mk3 4, Mk4 5 by default).
 */
public class MachineUpgrades extends Upgrades {
    public static final int MAX_SLOTS = 5;

    private final IntSupplier active;

    public MachineUpgrades(Set<UpgradeKind> kinds, ToIntFunction<UpgradeKind> caps, IntSupplier active, Runnable onChanged) {
        super(MAX_SLOTS, kinds, caps, onChanged);
        this.active = active;
    }

    /** Slots that take cards on this machine. */
    public int activeSlots() {
        return Math.max(0, Math.min(MAX_SLOTS, active.getAsInt()));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot < activeSlots() && super.isItemValid(slot, stack);
    }
}
