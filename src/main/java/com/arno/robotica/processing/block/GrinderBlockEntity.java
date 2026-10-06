package com.arno.robotica.processing.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.item.CellItem;
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
import net.minecraft.world.level.Level;
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
 * ores and raw ores, not gem ores (see {@link GrindingLogic.Boost}); media also has a chance at a byproduct
 * dust. One media item at a time is loaded into the machine straight away and lasts N ores (shown by the media bar),
 * then the next one is taken, so the media slot holds plain stackable items the player can add or take any time.
 * A Void card deletes byproducts that do not fit instead of stopping the machine.
 */
public class GrinderBlockEntity extends ProcessingMachineBlockEntity {
    public static final int INPUT = 0;
    public static final int MEDIA = 1;
    public static final int OUT_FIRST = 2;
    public static final int OUT_COUNT = 3;
    public static final int BATTERY = 5;
    public static final int SLOTS = 6;

    public static final Set<UpgradeKind> KINDS = Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.FORTUNE,
            UpgradeKind.VOID);

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
            setChanged();
        }
    };

    private final IItemHandler sideAccess = new SidedItems(items,
            (slot, stack) -> slot == INPUT || slot == MEDIA || slot == BATTERY,
            (slot, stack) -> isOutput(slot) || (slot == BATTERY && isEmptyBattery(stack)));
    /** Right-click: ores, media, and batteries (cells, Mainspring) but never a charged tool. */
    private final IItemHandler quickInsert = new SidedItems(items,
            (slot, stack) -> slot == INPUT || slot == MEDIA || (slot == BATTERY && isBattery(stack)), SidedItems.NEVER);

    private int progress;
    private int needed;
    /** The media item loaded into the machine (air when none) and the ores it still grinds. */
    private Item loadedMedia = Items.AIR;
    private int loadedLeft;

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

    /** Batteries made to power machines (cells, Mainspring), not tools that happen to hold FE. */
    static boolean isBattery(ItemStack stack) {
        return stack.getItem() instanceof CellItem;
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
    protected IItemHandler automationRules() {
        return sideAccess;
    }

    @Override
    public IItemHandler quickInsertTarget() {
        return quickInsert;
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    /** The loaded media item, air when none is loaded. */
    public Item loadedMedia() {
        return loadedMedia;
    }

    /** Ores the loaded media still grinds, 0 when none is loaded. */
    public int mediaLeft() {
        return loadedLeft;
    }

    /** Uses of a fresh item of the loaded media, 0 when none is loaded. */
    public int mediaUses() {
        GrindingMedia media = loaded();
        return media == null ? 0 : media.uses();
    }

    @Nullable
    private GrindingMedia loaded() {
        return loadedLeft > 0 && loadedMedia != Items.AIR ? GrindingMedia.of(new ItemStack(loadedMedia)) : null;
    }

    /**
     * Loads one media item from the slot when none is loaded (or the loaded one stopped being media after a data pack
     * change). The item is used up at once; its uses go into the machine. Returns true when media is loaded.
     */
    public boolean loadMedia() {
        if (loaded() != null) return true;
        loadedMedia = Items.AIR;
        loadedLeft = 0;
        ItemStack stack = items.getStackInSlot(MEDIA);
        GrindingMedia media = GrindingMedia.of(stack);
        if (media == null || !media.fits(tier)) return false;
        // Stacks from before media was loaded still carry their wear: the worn item goes in with what it had left.
        int wear = stack.getOrDefault(ProcessingRegistry.MEDIA_WEAR.get(), 0);
        loadedMedia = stack.getItem();
        loadedLeft = Math.max(1, media.uses() - wear);
        ItemStack rest = stack.copyWithCount(stack.getCount() - 1);
        rest.remove(ProcessingRegistry.MEDIA_WEAR.get());
        items.setStackInSlot(MEDIA, rest.isEmpty() ? ItemStack.EMPTY : rest);
        setChanged();
        return true;
    }

    /** Old media stacks (with the wear component) are loaded or, behind loaded media, become plain items again. */
    private void stripLegacyWear() {
        ItemStack stack = items.getStackInSlot(MEDIA);
        if (stack.isEmpty() || !stack.has(ProcessingRegistry.MEDIA_WEAR.get())) return;
        if (loadMedia() && stack == items.getStackInSlot(MEDIA)) {
            ItemStack plain = stack.copy();
            plain.remove(ProcessingRegistry.MEDIA_WEAR.get());
            items.setStackInSlot(MEDIA, plain);
        }
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
        stripLegacyWear();
        loadMedia();
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
        if (!hasRoom(plan)) {
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
     * The media that works on this plan: the loaded one, or (not loaded yet) the one the slot would load. Null when the
     * plan takes no media.
     */
    @Nullable
    private GrindingMedia activeMedia(GrindingLogic.Plan plan) {
        if (!plan.boost().media()) return null;
        GrindingMedia media = loaded();
        if (media != null) return media;
        media = GrindingMedia.of(items.getStackInSlot(MEDIA));
        return media != null && media.fits(tier) ? media : null;
    }

    private double mainAmount(GrindingLogic.Plan plan, @Nullable GrindingMedia media) {
        return GrindingLogic.mainAmount(plan, media != null ? media.bonus() : 0.0, upgrades.level(UpgradeKind.FORTUNE));
    }

    /**
     * True when the best roll still fits: the most main output, every extra, and any byproduct the media could add (with
     * a Void card only the main output has to fit). Checked before energy is used and before rolling, so a full output
     * waits instead of re-rolling until a small result fits.
     */
    public boolean hasRoom(GrindingLogic.Plan plan) {
        GrindingMedia media = activeMedia(plan);
        double amount = mainAmount(plan, media);
        int most = Math.max(1, (int) Math.ceil(amount - 1e-6));
        List<ItemStack> worst = new ArrayList<>();
        worst.add(plan.main().copyWithCount(most));
        if (upgrades.level(UpgradeKind.VOID) > 0) return fits(worst);
        for (GrindingRecipe.Extra extra : plan.extras()) worst.add(extra.result().copy());
        if (!fits(worst)) return false;
        if (media != null && media.secondary() > 0) {
            for (Item by : GrindingLogic.byproductOptions(items.getStackInSlot(INPUT), plan.boost().fallback())) {
                List<ItemStack> with = new ArrayList<>(worst);
                with.add(new ItemStack(by));
                if (!fits(with)) return false;
            }
        }
        return true;
    }

    /**
     * Finishes one item: rolls the outputs (main with media and Fortune bonus, recipe extras, media byproduct), puts
     * them in the output slots, uses up the input and wears the media. False (and nothing changes, nothing is rolled)
     * when the best roll would not fit.
     */
    public boolean grindOne(RandomSource random, GrindingLogic.Plan plan) {
        ItemStack input = items.getStackInSlot(INPUT);
        if (input.isEmpty() || !hasRoom(plan)) return false;
        if (plan.boost().media()) loadMedia();
        GrindingMedia media = activeMedia(plan);
        int mainCount = GrindingLogic.roll(mainAmount(plan, media), random);
        ItemStack main = plan.main().copyWithCount(Math.max(1, mainCount));
        List<ItemStack> extras = new ArrayList<>();
        for (GrindingRecipe.Extra extra : plan.extras()) {
            if (random.nextFloat() < extra.chance()) extras.add(extra.result().copy());
        }
        if (media != null && media.secondary() > 0 && random.nextFloat() < media.secondary()) {
            ItemStack by = GrindingLogic.byproduct(input, plan.boost().fallback(), random);
            if (!by.isEmpty()) extras.add(by);
        }
        insert(main);
        for (ItemStack extra : extras) insert(extra);   // with a Void card, what does not fit is deleted
        items.extractItem(INPUT, 1, false);
        if (media != null) wearMedia();
        setChanged();
        return true;
    }

    /** One ore less on the loaded media; when it runs out the next item from the slot is loaded. */
    private void wearMedia() {
        if (--loadedLeft > 0) return;
        loadedMedia = Items.AIR;
        loadedLeft = 0;
        loadMedia();
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

    /** An untouched loaded media item comes back out with the rest; a worn one is gone. */
    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        GrindingMedia media = loaded();
        if (media != null && loadedLeft >= media.uses()) popOut(level, pos, new ItemStack(loadedMedia));
        loadedMedia = Items.AIR;
        loadedLeft = 0;
    }

    @Override
    protected void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeContents(tag, registries);
        tag.putInt("progress", progress);
        if (loadedLeft > 0 && loadedMedia != Items.AIR) {
            tag.putString("media", BuiltInRegistries.ITEM.getKey(loadedMedia).toString());
            tag.putInt("mediaLeft", loadedLeft);
        }
    }

    @Override
    protected void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        super.readContents(tag, registries);
        progress = tag.getInt("progress");
        ResourceLocation mediaId = ResourceLocation.tryParse(tag.getString("media"));
        loadedMedia = mediaId == null ? Items.AIR : BuiltInRegistries.ITEM.get(mediaId);
        loadedLeft = loadedMedia == Items.AIR ? 0 : tag.getInt("mediaLeft");
        // Saves from before wear lived on the media stack: put the old machine-side counter on it, loaded on the next tick.
        ItemStack media = items.getStackInSlot(MEDIA);
        int oldWear = tag.getInt("wear");
        if (oldWear > 0 && !media.isEmpty() && !media.has(ProcessingRegistry.MEDIA_WEAR.get())) {
            ItemStack worn = media.copy();
            worn.set(ProcessingRegistry.MEDIA_WEAR.get(), oldWear);
            items.setStackInSlot(MEDIA, worn);
        }
    }
}
