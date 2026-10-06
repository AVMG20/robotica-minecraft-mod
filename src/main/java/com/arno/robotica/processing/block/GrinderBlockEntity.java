package com.arno.robotica.processing.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.media.GrindingMedia;
import com.arno.robotica.processing.menu.GrinderMenu;
import com.arno.robotica.processing.recipe.GrindingLogic;
import com.arno.robotica.processing.recipe.GrindingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Grinder: input, grinding media, three output slots and a battery. Ores become two dusts (config), raw ores one plus
 * a chance, ingots one; {@code robotica:grinding} recipes cover the rest. Media and Fortune cards add main output on
 * ores and raw ores; media also has a chance at a byproduct dust and wears out after N ores. A Void card deletes
 * byproducts that do not fit instead of stopping the machine.
 */
public class GrinderBlockEntity extends ProcessingMachineBlockEntity {
    public static final int INPUT = 0;
    public static final int MEDIA = 1;
    public static final int OUT_FIRST = 2;
    public static final int OUT_COUNT = 3;
    public static final int BATTERY = 5;
    public static final int SLOTS = 6;

    public static final Set<UpgradeKind> KINDS = Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.FORTUNE,
            UpgradeKind.VOID, UpgradeKind.CARRY);

    public final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case INPUT -> canGrind(stack);
                case MEDIA -> isMedia(stack);
                case BATTERY -> EnergyUtil.isEnergyItem(stack);
                default -> true;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == BATTERY ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == MEDIA) {
                Item now = getStackInSlot(MEDIA).getItem();
                if (now != wearItem) {
                    wear = 0;
                    wearItem = now;
                }
            }
            setChanged();
        }
    };

    private final IItemHandler sideAccess = new SidedItems(items,
            (slot, stack) -> slot == INPUT || slot == MEDIA || slot == BATTERY,
            (slot, stack) -> isOutput(slot) || (slot == BATTERY && isEmptyBattery(stack)));
    private final IItemHandler bottomAccess = new SidedItems(items, SidedItems.NEVER,
            (slot, stack) -> isOutput(slot) || (slot == BATTERY && isEmptyBattery(stack)));
    private final IItemHandler quickInsertWithBattery = new SidedItems(items, (slot, stack) -> slot == INPUT || slot == MEDIA || slot == BATTERY,
            SidedItems.NEVER);

    private int progress;
    private int needed;
    private int wear;
    private Item wearItem = Items.AIR;

    private Item planItem = Items.AIR;
    private int planGeneration = -1;
    private GrindingLogic.Lookup lookup = GrindingLogic.Lookup.NONE;

    public GrinderBlockEntity(BlockPos pos, BlockState state) {
        super(ProcessingRegistry.GRINDER_BE.get(), pos, state, KINDS);
        this.needed = ticksFor(ProcessingConfig.grinderTicks());
    }

    private static boolean isOutput(int slot) {
        return slot >= OUT_FIRST && slot < OUT_FIRST + OUT_COUNT;
    }

    private static boolean isEmptyBattery(ItemStack stack) {
        var cap = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        return cap == null || cap.getEnergyStored() <= 0;
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
        return quickInsertWithBattery;
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    public int wear() {
        return wear;
    }

    /** Ores left before the media item in the slot wears out, 0 without media. */
    public int mediaLeft() {
        GrindingMedia media = GrindingMedia.of(items.getStackInSlot(MEDIA));
        return media == null ? 0 : Math.max(0, media.uses() - wear);
    }

    /** Mk a recipe for the current input needs, 0 when none. */
    public int neededTier() {
        return lookup.neededTier();
    }

    public boolean canGrind(ItemStack stack) {
        if (level == null) return true;
        return GrindingLogic.find(level, stack, tier).plan() != null;
    }

    public boolean isMedia(ItemStack stack) {
        GrindingMedia media = GrindingMedia.of(stack);
        return media != null && media.fits(tier);
    }

    @Nullable
    private GrindingLogic.Plan plan(ServerLevel level, ItemStack input) {
        if (input.getItem() != planItem || planGeneration != GrindingLogic.generation()) {
            planItem = input.getItem();
            planGeneration = GrindingLogic.generation();
            lookup = GrindingLogic.find(level, input, tier);
        }
        return lookup.plan();
    }

    @Override
    protected void work(ServerLevel level) {
        ItemStack input = items.getStackInSlot(INPUT);
        if (input.isEmpty()) {
            progress = 0;
            status = Status.IDLE;
            return;
        }
        GrindingLogic.Plan plan = plan(level, input);
        if (plan == null) {
            progress = 0;
            status = lookup.neededTier() > 0 ? Status.NEEDS_TIER : Status.IDLE;
            return;
        }
        if (!fits(List.of(plan.main()))) {
            status = Status.OUTPUT_FULL;
            return;
        }
        needed = ticksFor(plan.time() > 0 ? plan.time() : ProcessingConfig.grinderTicks());
        if (progress < needed) {
            if (!useEnergy(powerFor(ProcessingConfig.grinderPower()))) {
                status = Status.NO_ENERGY;
                return;
            }
            progress++;
        }
        status = Status.WORKING;
        if (progress >= needed) {
            if (grindOne(level.random, plan)) {
                progress = 0;
                CoreSounds.play(level, worldPosition, () -> SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35F, 0.8F + level.random.nextFloat() * 0.3F);
            } else {
                status = Status.OUTPUT_FULL;
            }
        }
    }

    /**
     * Finishes one item: rolls the outputs (main with media and Fortune bonus, recipe extras, media byproduct), puts
     * them in the output slots, uses up the input and wears the media. False (and nothing changes) when they do not fit.
     */
    public boolean grindOne(RandomSource random, GrindingLogic.Plan plan) {
        ItemStack input = items.getStackInSlot(INPUT);
        if (input.isEmpty()) return false;
        GrindingMedia media = plan.boostable() ? GrindingMedia.of(items.getStackInSlot(MEDIA)) : null;
        if (media != null && !media.fits(tier)) media = null;
        double bonus = (media != null ? media.bonus() : 0.0)
                + (plan.boostable() ? upgrades.level(UpgradeKind.FORTUNE) * ProcessingConfig.fortuneBonus() : 0.0);
        int mainCount = GrindingLogic.roll(plan.main().getCount() * (1.0 + bonus), random);
        ItemStack main = plan.main().copyWithCount(Math.max(1, mainCount));
        List<ItemStack> extras = new ArrayList<>();
        for (GrindingRecipe.Extra extra : plan.extras()) {
            if (random.nextFloat() < extra.chance()) extras.add(extra.result().copy());
        }
        if (media != null && media.secondary() > 0 && random.nextFloat() < media.secondary()) {
            ItemStack by = GrindingLogic.byproduct(input, random);
            if (!by.isEmpty()) extras.add(by);
        }
        List<ItemStack> all = new ArrayList<>();
        all.add(main);
        all.addAll(extras);
        boolean voids = upgrades.level(UpgradeKind.VOID) > 0;
        if (!fits(all)) {
            if (!voids || !fits(List.of(main))) return false;
        }
        insert(main);
        for (ItemStack extra : extras) insert(extra);   // with a Void card, what does not fit is deleted
        items.extractItem(INPUT, 1, false);
        if (media != null) {
            wear++;
            if (wear >= media.uses()) {
                wear = 0;
                items.extractItem(MEDIA, 1, false);
                wearItem = items.getStackInSlot(MEDIA).getItem();
            }
        }
        setChanged();
        return true;
    }

    /** True when all stacks fit into the output slots together. */
    private boolean fits(List<ItemStack> stacks) {
        ItemStack[] slots = new ItemStack[OUT_COUNT];
        for (int i = 0; i < OUT_COUNT; i++) slots[i] = items.getStackInSlot(OUT_FIRST + i).copy();
        for (ItemStack stack : stacks) {
            int left = stack.getCount();
            for (int i = 0; i < OUT_COUNT && left > 0; i++) {
                if (!slots[i].isEmpty() && ItemStack.isSameItemSameComponents(slots[i], stack)) {
                    int room = Math.min(slots[i].getMaxStackSize(), 64) - slots[i].getCount();
                    int moved = Math.min(room, left);
                    if (moved > 0) {
                        slots[i].grow(moved);
                        left -= moved;
                    }
                }
            }
            for (int i = 0; i < OUT_COUNT && left > 0; i++) {
                if (slots[i].isEmpty()) {
                    int moved = Math.min(stack.getMaxStackSize(), left);
                    slots[i] = stack.copyWithCount(moved);
                    left -= moved;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }

    /** Puts a stack into the output slots, merging first; returns what did not fit (dropped by the caller). */
    private ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < OUT_COUNT && !rest.isEmpty(); i++) {
                ItemStack in = items.getStackInSlot(OUT_FIRST + i);
                if (pass == 0 && in.isEmpty()) continue;
                rest = items.insertItem(OUT_FIRST + i, rest, false);
            }
        }
        return rest;
    }

    @Override
    protected int progressPercent() {
        return status == Status.WORKING && needed > 0 ? Math.min(100, progress * 100 / needed) : -1;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new GrinderMenu(id, inv, this);
    }

    @Override
    protected void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeContents(tag, registries);
        tag.putInt("progress", progress);
        tag.putInt("wear", wear);
        tag.putString("wearItem", BuiltInRegistries.ITEM.getKey(wearItem).toString());
    }

    @Override
    protected void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.readContents(tag, registries);
        progress = tag.getInt("progress");
        wear = tag.getInt("wear");
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("wearItem"));
        wearItem = id == null ? items.getStackInSlot(MEDIA).getItem() : BuiltInRegistries.ITEM.get(id);
    }
}
