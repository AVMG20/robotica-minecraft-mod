package com.arno.robotica.core.energy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * Block entity energy buffer. Exposed to other mods through the FE capability with the given I/O limits.
 * Internal methods ignore those limits. {@code onChanged} usually calls {@code setChanged()}.
 * Save with {@code serializeNBT(provider)} / {@code deserializeNBT(provider, tag)} (inherited).
 */
public class MachineEnergyStorage extends EnergyStorage {
    private final Runnable onChanged;

    public MachineEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int r = super.receiveEnergy(toReceive, simulate);
        if (r > 0 && !simulate) onChanged.run();
        return r;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int r = super.extractEnergy(toExtract, simulate);
        if (r > 0 && !simulate) onChanged.run();
        return r;
    }

    /** Loads the saved amount, clamped to the current capacity (it can shrink between saves, e.g. by config). */
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, Tag nbt) {
        super.deserializeNBT(provider, nbt);
        energy = Math.max(0, Math.min(capacity, energy));
    }

    public void setEnergy(int value) {
        int clamped = Math.max(0, Math.min(capacity, value));
        if (clamped != energy) {
            energy = clamped;
            onChanged.run();
        }
    }

    /** Generator output: adds energy ignoring maxReceive. Returns amount added. */
    public int generate(int amount) {
        int added = Math.min(amount, capacity - energy);
        if (added > 0) {
            energy += added;
            onChanged.run();
        }
        return Math.max(0, added);
    }

    /** Machine consumption: removes energy only if enough is stored, ignoring maxExtract. */
    public boolean consume(int amount) {
        if (energy < amount) return false;
        if (amount > 0) {
            energy -= amount;
            onChanged.run();
        }
        return true;
    }

    public int getSpace() {
        return capacity - energy;
    }
}
