package com.arno.robotica.energy.block;

import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.menu.BankMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Capacitor Bank controller. The energy is a long in this block entity (a 9x9x9 bank of Ender Capacitors holds about
 * 175 billion FE); ports clamp every transfer to an int. Input and output are each limited to the sum of the Transfer
 * Coils per tick, shared by all ports. Output ports push into the block outside them and can be pulled from (Tesla
 * Coils, cables). The energy stays in the controller when the structure breaks and travels with the controller item.
 * A bank that holds more than its capacity (it was rebuilt smaller) takes nothing in until it drained below it.
 */
public class BankControllerBlockEntity extends StructureControllerBlockEntity {
    public static final int HISTORY = 60;
    public static final String MILESTONE = "bank_formed";

    public static final CuboidSpec SPEC = CuboidSpec.builder(Component.translatable("multiblock.robotica.capacitor_bank"))
            .frame(s -> s.is(EnergyRegistry.BANK_CASING.get()), Component.translatable("block.robotica.bank_casing"))
            .wall(s -> s.is(EnergyRegistry.BANK_CASING.get()) || s.is(EnergyRegistry.BANK_GLASS.get()) || s.is(EnergyRegistry.BANK_PORT.get()),
                    Component.translatable("multiblock.robotica.bank_wall"))
            .controller(s -> s.is(EnergyRegistry.BANK_CONTROLLER.get()))
            .size(EnergyConfig::bankMinSize, EnergyConfig::bankMaxSize)
            .build();

    private long energy;
    private long capacity;
    private int rate;
    private int capacitors, coils;

    // per-tick budget, shared by all ports
    private long budgetTick = Long.MIN_VALUE;
    private long inUsed, outUsed;
    // per-second statistics
    private long secondIn, secondOut;
    private long avgIn, avgOut;
    private final long[] history = new long[HISTORY];
    private int historyHead;

    public BankControllerBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyRegistry.BANK_BE.get(), pos, state);
    }

    /** Frame light when it forms: cyan, like the charge bar. */
    @Override
    protected int formColor() {
        return 0x4FC8FF;
    }

    @Override
    protected CuboidSpec spec() {
        return SPEC;
    }

    @Override
    protected Visitor newVisitor() {
        return new BankVisitor();
    }

    /** Interior: any mix of Capacitors and Transfer Coils, the rest air. */
    static final class BankVisitor extends Visitor {
        long capacity;
        long rate;
        int capacitors, coils;

        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            if (state.getBlock() instanceof CapacitorBlock c) {
                // Saturating sums: extreme configs must not wrap around to a negative capacity.
                if (c.kind() == CapacitorBlock.Kind.CAPACITOR) {
                    capacity = saturatingAdd(capacity, c.capacity());
                    capacitors++;
                } else {
                    rate = saturatingAdd(rate, c.rate());
                    coils++;
                }
                return null;
            }
            if (state.isAir()) return null;
            return StructureProblem.of(pos, "multiblock.robotica.bank_inside", StructureProblem.name(state), StructureProblem.at(pos));
        }

        @Override
        public StructureProblem finish(BoundingBox box) {
            if (capacitors == 0) return StructureProblem.of(null, "multiblock.robotica.bank_no_capacitor");
            if (coils == 0) return StructureProblem.of(null, "multiblock.robotica.bank_no_coil");
            if (ports(PortBlock.Kind.BANK) == 0) return StructureProblem.of(null, "multiblock.robotica.bank_no_port");
            return null;
        }
    }

    static long saturatingAdd(long a, long b) {
        long sum = a + Math.max(0, b);
        return sum < a ? Long.MAX_VALUE : sum;
    }

    @Override
    protected void onFormed(Visitor visitor) {
        BankVisitor v = (BankVisitor) visitor;
        capacity = v.capacity;
        // Formed smaller than the energy it holds (capacitors removed, or a full controller in a small bank): the
        // excess is lost.
        if (energy > capacity) {
            energy = capacity;
            setChanged();
        }
        rate = (int) Math.min(Integer.MAX_VALUE, v.rate);
        capacitors = v.capacitors;
        coils = v.coils;
    }

    @Override
    protected void onUnformed() {
        capacity = 0;
        rate = 0;
        capacitors = coils = 0;
    }

    @Override
    protected String formedMilestone() {
        return MILESTONE;
    }

    private void budget() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            inUsed = 0;
            outUsed = 0;
        }
    }

    @Override
    protected void tickController(ServerLevel level, long now) {
        if (isFormed() && energy > 0) {
            budget();
            pushOut(level, p -> p.kind() == PortBlock.Kind.BANK && isOutputPort(p), rate - outUsed, () -> energy, n -> {
                energy -= n;
                outUsed += n;
                secondOut += n;
                setChanged();
            });
        }
        if (now % 20 == 0) {
            avgIn = secondIn / 20;
            avgOut = secondOut / 20;
            history[historyHead] = avgIn - avgOut;
            historyHead = (historyHead + 1) % HISTORY;
            secondIn = secondOut = 0;
            setLit(isFormed() && (avgIn > 0 || avgOut > 0));
        }
    }

    private boolean isOutputPort(Port port) {
        return level != null && PortBlock.isOutput(level.getBlockState(port.pos()));
    }

    // ---------------------------------------------------------------- ports

    @Override
    public int portReceive(PortBlockEntity port, int amount, boolean simulate) {
        if (!isFormed() || port.isOutput()) return 0;
        budget();
        long accepted = Math.min(amount, Math.min(rate - inUsed, capacity - energy));
        if (accepted <= 0) return 0;
        if (!simulate) {
            energy += accepted;
            inUsed += accepted;
            secondIn += accepted;
            setChanged();
        }
        return (int) accepted;
    }

    @Override
    public int portExtract(PortBlockEntity port, int amount, boolean simulate) {
        if (!isFormed() || !port.isOutput()) return 0;
        budget();
        long taken = Math.min(amount, Math.min(rate - outUsed, energy));
        if (taken <= 0) return 0;
        if (!simulate) {
            energy -= taken;
            outUsed += taken;
            secondOut += taken;
            setChanged();
        }
        return (int) taken;
    }

    @Override
    public boolean portCanReceive(PortBlockEntity port) {
        return isFormed() && !port.isOutput();
    }

    @Override
    public boolean portCanExtract(PortBlockEntity port) {
        return isFormed() && port.isOutput();
    }

    @Override
    public long portStored() {
        return energy;
    }

    @Override
    public long portCapacity() {
        return capacity;
    }

    // ---------------------------------------------------------------- accessors

    public long energy() { return energy; }

    @Override
    protected int infoProgress() {
        return capacity <= 0 ? -1 : (int) Math.min(100, energy * 100 / capacity);
    }

    public long capacity() { return capacity; }
    public int rate() { return rate; }
    public long averageIn() { return avgIn; }
    public long averageOut() { return avgOut; }

    /** Test hook and creative use. */
    public void setEnergy(long value) {
        energy = Math.max(0, value);
        setChanged();
    }

    // ---------------------------------------------------------------- item keeps the energy

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy > 0) components.set(EnergyRegistry.BANK_ENERGY.get(), energy);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Long stored = input.get(EnergyRegistry.BANK_ENERGY.get());
        if (stored != null) energy = Math.max(0, stored);
    }

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new BankMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        tag.putLong("energy", energy);
        tag.putLong("capacity", capacity);
        tag.putInt("rate", rate);
        tag.putLong("in", avgIn);
        tag.putLong("out", avgOut);
        tag.putInt("capacitors", capacitors);
        tag.putInt("coils", coils);
        long[] ordered = new long[HISTORY];
        for (int i = 0; i < HISTORY; i++) ordered[i] = history[(historyHead + i) % HISTORY];
        tag.putLongArray("history", ordered);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getLong("energy"));
    }
}
