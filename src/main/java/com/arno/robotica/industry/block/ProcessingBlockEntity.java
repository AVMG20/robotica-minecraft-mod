package com.arno.robotica.industry.block;

import com.arno.robotica.compat.InfoSource;
import com.arno.robotica.compat.MachineInfo;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.core.side.SideConfig;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.industry.IndustryConfig;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.menu.ProcessingMenu;
import com.arno.robotica.industry.recipe.ChanceResult;
import com.arno.robotica.industry.recipe.Machine;
import com.arno.robotica.industry.recipe.ProcessingInput;
import com.arno.robotica.industry.recipe.ProcessingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * One block entity class for every processing machine (Alloy Smelter, Centrifuge, Assembler) and every Mk.
 * Slots: {@link Machine#inputs} inputs then {@link Machine#outputs} outputs in {@link #items}, a battery slot, and
 * Mk + 1 upgrade slots. Each tick it works on the first recipe of its type that matches the inputs (shapeless, one
 * ingredient per slot), at {@code power} FE/t times the Mk speed and the card multipliers, for {@code time} ticks
 * divided by the same speed (so FE per craft only rises by the speed cards' energy penalty).
 *
 * <p>Progress is counted in FE: a craft needs {@code power x time} FE. When that FE/t is more than the buffer or the
 * input rate can deliver (a Mk4 full of speed cards on an expensive recipe) the machine draws only what it can
 * sustain each tick and the craft takes longer, so it never stalls and FE per craft stays as designed.
 */
public class ProcessingBlockEntity extends IndustryBlockEntity implements MenuProvider, InfoSource {
    public static final int IDLE = 0, WORKING = 1, NO_ENERGY = 2, OUTPUT_FULL = 3;

    public final Machine machine;
    public final int tier;
    public final ItemStackHandler items;
    public final Upgrades upgrades;
    public final MachineEnergyStorage energy;
    private final IItemHandler automation;
    /** Per-face item access and auto-transfer; every face in and out by default. */
    public final SideConfig sides;

    @Nullable
    private RecipeHolder<ProcessingRecipe> current;
    private boolean inputsChanged = true;
    /** FE (or work units for free recipes) put into the current craft. */
    private long work;
    private int progress;
    private int needed;
    private int lastUse;
    private int status = IDLE;

    public ProcessingBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryRegistry.machineBlockEntity(((ProcessingBlock) state.getBlock()).machine()).get(), pos, state);
        ProcessingBlock block = (ProcessingBlock) state.getBlock();
        this.machine = block.machine();
        this.tier = block.tier();
        this.items = new ItemStackHandler(machine.slots()) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot < machine.inputs && acceptsInput(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (slot < machine.inputs) inputsChanged = true;
                setChanged();
            }
        };
        this.upgrades = Upgrades.forMk(() -> tier, acceptedKinds(machine), this::setChanged);
        this.energy = new MachineEnergyStorage(scaled(IndustryConfig.machineBuffer(), tier), scaled(IndustryConfig.machineInput(), tier), 0, this::setChanged);
        this.automation = new MachineItemAccess(this);
        this.sides = new SideConfig(this, () -> automation);
    }

    /** Buffer and input grow with the Mk (x1 to x4), saturating instead of overflowing with extreme configs. */
    private static int scaled(int base, int tier) {
        return (int) Math.min(Integer.MAX_VALUE, (long) base * Math.max(1, tier));
    }

    // ---------------------------------------------------------------- cards

    /** Every machine takes speed, efficiency and fortune; the Centrifuge also a void card. Slots and caps: the Mk rule. */
    public static Set<UpgradeKind> acceptedKinds(Machine machine) {
        Set<UpgradeKind> kinds = EnumSet.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY, UpgradeKind.FORTUNE);
        if (machine == Machine.CENTRIFUGE) kinds.add(UpgradeKind.VOID);
        return kinds;
    }

    // ---------------------------------------------------------------- inputs

    /** True if some recipe of this machine uses the stack. */
    public boolean acceptsInput(ItemStack stack) {
        if (level == null || stack.isEmpty()) return level == null;
        for (RecipeHolder<ProcessingRecipe> holder : level.getRecipeManager().getAllRecipesFor(IndustryRegistry.recipeType(machine).get())) {
            if (holder.value().usesItem(stack)) return true;
        }
        return false;
    }

    private ProcessingInput input() {
        List<ItemStack> list = new ArrayList<>(machine.inputs);
        for (int i = 0; i < machine.inputs; i++) list.add(items.getStackInSlot(i));
        return new ProcessingInput(list);
    }

    @Nullable
    public RecipeHolder<ProcessingRecipe> currentRecipe() {
        return current;
    }

    // ---------------------------------------------------------------- work

    /** Work speed of this Mk with these speed cards (time divisor and FE/t multiplier). */
    public double speedFactor() {
        return Upgrades.speedMultiplier(upgrades.level(UpgradeKind.SPEED)) * IndustryConfig.tierSpeed(tier);
    }

    /** FE/t a recipe draws in this machine right now. */
    public int powerFor(ProcessingRecipe recipe) {
        int base = CoreConfig.scaleEnergy(recipe.effectivePower());
        if (base == 0) return 0;
        int speed = upgrades.level(UpgradeKind.SPEED);
        int fortune = recipe.fortune() ? upgrades.level(UpgradeKind.FORTUNE) : 0;
        double m = speedFactor() * Upgrades.energyMultiplier(speed, upgrades.level(UpgradeKind.EFFICIENCY)) * (1.0 + 0.25 * fortune);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE / 2, Math.round(base * m)));
    }

    /** Most FE/t the machine can sustain: what the buffer holds and what its input lets in per tick. */
    public int maxDraw() {
        return Math.max(1, Math.min(energy.getMaxEnergyStored(), scaled(IndustryConfig.machineInput(), tier)));
    }

    /** FE/t actually drawn for a recipe: its power, clamped to {@link #maxDraw}. */
    public int drawFor(ProcessingRecipe recipe) {
        return Math.min(powerFor(recipe), maxDraw());
    }

    /** FE one craft costs (work units, one per tick, for a free recipe). */
    public long workFor(ProcessingRecipe recipe) {
        return (long) Math.max(1, powerFor(recipe)) * ticksFor(recipe);
    }

    /** Ticks a craft really takes when power is plentiful: longer than {@link #ticksFor} if the draw is clamped. */
    public int effectiveTicksFor(ProcessingRecipe recipe) {
        int step = powerFor(recipe) == 0 ? 1 : drawFor(recipe);
        return (int) Math.min(Integer.MAX_VALUE, (workFor(recipe) + step - 1) / step);
    }

    /** Ticks a recipe takes in this machine right now. */
    public int ticksFor(ProcessingRecipe recipe) {
        return (int) Math.max(1, Math.round(CoreConfig.scaleInterval(recipe.time()) / speedFactor()));
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        pullBattery();
        if (inputsChanged || (current == null && (level.getGameTime() + pos.asLong()) % 100 == 0)) {
            inputsChanged = false;
            ResourceLocation before = current == null ? null : current.id();
            // Hint by id: after /reload the old holder is gone, so a holder hint would keep a stale recipe.
            current = level.getRecipeManager().getRecipeFor(IndustryRegistry.recipeType(machine).get(), input(), level, before).orElse(null);
            ResourceLocation after = current == null ? null : current.id();
            if (before != null && !before.equals(after)) work = 0;
        }
        if (current == null) {
            status = IDLE;
            work = 0;
            progress = 0;
            lastUse = 0;
        } else {
            ProcessingRecipe recipe = current.value();
            if (!canOutput(recipe)) {
                status = OUTPUT_FULL;
                lastUse = 0;
            } else {
                int power = powerFor(recipe);
                long total = workFor(recipe);
                int step = power == 0 ? 1 : drawFor(recipe);
                needed = effectiveTicksFor(recipe);
                int use = (int) Math.max(0, Math.min(step, total - work));
                int cost = power == 0 ? 0 : use;
                if (energy.consume(cost)) {
                    status = WORKING;
                    lastUse = cost;
                    work += use;
                    progress = (int) Math.min(needed, work / step);
                    if (work >= total) {
                        work = 0;
                        progress = 0;
                        craft(level, recipe);
                    } else if ((level.getGameTime() + pos.asLong()) % 40 == 0) {
                        playSound(level, pos, false);
                    }
                } else {
                    status = NO_ENERGY;
                    lastUse = 0;
                }
            }
        }
        setLit(status == WORKING);
        sides.tick(level);
    }

    private void craft(ServerLevel level, ProcessingRecipe recipe) {
        int[] slots = recipe.assign(input());
        if (slots == null) {
            inputsChanged = true;
            return;
        }
        for (int i = 0; i < slots.length; i++) {
            ItemStack in = items.getStackInSlot(slots[i]).copy();
            in.shrink(recipe.inputs().get(i).count());
            items.setStackInSlot(slots[i], in.isEmpty() ? ItemStack.EMPTY : in);
        }
        int fortune = recipe.fortune() ? upgrades.level(UpgradeKind.FORTUNE) : 0;
        for (int i = 0; i < recipe.results().size(); i++) {
            ItemStack out = recipe.results().get(i).roll(level.random);
            if (out.isEmpty()) continue;
            if (i == 0 && fortune > 0 && level.random.nextInt(100) < fortune * IndustryConfig.fortuneBonus()) out.grow(1);
            insertOutput(out, false);
        }
        playSound(level, worldPosition, true);
        setChanged();
    }

    /** Room for every result at its largest roll (a void card lets chance results that do not fit vanish). */
    private boolean canOutput(ProcessingRecipe recipe) {
        List<ItemStack> sim = new ArrayList<>(machine.outputs);
        for (int i = 0; i < machine.outputs; i++) sim.add(items.getStackInSlot(machine.inputs + i).copy());
        boolean voids = upgrades.level(UpgradeKind.VOID) > 0;
        boolean fortune = recipe.fortune() && upgrades.level(UpgradeKind.FORTUNE) > 0;
        for (int i = 0; i < recipe.results().size(); i++) {
            ChanceResult r = recipe.results().get(i);
            ItemStack stack = r.stack().copy();
            if (i == 0 && fortune) stack.grow(1);
            if (!merge(sim, stack).isEmpty() && !(voids && !r.guaranteed())) return false;
        }
        return true;
    }

    /** Puts a stack into the output slots (ignoring the slot rules). Returns what did not fit; extra is voided by the caller. */
    private ItemStack insertOutput(ItemStack stack, boolean simulate) {
        List<ItemStack> outs = new ArrayList<>(machine.outputs);
        for (int i = 0; i < machine.outputs; i++) outs.add(items.getStackInSlot(machine.inputs + i).copy());
        ItemStack rest = merge(outs, stack.copy());
        if (!simulate) {
            for (int i = 0; i < machine.outputs; i++) {
                if (!ItemStack.matches(outs.get(i), items.getStackInSlot(machine.inputs + i))) items.setStackInSlot(machine.inputs + i, outs.get(i));
            }
        }
        return rest;
    }

    /** Merges into same-item stacks first, then the first empty slot. Mutates {@code slots}; returns the rest. */
    private static ItemStack merge(List<ItemStack> slots, ItemStack stack) {
        for (ItemStack s : slots) {
            if (stack.isEmpty()) break;
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                if (move > 0) {
                    s.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, stack.copy());
                stack = ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private void playSound(Level level, BlockPos pos, boolean done) {
        SoundEvent sound = switch (machine) {
            case ALLOY_SMELTER -> done ? SoundEvents.LAVA_POP : SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
            case CENTRIFUGE -> done ? SoundEvents.BREWING_STAND_BREW : SoundEvents.BEACON_AMBIENT;
            case ASSEMBLER -> done ? SoundEvents.SMITHING_TABLE_USE : SoundEvents.PISTON_EXTEND;
        };
        float volume = done ? 0.5F : 0.25F;
        float pitch = machine == Machine.CENTRIFUGE && !done ? 1.6F : 0.9F + level.random.nextFloat() * 0.2F;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ---------------------------------------------------------------- access

    /** The rules for pipes and hoppers on a face that allows both ways (and for a null side). */
    public IItemHandler automation() {
        return automation;
    }

    /** The item capability of a face, as the side config allows. */
    @Nullable
    public IItemHandler automation(@Nullable Direction side) {
        return sides.access(side);
    }

    @Override
    public MachineEnergyStorage energy() {
        return energy;
    }

    @Nullable
    @Override
    public IItemHandler quickInsertTarget(ItemStack stack) {
        if (stack.getItem() instanceof UpgradeCardItem) return upgrades;
        if (isBattery(stack) && !acceptsInput(stack)) return battery;
        return automation;
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    public int lastUse() {
        return lastUse;
    }

    public int status() {
        return status;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        drop(level, pos, items);
        drop(level, pos, upgrades);
    }

    @Override
    public void collectInfo(ServerLevel level, MachineInfo info) {
        info.status = switch (status) {
            case WORKING -> "working";
            case NO_ENERGY -> "no_energy";
            case OUTPUT_FULL -> "output_full";
            default -> "idle";
        };
        info.tier = tier;
        if (status == WORKING && needed > 0) info.progress = 100 * progress / needed;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ProcessingMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.putLong("work", work);
        tag.put("sides", sides.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) loadInto(items, registries, tag.getCompound("items"));
        if (tag.contains("upgrades")) loadInto(upgrades, registries, tag.getCompound("upgrades"));
        work = tag.getLong("work");
        sides.load(tag.getCompound("sides"));
        inputsChanged = true;
    }
}
