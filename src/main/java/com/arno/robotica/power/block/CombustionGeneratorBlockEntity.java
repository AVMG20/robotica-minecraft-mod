package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.menu.CombustionGeneratorMenu;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Fuel slot, FE buffer, lit state. Pushes into every neighbouring FE receiver; Tesla Coils on it pull from the buffer.
 * Two card slots ({@link com.arno.robotica.core.upgrade.UpgradeRules.Fixed#COMBUSTION_GENERATOR}): speed cards multiply
 * the FE/t like a machine's work rate; fuel burns at that rate times {@link Upgrades#energyMultiplier}, the same energy
 * math as every machine (efficiency cards save less here), so speed costs fuel per FE and efficiency saves it.
 */
public class CombustionGeneratorBlockEntity extends PowerBlockEntity implements MenuProvider {
    /** FE/t the buffer may be drained at (by neighbours and Tesla Coils). */
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
    @Override
    public net.neoforged.neoforge.items.IItemHandler quickInsertTarget() {
        return fuel;
    }

    public final Upgrades upgrades = Upgrades.fixed(com.arno.robotica.core.upgrade.UpgradeRules.Fixed.COMBUSTION_GENERATOR, this::setChanged);

    @Override
    public net.neoforged.neoforge.items.IItemHandler quickUpgrades() {
        return upgrades;
    }

    /** Receive 0: only the burning fills it. Extract is limited so Tesla Coils cannot empty it faster than MAX_OUTPUT. */
    public final MachineEnergyStorage energy = new MachineEnergyStorage(PowerConfig.generatorBuffer(), 0, MAX_OUTPUT, this::setChanged);

    private final IItemHandler automation = new ItemAccess(fuel, (slot, stack) -> isFuel(stack), (slot, stack) -> !isFuel(stack));
    /** Per-face item config (fuel in, empty buckets out); every face both ways by default. */
    public final com.arno.robotica.core.side.SideConfig sides = new com.arno.robotica.core.side.SideConfig(this, () -> automation);

    private int burnTime;
    private int burnTotal;
    /** Fraction of a fuel tick already burned (cards make the burn rate fractional). */
    private float burnDebt;

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

    /** FE/t while burning with the installed speed cards. */
    public int output() {
        return CoreConfig.scaleGeneration(PowerConfig.generatorOutput()) * Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED));
    }

    /**
     * Fuel ticks burned per tick: the speed multiplier times the machine energy multiplier, with the generator's own,
     * smaller saving per efficiency card ({@link PowerConfig#generatorEfficiencyPerCard}, 4 cards 1.67x FE per fuel).
     */
    public static double fuelPerTick(int speedCards, int efficiencyCards) {
        return Upgrades.speedMultiplier(speedCards) * Upgrades.energyMultiplier(speedCards, efficiencyCards, PowerConfig.generatorEfficiencyPerCard());
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        sides.tick(level);
        int gen = output();
        if (burnTime == 0 && gen > 0 && energy.getSpace() >= gen) ignite();
        if (burnTime > 0 && energy.getSpace() >= gen) {
            energy.generate(gen);
            burnDebt += (float) fuelPerTick(upgrades.level(UpgradeKind.SPEED), upgrades.level(UpgradeKind.EFFICIENCY));
            int whole = (int) burnDebt;
            burnDebt -= whole;
            burnTime = Math.max(0, burnTime - whole);
            if (CoreSounds.due(level, pos, 50)) CoreSounds.play(level, pos, CoreSounds.GENERATOR_BURN, SoundSource.BLOCKS, 0.5F, 1.0F);
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
        drop(level, pos, upgrades);
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
        tag.putFloat("burnDebt", burnDebt);
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("sides", sides.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        burnTime = tag.getInt("burnTime");
        burnTotal = tag.getInt("burnTotal");
        burnDebt = tag.getFloat("burnDebt");
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        sides.load(tag.getCompound("sides"));
    }
}
