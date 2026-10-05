package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreSounds;
import net.minecraft.sounds.SoundSource;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.util.ItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Holds one Mainspring. Winds it by hand or from any FE source (up to the configured FE/t). */
public class WindingCrankBlockEntity extends PowerBlockEntity {
    /** The wound Mainspring. Players may always take it, automation only when it is full. */
    public final ItemStackHandler spring = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isWindable(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            updateSpringState();
        }
    };

    private final IItemHandler automation = new ItemAccess(spring,
            (slot, stack) -> isWindable(stack) && spring.getStackInSlot(0).isEmpty(),
            (slot, stack) -> ItemEnergy.get(stack) >= ItemEnergy.capacity(stack));

    private long lastReceiveTick = Long.MIN_VALUE;
    private int receivedThisTick;

    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!hasSpring() || toReceive <= 0 || level == null) return 0;
            long now = level.getGameTime();
            int used = now == lastReceiveTick ? receivedThisTick : 0;
            int accepted = Math.min(toReceive, PowerConfig.crankAutoRate() - used);
            ItemStack stack = spring.getStackInSlot(0);
            accepted = Math.min(accepted, ItemEnergy.capacity(stack) - ItemEnergy.get(stack));
            if (accepted <= 0) return 0;
            if (!simulate) {
                lastReceiveTick = now;
                receivedThisTick = used + accepted;
                ItemEnergy.addInternal(stack, accepted);
                setChanged();
                if (ItemEnergy.get(stack) >= ItemEnergy.capacity(stack)) {
                    CoreSounds.play(level, worldPosition, CoreSounds.CRANK_FULL, SoundSource.BLOCKS, 0.6F, 1.0F);
                }
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return ItemEnergy.get(spring.getStackInSlot(0));
        }

        @Override
        public int getMaxEnergyStored() {
            return ItemEnergy.capacity(spring.getStackInSlot(0));
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return hasSpring();
        }
    };

    public WindingCrankBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.WINDING_CRANK_BE.get(), pos, state);
    }

    /** The crank winds a Mainspring, and by hand it also charges any other FE item: a cell, a drill, a weapon. */
    public static boolean isWindable(ItemStack stack) {
        return stack.is(CoreItems.MAINSPRING.get()) || (stack.getItem() instanceof com.arno.robotica.core.energy.EnergyItem && ItemEnergy.capacity(stack) > 0);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public IItemHandler automation() {
        return automation;
    }

    public boolean hasSpring() {
        return !spring.getStackInSlot(0).isEmpty();
    }

    /** Hand winding. Returns the FE actually added. */
    public int wind(int amount) {
        ItemStack stack = spring.getStackInSlot(0);
        if (stack.isEmpty()) return 0;
        int added = ItemEnergy.addInternal(stack, amount);
        if (added > 0) setChanged();
        return added;
    }

    private void updateSpringState() {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide) return;
        BlockState state = getBlockState();
        boolean has = hasSpring();
        if (state.hasProperty(WindingCrankBlock.SPRING) && state.getValue(WindingCrankBlock.SPRING) != has) {
            lvl.setBlock(worldPosition, state.setValue(WindingCrankBlock.SPRING, has), Block.UPDATE_ALL);
        }
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, spring);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("spring", spring.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("spring")) spring.deserializeNBT(registries, tag.getCompound("spring"));
    }
}
