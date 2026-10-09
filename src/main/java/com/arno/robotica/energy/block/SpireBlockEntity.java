package com.arno.robotica.energy.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.multiblock.CuboidScanner;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.multiblock.StructureProblem;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyDataMaps.SpireConductor;
import com.arno.robotica.energy.EnergyDataMaps.SpireFuel;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.menu.SpireMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Tesla Spire, controlled from its base. Shape: the Spire Base, a column of conductor blocks (block data map
 * robotica:spire_conductor) straight above it, the Spire Crown on top. The crown needs the sky.
 * <ul>
 *   <li>Output {@code = sum of conductor power x altitude x weather x interference} FE/t (x the generation multiplier),
 *       paid from fuel (item data map robotica:spire_fuel): an item's {@code energy} times the column's efficiency (the
 *       power-weighted average of its conductors) is what the spire gets out of it.</li>
 *   <li>Altitude: +0.25% per block the crown sits above Y 64, capped by {@code spireAltitudeMaxBonus}. Weather at the
 *       crown: rain x1.25, thunder x1.5. Interference: see {@link SpireField}.</li>
 *   <li>Lightning: once a second a strike lands with chance 1 / {@code spireStrikeEvery<weather>} and adds
 *       {@code spireStrikeSeconds} of full output (at least 200k FE), fuel or not. Visual only: no fire, no damage.</li>
 * </ul>
 * With less buffer room than a tick's output it burns only that share of fuel.
 */
public class SpireBlockEntity extends StructureControllerBlockEntity {
    public static final int FUEL_SLOTS = 2;
    public static final int STRIKE_EVENT = 1;
    public static final double ALTITUDE_PER_BLOCK = 0.0025;
    public static final int STRIKE_MIN = 200_000;
    public static final String MILESTONE = "spire_formed";

    public enum State { NOT_FORMED, RUNNING, NO_SKY, NO_FUEL, WASTE_FULL, BUFFER_FULL }

    public static ItemStackHandler newFuelHandler(Runnable onChanged) {
        return new ItemStackHandler(FUEL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return EnergyDataMaps.spireFuel(stack) != null;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public static ItemStackHandler newWasteHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public final ItemStackHandler fuel = newFuelHandler(this::setChanged);
    public final ItemStackHandler waste = newWasteHandler(this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(EnergyConfig.spireBuffer(), 0, Integer.MAX_VALUE, this::setChanged);
    private final IItemHandler items = new ItemsHandler();
    private final com.arno.robotica.core.energy.EnergyNeighbors neighbors = new com.arno.robotica.core.energy.EnergyNeighbors();

    // saved
    private double fuelLeft;
    private int fuelTotal;
    @Nullable
    private Item burnWaste;
    private ItemStack pendingWaste = ItemStack.EMPTY;
    private long lastStrike = Long.MIN_VALUE / 2;

    // from the last scan
    private int conductors;
    private int basePower;
    private double efficiency = 1.0;

    // refreshed every second
    private boolean sky;
    private int weather;
    private double interference = 1.0;
    private int neighbours;

    // live
    private int fePerTick;
    private int potential;
    private State state = State.NOT_FORMED;
    private int litHold;
    private boolean running;

    public SpireBlockEntity(BlockPos pos, BlockState blockState) {
        super(EnergyRegistry.SPIRE_BE.get(), pos, blockState);
    }

    // ---------------------------------------------------------------- structure

    @Override
    protected CuboidSpec spec() {
        return null;
    }

    @Override
    protected Visitor newVisitor() {
        return new SpireVisitor();
    }

    static final class SpireVisitor extends Visitor {
        int conductors, power;
        double weighted;

        @Override
        public StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box) {
            return null;
        }
    }

    /** Walks up from the base over conductors to the crown. */
    @Override
    protected CuboidScanner.Result scanStructure(ServerLevel level) {
        int max = EnergyConfig.spireMaxConductors(), min = EnergyConfig.spireMinConductors();
        SpireVisitor v = new SpireVisitor();
        BlockPos.MutableBlockPos p = worldPosition.mutable();
        for (int i = 0; i <= max; i++) {
            p.move(Direction.UP);
            if (level.isOutsideBuildHeight(p)) {
                return invalid(StructureProblem.of(p, "multiblock.robotica.spire_build_height"));
            }
            if (!level.isLoaded(p)) return new CuboidScanner.Result(CuboidScanner.Status.UNLOADED, null, null, null);
            BlockState state = level.getBlockState(p);
            if (state.is(EnergyRegistry.SPIRE_CROWN.get())) {
                if (v.conductors < min) {
                    return invalid(StructureProblem.of(p, "multiblock.robotica.spire_too_short", v.conductors, min));
                }
                BoundingBox box = BoundingBox.fromCorners(worldPosition, p);
                return new CuboidScanner.Result(CuboidScanner.Status.FORMED, box, null, v);
            }
            SpireConductor c = EnergyDataMaps.conductor(state);
            if (c == null) {
                if (v.conductors == 0) return invalid(StructureProblem.of(p, "multiblock.robotica.spire_empty", min, max));
                return invalid(state.isAir() && v.conductors >= min
                        ? StructureProblem.of(p, "multiblock.robotica.spire_no_crown", StructureProblem.at(p))
                        : StructureProblem.of(p, "multiblock.robotica.spire_column", StructureProblem.name(state), StructureProblem.at(p)));
            }
            if (i == max) return invalid(StructureProblem.of(p, "multiblock.robotica.spire_too_tall", max));
            v.conductors++;
            v.power += c.power();
            v.weighted += c.power() * (double) c.efficiency();
        }
        return invalid(StructureProblem.of(p, "multiblock.robotica.spire_too_tall", max));
    }

    private static CuboidScanner.Result invalid(StructureProblem problem) {
        return new CuboidScanner.Result(CuboidScanner.Status.INVALID, null, problem, null);
    }

    @Override
    protected BoundingBox searchArea() {
        return new BoundingBox(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX(), worldPosition.getY() + EnergyConfig.spireMaxConductors() + 1, worldPosition.getZ());
    }

    @Override
    protected void onFormed(Visitor visitor) {
        SpireVisitor v = (SpireVisitor) visitor;
        boolean changed = conductors != v.conductors;
        conductors = v.conductors;
        basePower = v.power;
        efficiency = v.power > 0 ? v.weighted / v.power : 1.0;
        if (level != null) SpireField.add(level, worldPosition);
        refreshSurroundings();
        setCrown(true, running); // also lights a crown that moved
        if (changed) setChangedAndSync();
    }

    @Override
    protected void onUnformed() {
        setCrown(false, false);
        running = false;
        litHold = 0;
        fePerTick = potential = 0;
        conductors = 0;
        basePower = 0;
        efficiency = 1.0;
        if (level != null) SpireField.remove(level, worldPosition);
        setChangedAndSync();
    }

    @Override
    protected String formedMilestone() {
        return MILESTONE;
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) SpireField.remove(level, worldPosition);
        super.setRemoved();
    }

    @Nullable
    public BlockPos crown() {
        return conductors > 0 ? worldPosition.above(conductors + 1) : null;
    }

    // ---------------------------------------------------------------- numbers

    /** Altitude factor of the crown. */
    public double altitude() {
        BlockPos crown = crown();
        if (crown == null) return 1.0;
        return 1.0 + Math.min(EnergyConfig.spireAltitudeMaxBonus(), Math.max(0, (crown.getY() - 64) * ALTITUDE_PER_BLOCK));
    }

    public double weatherFactor() {
        return weather == 2 ? 1.5 : weather == 1 ? 1.25 : 1.0;
    }

    /** Sky, weather and neighbours, once a second (and on every scan). */
    private void refreshSurroundings() {
        BlockPos crown = crown();
        if (level == null || crown == null) return;
        sky = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.canSeeSky(crown.above());
        // isRainingAt is false where it snows (peaks, snowy biomes): a storm counts there too
        boolean wet = level.isRaining() && level.canSeeSky(crown.above()) && level.getBiome(crown).value().hasPrecipitation();
        weather = !wet ? 0 : level.isThundering() ? 2 : 1;
        int spacing = EnergyConfig.spireSpacing();
        interference = SpireField.interference(level, worldPosition, spacing);
        neighbours = SpireField.neighbours(level, worldPosition, spacing);
    }

    /** FE/t this spire makes with fuel right now (before the buffer). */
    private double potentialNow() {
        if (!isFormed() || !sky) return 0;
        return basePower * altitude() * weatherFactor() * interference * CoreConfig.generation();
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void tickController(ServerLevel level, long now) {
        if (!pendingWaste.isEmpty()) pendingWaste = ItemHandlerHelper.insertItem(waste, pendingWaste, false);
        if (!isFormed()) {
            fePerTick = potential = 0;
            state = State.NOT_FORMED;
            setLit(false);
            return;
        }
        if (now % 20 == Math.floorMod(worldPosition.asLong(), 20L)) {
            refreshSurroundings();
            if (sky) rollStrike(level, now);
        }
        if (energy.getEnergyStored() > 0) {
            EnergyUtil.pushToNeighbors(level, worldPosition, energy, Math.max(4096, potential * 8), neighbors);
        }
        step();
        if (running && CoreSounds.due(level, worldPosition, 50)) {
            BlockPos crown = crown();
            if (crown != null) CoreSounds.play(level, crown, CoreSounds.SPIRE_HUM, SoundSource.BLOCKS, 0.9F, 0.9F + 0.2F * (float) Math.min(1.0, potential / 10_000.0));
        }
    }

    /** One spire tick. Public for game tests. */
    public void simulate(int ticks) {
        for (int i = 0; i < ticks; i++) step();
    }

    private void step() {
        double full = potentialNow();
        potential = (int) Math.min(Integer.MAX_VALUE, Math.round(full));
        fePerTick = 0;
        boolean burning = false;
        if (!sky) state = State.NO_SKY;
        else if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) state = State.BUFFER_FULL;
        else if (!pendingWaste.isEmpty()) state = State.WASTE_FULL;
        else if (full <= 0) state = State.NO_FUEL;
        else {
            int ready = ensureFuel();
            if (ready == 1) {
                double room = energy.getMaxEnergyStored() - energy.getEnergyStored();
                double out = Math.min(full, room);
                // fuel per tick: the FE it makes divided by the efficiency, never more than what is left
                double need = out / Math.max(0.01, efficiency * CoreConfig.generation());
                if (need > fuelLeft) {
                    out *= fuelLeft / need;
                    need = fuelLeft;
                }
                fuelLeft -= need;
                fePerTick = (int) Math.round(out);
                if (fePerTick > 0) energy.generate(fePerTick);
                if (fuelLeft <= 1e-6) finishUnit();
                state = State.RUNNING;
                burning = fePerTick > 0;
            } else {
                state = ready == 0 ? State.NO_FUEL : State.WASTE_FULL;
            }
        }
        if (burning) litHold = 40;
        else if (litHold > 0) litHold--;
        setLit(litHold > 0);
        setRunning(litHold > 0);
    }

    /** 1 fuel ready, 0 none, -1 only fuel whose waste has no room. */
    private int ensureFuel() {
        if (fuelLeft > 1e-6 && fuelTotal > 0) return 1;
        boolean blocked = false;
        for (int slot = 0; slot < fuel.getSlots(); slot++) {
            SpireFuel f = EnergyDataMaps.spireFuel(fuel.getStackInSlot(slot));
            if (f == null) continue;
            Item wasteItem = f.waste().orElse(null);
            if (wasteItem != null && !ItemHandlerHelper.insertItem(waste, new ItemStack(wasteItem), true).isEmpty()) {
                blocked = true;
                continue;
            }
            fuel.extractItem(slot, 1, false);
            fuelTotal = f.energy();
            fuelLeft = f.energy();
            burnWaste = wasteItem;
            return 1;
        }
        return blocked ? -1 : 0;
    }

    private void finishUnit() {
        if (burnWaste != null) pendingWaste = ItemHandlerHelper.insertItem(waste, new ItemStack(burnWaste), false);
        fuelLeft = 0;
        fuelTotal = 0;
        burnWaste = null;
    }

    private void rollStrike(ServerLevel level, long now) {
        int every = EnergyConfig.spireStrikeEvery(weather);
        if (every <= 0 || level.random.nextInt(every) != 0) return;
        strike(level, now);
    }

    /** A lightning strike on the crown: a burst of FE, the bolt on every client in range, thunder and sparks. */
    public void strike(ServerLevel level, long now) {
        BlockPos crown = crown();
        if (crown == null) return;
        double full = basePower * altitude() * weatherFactor() * interference * CoreConfig.generation();
        long burst = Math.max(STRIKE_MIN, Math.round(full * 20.0 * EnergyConfig.spireStrikeSeconds()));
        energy.generate((int) Math.min(Integer.MAX_VALUE, burst));
        lastStrike = now;
        // to every player who has the chunk loaded (a block event only reaches 64 blocks; the bolt draws out to 192)
        var event = new net.minecraft.network.protocol.game.ClientboundBlockEventPacket(worldPosition, getBlockState().getBlock(), STRIKE_EVENT, conductors);
        for (var player : level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false)) {
            player.connection.send(event);
        }
        CoreSounds.play(level, crown, CoreSounds.SPIRE_STRIKE, SoundSource.WEATHER, 6.0F, 0.9F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, crown.getX() + 0.5, crown.getY() + 0.7, crown.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.6);
        level.sendParticles(ParticleTypes.FLASH, crown.getX() + 0.5, crown.getY() + 1.0, crown.getZ() + 0.5, 1, 0, 0, 0, 0);
        for (int i = 1; i <= conductors; i += 2) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + i + 0.5, worldPosition.getZ() + 0.5, 4, 0.4, 0.3, 0.4, 0.15);
        }
        setChanged();
    }

    private void setRunning(boolean value) {
        if (running != value) {
            running = value;
            setCrown(true, value);
            setChangedAndSync();
        }
    }

    /** The crown's formed look (wide toroid) and its glow; only sends an update when one changes. */
    private void setCrown(boolean formed, boolean lit) {
        BlockPos crown = crown();
        if (level == null || crown == null || !level.isLoaded(crown)) return;
        BlockState state = level.getBlockState(crown);
        if (!state.is(EnergyRegistry.SPIRE_CROWN.get())) return;
        BlockState next = state.setValue(SpireCrownBlock.FORMED, formed).setValue(SpireCrownBlock.LIT, lit);
        if (next != state) level.setBlock(crown, next, Block.UPDATE_CLIENTS);
    }

    // ---------------------------------------------------------------- client: strike event

    /** Client tick of the last strike, for the renderer's bolt. */
    private long clientStrike = Long.MIN_VALUE / 2;

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == STRIKE_EVENT) {
            if (level != null && level.isClientSide) clientStrike = level.getGameTime();
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public long clientStrike() { return clientStrike; }

    // ---------------------------------------------------------------- accessors

    @Override
    protected boolean litSounds() {
        return true;
    }

    public int fePerTick() { return fePerTick; }
    public int conductors() { return conductors; }
    public int basePower() { return basePower; }
    public double efficiency() { return efficiency; }
    public double interference() { return interference; }
    public boolean hasSky() { return sky; }
    public State state() { return state; }
    public boolean running() { return running; }
    public double fuelLeft() { return fuelLeft; }

    /** Test hook: the sky check without a real sky (the test arena has a roof of air but may be covered). */
    public void setSkyForTest(boolean value) {
        sky = value;
    }

    // ---------------------------------------------------------------- capabilities

    /** FE out of every side but the top (the column). */
    @Nullable
    public IEnergyStorage energyView(@Nullable Direction side) {
        return side == Direction.UP ? null : energy;
    }

    /** Fuel in, waste out (hoppers and pipes). */
    public IItemHandler itemView() {
        return items;
    }

    private final class ItemsHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return FUEL_SLOTS + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < FUEL_SLOTS ? fuel.getStackInSlot(slot) : waste.getStackInSlot(0);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < FUEL_SLOTS ? fuel.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < FUEL_SLOTS ? ItemStack.EMPTY : waste.extractItem(0, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < FUEL_SLOTS && fuel.isItemValid(slot, stack);
        }
    }

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return Component.translatable("multiblock.robotica.tesla_spire");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SpireMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        tag.putInt("fe", fePerTick);
        tag.putInt("potential", potential);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("capacity", energy.getMaxEnergyStored());
        tag.putFloat("burn", fuelTotal > 0 ? (float) (fuelLeft / fuelTotal) : 0F);
        tag.putInt("conductors", conductors);
        tag.putInt("basePower", basePower);
        tag.putInt("eff", (int) Math.round(efficiency * 100));
        tag.putInt("altitude", (int) Math.round((altitude() - 1.0) * 100));
        tag.putInt("weather", weather);
        tag.putInt("interference", (int) Math.round(interference * 100));
        tag.putInt("neighbours", neighbours);
        tag.putBoolean("sky", sky);
        long since = level == null ? -1 : (level.getGameTime() - lastStrike) / 20;
        tag.putLong("strikeAgo", since > 1_000_000 ? -1 : since);
        tag.putInt("minH", EnergyConfig.spireMinConductors() + 2);
        tag.putInt("state", state.ordinal());
    }

    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("conductors", conductors);
        tag.putBoolean("running", running);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("waste", waste.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putDouble("fuelLeft", fuelLeft);
        tag.putLong("lastStrike", lastStrike);
        tag.putInt("fuelTotal", fuelTotal);
        if (burnWaste != null) tag.putString("burnWaste", BuiltInRegistries.ITEM.getKey(burnWaste).toString());
        if (!pendingWaste.isEmpty()) tag.put("pendingWaste", pendingWaste.save(registries));
        saveClientData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("waste")) waste.deserializeNBT(registries, tag.getCompound("waste"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        fuelLeft = tag.getDouble("fuelLeft");
        if (tag.contains("lastStrike")) lastStrike = tag.getLong("lastStrike");
        fuelTotal = tag.getInt("fuelTotal");
        burnWaste = tag.contains("burnWaste") ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(tag.getString("burnWaste"))).orElse(null) : null;
        pendingWaste = tag.contains("pendingWaste") ? ItemStack.parseOptional(registries, tag.getCompound("pendingWaste")) : ItemStack.EMPTY;
        conductors = tag.getInt("conductors");
        running = tag.getBoolean("running");
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : new ItemStackHandler[]{fuel, waste}) {
            for (int i = 0; i < handler.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
        }
        if (!pendingWaste.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pendingWaste);
    }
}
