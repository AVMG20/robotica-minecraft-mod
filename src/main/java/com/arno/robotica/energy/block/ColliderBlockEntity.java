package com.arno.robotica.energy.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.multiblock.CuboidScanner;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.core.progress.Milestones;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyDataMaps.ColliderFuel;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.menu.ColliderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring Collider controller. Shape: a flat closed loop of {@link AcceleratorSegmentBlock}s at the controller's height,
 * the controller one block of it; every loop block joins exactly two others (north, south, east, west).
 * <ol>
 *   <li>Spin-up: FE comes in (at most {@code colliderChargeRate} per tick) until the charge holds
 *       {@code colliderSpinupPerSegment} per loop block.</li>
 *   <li>Start: with a full charge, fuel (item data map robotica:collider_fuel) and switched on, the charge is spent and
 *       the beam starts; it ramps to full over {@code colliderWarmupTicks}.</li>
 *   <li>Running: output {@code = sum of segment power x beam} FE/t. A fuel unit lasts {@code ticks x 64 / length}
 *       ticks. Luminosity (1 per Accelerator Segment, 2 per Resonant Segment, per tick) adds up to Strange Matter.
 *       While the buffer is full the beam idles: no fuel, no power, no matter.</li>
 *   <li>Out of fuel for {@code colliderStarveTicks}, switched off or broken, the beam collapses and needs a new charge.
 *       No explosions, no block changes.</li>
 * </ol>
 */
public class ColliderBlockEntity extends StructureControllerBlockEntity {
    public static final int FUEL_SLOTS = 3, OUTPUT_SLOTS = 3;
    public static final int REFERENCE_LENGTH = 64;
    public static final String MILESTONE = "collider_running";

    public enum State { NOT_FORMED, OFF, CHARGING, NO_FUEL, RUNNING, STARVING, BUFFER_FULL }

    public static ItemStackHandler newFuelHandler(Runnable onChanged) {
        return new ItemStackHandler(FUEL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return EnergyDataMaps.colliderFuel(stack) != null;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public static ItemStackHandler newOutputHandler(Runnable onChanged) {
        return new ItemStackHandler(OUTPUT_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public final ItemStackHandler fuel = newFuelHandler(this::setChanged);
    public final ItemStackHandler output = newOutputHandler(this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(EnergyConfig.colliderBuffer(), 0, 0, this::setChanged);

    // saved
    private boolean enabled = true;
    private long charge;
    private boolean beamOn;
    private double beam;
    private double fuelLeft;
    private int fuelTotal;
    private int starve;
    private long luminosity;

    // from the last scan
    private final List<BlockPos> ring = new ArrayList<>();
    private int segments1, segments2;

    // live
    private int fePerTick;
    private State state = State.NOT_FORMED;
    private long chargeTick = Long.MIN_VALUE;
    private long chargedThisTick;
    /** Synced to clients in steps: beam strength 0-10, charge 0-10 while charging. */
    private int shownBeam, shownCharge;

    public ColliderBlockEntity(BlockPos pos, BlockState blockState) {
        super(EnergyRegistry.COLLIDER_BE.get(), pos, blockState);
    }

    // ---------------------------------------------------------------- structure

    @Override
    protected CuboidSpec spec() {
        return null;
    }

    @Override
    protected Visitor newVisitor() {
        return new RingVisitor();
    }

    static final class RingVisitor extends Visitor {
        final List<BlockPos> ring = new ArrayList<>();
        int tier1, tier2;

        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            return null;
        }
    }

    /** Ring neighbours of a position: the loop blocks north, south, east and west of it. Null if one is unloaded. */
    private static List<Direction> ringSides(Level level, BlockPos pos) {
        List<Direction> sides = new ArrayList<>(2);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            if (!level.isLoaded(n)) return null;
            if (AcceleratorSegmentBlock.isRing(level.getBlockState(n))) sides.add(dir);
        }
        return sides;
    }

    private static final CuboidScanner.Result UNLOADED = new CuboidScanner.Result(CuboidScanner.Status.UNLOADED, null, null, null);

    /** Walks the loop from the controller back to it. */
    @Override
    protected CuboidScanner.Result scanStructure(ServerLevel level) {
        int max = EnergyConfig.colliderMaxLength(), min = EnergyConfig.colliderMinLength();
        List<Direction> start = ringSides(level, worldPosition);
        if (start == null) return UNLOADED;
        if (start.size() != 2) {
            return invalid(StructureProblem.of(null, start.isEmpty() ? "multiblock.robotica.collider_alone" : "multiblock.robotica.collider_controller_sides", start.size()));
        }
        RingVisitor v = new RingVisitor();
        v.ring.add(worldPosition.immutable());
        BlockPos prev = worldPosition;
        BlockPos cur = worldPosition.relative(start.get(0));
        while (true) {
            if (!level.isLoaded(cur)) return UNLOADED;
            if (cur.equals(worldPosition)) break;
            BlockState state = level.getBlockState(cur);
            if (!(state.getBlock() instanceof AcceleratorSegmentBlock segment)) {
                return invalid(StructureProblem.of(cur, "multiblock.robotica.collider_stranger", StructureProblem.name(state), StructureProblem.at(cur)));
            }
            if (v.ring.size() >= max) return invalid(StructureProblem.of(cur, "multiblock.robotica.collider_too_long", max));
            List<Direction> sides = ringSides(level, cur);
            if (sides == null) return UNLOADED;
            if (sides.size() != 2) {
                return invalid(StructureProblem.of(cur, sides.size() > 2 ? "multiblock.robotica.collider_branch" : "multiblock.robotica.collider_open",
                        StructureProblem.at(cur)));
            }
            v.ring.add(cur.immutable());
            if (segment.tier() >= 2) v.tier2++;
            else v.tier1++;
            BlockPos a = cur.relative(sides.get(0)), b = cur.relative(sides.get(1));
            BlockPos next = a.equals(prev) ? b : a;
            prev = cur;
            cur = next;
        }
        if (v.ring.size() < min) return invalid(StructureProblem.of(null, "multiblock.robotica.collider_too_short", v.ring.size(), min));
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : v.ring) {
            x0 = Math.min(x0, p.getX());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            z1 = Math.max(z1, p.getZ());
        }
        return new CuboidScanner.Result(CuboidScanner.Status.FORMED,
                new BoundingBox(x0, worldPosition.getY(), z0, x1, worldPosition.getY(), z1), null, v);
    }

    private static CuboidScanner.Result invalid(StructureProblem problem) {
        return new CuboidScanner.Result(CuboidScanner.Status.INVALID, null, problem, null);
    }

    @Override
    protected BoundingBox searchArea() {
        int r = Math.max(8, EnergyConfig.colliderMaxLength() / 2);
        return new BoundingBox(worldPosition.getX() - r, worldPosition.getY(), worldPosition.getZ() - r,
                worldPosition.getX() + r, worldPosition.getY(), worldPosition.getZ() + r);
    }

    @Override
    protected void onFormed(Visitor visitor) {
        RingVisitor v = (RingVisitor) visitor;
        boolean changed = !ring.equals(v.ring);
        ring.clear();
        ring.addAll(v.ring);
        segments1 = v.tier1;
        segments2 = v.tier2;
        if (changed) {
            if (beamOn) collapse();
            setChangedAndSync();
        }
    }

    @Override
    protected void onUnformed() {
        if (beamOn) collapse();
        ring.clear();
        segments1 = segments2 = 0;
        setChangedAndSync();
    }

    // ---------------------------------------------------------------- numbers

    public int length() {
        return ring.size();
    }

    /** FE the charge needs to start the beam. */
    public long chargeNeeded() {
        return (long) EnergyConfig.colliderSpinupPerSegment() * length();
    }

    /** FE/t at full beam. */
    public long fullPower() {
        return (long) segments1 * EnergyConfig.colliderSegmentPower() + (long) segments2 * EnergyConfig.colliderResonantPower();
    }

    public int luminosityPerTick() {
        return segments1 + 2 * segments2;
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void tickController(ServerLevel level, long now) {
        if (!isFormed()) {
            fePerTick = 0;
            state = State.NOT_FORMED;
            setLit(false);
            return;
        }
        pushOut(level, now);
        step();
        effects(level, now);
    }

    /** Sends FE into every neighbour that takes it (cables, Tesla Coils, machines); segments take none. */
    private void pushOut(ServerLevel level, long now) {
        for (Direction dir : Direction.values()) {
            int left = energy.getEnergyStored();
            if (left <= 0) return;
            BlockPos target = worldPosition.relative(dir);
            if (!level.isLoaded(target)) continue;
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, dir.getOpposite());
            if (storage == null || !storage.canReceive()) continue;
            int accepted = storage.receiveEnergy(left, false);
            if (accepted > 0) energy.consume(accepted);
        }
    }

    /** One collider tick (game tests call it directly). */
    public void step() {
        fePerTick = 0;
        if (!enabled) {
            if (beamOn) collapse();
            state = State.OFF;
        } else if (!beamOn) {
            if (charge < chargeNeeded()) state = State.CHARGING;
            else if (!hasFuel()) state = State.NO_FUEL;
            else start();
        }
        if (beamOn) {
            if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) {
                state = State.BUFFER_FULL;
            } else {
                if (fuelLeft <= 1e-9) takeFuel();
                int warmup = Math.max(1, EnergyConfig.colliderWarmupTicks());
                if (fuelLeft > 1e-9) {
                    starve = 0;
                    beam = Math.min(1.0, beam + 1.0 / warmup);
                    fuelLeft -= (double) length() / REFERENCE_LENGTH;
                    double fe = fullPower() * beam * CoreConfig.generation();
                    fePerTick = (int) Math.min(Integer.MAX_VALUE, Math.round(fe));
                    energy.generate(fePerTick);
                    addLuminosity(luminosityPerTick() * beam);
                    state = State.RUNNING;
                } else {
                    starve++;
                    beam = Math.max(0.0, beam - 1.0 / warmup);
                    state = State.STARVING;
                    if (starve > EnergyConfig.colliderStarveTicks()) collapse();
                }
            }
        }
        setLit(beamOn);
        syncSteps();
    }

    private double luminosityFrac;

    private void addLuminosity(double amount) {
        luminosityFrac += amount;
        long whole = (long) luminosityFrac;
        luminosityFrac -= whole;
        long cost = EnergyConfig.colliderMatterCost();
        luminosity = Math.min(cost, luminosity + whole);
        if (luminosity >= cost) {
            ItemStack left = ItemHandlerHelper.insertItem(output, new ItemStack(EnergyRegistry.STRANGE_MATTER.get()), false);
            if (left.isEmpty()) {
                luminosity -= cost;
                if (level instanceof ServerLevel sl) {
                    CoreSounds.play(sl, worldPosition, CoreSounds.COLLIDER_MATTER, SoundSource.BLOCKS, 1.0F, 1.0F);
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    private boolean hasFuel() {
        if (fuelLeft > 1e-9) return true;
        for (int i = 0; i < fuel.getSlots(); i++) {
            if (EnergyDataMaps.colliderFuel(fuel.getStackInSlot(i)) != null) return true;
        }
        return false;
    }

    private void takeFuel() {
        for (int i = 0; i < fuel.getSlots(); i++) {
            ColliderFuel f = EnergyDataMaps.colliderFuel(fuel.getStackInSlot(i));
            if (f == null) continue;
            fuel.extractItem(i, 1, false);
            fuelTotal = f.ticks();
            fuelLeft = f.ticks();
            return;
        }
    }

    private void start() {
        charge = 0;
        beamOn = true;
        beam = 0;
        starve = 0;
        if (level instanceof ServerLevel sl) {
            CoreSounds.play(sl, worldPosition, CoreSounds.COLLIDER_START, SoundSource.BLOCKS, 2.0F, 1.0F);
            Milestones.awardOwner(sl, worldPosition, owner(), MILESTONE);
        }
        setChangedAndSync();
    }

    private void collapse() {
        beamOn = false;
        beam = 0;
        starve = 0;
        if (level instanceof ServerLevel sl) CoreSounds.play(sl, worldPosition, CoreSounds.COLLIDER_STOP, SoundSource.BLOCKS, 1.5F, 1.0F);
        setChangedAndSync();
    }

    /** Hum and collision sparks while the beam runs. */
    private void effects(ServerLevel level, long now) {
        if (!beamOn || beam <= 0) return;
        if (CoreSounds.due(level, worldPosition, 60)) {
            CoreSounds.play(level, worldPosition, CoreSounds.COLLIDER_HUM, SoundSource.BLOCKS, 1.2F, 0.7F + 0.5F * (float) beam);
        }
        if (now % 10 == 0) {
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.05, z = worldPosition.getZ() + 0.5;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, (int) (4 + 8 * beam), 0.3, 0.2, 0.3, 0.25);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.2, z, (int) (1 + 2 * beam), 0.1, 0.1, 0.1, 0.05);
        }
        if (beam >= 1.0 && CoreSounds.due(level, worldPosition, 37)) {
            CoreSounds.play(level, worldPosition, CoreSounds.COLLIDER_COLLIDE, SoundSource.BLOCKS, 0.7F, 0.8F + level.random.nextFloat() * 0.4F);
        }
    }

    /** Sends the beam and charge to clients when they cross a 10% step (the renderer's brightness). */
    private void syncSteps() {
        int b = beamOn ? (int) Math.ceil(beam * 10) : 0;
        long need = chargeNeeded();
        int c = beamOn || need <= 0 ? 0 : (int) Math.min(10, charge * 10 / need);
        if (b != shownBeam || c != shownCharge) {
            shownBeam = b;
            shownCharge = c;
            setChangedAndSync();
        }
    }

    // ---------------------------------------------------------------- controls and accessors

    public void setEnabled(boolean value) {
        enabled = value;
        setChanged();
    }

    /** Test hook: sets the spin-up charge directly. */
    public void setCharge(long value) {
        charge = Math.max(0, Math.min(chargeNeeded(), value));
        setChanged();
    }

    public boolean enabled() { return enabled; }
    public boolean beamOn() { return beamOn; }
    public double beam() { return beam; }
    public long charge() { return charge; }
    public int fePerTick() { return fePerTick; }
    public State state() { return state; }
    public long luminosity() { return luminosity; }
    public List<BlockPos> ring() { return ring; }
    /** Client: beam strength 0-10 and charge 0-10 as last synced. */
    public int shownBeam() { return shownBeam; }
    public int shownCharge() { return shownCharge; }

    @Override
    protected int infoProgress() {
        long need = chargeNeeded();
        return beamOn || need <= 0 || charge <= 0 ? -1 : (int) Math.min(100, charge * 100 / need);
    }

    // ---------------------------------------------------------------- capabilities

    private boolean charging() {
        return isFormed() && enabled && !beamOn && charge < chargeNeeded();
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (!charging() || amount <= 0) return 0;
            long now = level == null ? 0 : level.getGameTime();
            if (now != chargeTick) {
                chargeTick = now;
                chargedThisTick = 0;
            }
            long room = chargeNeeded() - charge;
            long accepted = Math.max(0, Math.min(amount, Math.min(room, EnergyConfig.colliderChargeRate() - chargedThisTick)));
            if (!simulate && accepted > 0) {
                charge += accepted;
                chargedThisTick += accepted;
                setChanged();
            }
            return (int) accepted;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int take = Math.min(amount, energy.getEnergyStored());
            if (!simulate && take > 0) energy.consume(take);
            return take;
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return charging();
        }
    };

    public IEnergyStorage energyView() {
        return energyView;
    }

    /** Fuel in (slots 0-2), Strange Matter out (slots 3-5). */
    private final IItemHandler items = new IItemHandler() {
        @Override
        public int getSlots() {
            return FUEL_SLOTS + OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < FUEL_SLOTS ? fuel.getStackInSlot(slot) : output.getStackInSlot(slot - FUEL_SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < FUEL_SLOTS ? fuel.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < FUEL_SLOTS ? ItemStack.EMPTY : output.extractItem(slot - FUEL_SLOTS, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < FUEL_SLOTS && fuel.isItemValid(slot, stack);
        }
    };

    public IItemHandler itemView() {
        return items;
    }

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return Component.translatable("multiblock.robotica.ring_collider");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ColliderMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("beamOn", beamOn);
        tag.putLong("charge", charge);
        tag.putLong("chargeNeed", chargeNeeded());
        tag.putInt("beam", (int) Math.round(beam * 100));
        tag.putInt("fe", fePerTick);
        tag.putLong("full", fullPower());
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("capacity", energy.getMaxEnergyStored());
        tag.putFloat("burn", fuelTotal > 0 ? (float) Math.max(0, fuelLeft / fuelTotal) : 0F);
        tag.putInt("length", length());
        tag.putInt("seg1", segments1);
        tag.putInt("seg2", segments2);
        tag.putFloat("matter", (float) luminosity / Math.max(1, EnergyConfig.colliderMatterCost()));
        tag.putInt("state", state.ordinal());
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        long[] path = new long[ring.size()];
        for (int i = 0; i < path.length; i++) path[i] = ring.get(i).asLong();
        tag.putLongArray("ring", path);
        tag.putInt("shownBeam", shownBeam);
        tag.putInt("shownCharge", shownCharge);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("output", output.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putBoolean("enabled", enabled);
        tag.putLong("charge", charge);
        tag.putBoolean("beamOn", beamOn);
        tag.putDouble("beamLevel", beam);
        tag.putDouble("fuelLeft", fuelLeft);
        tag.putInt("fuelTotal", fuelTotal);
        tag.putLong("luminosity", luminosity);
        saveClientData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("output")) output.deserializeNBT(registries, tag.getCompound("output"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        charge = tag.getLong("charge");
        beamOn = tag.getBoolean("beamOn");
        beam = tag.getDouble("beamLevel");
        fuelLeft = tag.getDouble("fuelLeft");
        fuelTotal = tag.getInt("fuelTotal");
        luminosity = tag.getLong("luminosity");
        if (tag.contains("ring", Tag.TAG_LONG_ARRAY)) {
            ring.clear();
            for (long l : tag.getLongArray("ring")) ring.add(BlockPos.of(l));
        }
        shownBeam = tag.getInt("shownBeam");
        shownCharge = tag.getInt("shownCharge");
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : new ItemStackHandler[]{fuel, output}) {
            for (int i = 0; i < handler.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
        }
    }
}
