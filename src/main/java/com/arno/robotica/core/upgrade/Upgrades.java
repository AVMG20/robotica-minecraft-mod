package com.arno.robotica.core.upgrade;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Upgrade slot inventory plus the shared effect math. Machines own one of these,
 * declare which kinds they accept, and read levels with {@link #level}.
 */
public class Upgrades extends ItemStackHandler {
    private final Set<UpgradeKind> accepted;
    private final Runnable onChanged;
    private final Map<UpgradeKind, Integer> cache = new EnumMap<>(UpgradeKind.class);
    private boolean dirty = true;

    public Upgrades(int slots, Set<UpgradeKind> accepted, Runnable onChanged) {
        super(slots);
        this.accepted = accepted;
        this.onChanged = onChanged;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!(stack.getItem() instanceof UpgradeCardItem card) || !accepted.contains(card.getKind())) return false;
        // One card per kind. Silk and fortune exclude each other.
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

    public Set<UpgradeKind> acceptedKinds() {
        return accepted;
    }

    /** Highest installed level of a kind, 0 if none. */
    public int level(UpgradeKind kind) {
        if (dirty) {
            cache.clear();
            for (int i = 0; i < getSlots(); i++) {
                if (getStackInSlot(i).getItem() instanceof UpgradeCardItem card) {
                    cache.merge(card.getKind(), card.getLevel(), Math::max);
                }
            }
            dirty = false;
        }
        return cache.getOrDefault(kind, 0);
    }

    public static int level(IItemHandler handler, UpgradeKind kind) {
        if (handler instanceof Upgrades u) return u.level(kind);
        int best = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() instanceof UpgradeCardItem card && card.getKind() == kind) {
                best = Math.max(best, card.getLevel());
            }
        }
        return best;
    }

    // ---- Shared effect math (keep every machine consistent) ----

    /** Speed level 0-4 → work rate multiplier 1, 2, 4, 10, 20. */
    public static int speedMultiplier(int speedLevel) {
        return switch (speedLevel) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 10;
            case 4 -> 20;
            default -> 1;
        };
    }

    /** Energy multiplier per action: +25% per speed level, −15% per efficiency level, floor 40%. */
    public static double energyMultiplier(int speedLevel, int efficiencyLevel) {
        double m = (1.0 + 0.25 * speedLevel) * (1.0 - 0.15 * efficiencyLevel);
        return Math.max(0.4, m);
    }

    /** Fortune card level 2-4 → enchantment level 1-3. */
    public static int fortuneEnchantLevel(int fortuneCardLevel) {
        return fortuneCardLevel <= 1 ? 0 : fortuneCardLevel - 1;
    }

    /** Growth card level 0-4 → extra growth multiplier added on top of the machine's own boost. */
    public static double growthBonus(int growthLevel) {
        return 0.5 * growthLevel;
    }
}
