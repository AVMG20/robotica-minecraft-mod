package com.arno.robotica.processing.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.side.RelativeSide;
import com.arno.robotica.core.side.SideConfig;
import com.arno.robotica.core.side.SideMode;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.processing.ProcessingConfig;
import net.minecraft.ChatFormatting;
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

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared base of the Grinder and the Electric Furnace: Mk tier from the block, FE buffer and input rate per Mk,
 * a battery slot, upgrade slots (the Mk rule in {@link UpgradeRules}), status for the GUI and Jade, and the stored
 * energy as an item component. Everything inside spills when the machine breaks, like a furnace.
 */
public abstract class ProcessingMachineBlockEntity extends SyncedBlockEntity implements MenuProvider, InfoSource {
    public enum Status { IDLE, WORKING, NO_ENERGY, OUTPUT_FULL, NEEDS_TIER }

    protected final int tier;
    public final MachineEnergyStorage energy;
    public final Upgrades upgrades;
    /** Per-face item access and auto-transfer. Defaults match the old rules: in and out on top and sides, only out below. */
    public final SideConfig sides = new SideConfig(this, this::automationRules).with(RelativeSide.BOTTOM, SideMode.OUTPUT);
    protected Status status = Status.IDLE;
    /** FE used in the last tick, for the GUI. */
    protected int lastUse;
    /** Set while the block is swapped for its next Mk in place: the contents move over instead of spilling. */
    public boolean keepContents;

    protected ProcessingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Set<UpgradeKind> kinds) {
        super(type, pos, state);
        this.tier = state.getBlock() instanceof ProcessingMachineBlock block ? block.tier() : 1;
        this.energy = new MachineEnergyStorage(ProcessingConfig.buffer(tier), ProcessingConfig.maxInput(tier), 0, this::setChanged);
        this.upgrades = Upgrades.forMk(() -> tier, kinds, this::setChanged);
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

    /** What pipes and hoppers may do on any face that allows both ways: put inputs in, take outputs (and empty batteries) out. */
    protected abstract IItemHandler automationRules();

    /** The item capability of a face, as the side config allows; a null side (probes, some pipes) gets the plain rules. */
    @Nullable
    public IItemHandler automation(@Nullable Direction side) {
        return sides.access(side);
    }

    /** Where a right-click with an item puts it. */
    public abstract IItemHandler quickInsertTarget();

    protected abstract void work(ServerLevel level);

    /** 0-100 for Jade, -1 when idle. */
    protected abstract int progressPercent();

    // ------------------------------------------------------------------ tick

    /** Ticks without work before the machine goes dark, so a machine on weak power does not flicker. */
    private static final int LIT_GRACE = 30;
    private int idleTicks;

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack battery = items().getStackInSlot(batterySlot());
        if (!battery.isEmpty() && energy.getSpace() > 0) {
            EnergyUtil.dischargeItem(battery, energy, ProcessingConfig.maxInput(tier));
        }
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) tidyHiddenSlots(level, pos);
        lastUse = 0;
        work(level);
        sides.tick(level);
        if (status == Status.WORKING) {
            idleTicks = 0;
            setLit(true);
        } else if (idleTicks < LIT_GRACE && ++idleTicks >= LIT_GRACE) {
            setLit(false);
        }
    }

    /** Furnace lanes closed by a lowered {@code lanesMk}: see the Electric Furnace. */
    protected void tidyHiddenSlots(ServerLevel level, BlockPos pos) {}

    protected static void popOut(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
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
        double cost = Math.ceil((double) base * speedFactor() * mult);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, cost));
    }

    /** Takes FE from the buffer; long so that cost x count never wraps negative (more than the buffer just fails). */
    protected boolean useEnergy(long amount) {
        if (amount < 0 || amount > Integer.MAX_VALUE) return false;
        if (!energy.consume((int) amount)) return false;
        lastUse = (int) Math.min(Integer.MAX_VALUE, (long) lastUse + amount);
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

    /**
     * The block is gone: everything inside spills (inputs, outputs, media, battery, cards), however it broke. The
     * loot table drops the machine with only its energy. The slots are emptied so nothing can be dropped twice.
     */
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, items());
        drop(level, pos, upgrades);
    }

    protected static void drop(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            handler.setStackInSlot(i, ItemStack.EMPTY);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        }
    }

    /** Only the stored energy rides in the dropped item; the contents spill instead. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.getEnergyStored() > 0) components.set(CoreComponents.ENERGY.get(), energy.getEnergyStored());
    }

    /** Energy from the item. */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer stored = input.get(CoreComponents.ENERGY.get());
        if (stored != null) energy.setEnergy(stored);
    }

    /** Everything inside: items, cards and machine state. Saved with the block. */
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
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.put("sides", sides.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readContents(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        sides.load(tag.getCompound("sides"));
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

    /**
     * What a card does in this kind of machine at this Mk, for the GUI tooltips: one line, plus how many count here.
     * Grinder Fortune adds dust on metal ores, Range adds items per lane and cycle.
     */
    public static List<Component> cardHelp(ProcessingMachineBlock.Kind machine, int tier, UpgradeKind kind) {
        String name = machine == ProcessingMachineBlock.Kind.GRINDER ? "grinder" : "electric_furnace";
        Set<UpgradeKind> kinds = machine == ProcessingMachineBlock.Kind.GRINDER ? GrinderBlockEntity.KINDS : ElectricFurnaceBlockEntity.KINDS;
        int cap = kinds.contains(kind) ? UpgradeRules.mkCap(tier, kind) : 0;
        if (cap <= 0) return List.of(Component.translatable("gui.robotica.processing.card.unused").withStyle(ChatFormatting.RED));
        String key = "gui.robotica.processing.card." + name + "." + kind.name().toLowerCase(Locale.ROOT);
        Component line = switch (kind) {
            case FORTUNE -> Component.translatable(key, Math.round(ProcessingConfig.fortuneBonus() * 100));
            default -> Component.translatable(key);
        };
        return List.of(line.copy().withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.robotica.processing.card.cap", cap, tier).withStyle(ChatFormatting.DARK_GRAY));
    }

    public String statusKey() {
        return "gui.robotica.processing.status." + status.name().toLowerCase(Locale.ROOT);
    }
}
