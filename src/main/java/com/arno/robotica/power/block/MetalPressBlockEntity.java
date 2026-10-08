package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.menu.MetalPressMenu;
import com.arno.robotica.power.recipe.PressingLogic;
import com.arno.robotica.power.recipe.PressingRecipe;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import com.arno.robotica.core.side.SideConfig;
import net.minecraft.core.Direction;

/**
 * Metal Press: slot 0 input, slot 1 output, two upgrade slots (speed, efficiency). Recipes: {@link PressingLogic}
 * ({@code robotica:pressing} recipes, then any {@code c:ingots/<m>} into {@code c:plates/<m>}).
 * Base cost is {@link PowerConfig#pressPower()} FE/t over the recipe time. A speed card divides the time and multiplies
 * the FE/t by the same factor (so the cost per plate only rises by the card's energy penalty); an efficiency card lowers it.
 */
public class MetalPressBlockEntity extends PowerBlockEntity implements MenuProvider, com.arno.robotica.compat.InfoSource {
    public static final int ENERGY_CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 1_000;

    private final RecipeManager.CachedCheck<SingleRecipeInput, PressingRecipe> recipeCheck = RecipeManager.createCheck(PowerRegistry.PRESSING_TYPE.get());

    public final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && hasRecipe(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public final Upgrades upgrades = Upgrades.fixed(com.arno.robotica.core.upgrade.UpgradeRules.Fixed.METAL_PRESS, this::setChanged);
    @Override
    public net.neoforged.neoforge.items.IItemHandler quickInsertTarget() {
        return new net.neoforged.neoforge.items.wrapper.RangedWrapper(items, 0, 1);
    }

    @Override
    public net.neoforged.neoforge.items.IItemHandler quickUpgrades() {
        return upgrades;
    }

    public final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0, this::setChanged);

    private final IItemHandler automation = new ItemAccess(items, (slot, stack) -> slot == 0, (slot, stack) -> slot == 1);
    /** Per-face item access and auto-transfer; every face in and out by default. */
    public final SideConfig sides = new SideConfig(this, () -> automation);

    private int progress;
    private int needed = PressingRecipe.DEFAULT_TIME;

    public MetalPressBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.METAL_PRESS_BE.get(), pos, state);
    }

    public IItemHandler automation() {
        return automation;
    }

    /** The item capability of a face, as the side config allows. */
    @Nullable
    public IItemHandler automation(@Nullable Direction side) {
        return sides.access(side);
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    private boolean hasRecipe(ItemStack stack) {
        return level == null || findRecipe(stack) != null;
    }

    @Nullable
    private PressingRecipe findRecipe(ItemStack stack) {
        if (level == null || stack.isEmpty()) return null;
        Optional<RecipeHolder<PressingRecipe>> holder = recipeCheck.getRecipeFor(new SingleRecipeInput(stack), level);
        return holder.isPresent() ? holder.get().value() : PressingLogic.tagRecipe(stack);
    }

    private boolean canOutput(PressingRecipe recipe) {
        ItemStack out = items.getStackInSlot(1);
        ItemStack result = recipe.result();
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), items.getSlotLimit(1));
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        PressingRecipe recipe = findRecipe(items.getStackInSlot(0));
        boolean working = false;
        if (recipe != null && canOutput(recipe)) {
            int speed = upgrades.level(UpgradeKind.SPEED);
            int efficiency = upgrades.level(UpgradeKind.EFFICIENCY);
            int multiplier = Upgrades.speedMultiplier(speed);
            needed = Math.max(1, CoreConfig.scaleInterval(recipe.time()) / multiplier);
            int base = CoreConfig.scaleEnergy(PowerConfig.pressPower());
            int perTick = base == 0 ? 0 : (int) Math.max(1, Math.round(base * multiplier * Upgrades.energyMultiplier(speed, efficiency)));
            if (energy.consume(perTick)) {
                working = true;
                progress++;
                if (progress >= needed) {
                    finish(recipe);
                    CoreSounds.play(level, pos, CoreSounds.PRESS_STAMP, SoundSource.BLOCKS, 0.8F, 0.9F + level.random.nextFloat() * 0.2F);
                    progress = 0;
                }
            }
        } else if (progress != 0) {
            progress = 0;
        }
        setLit(working);
        sides.tick(level);
    }

    private void finish(PressingRecipe recipe) {
        ItemStack in = items.getStackInSlot(0);
        in.shrink(1);
        if (in.isEmpty()) items.setStackInSlot(0, ItemStack.EMPTY);
        ItemStack out = items.getStackInSlot(1);
        if (out.isEmpty()) {
            items.setStackInSlot(1, recipe.result().copy());
        } else {
            out.grow(recipe.result().getCount());
        }
        setChanged();
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, items);
        drop(level, pos, upgrades);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.robotica.metal_press");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MetalPressMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("progress", progress);
        tag.put("sides", sides.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        if (tag.contains("upgrades")) upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        progress = tag.getInt("progress");
        sides.load(tag.getCompound("sides"));
    }

    /** Jade: working, output full, no energy or idle, and the plate progress. */
    @Override
    public void collectInfo(ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        boolean lit = getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        PressingRecipe recipe = findRecipe(items.getStackInSlot(0));
        if (lit) info.status = "working";
        else if (recipe != null && !canOutput(recipe)) info.status = "output_full";
        else if (recipe != null && energy.getEnergyStored() <= 0) info.status = "no_energy";
        else info.status = "idle";
        if (needed > 0 && progress > 0) info.progress = (int) Math.min(100, 100L * progress / needed);
    }
}
