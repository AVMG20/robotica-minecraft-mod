package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.menu.CombustionGeneratorMenu;
import com.arno.robotica.power.util.ItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Fuel slot, FE buffer, lit state. Pushes into every neighbouring FE receiver; conduits pull from the buffer. */
public class CombustionGeneratorBlockEntity extends PowerBlockEntity implements MenuProvider {
    /** FE/t the buffer may be drained at (by neighbours and conduits). */
    public static final int MAX_OUTPUT = 400;

    public final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Receive 0: only the burning fills it. Extract is limited so conduits cannot empty it faster than MAX_OUTPUT. */
    public final MachineEnergyStorage energy = new MachineEnergyStorage(PowerConfig.generatorBuffer(), 0, MAX_OUTPUT, this::setChanged);

    private final IItemHandler automation = new ItemAccess(fuel, (slot, stack) -> isFuel(stack), (slot, stack) -> !isFuel(stack));

    private int burnTime;
    private int burnTotal;

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.COMBUSTION_GENERATOR_BE.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public IItemHandler automation() {
        return automation;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnTotal() {
        return burnTotal;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int gen = CoreConfig.scaleGeneration(PowerConfig.generatorOutput());
        if (burnTime == 0 && gen > 0 && energy.getSpace() >= gen) ignite();
        if (burnTime > 0 && energy.getSpace() >= gen) {
            energy.generate(gen);
            burnTime--;
            if (burnTime == 0) setChanged();
        }
        setLit(burnTime > 0);
        if (energy.getEnergyStored() > 0) EnergyUtil.pushToNeighbors(level, pos, energy, MAX_OUTPUT);
    }

    private void ignite() {
        ItemStack stack = fuel.getStackInSlot(0);
        int time = stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
        if (time <= 0) return;
        burnTime = time;
        burnTotal = time;
        ItemStack remainder = stack.getCraftingRemainingItem();
        stack.shrink(1);
        if (stack.isEmpty()) fuel.setStackInSlot(0, remainder);
        setChanged();
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, fuel);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.robotica.combustion_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CombustionGeneratorMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("burnTime", burnTime);
        tag.putInt("burnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        burnTime = tag.getInt("burnTime");
        burnTotal = tag.getInt("burnTotal");
    }
}
