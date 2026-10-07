package com.arno.robotica.gear.bench;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.GearBlocks;
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

import java.util.List;

/**
 * Tinker's Bench screen: one slot for a power tool, FE weapon or Exo armor piece and five module slots, of which the
 * item's Age or Mk opens some ({@link Modules#slots}). The module slots are a view of the item's installed modules:
 * putting a module in installs it (used up), taking it out gives it back. Anything the item cannot take is refused with
 * a reason ({@link Modules#refusal}). The bench stores nothing: the item returns to the player when the screen closes.
 */
public class TinkersBenchMenu extends MachineMenu {
    public static final int TOOL_SLOT = 0, FIRST_MODULE = 1, MACHINE_SLOTS = FIRST_MODULE + Modules.MAX_SLOTS;
    public static final int TOOL_X = 17, TOOL_Y = 35, MODULE_X = 62, MODULE_Y = 35;

    private final ContainerLevelAccess access;
    private final SimpleContainer tools = new SimpleContainer(1);

    /** Client side. */
    public TinkersBenchMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public TinkersBenchMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(GearBlocks.TINKERS_BENCH_MENU.get(), id);
        this.access = access;
        addSlot(new Slot(tools, 0, TOOL_X, TOOL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Modules.accepts(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        Installed installed = new Installed();
        for (int i = 0; i < Modules.MAX_SLOTS; i++) addSlot(new ModuleSlot(installed, i));
        addPlayerInventory(inv, 8, 84);
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

    /** A module slot: opens with the item's tier, takes modules that fit (see {@link Modules#refusal}). */
    public class ModuleSlot extends Slot {
        public final int module;

        ModuleSlot(Container container, int module) {
            super(container, module, MODULE_X + module * 18, MODULE_Y);
            this.module = module;
        }

        /** Why the stack cannot go here, or null. A piece in the bench is not worn, so the one-per-suit rule waits for the J screen. */
        @Nullable
        public Component refusal(ItemStack stack) {
            return Modules.refusal(tool(), module, stack, List.of());
        }

        public boolean locked() {
            return module >= Modules.slots(tool());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
        }

        @Override
        public boolean isActive() {
            return Modules.accepts(tool());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The module slots: reads and writes the item in the bench, holds nothing of its own. */
    private class Installed implements Container {
        @Override
        public int getContainerSize() {
            return Modules.MAX_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < Modules.MAX_SLOTS; i++) {
                if (!getItem(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            if (!Modules.accepts(tool())) return ItemStack.EMPTY;
            return Modules.module(tool(), slot).copy();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (amount <= 0 || current.isEmpty()) return ItemStack.EMPTY;
            Modules.setModule(tool(), slot, ItemStack.EMPTY);
            tools.setChanged();
            return current;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!Modules.accepts(tool()) || slot < 0 || slot >= Modules.MAX_SLOTS) return;
            boolean wasEmpty = Modules.module(tool(), slot).isEmpty();
            Modules.setModule(tool(), slot, stack);
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
