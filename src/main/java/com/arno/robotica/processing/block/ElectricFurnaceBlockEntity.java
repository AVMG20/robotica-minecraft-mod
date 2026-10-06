package com.arno.robotica.processing.block;

import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.menu.ElectricFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Electric Furnace: smelts every vanilla {@code smelting} recipe on FE. Each Mk runs more lanes in parallel
 * (1 / 2 / 4 / 8 by default), each lane with its own input and output slot and its own progress. Range cards smelt
 * one more item per lane and cycle each, Fortune cards add experience, which the furnace keeps until a player takes
 * the output by hand.
 */
public class ElectricFurnaceBlockEntity extends ProcessingMachineBlockEntity {
    public static final int MAX_LANES = 8;
    public static final int OUT_FIRST = MAX_LANES;
    public static final int BATTERY = MAX_LANES * 2;
    public static final int SLOTS = BATTERY + 1;
    /** Experience kept in the furnace is capped so an automated furnace does not hoard forever. */
    public static final float MAX_XP = 5_000F;

    public static final Set<UpgradeKind> KINDS = Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.RANGE,
            UpgradeKind.FORTUNE, UpgradeKind.CARRY);

    @SuppressWarnings("unchecked")
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe>[] checks = new RecipeManager.CachedCheck[MAX_LANES];

    public final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < MAX_LANES) return slot < lanes() && canSmelt(stack);
            if (slot == BATTERY) return EnergyUtil.isEnergyItem(stack);
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == BATTERY ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final IItemHandler sideAccess = new SidedItems(items,
            (slot, stack) -> slot < MAX_LANES || slot == BATTERY,
            (slot, stack) -> isOutput(slot) || (slot == BATTERY && isEmptyBattery(stack)));
    private final IItemHandler bottomAccess = new SidedItems(items, SidedItems.NEVER,
            (slot, stack) -> isOutput(slot) || (slot == BATTERY && isEmptyBattery(stack)));

    private final int[] progress = new int[MAX_LANES];
    private final int[] needed = new int[MAX_LANES];
    private float storedXp;

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ProcessingRegistry.ELECTRIC_FURNACE_BE.get(), pos, state, KINDS);
        for (int i = 0; i < MAX_LANES; i++) checks[i] = RecipeManager.createCheck(RecipeType.SMELTING);
    }

    private static boolean isOutput(int slot) {
        return slot >= OUT_FIRST && slot < OUT_FIRST + MAX_LANES;
    }

    private static boolean isEmptyBattery(ItemStack stack) {
        var cap = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        return cap == null || cap.getEnergyStored() <= 0;
    }

    /** Lanes this Mk runs in parallel. */
    public int lanes() {
        return Math.max(1, Math.min(MAX_LANES, ProcessingConfig.lanes(tier)));
    }

    /** Items one lane smelts per cycle: 1 plus the Range cards. */
    public int batch() {
        return 1 + upgrades.level(UpgradeKind.RANGE);
    }

    public int progress(int lane) {
        return progress[lane];
    }

    public int needed(int lane) {
        return needed[lane];
    }

    public float storedXp() {
        return storedXp;
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    @Override
    public int batterySlot() {
        return BATTERY;
    }

    @Override
    public IItemHandler automation(@Nullable Direction side) {
        if (side == null) return items;
        return side == Direction.DOWN ? bottomAccess : sideAccess;
    }

    @Override
    public IItemHandler quickInsertTarget() {
        return sideAccess;
    }

    public boolean canSmelt(ItemStack stack) {
        if (level == null) return true;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
    }

    @Override
    protected void work(ServerLevel level) {
        boolean worked = false, noEnergy = false, full = false;
        int lanes = lanes();
        int batch = batch();
        double xpBoost = 1.0 + upgrades.level(UpgradeKind.FORTUNE) * ProcessingConfig.xpPerFortune();
        for (int lane = 0; lane < MAX_LANES; lane++) {
            ItemStack input = items.getStackInSlot(lane);
            if (lane >= lanes || input.isEmpty()) {
                progress[lane] = 0;
                continue;
            }
            Optional<RecipeHolder<SmeltingRecipe>> found = checks[lane].getRecipeFor(new SingleRecipeInput(input), level);
            if (found.isEmpty()) {
                progress[lane] = 0;
                continue;
            }
            SmeltingRecipe recipe = found.get().value();
            ItemStack result = recipe.assemble(new SingleRecipeInput(input), level.registryAccess());
            if (result.isEmpty()) continue;
            int count = Math.min(batch, input.getCount());
            ItemStack out = items.getStackInSlot(OUT_FIRST + lane);
            int total = result.getCount() * count;
            boolean fits = out.isEmpty() ? total <= result.getMaxStackSize()
                    : ItemStack.isSameItemSameComponents(out, result) && out.getCount() + total <= out.getMaxStackSize();
            if (!fits) {
                full = true;
                continue;
            }
            needed[lane] = ticksFor((int) Math.max(1, Math.round(recipe.getCookingTime() * ProcessingConfig.furnaceTimeFactor())));
            if (!useEnergy(powerFor(ProcessingConfig.furnacePower()) * count)) {
                noEnergy = true;
                continue;
            }
            worked = true;
            if (++progress[lane] >= needed[lane]) {
                progress[lane] = 0;
                items.extractItem(lane, count, false);
                if (out.isEmpty()) items.setStackInSlot(OUT_FIRST + lane, result.copyWithCount(total));
                else out.grow(total);
                storedXp = Math.min(MAX_XP, storedXp + (float) (recipe.getExperience() * count * xpBoost));
                setChanged();
            }
        }
        status = worked ? Status.WORKING : noEnergy ? Status.NO_ENERGY : full ? Status.OUTPUT_FULL : Status.IDLE;
    }

    /** Pops the stored experience as orbs at a position (a player taking the output, or the block breaking). */
    public void popExperience(ServerLevel level, Vec3 at) {
        int whole = Mth.floor(storedXp);
        float frac = storedXp - whole;
        if (frac > 0 && level.random.nextFloat() < frac) whole++;
        storedXp = 0;
        if (whole > 0) ExperienceOrb.award(level, at, whole);
        setChanged();
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        if (level instanceof ServerLevel server && !hasCarry()) popExperience(server, Vec3.atCenterOf(pos));
    }

    @Override
    protected int progressPercent() {
        if (status != Status.WORKING) return -1;
        int best = 0;
        for (int lane = 0; lane < lanes(); lane++) {
            if (needed[lane] > 0) best = Math.max(best, progress[lane] * 100 / needed[lane]);
        }
        return Math.min(100, best);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ElectricFurnaceMenu(id, inv, this);
    }

    @Override
    protected void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeContents(tag, registries);
        tag.putIntArray("progress", progress);
        tag.putFloat("xp", storedXp);
    }

    @Override
    protected void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.readContents(tag, registries);
        int[] saved = tag.getIntArray("progress");
        for (int i = 0; i < MAX_LANES; i++) progress[i] = i < saved.length ? saved[i] : 0;
        storedXp = tag.getFloat("xp");
    }
}
