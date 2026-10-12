package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.SupplyCrateBlock;
import com.arno.robotica.automation.menu.SupplyCrateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Chest-like storage with an item capability on every side; the slot count comes from the crate's Mk. */
public class SupplyCrateBlockEntity extends BlockEntity implements MenuProvider {
    public final ItemStackHandler items;

    public SupplyCrateBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.SUPPLY_CRATE_BE.get(), pos, state);
        int slots = state.getBlock() instanceof SupplyCrateBlock crate ? crate.slots() : 27;
        this.items = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public void setSize(int size) {
                // Loading resizes to the saved size; the slot count belongs to the block, so keep it.
                super.setSize(slots);
            }
        };
    }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SupplyCrateMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
    }
}
