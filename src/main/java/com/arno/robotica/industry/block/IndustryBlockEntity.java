package com.arno.robotica.industry.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.block.SyncedBlockEntity;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity base of the industry machines: an FE buffer, a battery slot that tops the buffer up from a cell or
 * Mainspring, the LIT state with start/stop sounds and content dropping. Ticks on the server only.
 */
public abstract class IndustryBlockEntity extends SyncedBlockEntity {
    /** Max FE pulled out of the battery item per tick. */
    public static final int BATTERY_PULL = 4_000;
    private static final int WORK_SOUND_GAP = 40;

    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isBattery(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private long lastWorkSound = Long.MIN_VALUE / 2;
    /** Set while the block is swapped for its next Mk in place: the contents move over instead of dropping. */
    public boolean keepContents;

    protected IndustryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The machine's FE buffer (null for blocks without one). */
    @Nullable
    public abstract MachineEnergyStorage energy();

    public abstract void serverTick(ServerLevel level, BlockPos pos, BlockState state);

    /** Where a right-click with an item puts it. Null: the click opens the GUI. */
    @Nullable
    public IItemHandler quickInsertTarget(ItemStack stack) {
        return null;
    }

    /** Called when the block is broken: drop every inventory. */
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, battery);
    }

    public static boolean isBattery(ItemStack stack) {
        if (stack.isEmpty()) return false;
        IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap != null && cap.canExtract();
    }

    /** Tops the buffer up from the battery slot. */
    protected void pullBattery() {
        MachineEnergyStorage energy = energy();
        ItemStack stack = battery.getStackInSlot(0);
        if (energy == null || stack.isEmpty() || energy.getSpace() <= 0) return;
        EnergyUtil.dischargeItem(stack, energy, BATTERY_PULL);
    }

    /**
     * Loads a saved inventory into {@code handler} without changing its slot count (a plain deserializeNBT would
     * resize it to the saved size, which breaks when a machine moves to a Mk with more upgrade slots).
     */
    public static void loadInto(ItemStackHandler handler, HolderLookup.Provider registries, CompoundTag tag) {
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag);
        for (int i = 0; i < handler.getSlots(); i++) {
            handler.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
        }
    }

    protected static void drop(Level level, BlockPos pos, IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        }
    }

    /** Sets LIT if it changed (client update only) and plays the start or stop sound, rate limited. */
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("battery", battery.serializeNBT(registries));
        MachineEnergyStorage energy = energy();
        if (energy != null) tag.put("energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("battery")) loadInto(battery, registries, tag.getCompound("battery"));
        MachineEnergyStorage energy = energy();
        if (energy != null && tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
    }
}
