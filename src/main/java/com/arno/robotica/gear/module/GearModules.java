package com.arno.robotica.gear.module;

import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.tool.ToggleKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

/**
 * What is installed in a power tool or FE weapon, and the install rules of the Tinker's Bench. Safe on both sides.
 *
 * <p>Layout of the {@code gear_installed} component: index 0 Auto-Pickup card, 1 Void Filter card (power tools only;
 * cards do not use a module slot), 2-5 module slots. How many module slots work depends on the Age of the item
 * ({@link GearConfig#moduleSlots}). Tools from 0.3 stored their cards as bits in {@code gear_modules};
 * {@link #migrate} moves them over and every read falls back to the old bits until then.
 */
public final class GearModules {
    private GearModules() {}

    public static final int CARD_PICKUP = 0, CARD_VOID = 1, FIRST_MODULE = 2, MAX_MODULES = 4, SIZE = FIRST_MODULE + MAX_MODULES;

    // ---------------------------------------------------------------- raw access

    /** The installed stack at a layout index (never modify the result), or EMPTY. */
    public static ItemStack get(ItemStack tool, int index) {
        ItemContainerContents contents = tool.get(GearComponents.INSTALLED.get());
        if (contents == null || index < 0 || index >= contents.getSlots()) return ItemStack.EMPTY;
        return contents.getStackInSlot(index);
    }

    public static void set(ItemStack tool, int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) return;
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = i == index ? (stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)) : get(tool, i).copy();
            list.set(i, s);
            any |= !s.isEmpty();
        }
        if (any) tool.set(GearComponents.INSTALLED.get(), ItemContainerContents.fromItems(list));
        else tool.remove(GearComponents.INSTALLED.get());
    }

    // ---------------------------------------------------------------- cards (Auto-Pickup, Void Filter)

    /** Layout index of a card toggle, -1 for plain settings. */
    public static int cardIndex(ToggleKind kind) {
        return switch (kind) {
            case AUTO_PICKUP -> CARD_PICKUP;
            case VOID_FILTER -> CARD_VOID;
            default -> -1;
        };
    }

    public static boolean cardInstalled(ItemStack tool, ToggleKind kind) {
        int index = cardIndex(kind);
        if (index < 0) return false;
        if (!get(tool, index).isEmpty()) return true;
        return (tool.getOrDefault(GearComponents.MODULES.get(), 0) & kind.bit) != 0;
    }

    public static void setCard(ItemStack tool, ToggleKind kind, boolean on) {
        int index = cardIndex(kind);
        if (index < 0 || kind.module == null) return;
        migrate(tool);
        set(tool, index, on ? new ItemStack(CoreItems.card(kind.module).get()) : ItemStack.EMPTY);
    }

    /** Power tools have the two card slots; weapons do not. */
    public static boolean hasCardSlots(ItemStack tool) {
        return GearCategory.of(tool).isTool();
    }

    /**
     * Moves cards installed by 0.3 (bits in {@code gear_modules}) into the new layout. Returns true when it changed the
     * stack. Called when a tool ticks in an inventory and when it goes into the bench.
     */
    public static boolean migrate(ItemStack tool) {
        Integer legacy = tool.get(GearComponents.MODULES.get());
        if (legacy == null) return false;
        tool.remove(GearComponents.MODULES.get());
        for (ToggleKind kind : new ToggleKind[]{ToggleKind.AUTO_PICKUP, ToggleKind.VOID_FILTER}) {
            if ((legacy & kind.bit) != 0 && get(tool, cardIndex(kind)).isEmpty()) {
                set(tool, cardIndex(kind), new ItemStack(CoreItems.card(kind.module).get()));
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- modules

    /** Module slots that work on this item (0 for items that take no modules). */
    public static int slots(ItemStack tool) {
        if (GearCategory.of(tool) == GearCategory.NONE) return 0;
        return Math.min(MAX_MODULES, GearConfig.moduleSlots(GearCategory.age(tool)));
    }

    /** True for items the Tinker's Bench accepts. */
    public static boolean acceptsModules(ItemStack tool) {
        return GearCategory.of(tool) != GearCategory.NONE;
    }

    /** Lowest Age whose items have more than {@code slot} module slots, or 0 when no Age does. */
    public static int ageForSlot(int slot) {
        for (int age = 1; age <= 4; age++) {
            if (GearConfig.moduleSlots(age) > slot) return age;
        }
        return 0;
    }

    public static ItemStack module(ItemStack tool, int slot) {
        return slot < 0 || slot >= MAX_MODULES ? ItemStack.EMPTY : get(tool, FIRST_MODULE + slot);
    }

    public static void setModule(ItemStack tool, int slot, ItemStack module) {
        if (slot >= 0 && slot < MAX_MODULES) set(tool, FIRST_MODULE + slot, module);
    }

    /** True when the module in this slot may work: inside the item's slot count, fitting the item, Age high enough. */
    public static boolean works(ItemStack tool, int slot) {
        return slot < slots(tool) && module(tool, slot).getItem() instanceof GearModuleItem m
                && m.kind.fits(GearCategory.of(tool)) && GearCategory.age(tool) >= m.minAge();
    }

    /** Highest installed level of a kind that may work, 0 when absent (on or off). */
    public static int level(ItemStack tool, GearModuleKind kind) {
        int best = 0;
        for (int i = 0; i < MAX_MODULES; i++) {
            if (module(tool, i).getItem() instanceof GearModuleItem m && m.kind == kind && works(tool, i)) best = Math.max(best, m.level);
        }
        return best;
    }

    public static boolean enabled(ItemStack tool, GearModuleKind kind) {
        return (tool.getOrDefault(GearComponents.MODULES_OFF.get(), 0) & (1 << kind.ordinal())) == 0;
    }

    public static void setEnabled(ItemStack tool, GearModuleKind kind, boolean on) {
        int mask = tool.getOrDefault(GearComponents.MODULES_OFF.get(), 0);
        mask = on ? mask & ~(1 << kind.ordinal()) : mask | (1 << kind.ordinal());
        if (mask == 0) tool.remove(GearComponents.MODULES_OFF.get());
        else tool.set(GearComponents.MODULES_OFF.get(), mask);
    }

    /** Level of a kind that is installed, working and switched on; 0 otherwise. */
    public static int active(ItemStack tool, GearModuleKind kind) {
        return enabled(tool, kind) ? level(tool, kind) : 0;
    }

    /**
     * Why {@code stack} cannot go into module slot {@code slot} of {@code tool}, or null when it may. The bench refuses
     * with this text: wrong item, wrong tool type, Age too low, slot locked, already installed.
     */
    @Nullable
    public static Component refusal(ItemStack tool, int slot, ItemStack stack) {
        GearCategory category = GearCategory.of(tool);
        if (category == GearCategory.NONE) return Component.translatable("gear.robotica.refuse.no_tool");
        if (stack.getItem() instanceof UpgradeCardItem) return Component.translatable("gear.robotica.refuse.card");
        if (!(stack.getItem() instanceof GearModuleItem module)) return Component.translatable("gear.robotica.refuse.not_module");
        if (!module.kind.fits(category)) return module.kind.fitsLine();
        int age = GearCategory.age(tool);
        if (age < module.minAge()) return Component.translatable("gear.robotica.refuse.age", module.minAge(), age);
        if (slot >= slots(tool)) {
            int opens = ageForSlot(slot);
            return opens > 0 ? Component.translatable("gear.robotica.refuse.locked", opens) : Component.translatable("gear.robotica.refuse.no_slot");
        }
        for (int i = 0; i < MAX_MODULES; i++) {
            if (i != slot && module(tool, i).getItem() instanceof GearModuleItem other && other.kind == module.kind) {
                return Component.translatable("gear.robotica.refuse.duplicate", module.kind.displayName());
            }
        }
        return null;
    }

    /** Why {@code stack} cannot go into the card slot of {@code kind}, or null when it may. */
    @Nullable
    public static Component cardRefusal(ItemStack tool, ToggleKind kind, ItemStack stack) {
        if (!hasCardSlots(tool)) return Component.translatable("gear.robotica.refuse.cards_tools_only");
        if (stack.getItem() instanceof UpgradeCardItem card && card.getKind() == kind.module) return null;
        return Component.translatable("gear.robotica.refuse.wrong_card", CoreItems.card(kind.module).get().getDescription());
    }

    // ---------------------------------------------------------------- display

    /** "Auto-Pickup Upgrade, Armor Pierce II (off)" for tooltips, or null when nothing is installed. */
    @Nullable
    public static MutableComponent describe(ItemStack tool) {
        MutableComponent out = null;
        for (ToggleKind kind : new ToggleKind[]{ToggleKind.AUTO_PICKUP, ToggleKind.VOID_FILTER}) {
            if (!cardInstalled(tool, kind)) continue;
            out = append(out, CoreItems.card(kind.module).get().getDescription().copy().withStyle(ChatFormatting.DARK_AQUA));
        }
        for (int i = 0; i < MAX_MODULES; i++) {
            if (!(module(tool, i).getItem() instanceof GearModuleItem m)) continue;
            MutableComponent name = m.kind.displayName(m.level).copy();
            if (!works(tool, i)) name.append(Component.translatable("gear.robotica.module.inactive")).withStyle(ChatFormatting.DARK_GRAY);
            else if (!enabled(tool, m.kind)) name.append(Component.translatable("gear.robotica.module.off")).withStyle(ChatFormatting.DARK_GRAY);
            else name.withStyle(ChatFormatting.AQUA);
            out = append(out, name);
        }
        return out;
    }

    private static MutableComponent append(@Nullable MutableComponent list, Component part) {
        if (list == null) return Component.empty().append(part);
        return list.append(Component.literal(", ").withStyle(ChatFormatting.GRAY)).append(part);
    }
}
