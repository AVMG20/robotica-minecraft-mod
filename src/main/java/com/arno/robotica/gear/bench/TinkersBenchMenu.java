package com.arno.robotica.gear.bench;

import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToggleKind;
import com.arno.robotica.gear.tool.ToolSettings;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tinker's Bench screen: one tool slot and one slot per module. The module slots are a view of the tool's
 * {@code gear_modules} component: putting a card in installs it (the card is used up), taking it out removes the module
 * and gives the card back. The tool returns to the player when the screen closes.
 */
public class TinkersBenchMenu extends MachineMenu {
    /** Module slot order. */
    public static final ToggleKind[] MODULES = {ToggleKind.AUTO_PICKUP, ToggleKind.VOID_FILTER};
    public static final int TOOL_X = 44, SLOT_Y = 35, MODULE_X = 98;

    private final ContainerLevelAccess access;
    private final SimpleContainer tools = new SimpleContainer(1);

    /** Client side. */
    public TinkersBenchMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public TinkersBenchMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(GearBlocks.TINKERS_BENCH_MENU.get(), id);
        this.access = access;
        addSlot(new Slot(tools, 0, TOOL_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsTool(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        Modules modules = new Modules();
        for (int i = 0; i < MODULES.length; i++) {
            ToggleKind kind = MODULES[i];
            addSlot(new Slot(modules, i, MODULE_X + i * 18, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return supports(kind) && stack.getItem() instanceof UpgradeCardItem card && card.getKind() == kind.module;
                }

                @Override
                public boolean isActive() {
                    return supports(kind);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addPlayerInventory(inv, 8, 84);
    }

    /** Power tools only (FE drills and chainsaw) with at least one module toggle. */
    public static boolean acceptsTool(ItemStack stack) {
        if (!(stack.getItem() instanceof GearToolItem tool) || !tool.spec.isEnergy()) return false;
        for (ToggleKind kind : MODULES) {
            if (tool.spec.toggles.contains(kind)) return true;
        }
        return false;
    }

    public ItemStack tool() {
        return tools.getItem(0);
    }

    /** True when the tool in the bench can take this module. */
    public boolean supports(ToggleKind kind) {
        return tool().getItem() instanceof GearToolItem tool && tool.spec.toggles.contains(kind);
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

    /** The module slots: reads and writes the modules of the tool in the bench, holds no items of its own. */
    private class Modules implements Container {
        private boolean installed(int slot) {
            return slot >= 0 && slot < MODULES.length && supports(MODULES[slot]) && ToolSettings.installed(tool(), MODULES[slot]);
        }

        private ItemStack card(int slot) {
            return new ItemStack(CoreItems.card(MODULES[slot].module).get());
        }

        @Override
        public int getContainerSize() {
            return MODULES.length;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < MODULES.length; i++) {
                if (installed(i)) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return installed(slot) ? card(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (amount <= 0 || !installed(slot)) return ItemStack.EMPTY;
            ToolSettings.setInstalled(tool(), MODULES[slot], false);
            tools.setChanged();
            return card(slot);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= MODULES.length || !supports(MODULES[slot])) return;
            ToolSettings.setInstalled(tool(), MODULES[slot], !stack.isEmpty());
            tools.setChanged();
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

        /** Never wipes the tool's modules. */
        @Override
        public void clearContent() {
        }
    }
}
