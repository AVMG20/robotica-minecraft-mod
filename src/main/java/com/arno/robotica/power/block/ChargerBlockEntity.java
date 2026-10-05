package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.menu.ChargerMenu;
import com.arno.robotica.power.util.ItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** One slot, an FE buffer that accepts energy from every side, and a fixed charge rate into the item. */
public class ChargerBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int ENERGY_CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 2_000;

    /** True for items that take FE from outside. A Mainspring does not (it only winds at the crank). */
    public static boolean canCharge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap != null && cap.canReceive();
    }

    private static boolean isDone(ItemStack stack) {
        IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap == null || !cap.canReceive() || cap.getEnergyStored() >= cap.getMaxEnergyStored();
    }

    public final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canCharge(stack);
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
    @Override
    public net.neoforged.neoforge.items.IItemHandler quickInsertTarget() {
        return slot;
    }

    public final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0, this::setChanged);

    private final IItemHandler automation = new ItemAccess(slot, (s, stack) -> canCharge(stack), (s, stack) -> isDone(stack));

    private boolean wasCharging;

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.CHARGER_BE.get(), pos, state);
    }

    public IItemHandler automation() {
        return automation;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack stack = slot.getStackInSlot(0);
        boolean charging = false;
        if (!stack.isEmpty() && energy.getEnergyStored() > 0) {
            int moved = EnergyUtil.chargeItem(energy, stack, PowerConfig.chargerRate());
            if (moved > 0) {
                charging = true;
                setChanged();
            }
        }
        if (charging) {
            if (CoreSounds.due(level, pos, 80)) CoreSounds.play(level, pos, CoreSounds.CHARGER_HUM, SoundSource.BLOCKS, 0.5F, 1.0F);
        } else if (wasCharging && !stack.isEmpty() && isDone(stack)) {
            CoreSounds.play(level, pos, CoreSounds.CHARGE_COMPLETE, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        wasCharging = charging;
        setLit(charging);
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, slot);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.robotica.charger");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChargerMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("slot", slot.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("slot")) slot.deserializeNBT(registries, tag.getCompound("slot"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
    }
}
