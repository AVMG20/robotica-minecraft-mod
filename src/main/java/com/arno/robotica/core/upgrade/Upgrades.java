package com.arno.robotica.core.upgrade;

import com.arno.robotica.core.menu.MachineSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * Upgrade slot inventory plus the shared effect math.
 *
 * <p>How cards work: each card kind takes one slot; stackable kinds stack there and every card adds one step, up to
 * the machine's cap. Silk and fortune exclude each other. Slots and caps come from {@link UpgradeRules}: create a Mk
 * machine's slots with {@link #forMk} and a fixed machine's with {@link #fixed}, read the effect with {@link #level}
 * and use the static helpers so every machine agrees on the numbers.
 */
public class Upgrades extends ItemStackHandler {
    private final Set<UpgradeKind> accepted;
    private final ToIntFunction<UpgradeKind> caps;
    private final IntSupplier active;
    private final Runnable onChanged;

    private Upgrades(int slots, IntSupplier active, Set<UpgradeKind> accepted, ToIntFunction<UpgradeKind> caps, Runnable onChanged) {
        super(slots);
        this.active = active;
        this.accepted = Set.copyOf(accepted);
        this.caps = caps;
        this.onChanged = onChanged;
    }

    /**
     * Card slots of a machine with a Mk: {@link UpgradeRules#MAX_SLOTS} slots inside, of which the Mk opens Mk + 1, caps
     * from {@link UpgradeRules#mkCap}. {@code mk} is read on demand, so a Mk kit or an in-place swap needs nothing else.
     */
    public static Upgrades forMk(IntSupplier mk, Set<UpgradeKind> kinds, Runnable onChanged) {
        return new Upgrades(UpgradeRules.MAX_SLOTS, () -> UpgradeRules.mkSlots(mk.getAsInt()), kinds,
                k -> UpgradeRules.mkCap(mk.getAsInt(), k), onChanged);
    }

    /** Card slots of a machine without a Mk, as listed in {@link UpgradeRules.Fixed}. */
    public static Upgrades fixed(UpgradeRules.Fixed machine, Runnable onChanged) {
        return new Upgrades(machine.slots, () -> machine.slots, machine.kinds(), machine::cap, onChanged);
    }

    /** Slots that take cards now (the Mk opens them); cards only count there. */
    public int activeSlots() {
        return Math.max(0, Math.min(getSlots(), active.getAsInt()));
    }

    /** How many cards of this kind the machine accepts, 0 if none. */
    public int cap(UpgradeKind kind) {
        return accepted.contains(kind) ? Math.max(0, Math.min(kind.maxStack, caps.applyAsInt(kind))) : 0;
    }

    public Set<UpgradeKind> acceptedKinds() {
        return accepted;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot >= activeSlots()) return false;
        if (!(stack.getItem() instanceof UpgradeCardItem card) || cap(card.getKind()) <= 0) return false;
        // One slot per kind (the cards stack there). Silk and fortune exclude each other.
        for (int i = 0; i < getSlots(); i++) {
            if (i == slot) continue;
            ItemStack other = getStackInSlot(i);
            if (other.getItem() instanceof UpgradeCardItem o) {
                if (o.getKind() == card.getKind()) return false;
                if (isSilkFortunePair(o.getKind(), card.getKind())) return false;
            }
        }
        return true;
    }

    private static boolean isSilkFortunePair(UpgradeKind a, UpgradeKind b) {
        return (a == UpgradeKind.SILK && b == UpgradeKind.FORTUNE) || (a == UpgradeKind.FORTUNE && b == UpgradeKind.SILK);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        if (stack.getItem() instanceof UpgradeCardItem card) return Math.max(1, Math.min(stack.getMaxStackSize(), cap(card.getKind())));
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    /** Loads slot by slot: the slot count never changes, whatever the tag says. */
    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag);
        for (int i = 0; i < getSlots(); i++) stacks.set(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
        onLoad();
    }

    /** Number of cards of a kind that count: in open slots, capped by the machine. 0 if none. */
    public int level(UpgradeKind kind) {
        int n = 0;
        for (int i = 0; i < activeSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeCardItem card && card.getKind() == kind) n += stack.getCount();
        }
        return Math.min(n, cap(kind));
    }

    /** Puts one card from {@code stack} into the slot of its kind (or a free slot); the caller removes the card. */
    public boolean insertOne(ItemStack stack) {
        return insertOne(stack, false);
    }

    /** Same as {@link #insertOne(ItemStack)}; with {@code simulate} only checks (call that on the client). */
    public boolean insertOne(ItemStack stack, boolean simulate) {
        if (!(stack.getItem() instanceof UpgradeCardItem)) return false;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < activeSlots(); i++) {
                ItemStack in = getStackInSlot(i);
                boolean sameKind = !in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack);
                if (pass == 0 ? !sameKind : !in.isEmpty()) continue;
                if (insertItem(i, stack.copyWithCount(1), true).isEmpty()) {
                    if (!simulate) insertItem(i, stack.copyWithCount(1), false);
                    return true;
                }
            }
        }
        return false;
    }

    /** A menu slot for card slot {@code index}; hidden while the machine's Mk has not opened it. */
    public MachineSlot slot(int index, int x, int y) {
        return new MachineSlot(this, index, x, y) {
            @Override
            public boolean isActive() {
                return index < activeSlots();
            }
        };
    }

    // ---- Shared effect math (every machine uses these) ----

    private static final int[] SPEED = {1, 2, 3, 4, 6, 8, 11, 15, 20};

    /** Speed cards 0-8 → work rate multiplier 1, 2, 3, 4, 6, 8, 11, 15, 20. */
    public static int speedMultiplier(int speedCards) {
        return SPEED[Math.max(0, Math.min(SPEED.length - 1, speedCards))];
    }

    /** Energy saved per efficiency card, and the lowest the multiplier goes. */
    public static final double EFFICIENCY_PER_CARD = 0.15, ENERGY_FLOOR = 0.4;

    /**
     * Energy per action: speed cards cost a little more per action, growing with the square (1 card +30%, 4 cards
     * +180%, 8 cards +520%); each efficiency card takes 15% off, never below 40%.
     */
    public static double energyMultiplier(int speedCards, int efficiencyCards) {
        double s = Math.max(0, speedCards);
        return efficiency(1.0 + 0.25 * s + 0.05 * s * s, efficiencyCards);
    }

    /**
     * Energy per action for the quarries (Excavator, Survey Rig): every speed card costs more than the one before
     * (1 card x1.75, 2 x3, 4 x7, 8 x21), so x20 speed needs serious power. Efficiency works the same.
     */
    public static double steepEnergyMultiplier(int speedCards, int efficiencyCards) {
        double s = Math.max(0, speedCards);
        return efficiency(1.0 + 0.5 * s + 0.25 * s * s, efficiencyCards);
    }

    private static double efficiency(double speedFactor, int efficiencyCards) {
        return Math.max(ENERGY_FLOOR, speedFactor * (1.0 - EFFICIENCY_PER_CARD * Math.max(0, efficiencyCards)));
    }

    /** Fortune cards 0-3 → Fortune (or Looting) level 0-3. */
    public static int fortuneEnchantLevel(int fortuneCards) {
        return Math.max(0, Math.min(3, fortuneCards));
    }

    /** Growth cards 0-4 → extra growth multiplier added on top of the machine's own boost. */
    public static double growthBonus(int growthCards) {
        return 0.5 * growthCards;
    }
}
