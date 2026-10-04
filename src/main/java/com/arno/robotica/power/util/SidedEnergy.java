package com.arno.robotica.power.util;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** View of an energy storage that only allows input and/or output (used for per-face capabilities). */
public final class SidedEnergy implements IEnergyStorage {
    private final IEnergyStorage delegate;
    private final boolean allowInput;
    private final boolean allowOutput;

    public SidedEnergy(IEnergyStorage delegate, boolean allowInput, boolean allowOutput) {
        this.delegate = delegate;
        this.allowInput = allowInput;
        this.allowOutput = allowOutput;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return allowInput ? delegate.receiveEnergy(toReceive, simulate) : 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return allowOutput ? delegate.extractEnergy(toExtract, simulate) : 0;
    }

    @Override
    public int getEnergyStored() {
        return delegate.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return delegate.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return allowOutput && delegate.canExtract();
    }

    @Override
    public boolean canReceive() {
        return allowInput && delegate.canReceive();
    }
}
