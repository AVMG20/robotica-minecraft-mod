package com.arno.robotica.energy.block;

import com.arno.robotica.energy.EnergyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Port block entity: remembers the controller that formed it (saved, so a port whose chunk reloads next to a running
 * controller works right away; {@link #controller()} only trusts the link while that controller is formed and lists
 * this port) and exposes fixed capability objects that forward to it. The objects never change, so no
 * capability invalidation is needed: an unlinked port simply moves nothing and shows 0 slots.
 */
public class PortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            StructureControllerBlockEntity c = controller();
            return c == null || toReceive <= 0 ? 0 : c.portReceive(PortBlockEntity.this, toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            StructureControllerBlockEntity c = controller();
            return c == null || toExtract <= 0 ? 0 : c.portExtract(PortBlockEntity.this, toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            StructureControllerBlockEntity c = controller();
            return c == null ? 0 : reportedStored(c.portStored(), c.portCapacity());
        }

        @Override
        public int getMaxEnergyStored() {
            StructureControllerBlockEntity c = controller();
            return c == null ? 0 : clamp(c.portCapacity());
        }

        @Override
        public boolean canExtract() {
            StructureControllerBlockEntity c = controller();
            return c != null && c.portCanExtract(PortBlockEntity.this);
        }

        @Override
        public boolean canReceive() {
            StructureControllerBlockEntity c = controller();
            return c != null && c.portCanReceive(PortBlockEntity.this);
        }
    };

    private final IItemHandler itemView = new IItemHandler() {
        @Nullable
        private IItemHandler target() {
            StructureControllerBlockEntity c = controller();
            return c == null ? null : c.portItems();
        }

        @Override
        public int getSlots() {
            IItemHandler t = target();
            return t == null ? 0 : t.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler t = target();
            return t == null || slot >= t.getSlots() ? ItemStack.EMPTY : t.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler t = target();
            return t == null || slot >= t.getSlots() ? stack : t.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler t = target();
            return t == null || slot >= t.getSlots() ? ItemStack.EMPTY : t.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler t = target();
            return t == null || slot >= t.getSlots() ? 0 : t.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler t = target();
            return t != null && slot < t.getSlots() && t.isItemValid(slot, stack);
        }
    };

    public PortBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyRegistry.PORT_BE.get(), pos, state);
    }

    static int clamp(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    /**
     * Stored FE as the int FE API sees it, next to {@link #clamp clamp(capacity)}. Banks beyond an int scale stored
     * by the same factor as the capacity, and a bank that is not full never reports itself full.
     */
    static int reportedStored(long stored, long capacity) {
        if (capacity <= 0) return 0;
        stored = Math.max(0, Math.min(stored, capacity));
        int reported;
        if (capacity <= Integer.MAX_VALUE) {
            reported = (int) stored;
        } else {
            reported = (int) Math.min(Integer.MAX_VALUE, (long) Math.floor((double) stored * Integer.MAX_VALUE / capacity));
        }
        if (stored < capacity && reported >= clamp(capacity)) reported = clamp(capacity) - 1;
        return reported;
    }

    public PortBlock.Kind kind() {
        return getBlockState().getBlock() instanceof PortBlock port ? port.kind() : PortBlock.Kind.REACTOR_POWER;
    }

    /** Bank Ports only: true while set to send FE out. */
    public boolean isOutput() {
        return PortBlock.isOutput(getBlockState());
    }

    void link(BlockPos controller) {
        if (controller.equals(controllerPos)) return;
        controllerPos = controller.immutable();
        setChanged();
    }

    void unlink(BlockPos controller) {
        if (controller.equals(controllerPos)) {
            controllerPos = null;
            setChanged();
        }
    }

    /** Back after a chunk load: let the controller re-check its structure soon (it may have missed changes). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && controllerPos != null && level.isLoaded(controllerPos)
                && level.getBlockEntity(controllerPos) instanceof StructureControllerBlockEntity c) {
            c.markDirty();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }

    /** The formed controller this port belongs to, or null. */
    @Nullable
    public StructureControllerBlockEntity controller() {
        if (controllerPos == null || level == null || !level.isLoaded(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof StructureControllerBlockEntity c && c.isFormed() && c.hasPort(worldPosition)) {
            return c;
        }
        return null;
    }

    /** FE capability: Power and Bank Ports only. */
    @Nullable
    public IEnergyStorage energyView() {
        return kind() == PortBlock.Kind.REACTOR_ACCESS ? null : energyView;
    }

    @Nullable
    public IItemHandler itemView() {
        return kind() == PortBlock.Kind.REACTOR_ACCESS ? itemView : null;
    }
}
