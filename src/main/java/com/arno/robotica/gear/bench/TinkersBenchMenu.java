package com.arno.robotica.gear.bench;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.module.GearModules;
import com.arno.robotica.gear.tool.ToggleKind;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tinker's Bench screen: one slot for a power tool or FE weapon, two card slots (Auto-Pickup, Void Filter; power tools
 * only, they use no module slot) and four module slots, of which the item's Age opens 1 to 4. The card and module slots
 * are a view of the item's {@code gear_installed} component: putting something in installs it (used up), taking it out
 * gives it back. Anything the item cannot take is refused with a reason ({@link GearModules#refusal}). The bench stores
 * nothing: the item returns to the player when the screen closes.
 */
public class TinkersBenchMenu extends MachineMenu {
    /** Card slot order. */
    public static final ToggleKind[] CARDS = {ToggleKind.AUTO_PICKUP, ToggleKind.VOID_FILTER};
    public static final int TOOL_SLOT = 0, FIRST_CARD = 1, FIRST_MODULE = 1 + CARDS.length, MACHINE_SLOTS = FIRST_MODULE + GearModules.MAX_MODULES;
    public static final int TOOL_X = 17, TOOL_Y = 35, CARD_X = 62, CARD_Y = 24, MODULE_X = 98, MODULE_Y = 35;

    private final ContainerLevelAccess access;
    private final SimpleContainer tools = new SimpleContainer(1);

    /** Client side. */
    public TinkersBenchMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public TinkersBenchMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(GearBlocks.TINKERS_BENCH_MENU.get(), id);
        this.access = access;
        tools.addListener(c -> GearModules.migrate(tools.getItem(0)));
        addSlot(new Slot(tools, 0, TOOL_X, TOOL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsTool(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        Installed installed = new Installed();
        for (int i = 0; i < CARDS.length; i++) addSlot(new CardSlot(installed, i, CARDS[i]));
        for (int i = 0; i < GearModules.MAX_MODULES; i++) addSlot(new ModuleSlot(installed, i));
        addPlayerInventory(inv, 8, 84);
    }

    /** Power tools (FE drills and the Chainsaw) and FE weapons. */
    public static boolean acceptsTool(ItemStack stack) {
        return GearModules.acceptsModules(stack);
    }

    public ItemStack tool() {
        return tools.getItem(0);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, GearBlocks.TINKERS_BENCH.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, tools));
    }

    private void installed() {
        tools.setChanged();
        access.execute((level, pos) -> CoreSounds.play(level, pos, CoreSounds.UPGRADE_INSTALL, SoundSource.BLOCKS, 0.6F, 1.1F));
    }

    /** A card slot: only the matching card, only on power tools. */
    public class CardSlot extends Slot {
        public final ToggleKind kind;

        CardSlot(Container container, int index, ToggleKind kind) {
            super(container, index, CARD_X, CARD_Y + index * 20);
            this.kind = kind;
        }

        @Nullable
        public Component refusal(ItemStack stack) {
            return GearModules.cardRefusal(tool(), kind, stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
        }

        @Override
        public boolean isActive() {
            return GearModules.hasCardSlots(tool());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** A module slot: opens with the item's Age, takes modules that fit (see {@link GearModules#refusal}). */
    public class ModuleSlot extends Slot {
        public final int module;

        ModuleSlot(Container container, int module) {
            super(container, GearModules.FIRST_MODULE + module, MODULE_X + module * 18, MODULE_Y);
            this.module = module;
        }

        @Nullable
        public Component refusal(ItemStack stack) {
            return GearModules.refusal(tool(), module, stack);
        }

        public boolean locked() {
            return module >= GearModules.slots(tool());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
        }

        @Override
        public boolean isActive() {
            return acceptsTool(tool());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The card and module slots: reads and writes the item in the bench, holds nothing of its own. */
    private class Installed implements Container {
        @Override
        public int getContainerSize() {
            return GearModules.SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < GearModules.SIZE; i++) {
                if (!getItem(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            if (!acceptsTool(tool())) return ItemStack.EMPTY;
            if (slot == GearModules.CARD_PICKUP || slot == GearModules.CARD_VOID) {
                ToggleKind kind = CARDS[slot];
                if (!GearModules.hasCardSlots(tool()) || !GearModules.cardInstalled(tool(), kind)) return ItemStack.EMPTY;
                return new ItemStack(CoreItems.card(kind.module).get());
            }
            return GearModules.get(tool(), slot).copy();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (amount <= 0 || current.isEmpty()) return ItemStack.EMPTY;
            GearModules.migrate(tool());
            GearModules.set(tool(), slot, ItemStack.EMPTY);
            tools.setChanged();
            return current;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!acceptsTool(tool()) || slot < 0 || slot >= GearModules.SIZE) return;
            GearModules.migrate(tool());
            boolean wasEmpty = GearModules.get(tool(), slot).isEmpty();
            GearModules.set(tool(), slot, stack);
            if (wasEmpty && !stack.isEmpty()) installed();
            else tools.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        /** Never wipes the item's modules. */
        @Override
        public void clearContent() {
        }
    }
}
