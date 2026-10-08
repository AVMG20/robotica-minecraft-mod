package com.arno.robotica.processing.block;

import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.util.RecipeAcceptCache;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.menu.ElectricFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * one more item per lane and cycle each (fixed when the cycle starts). Machines give no experience.
 */
public class ElectricFurnaceBlockEntity extends ProcessingMachineBlockEntity {
    public static final int MAX_LANES = 8;
    public static final int OUT_FIRST = MAX_LANES;
    public static final int BATTERY = MAX_LANES * 2;
    public static final int SLOTS = BATTERY + 1;
    public static final Set<UpgradeKind> KINDS = Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.RANGE);

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
    /** Right-click: smeltables and batteries (cells, Mainspring) but never a charged tool. */
    private final IItemHandler quickInsert = new SidedItems(items,
            (slot, stack) -> slot < MAX_LANES || (slot == BATTERY && GrinderBlockEntity.isBattery(stack)), SidedItems.NEVER);

    /** Experience a furnace saved before 0.5.5 still held: popped as orbs on its next tidy. */
    private float legacyXp;
    private final int[] progress = new int[MAX_LANES];
    private final int[] needed = new int[MAX_LANES];
    /** Items the lane's running cycle smelts, fixed when the cycle starts (0 = no cycle running). */
    private final int[] cycleItems = new int[MAX_LANES];

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

    @Override
    public ItemStackHandler items() {
        return items;
    }

    @Override
    public int batterySlot() {
        return BATTERY;
    }

    @Override
    protected IItemHandler automationRules() {
        return sideAccess;
    }

    @Override
    public IItemHandler quickInsertTarget() {
        return quickInsert;
    }

    /** Smeltable items, cached per recipe reload (hoppers and auto-input ask on every insert). */
    private static final RecipeAcceptCache SMELTABLE = new RecipeAcceptCache();
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> slotCheck = RecipeManager.createCheck(RecipeType.SMELTING);

    public boolean canSmelt(ItemStack stack) {
        if (level == null) return true;
        Level lvl = level;
        return SMELTABLE.test(lvl, stack, s -> slotCheck.getRecipeFor(new SingleRecipeInput(s), lvl).isPresent());
    }

    @Override
    protected void work(ServerLevel level) {
        boolean worked = false, noEnergy = false, full = false;
        int lanes = lanes();
        for (int lane = 0; lane < MAX_LANES; lane++) {
            ItemStack input = items.getStackInSlot(lane);
            if (lane >= lanes || input.isEmpty()) {
                progress[lane] = 0;
                cycleItems[lane] = 0;
                continue;
            }
            Optional<RecipeHolder<SmeltingRecipe>> found = checks[lane].getRecipeFor(new SingleRecipeInput(input), level);
            if (found.isEmpty()) {
                progress[lane] = 0;
                cycleItems[lane] = 0;
                continue;
            }
            SmeltingRecipe recipe = found.get().value();
            ItemStack result = recipe.assemble(new SingleRecipeInput(input), level.registryAccess());
            if (result.isEmpty()) continue;
            // The batch is fixed when a cycle starts: topping a lane up just before the end smelts no extra items.
            if (progress[lane] == 0 || cycleItems[lane] <= 0) cycleItems[lane] = Math.min(batch(), input.getCount());
            int count = Math.min(cycleItems[lane], input.getCount());
            ItemStack out = items.getStackInSlot(OUT_FIRST + lane);
            int total = result.getCount() * count;
            boolean fits = out.isEmpty() ? total <= result.getMaxStackSize()
                    : ItemStack.isSameItemSameComponents(out, result) && out.getCount() + total <= out.getMaxStackSize();
            if (!fits) {
                full = true;
                continue;
            }
            needed[lane] = ticksFor((int) Math.max(1, Math.round(recipe.getCookingTime() * ProcessingConfig.furnaceTimeFactor())));
            if (!useEnergy((long) powerFor(ProcessingConfig.furnacePower()) * cycleItems[lane])) {
                noEnergy = true;
                continue;
            }
            worked = true;
            if (++progress[lane] >= needed[lane]) {
                progress[lane] = 0;
                cycleItems[lane] = 0;
                items.extractItem(lane, count, false);
                if (out.isEmpty()) items.setStackInSlot(OUT_FIRST + lane, result.copyWithCount(total));
                else out.grow(total);
                setChanged();
            }
        }
        status = worked ? Status.WORKING : noEnergy ? Status.NO_ENERGY : full ? Status.OUTPUT_FULL : Status.IDLE;
    }

    /**
     * Lanes closed by a lowered {@code lanesMk}: inputs and outputs move to open lanes, or pop out on top. Furnaces from
     * before 0.5.5 also pop their stored experience and any Fortune cards here, once.
     */
    @Override
    protected void tidyHiddenSlots(ServerLevel level, BlockPos pos) {
        if (legacyXp >= 1) {
            ExperienceOrb.award(level, Vec3.atCenterOf(pos).add(0, 0.6, 0), (int) legacyXp);
            legacyXp = 0;
            setChanged();
        }
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack card = upgrades.getStackInSlot(i);
            if (card.getItem() instanceof UpgradeCardItem item && !KINDS.contains(item.getKind())) {
                upgrades.setStackInSlot(i, ItemStack.EMPTY);
                popOut(level, pos, card);
            }
        }
        int lanes = lanes();
        for (int lane = lanes; lane < MAX_LANES; lane++) {
            for (int slot : new int[]{lane, OUT_FIRST + lane}) {
                ItemStack stack = items.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ItemStack rest = stack.copy();
                int first = slot < MAX_LANES ? 0 : OUT_FIRST;
                for (int j = first; j < first + lanes && !rest.isEmpty(); j++) {
                    ItemStack there = items.getStackInSlot(j);
                    if (there.isEmpty() || ItemStack.isSameItemSameComponents(there, rest)) {
                        int room = Math.min(there.isEmpty() ? rest.getMaxStackSize() : there.getMaxStackSize() - there.getCount(), rest.getCount());
                        if (room <= 0) continue;
                        if (there.isEmpty()) items.setStackInSlot(j, rest.copyWithCount(room));
                        else there.grow(room);
                        rest.shrink(room);
                    }
                }
                items.setStackInSlot(slot, ItemStack.EMPTY);
                popOut(level, pos, rest);
            }
            progress[lane] = 0;
            cycleItems[lane] = 0;
        }
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
        tag.putIntArray("cycleItems", cycleItems);
        if (legacyXp > 0) tag.putFloat("xp", legacyXp);
    }

    @Override
    protected void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.readContents(tag, registries);
        legacyXp = tag.getFloat("xp");
        int[] saved = tag.getIntArray("progress");
        for (int i = 0; i < MAX_LANES; i++) progress[i] = i < saved.length ? saved[i] : 0;
        int[] cycle = tag.getIntArray("cycleItems");
        for (int i = 0; i < MAX_LANES; i++) cycleItems[i] = i < cycle.length ? cycle[i] : 0;
    }
}
