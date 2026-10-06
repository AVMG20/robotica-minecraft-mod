package com.arno.robotica.processing.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.processing.ProcessingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Shared base of the Grinder and the Electric Furnace: Mk tier from the block, FE buffer and input rate per Mk,
 * a battery slot, upgrade slots (one more per Mk, higher caps per Mk), status for the GUI and Jade, and the item
 * components (energy always, everything inside with a Carry card).
 */
public abstract class ProcessingMachineBlockEntity extends SyncedBlockEntity implements MenuProvider, InfoSource {
    public enum Status { IDLE, WORKING, NO_ENERGY, OUTPUT_FULL, NEEDS_TIER }

    protected final int tier;
    public final MachineEnergyStorage energy;
    public final MachineUpgrades upgrades;
    protected Status status = Status.IDLE;
    /** FE used in the last tick, for the GUI. */
    protected int lastUse;

    protected ProcessingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Set<UpgradeKind> kinds) {
        super(type, pos, state);
        this.tier = state.getBlock() instanceof ProcessingMachineBlock block ? block.tier() : 1;
        this.energy = new MachineEnergyStorage(ProcessingConfig.buffer(tier), ProcessingConfig.maxInput(tier), 0, this::setChanged);
        this.upgrades = new MachineUpgrades(kinds, k -> cap(k, tier), () -> ProcessingConfig.upgradeSlots(tier), this::setChanged);
    }

    /** How many cards of a kind a machine of this Mk takes (before the kind is accepted at all). */
    public static int cap(UpgradeKind kind, int tier) {
        return switch (kind) {
            case SPEED -> ProcessingConfig.speedCap(tier);
            case EFFICIENCY -> ProcessingConfig.efficiencyCap(tier);
            case FORTUNE -> ProcessingConfig.fortuneCap(tier);
            case RANGE -> ProcessingConfig.rangeCap(tier);
            case VOID, CARRY -> 1;
            default -> 0;
        };
    }

    public int tier() {
        return tier;
    }

    public Status status() {
        return status;
    }

    public int lastUse() {
        return lastUse;
    }

    public abstract ItemStackHandler items();

    /** Index of the battery slot in {@link #items()}. */
    public abstract int batterySlot();

    /** Pipes and hoppers: input on the top and sides, output taken from any side, nothing goes in from below. */
    public abstract IItemHandler automation(@Nullable Direction side);

    /** Where a right-click with an item puts it. */
    public abstract IItemHandler quickInsertTarget();

    protected abstract void work(ServerLevel level);

    /** 0-100 for Jade, -1 when idle. */
    protected abstract int progressPercent();

    public boolean hasCarry() {
        return upgrades.level(UpgradeKind.CARRY) > 0;
    }

    // ------------------------------------------------------------------ tick

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack battery = items().getStackInSlot(batterySlot());
        if (!battery.isEmpty() && energy.getSpace() > 0) {
            EnergyUtil.dischargeItem(battery, energy, ProcessingConfig.maxInput(tier));
        }
        lastUse = 0;
        work(level);
        setLit(status == Status.WORKING);
    }

    /** Work rate of this Mk with its Speed cards. */
    public double speedFactor() {
        return ProcessingConfig.tierSpeed(tier) * Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED));
    }

    /** Ticks per item for a base time at Mk1 without cards. */
    public int ticksFor(int baseTicks) {
        return (int) Math.max(1, Math.round(CoreConfig.scaleInterval(Math.max(1, baseTicks)) / speedFactor()));
    }

    /** FE/t for a base FE/t: rises with Mk and Speed, so FE per item only grows by the Speed card penalty. */
    public int powerFor(int basePower) {
        int base = CoreConfig.scaleEnergy(basePower);
        if (base <= 0) return 0;
        double mult = Upgrades.energyMultiplier(upgrades.level(UpgradeKind.SPEED), upgrades.level(UpgradeKind.EFFICIENCY));
        return (int) Math.max(1, Math.ceil(base * speedFactor() * mult));
    }

    protected boolean useEnergy(int amount) {
        if (!energy.consume(amount)) return false;
        lastUse += amount;
        return true;
    }

    /** Minimum ticks between two start/stop sounds, so a flickering machine stays quiet. */
    private static final int WORK_SOUND_GAP = 40;
    private long lastWorkSound = Long.MIN_VALUE / 2;

    protected void setLit(boolean lit) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
            long now = level.getGameTime();
            if (now - lastWorkSound >= WORK_SOUND_GAP) {
                lastWorkSound = now;
                CoreSounds.play(level, worldPosition, lit ? CoreSounds.MACHINE_START : CoreSounds.MACHINE_STOP, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
        }
    }

    // ------------------------------------------------------------------ breaking and item components

    /** Without a Carry card everything spills; with one it rides in the dropped item. */
    public void dropContents(Level level, BlockPos pos) {
        if (hasCarry()) return;
        drop(level, pos, items());
        drop(level, pos, upgrades);
    }

    protected static void drop(Level level, BlockPos pos, IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.getEnergyStored() > 0) components.set(CoreComponents.ENERGY.get(), energy.getEnergyStored());
        if (hasCarry() && level != null) {
            CompoundTag contents = new CompoundTag();
            writeContents(contents, level.registryAccess());
            components.set(CoreComponents.CONTENTS.get(), contents);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer stored = input.get(CoreComponents.ENERGY.get());
        if (stored != null) energy.setEnergy(stored);
        CompoundTag contents = input.get(CoreComponents.CONTENTS.get());
        if (contents != null && level != null) {
            readContents(contents, level.registryAccess());
            setChanged();
        }
    }

    /** Everything inside: items, cards and machine state. Saved with the block and carried by the item. */
    protected void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("items", items().serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
    }

    protected void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("items")) {
            ItemStackHandler fresh = new ItemStackHandler();
            fresh.deserializeNBT(registries, tag.getCompound("items"));
            ItemStackHandler items = items();
            for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, i < fresh.getSlots() ? fresh.getStackInSlot(i) : ItemStack.EMPTY);
        }
        if (tag.contains("upgrades")) {
            ItemStackHandler fresh = new ItemStackHandler();
            fresh.deserializeNBT(registries, tag.getCompound("upgrades"));
            for (int i = 0; i < upgrades.getSlots(); i++) upgrades.setStackInSlot(i, i < fresh.getSlots() ? fresh.getStackInSlot(i) : ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readContents(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        info.tier = tier;
        info.status = switch (status) {
            case WORKING -> "working";
            case NO_ENERGY -> "no_energy";
            case OUTPUT_FULL -> "output_full";
            default -> "idle";
        };
        int p = progressPercent();
        if (p >= 0) info.progress = p;
    }

    public String statusKey() {
        return "gui.robotica.processing.status." + status.name().toLowerCase(Locale.ROOT);
    }
}
