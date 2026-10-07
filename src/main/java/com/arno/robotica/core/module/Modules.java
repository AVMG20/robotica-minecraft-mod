package com.arno.robotica.core.module;

import com.arno.robotica.core.energy.ItemEnergy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The module framework: what is installed in a power tool, FE weapon or Exo piece ({@link ModuleHolder}), the install
 * rules and the on/off switches. Safe on both sides (reads and writes item components only).
 *
 * <p>Rules: the item's tier (Age or Mk) sets its slot count ({@link ModuleConfig}) and which levels fit
 * ({@link ModuleKind#minTier}); a kind fits certain {@link ModuleTarget}s and goes in once per item. A module that got
 * in anyway (commands) does nothing ({@link #works}). Every module starts switched on, except a second kind of a
 * {@link ModuleKind#group} (only one of a group is on).
 */
public final class Modules {
    private Modules() {}

    /** Most module slots of any item (Age 4 tools and weapons). */
    public static final int MAX_SLOTS = 5;
    /** Most module slots of an Exo piece (the J screen shows four). */
    public static final int MAX_ARMOR_SLOTS = 4;

    // ---------------------------------------------------------------- the holder

    @Nullable
    public static ModuleTarget target(ItemStack stack) {
        return stack.getItem() instanceof ModuleHolder h ? h.moduleTarget(stack) : null;
    }

    /** Age or Mk of a module holder, 0 for anything else. */
    public static int tier(ItemStack stack) {
        return target(stack) != null ? ((ModuleHolder) stack.getItem()).moduleTier(stack) : 0;
    }

    /** True for items that take modules (the Tinker's Bench accepts them). */
    public static boolean accepts(ItemStack stack) {
        return target(stack) != null;
    }

    /** Module slots that work on this item. */
    public static int slots(ItemStack stack) {
        ModuleTarget target = target(stack);
        if (target == null) return 0;
        return slots(target, tier(stack));
    }

    public static int slots(ModuleTarget target, int tier) {
        return target.isArmor() ? Math.min(MAX_ARMOR_SLOTS, ModuleConfig.armorSlots(tier)) : Math.min(MAX_SLOTS, ModuleConfig.gearSlots(tier));
    }

    /** Lowest tier whose items of this target have more than {@code slot} slots, or 0 when none does. */
    public static int tierForSlot(ModuleTarget target, int slot) {
        for (int tier = 1; tier <= 4; tier++) {
            if (slots(target, tier) > slot) return tier;
        }
        return 0;
    }

    // ---------------------------------------------------------------- installed modules

    /** The installed module stack in a slot (never modify the result), or EMPTY. */
    public static ItemStack module(ItemStack stack, int slot) {
        ItemContainerContents contents = stack.get(ModuleComponents.MODULES.get());
        if (contents == null || slot < 0 || slot >= contents.getSlots()) return ItemStack.EMPTY;
        return contents.getStackInSlot(slot);
    }

    @Nullable
    public static ModuleKind kind(ItemStack stack, int slot) {
        return module(stack, slot).getItem() instanceof ModuleItem m ? m.kind : null;
    }

    /**
     * Puts a module into a slot (EMPTY removes it). A changed slot starts switched on, unless another kind of its group
     * is on. Energy above a smaller battery (Capacitor Plating removed) is cut off.
     */
    public static void setModule(ItemStack stack, int slot, ItemStack module) {
        if (slot < 0 || slot >= MAX_SLOTS) return;
        ItemStack before = module(stack, slot);
        NonNullList<ItemStack> list = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack s = i == slot ? (module.isEmpty() ? ItemStack.EMPTY : module.copyWithCount(1)) : module(stack, i).copy();
            list.set(i, s);
            any |= !s.isEmpty();
        }
        if (any) stack.set(ModuleComponents.MODULES.get(), ItemContainerContents.fromItems(list));
        else stack.remove(ModuleComponents.MODULES.get());
        if (!ItemStack.isSameItem(before, module)) {
            boolean on = true;
            if (module.getItem() instanceof ModuleItem m && m.kind.group != 0) {
                for (int i = 0; i < MAX_SLOTS; i++) {
                    if (i != slot && kind(stack, i) != null && kind(stack, i).group == m.kind.group && enabled(stack, i)) on = false;
                }
            }
            setOffBit(stack, slot, !on);
        }
        if (ItemEnergy.get(stack) > ItemEnergy.capacity(stack) && ItemEnergy.capacity(stack) > 0) ItemEnergy.set(stack, ItemEnergy.capacity(stack));
    }

    /** True when the module in this slot may work: inside the slot count, fitting the item, tier high enough. */
    public static boolean works(ItemStack stack, int slot) {
        ModuleTarget target = target(stack);
        return target != null && slot < slots(stack) && module(stack, slot).getItem() instanceof ModuleItem m
                && m.kind.fits(target) && tier(stack) >= m.minTier();
    }

    /** Highest working level of a kind (on or off), 0 when absent. */
    public static int level(ItemStack stack, ModuleKind kind) {
        int best = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (module(stack, i).getItem() instanceof ModuleItem m && m.kind == kind && works(stack, i)) best = Math.max(best, m.level);
        }
        return best;
    }

    /** Highest installed level of a kind, working or not, 0 when absent. */
    public static int installed(ItemStack stack, ModuleKind kind) {
        int best = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (module(stack, i).getItem() instanceof ModuleItem m && m.kind == kind) best = Math.max(best, m.level);
        }
        return best;
    }

    /** Slot of a kind, or -1. */
    public static int slotOf(ItemStack stack, ModuleKind kind) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (kind(stack, i) == kind) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- on / off

    public static boolean enabled(ItemStack stack, int slot) {
        return (stack.getOrDefault(ModuleComponents.MODULES_OFF.get(), 0) & (1 << slot)) == 0;
    }

    /** True when the kind is installed and switched on (or not installed at all). */
    public static boolean enabled(ItemStack stack, ModuleKind kind) {
        int slot = slotOf(stack, kind);
        return slot < 0 || enabled(stack, slot);
    }

    /** Switches a slot on or off. Switching on turns the other kinds of its group off. */
    public static void setEnabled(ItemStack stack, int slot, boolean on) {
        if (slot < 0 || slot >= MAX_SLOTS) return;
        ModuleKind kind = kind(stack, slot);
        if (on && kind != null && kind.group != 0) {
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (i != slot && kind(stack, i) != null && kind(stack, i).group == kind.group) setOffBit(stack, i, true);
            }
        }
        setOffBit(stack, slot, !on);
    }

    public static void setEnabled(ItemStack stack, ModuleKind kind, boolean on) {
        setEnabled(stack, slotOf(stack, kind), on);
    }

    private static void setOffBit(ItemStack stack, int slot, boolean off) {
        int mask = stack.getOrDefault(ModuleComponents.MODULES_OFF.get(), 0);
        mask = off ? mask | (1 << slot) : mask & ~(1 << slot);
        if (mask == 0) stack.remove(ModuleComponents.MODULES_OFF.get());
        else stack.set(ModuleComponents.MODULES_OFF.get(), mask);
    }

    /** Level of a kind that is installed, working and switched on; 0 otherwise. */
    public static int active(ItemStack stack, ModuleKind kind) {
        int slot = slotOf(stack, kind);
        return slot >= 0 && enabled(stack, slot) && works(stack, slot) && module(stack, slot).getItem() instanceof ModuleItem m ? m.level : 0;
    }

    /**
     * Steps through a group: off, then each installed kind of the group in order, then off again (the B key: Fortune,
     * Silk Touch, off). Returns the kind now on, or null for off.
     */
    @Nullable
    public static ModuleKind cycleGroup(ItemStack stack, int group) {
        java.util.List<ModuleKind> kinds = new java.util.ArrayList<>();
        ModuleKind current = null;
        for (ModuleKind kind : ModuleKind.values()) {
            if (kind.group != group || level(stack, kind) <= 0) continue;
            kinds.add(kind);
            if (enabled(stack, kind)) current = kind;
        }
        if (kinds.isEmpty()) return null;
        int next = current == null ? 0 : kinds.indexOf(current) + 1;
        for (ModuleKind kind : kinds) setEnabled(stack, kind, false);
        if (next >= kinds.size()) return null;
        setEnabled(stack, kinds.get(next), true);
        return kinds.get(next);
    }

    // ---------------------------------------------------------------- power regulator

    /** What the Power Regulator leaves of every FE cost of this item (1 = none installed or switched off). */
    public static double regulatorFactor(ItemStack stack) {
        return 1.0 - ModuleConfig.regulatorSaving(active(stack, ModuleKind.POWER_REGULATOR));
    }

    /** {@code fe} after this item's Power Regulator (at least 1 when {@code fe} is above 0). */
    public static int regulated(ItemStack stack, int fe) {
        if (fe <= 0) return 0;
        return Math.max(1, (int) Math.round(fe * regulatorFactor(stack)));
    }

    // ---------------------------------------------------------------- rules

    /**
     * Why {@code module} cannot go into slot {@code slot} of {@code holder}, or null when it may: not a module holder,
     * not a module, wrong item type, tier too low, slot locked, already installed, already in the worn suit.
     *
     * @param others other worn pieces of the suit (one kind per suit); empty for tools, weapons and pieces not worn
     */
    @Nullable
    public static Component refusal(ItemStack holder, int slot, ItemStack module, List<ItemStack> others) {
        ModuleTarget target = target(holder);
        if (target == null) return Component.translatable("module.robotica.refuse.no_holder");
        if (!(module.getItem() instanceof ModuleItem m)) return Component.translatable("module.robotica.refuse.not_module");
        if (!m.kind.fits(target)) return m.kind.fitsLine();
        int tier = tier(holder);
        String unit = target.isArmor() ? "mark" : "age";
        if (tier < m.minTier()) return Component.translatable("module.robotica.refuse.tier_" + unit, m.minTier(), tier);
        if (slot >= slots(holder)) {
            int opens = tierForSlot(target, slot);
            return opens > 0 ? Component.translatable("module.robotica.refuse.locked_" + unit, opens) : Component.translatable("module.robotica.refuse.no_slot");
        }
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (i != slot && kind(holder, i) == m.kind) return Component.translatable("module.robotica.refuse.duplicate", m.kind.displayName());
        }
        if (target.isArmor() && !m.kind.perPiece()) {
            for (ItemStack other : others) {
                if (other == holder || other.isEmpty() || installed(other, m.kind) <= 0) continue;
                return Component.translatable("module.robotica.refuse.duplicate_suit", m.kind.displayName(), other.getHoverName());
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- display

    /** "Overclock II, Fortune III (off)" for tooltips, or null when nothing is installed. */
    @Nullable
    public static MutableComponent describe(ItemStack stack) {
        MutableComponent out = null;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!(module(stack, i).getItem() instanceof ModuleItem m)) continue;
            MutableComponent name = m.kind.displayName(m.level).copy();
            if (!works(stack, i)) name.append(Component.translatable("module.robotica.inactive")).withStyle(ChatFormatting.DARK_GRAY);
            else if (!enabled(stack, i)) name.append(Component.translatable("module.robotica.off")).withStyle(ChatFormatting.DARK_GRAY);
            else name.withStyle(ChatFormatting.AQUA);
            out = out == null ? Component.empty().append(name) : out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY)).append(name);
        }
        return out;
    }

    /** "Age 2" or "Mk2" of a holder. */
    public static Component tierName(ItemStack stack) {
        ModuleTarget target = target(stack);
        return Component.translatable(target != null && target.isArmor() ? "module.robotica.tier.mark" : "module.robotica.tier.age", tier(stack));
    }
}
