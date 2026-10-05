package com.arno.robotica.core.upgrade;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Upgrade slot inventory plus the shared effect math.
 *
 * <p>How upgrades work (one Codex page): a machine has a few upgrade slots. Each card kind takes one slot. Stackable
 * kinds (speed, efficiency, range, growth, fortune) stack in that slot and every card adds one step, up to what the
 * machine accepts ({@link #cap}). Silk and void are single cards; silk and fortune exclude each other.
 *
 * <p>API for machines: create with the kinds you accept (each capped at {@link UpgradeKind#maxStack}), a map of caps,
 * or a cap function (robots raise their caps with their Mk tier); read the effect with {@link #level} (the number of
 * cards of that kind, already capped). Use the static helpers so every machine agrees on the numbers.
 */
public class Upgrades extends ItemStackHandler {
    private final ToIntFunction<UpgradeKind> caps;
    private final Set<UpgradeKind> accepted;
    private final Runnable onChanged;
    private final Map<UpgradeKind, Integer> cache = new EnumMap<>(UpgradeKind.class);
    private boolean dirty = true;

    /** Accepts these kinds up to their {@link UpgradeKind#maxStack}. */
    public Upgrades(int slots, Set<UpgradeKind> accepted, Runnable onChanged) {
        this(slots, accepted, UpgradeKind::ordinal, onChanged, true);
    }

    /** Accepts the kinds in the map, each up to its value. */
    public Upgrades(int slots, Map<UpgradeKind, Integer> caps, Runnable onChanged) {
        this(slots, caps.keySet(), k -> Math.min(k.maxStack, caps.getOrDefault(k, 0)), onChanged, false);
    }

    /** Accepts these kinds with a cap computed on demand (for example from a robot's Mk tier). */
    public Upgrades(int slots, Set<UpgradeKind> accepted, ToIntFunction<UpgradeKind> caps, Runnable onChanged) {
        this(slots, accepted, k -> Math.min(k.maxStack, caps.applyAsInt(k)), onChanged, false);
    }

    private Upgrades(int slots, Set<UpgradeKind> accepted, ToIntFunction<UpgradeKind> caps, Runnable onChanged, boolean defaultCaps) {
        super(slots);
        this.accepted = Set.copyOf(accepted);
        this.caps = defaultCaps ? k -> k.maxStack : caps;
        this.onChanged = onChanged;
    }

    /** How many cards of this kind the machine accepts, 0 if none. */
    public int cap(UpgradeKind kind) {
        return accepted.contains(kind) ? Math.max(0, caps.applyAsInt(kind)) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
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
        dirty = true;
        onChanged.run();
    }

    @Override
    protected void onLoad() {
        dirty = true;
    }

    /** Marks the cached levels stale, for machines whose caps just changed (a robot getting a Mk kit). */
    public void capsChanged() {
        dirty = true;
    }

    public Set<UpgradeKind> acceptedKinds() {
        return accepted;
    }

    /** Number of cards of a kind that count (capped by the machine), 0 if none. */
    public int level(UpgradeKind kind) {
        if (dirty) {
            cache.clear();
            for (int i = 0; i < getSlots(); i++) {
                ItemStack stack = getStackInSlot(i);
                if (stack.getItem() instanceof UpgradeCardItem card) cache.merge(card.getKind(), stack.getCount(), Integer::sum);
            }
            cache.replaceAll((k, n) -> Math.min(n, cap(k)));
            dirty = false;
        }
        return cache.getOrDefault(kind, 0);
    }

    /** Same as {@link #level}: the number of cards of that kind. */
    public int count(UpgradeKind kind) {
        return level(kind);
    }

    /**
     * Puts one card from {@code stack} into the slot of its kind (or a free slot). Returns true and shrinks nothing:
     * the caller removes the card. Used by right-click quick insert.
     */
    public boolean insertOne(ItemStack stack) {
        return insertOne(stack, false);
    }

    /** Same as {@link #insertOne(ItemStack)}; with {@code simulate} only checks (call that on the client). */
    public boolean insertOne(ItemStack stack, boolean simulate) {
        if (!(stack.getItem() instanceof UpgradeCardItem)) return false;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < getSlots(); i++) {
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

    public static int level(IItemHandler handler, UpgradeKind kind) {
        if (handler instanceof Upgrades u) return u.level(kind);
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeCardItem card && card.getKind() == kind) n += stack.getCount();
        }
        return Math.min(n, kind.maxStack);
    }

    // ---- Shared effect math (keep every machine consistent) ----

    private static final int[] SPEED = {1, 2, 3, 4, 6, 8, 11, 15, 20};

    /** Speed cards 0-8 → work rate multiplier 1, 2, 3, 4, 6, 8, 11, 15, 20. */
    public static int speedMultiplier(int speedCards) {
        return SPEED[Math.max(0, Math.min(SPEED.length - 1, speedCards))];
    }

    /**
     * Energy multiplier per action for normal machines: speed cards cost a little more per action, growing with the
     * square (1 card +30%, 4 cards +180%, 8 cards +520%); each efficiency card takes 15% off, never below 40%.
     */
    public static double energyMultiplier(int speedCards, int efficiencyCards) {
        double s = Math.max(0, speedCards);
        double m = (1.0 + 0.25 * s + 0.05 * s * s) * (1.0 - 0.15 * Math.max(0, efficiencyCards));
        return Math.max(0.4, m);
    }

    /**
     * Energy multiplier per action for very strong machines (the Excavator): much steeper, each speed card costs more
     * than the one before (1 card x1.75, 2 x3, 4 x7, 8 x21 per block), so x20 speed needs serious power.
     */
    public static double steepEnergyMultiplier(int speedCards, int efficiencyCards) {
        double s = Math.max(0, speedCards);
        double m = (1.0 + 0.5 * s + 0.25 * s * s) * (1.0 - 0.15 * Math.max(0, efficiencyCards));
        return Math.max(0.4, m);
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
