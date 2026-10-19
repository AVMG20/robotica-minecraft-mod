package com.arno.robotica.replicator.farm;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.multiblock.CuboidSpec;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.energy.block.PortBlock;
import com.arno.robotica.energy.block.PortBlockEntity;
import com.arno.robotica.energy.block.StructureControllerBlockEntity;
import com.arno.robotica.replicator.ReplicatorConfig;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.logic.Harvest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller of a mob farm (Iron Golem Farmer or Wither Farmer, {@link FarmKind}). With every vial slot filled and
 * power coming in, each cycle builds the farm's mob in the 3x3x3 chamber, the three Impact Rams strike it down and
 * its loot (the mob's loot table; a Nether Star for the Wither) goes to the output, which Farm Ports and the GUI empty.
 * Vials are never used up. Speed and efficiency cards and a Plasma Actuator work as on the Mob Replicator.
 */
public class FarmControllerBlockEntity extends StructureControllerBlockEntity {
    public static final int OUTPUT_SLOTS = 18;
    public static final int UPGRADE_SLOTS = UpgradeRules.Fixed.MOB_FARM.slots;
    /** Extra FE cost of the plasma boost, on top of the speed it gives (as on the Replicator). */
    private static final double BOOST_ENERGY_FACTOR = 1.25;
    /** The glow stays on this long after the last working tick. */
    private static final int LIT_GRACE = 20;
    /** Finished cycles are synced to watching clients at most this often. */
    private static final int SYNC_INTERVAL = 10;

    /** Why the farm is not working (GUI word). */
    public enum State { NOT_FORMED, RUNNING, NO_VIALS, NO_ENERGY, OUTPUT_FULL }

    // ---- inventories (static factories so the client menu can build identical dummies) ----

    /** One slot per vial the farm needs; each takes a complete vial of its mob ({@link FarmKind.VialRule}). */
    public static ItemStackHandler newVialHandler(FarmKind kind, Runnable onChanged) {
        return new ItemStackHandler(kind.vialSlots()) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot < kind.vialSlots() && kind.vial(slot).accepts(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public static ItemStackHandler newBoostHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(CoreItems.PLASMA_ACTUATOR.get());
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    /** Caps in {@link UpgradeRules.Fixed#MOB_FARM}: speed 3, efficiency 4. */
    public static Upgrades newUpgrades(Runnable onChanged) {
        return Upgrades.fixed(UpgradeRules.Fixed.MOB_FARM, onChanged);
    }

    public static ItemStackHandler newOutputHandler(Runnable onChanged) {
        return new ItemStackHandler(OUTPUT_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    private final FarmKind kind;
    public final ItemStackHandler vials;
    public final ItemStackHandler boost = newBoostHandler(this::setChanged);
    public final Upgrades upgrades = newUpgrades(this::setChanged);
    public final ItemStackHandler output = newOutputHandler(this::setChanged);
    public final MachineEnergyStorage energy = new MachineEnergyStorage(ReplicatorConfig.farmBuffer(), ReplicatorConfig.farmMaxReceive(), 0, this::setChanged);
    private final IItemHandler outputAccess = new OutputAccess(output);

    // saved state
    private int progress;
    private int cycles;
    private final List<ItemStack> pending = new ArrayList<>();

    // from the last scan
    private final List<BlockPos> rams = new ArrayList<>();
    @Nullable
    private BlockPos center;

    // live state
    private int needed = 1;
    private State state = State.NOT_FORMED;
    private long litUntil;
    private boolean working;
    private boolean syncPending;
    private long lastSync = Long.MIN_VALUE / 2;
    private long lastStrikeSound = Long.MIN_VALUE / 2, lastDeathSound = Long.MIN_VALUE / 2;

    // client mirror (see saveClientData)
    private long clientSyncTime = Long.MIN_VALUE;

    public FarmControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ReplicatorRegistry.FARM_BE.get(), pos, blockState);
        this.kind = FarmKind.of(blockState);
        this.vials = newVialHandler(kind, this::setChanged);
    }

    public FarmKind kind() {
        return kind;
    }

    // ---------------------------------------------------------------- structure

    @Override
    protected CuboidSpec spec() {
        return FarmStructure.SPEC;
    }

    @Override
    protected Visitor newVisitor() {
        return new FarmStructure.Visitor(kind);
    }

    @Override
    protected int formColor() {
        return kind.color();
    }

    @Override
    protected String formedMilestone() {
        return kind.milestone();
    }

    @Override
    protected void onFormed(Visitor visitor) {
        FarmStructure.Visitor v = (FarmStructure.Visitor) visitor;
        BlockPos newCenter = box() == null ? null : box().getCenter().immutable();
        List<BlockPos> found = v.rams.stream().map(BlockPos::immutable).toList();
        if (!found.equals(rams) || center == null || !center.equals(newCenter)) {
            rams.clear();
            rams.addAll(found);
            center = newCenter;
            setChangedAndSync();
        }
    }

    @Override
    protected void onUnformed() {
        rams.clear();
        center = null;
        progress = 0;
        setChangedAndSync();
    }

    // ---------------------------------------------------------------- numbers

    public boolean hasBoost() {
        return boost.getStackInSlot(0).is(CoreItems.PLASMA_ACTUATOR.get());
    }

    public int speedLevel() {
        return upgrades.level(UpgradeKind.SPEED);
    }

    /** Total speed multiplier from speed cards and the plasma boost. */
    public int speedMultiplier() {
        return Upgrades.speedMultiplier(speedLevel()) * (hasBoost() ? ReplicatorConfig.boostMultiplier() : 1);
    }

    /** Ticks one mob takes with the current cards. */
    public int cycleTicks() {
        return Math.max(1, CoreConfig.scaleInterval(kind.baseCycleTicks()) / speedMultiplier());
    }

    /** FE per tick while working with the current cards. */
    public int energyPerTick() {
        int base = CoreConfig.scaleEnergy(kind.baseEnergyPerTick());
        if (base == 0) return 0;
        double factor = Upgrades.energyMultiplier(speedLevel(), upgrades.level(UpgradeKind.EFFICIENCY)) * (hasBoost() ? BOOST_ENERGY_FACTOR : 1.0);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(base * speedMultiplier() * factor)));
    }

    /** Whether every vial slot holds a vial its rule accepts. */
    public boolean vialsReady() {
        for (int i = 0; i < kind.vialSlots(); i++) {
            if (!kind.vial(i).accepts(vials.getStackInSlot(i))) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void tickController(ServerLevel level, long now) {
        int nextNeeded = cycleTicks();
        if (nextNeeded != needed) {
            needed = nextNeeded;
            if (progress >= needed) progress = needed - 1;
            syncPending = true;
        }
        boolean worked = false;
        if (!isFormed() || center == null) {
            state = State.NOT_FORMED;
        } else if (!vialsReady()) {
            state = State.NO_VIALS;
            if (progress != 0) {
                progress = 0;
                syncPending = true;
            }
        } else if (!pending.isEmpty() && !flushPending()) {
            state = State.OUTPUT_FULL;
        } else if (energy.consume(energyPerTick())) {
            state = State.RUNNING;
            worked = true;
            progress++;
            strike(level, now);
            if (progress >= needed) {
                progress = 0;
                complete(level, now);
            }
            setChanged();
        } else {
            state = State.NO_ENERGY;
        }
        if (worked) litUntil = now + LIT_GRACE;
        boolean lit = isFormed() && now < litUntil;
        setLit(lit);
        // a start or a stop is shown at once, so the client's cycle stays in step; a farm short of power (working every
        // other tick) keeps counting as running through the glow's grace, so it does not sync every tick
        boolean running = worked || (state == State.NO_ENERGY && lit);
        if (running != working) {
            working = running;
            syncPending = true;
            lastSync = Long.MIN_VALUE / 2;
        }
        if (syncPending && now - lastSync >= SYNC_INTERVAL) {
            syncPending = false;
            lastSync = now;
            setChangedAndSync();
        }
    }

    /** A ram hits on this tick of the cycle: its sound, the mob's hurt sound and sparks where it hits. */
    private void strike(ServerLevel level, long now) {
        int i = FarmCycle.strikeAt(progress, needed);
        if (i < 0 || i >= rams.size() || center == null) return;
        if (now - lastStrikeSound < 8) return;
        lastStrikeSound = now;
        BlockPos ram = rams.get(i);
        CoreSounds.play(level, ram, CoreSounds.FARM_RAM, SoundSource.BLOCKS, 0.9F, 0.9F + level.random.nextFloat() * 0.2F);
        level.playSound(null, center, kind.hurtSound(), SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
        BoundingBox box = box();
        if (box == null) return;
        Direction push = FarmStructure.ramPush(box, ram);
        double x = ram.getX() + 0.5 + push.getStepX() * 1.6, y = ram.getY() + 0.5, z = ram.getZ() + 0.5 + push.getStepZ() * 1.6;
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.15, 0.25, 0.15, 0.25);
    }

    /** The mob falls: its loot goes to the output (what does not fit waits in {@link #pending}). */
    private void complete(ServerLevel level, long now) {
        Harvest.Result result = Harvest.roll(level, kind.mob(), center, owner(), 0);
        List<ItemStack> drops = new ArrayList<>();
        if (result != null) drops.addAll(result.drops());
        // the Wither drops its star in code, not from its loot table
        if (kind == FarmKind.WITHER) drops.add(new ItemStack(Items.NETHER_STAR));
        for (ItemStack drop : drops) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(output, drop, false);
            if (!rest.isEmpty()) pending.add(rest);
        }
        cycles++;
        syncPending = true;
        if (now - lastDeathSound >= 20) {
            lastDeathSound = now;
            if (result != null && result.deathSound() != null) {
                level.playSound(null, center, result.deathSound(), SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
            }
            level.sendParticles(ParticleTypes.POOF, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 12, 0.4, 0.5, 0.4, 0.02);
        }
    }

    /** Moves leftovers into the output. True when nothing is left. */
    private boolean flushPending() {
        boolean changed = false;
        for (int i = pending.size() - 1; i >= 0; i--) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(output, pending.get(i), false);
            if (rest.getCount() != pending.get(i).getCount()) changed = true;
            if (rest.isEmpty()) pending.remove(i);
            else pending.set(i, rest);
        }
        if (changed) setChanged();
        return pending.isEmpty();
    }

    // ---------------------------------------------------------------- accessors (GUI, renderer, tests)

    public int progress() { return progress; }
    public int needed() { return needed; }
    public int cycles() { return cycles; }
    public State state() { return state; }
    public boolean working() { return working; }
    public int pendingCount() { return pending.size(); }
    @Nullable
    public BlockPos center() { return center; }
    public List<BlockPos> rams() { return rams; }

    /**
     * Client: where the cycle is now, in ticks (with the partial tick). The server syncs its progress when the farm
     * starts, stops or finishes a mob; in between the client counts on from there.
     */
    public float clientTick(long gameTime, float partialTick) {
        if (!working) return progress;
        if (clientSyncTime == Long.MIN_VALUE) clientSyncTime = gameTime;
        float t = progress + (gameTime - clientSyncTime) + partialTick;
        return needed <= 0 ? 0 : t % needed;
    }

    /** Jade: cycle progress. */
    @Override
    protected int infoProgress() {
        return needed <= 0 ? -1 : progress * 100 / needed;
    }

    // ---------------------------------------------------------------- ports

    @Override
    public int portReceive(PortBlockEntity port, int amount, boolean simulate) {
        return port.kind() == PortBlock.Kind.FARM ? energy.receiveEnergy(amount, simulate) : 0;
    }

    @Override
    public boolean portCanReceive(PortBlockEntity port) {
        return port.kind() == PortBlock.Kind.FARM;
    }

    @Override
    public long portStored() {
        return energy.getEnergyStored();
    }

    @Override
    public long portCapacity() {
        return energy.getMaxEnergyStored();
    }

    /** Farm Ports give the output (extract only). */
    @Override
    public IItemHandler portItems() {
        return outputAccess;
    }

    /** Extract-only view for hoppers, pipes and storage networks. */
    private static final class OutputAccess implements IItemHandler {
        private final IItemHandler inner;

        OutputAccess(IItemHandler inner) {
            this.inner = inner;
        }

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    // ---------------------------------------------------------------- menu and sync

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FarmMenu(id, inv, this);
    }

    @Override
    public void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSync(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("capacity", energy.getMaxEnergyStored());
        tag.putInt("fe", energyPerTick());
        tag.putInt("progress", progress);
        tag.putInt("needed", needed);
        tag.putInt("speed", speedMultiplier());
        tag.putInt("cycles", cycles);
        tag.putInt("state", state.ordinal());
    }

    /** Renderer data: the chamber, the rams and where the cycle is. */
    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        if (center != null) tag.putLong("center", center.asLong());
        ListTag list = new ListTag();
        for (BlockPos ram : rams) list.add(LongTag.valueOf(ram.asLong()));
        tag.put("rams", list);
        tag.putInt("progress", progress);
        tag.putInt("needed", needed);
        tag.putBoolean("working", working);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("vials", vials.serializeNBT(registries));
        tag.put("boost", boost.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("output", output.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("progress", progress);
        tag.putInt("cycles", cycles);
        ListTag list = new ListTag();
        for (ItemStack stack : pending) list.add(stack.save(registries));
        tag.put("pending", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("vials")) loadInto(vials, registries, tag.getCompound("vials"));
        if (tag.contains("boost")) boost.deserializeNBT(registries, tag.getCompound("boost"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.contains("output")) loadInto(output, registries, tag.getCompound("output"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        progress = tag.getInt("progress");
        if (tag.contains("cycles")) cycles = tag.getInt("cycles");
        if (tag.contains("pending", Tag.TAG_LIST)) {
            pending.clear();
            for (Tag entry : tag.getList("pending", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, entry).ifPresent(stack -> {
                    if (!stack.isEmpty()) pending.add(stack);
                });
            }
        }
        // client update
        if (tag.contains("rams", Tag.TAG_LIST)) {
            rams.clear();
            for (Tag t : tag.getList("rams", Tag.TAG_LONG)) rams.add(BlockPos.of(((LongTag) t).getAsLong()));
            center = tag.contains("center") ? BlockPos.of(tag.getLong("center")) : null;
            needed = Math.max(1, tag.getInt("needed"));
            working = tag.getBoolean("working");
            clientSyncTime = level != null ? level.getGameTime() : Long.MIN_VALUE;
        }
    }

    /** Loads a saved inventory slot by slot, keeping the handler's size (extra saved slots are dropped). */
    private static void loadInto(ItemStackHandler handler, HolderLookup.Provider registries, CompoundTag tag) {
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag);
        for (int i = 0; i < handler.getSlots(); i++) {
            handler.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
        }
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (IItemHandler handler : new IItemHandler[]{vials, boost, upgrades, output}) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
            }
        }
        for (ItemStack stack : pending) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        pending.clear();
    }
}
