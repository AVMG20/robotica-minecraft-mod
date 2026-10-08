package com.arno.robotica.industry.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyNeighbors;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.industry.IndustryConfig;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.IndustryTags;
import com.arno.robotica.industry.menu.RtgMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import com.arno.robotica.core.side.SideConfig;
import net.minecraft.core.Direction;

/**
 * Radioisotope Generator: slot 0 fuel pellets ({@code #robotica:rtg_fuel}), slot 1 waste. One pellet at a time decays
 * for {@link IndustryConfig#rtgPelletTicks()} ticks at {@link IndustryConfig#rtgPower()} FE/t, then leaves a Depleted
 * Fuel Pellet in the waste slot. It pauses while its buffer is full or the waste slot has no room. Pushes FE into every
 * neighbour; Tesla Coils on it pull from the buffer.
 */
public class RtgBlockEntity extends IndustryBlockEntity implements MenuProvider, InfoSource {
    public static final int FUEL = 0, WASTE = 1;

    public final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == FUEL && isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public final MachineEnergyStorage energy = new MachineEnergyStorage(IndustryConfig.rtgBuffer(), 0, IndustryConfig.rtgOutput(), this::setChanged);
    private final EnergyNeighbors neighbors = new EnergyNeighbors();
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == FUEL ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == WASTE ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == FUEL && isFuel(stack);
        }
    };

    /** Per-face item access and auto-transfer; every face in and out by default. */
    public final SideConfig sides = new SideConfig(this, () -> automation);

    private int decay;
    private int decayTotal;

    public RtgBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryRegistry.RTG_BE.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.is(IndustryTags.RTG_FUEL);
    }

    @Override
    public MachineEnergyStorage energy() {
        return energy;
    }

    public IItemHandler automation() {
        return automation;
    }

    /** The item capability of a face, as the side config allows. */
    @Nullable
    public IItemHandler automation(@Nullable Direction side) {
        return sides.access(side);
    }

    /** FE/t while a pellet decays. */
    public static int output() {
        return CoreConfig.scaleGeneration(IndustryConfig.rtgPower());
    }

    public int decay() {
        return decay;
    }

    public int decayTotal() {
        return decayTotal;
    }

    /** Sets the ticks left on the current pellet (game tests). */
    public void setDecay(int ticks) {
        decay = Math.max(0, ticks);
        setChanged();
    }

    public boolean isGenerating() {
        return decay > 0 && energy.getSpace() >= output();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int gen = output();
        if (decay <= 0 && gen > 0 && energy.getSpace() >= gen) startPellet();
        boolean working = false;
        if (decay > 0 && energy.getSpace() >= gen) {
            energy.generate(gen);
            working = true;
            if (--decay == 0) finishPellet();
            if ((level.getGameTime() + pos.asLong()) % 120 == 0) {
                level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.15F, 0.6F);
            }
        }
        setLit(working);
        if (energy.getEnergyStored() > 0) EnergyUtil.pushToNeighbors(level, pos, energy, IndustryConfig.rtgOutput(), neighbors);
        sides.tick(level);
    }

    private void startPellet() {
        ItemStack fuel = items.getStackInSlot(FUEL);
        ItemStack waste = items.getStackInSlot(WASTE);
        if (!isFuel(fuel)) return;
        if (!waste.isEmpty() && (!waste.is(IndustryRegistry.DEPLETED_FUEL_PELLET.get()) || waste.getCount() >= waste.getMaxStackSize())) return;
        ItemStack rest = fuel.copy();
        rest.shrink(1);
        items.setStackInSlot(FUEL, rest);
        decayTotal = Math.max(1, IndustryConfig.rtgPelletTicks());
        decay = decayTotal;
        setChanged();
    }

    private void finishPellet() {
        ItemStack waste = items.getStackInSlot(WASTE);
        if (waste.isEmpty()) {
            items.setStackInSlot(WASTE, new ItemStack(IndustryRegistry.DEPLETED_FUEL_PELLET.get()));
        } else {
            ItemStack grown = waste.copy();
            grown.grow(1);
            items.setStackInSlot(WASTE, grown);
        }
        setChanged();
    }

    @Nullable
    @Override
    public IItemHandler quickInsertTarget(ItemStack stack) {
        if (isFuel(stack)) return automation;
        return null;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        drop(level, pos, items);
    }

    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        info.status = isGenerating() ? "working" : "idle";
        if (decay > 0 && decayTotal > 0) info.progress = 100 - 100 * decay / decayTotal;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new RtgMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("decay", decay);
        tag.putInt("decayTotal", decayTotal);
        tag.put("sides", sides.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) loadInto(items, registries, tag.getCompound("items"));
        decay = tag.getInt("decay");
        decayTotal = tag.getInt("decayTotal");
        sides.load(tag.getCompound("sides"));
    }
}
