package com.arno.robotica.storage.block;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.EnergyUtil;
import com.arno.robotica.core.energy.MachineEnergyStorage;
import com.arno.robotica.storage.StorageConfig;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.item.StorageExpansionItem;
import com.arno.robotica.storage.menu.StorageMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Big sorted chest with a crafting grid. {@link #items} is a plain slot inventory of up to {@link #MAX_SLOTS} slots;
 * {@link #BASE_SLOTS} are always there, each Storage Expansion in the three expansion slots adds more.
 *
 * <p>Power rule: the terminal uses a little FE (idle drain plus a bit per expansion). Without power it takes no new
 * items, but everything stored can always be taken out and the GUI, search and crafting keep working. Nothing is ever
 * locked away.
 */
public class StorageTerminalBlockEntity extends BlockEntity implements MenuProvider, com.arno.robotica.compat.InfoSource {
    public static final int BASE_SLOTS = 81;
    /** Slots added by Mk1, Mk2, Mk3. One of each fits, so the maximum is 81 + 81 + 162 + 324 = 648 (12 double chests). */
    public static final int[] EXPANSION_SLOTS = {81, 162, 324};
    public static final int EXPANSION_BAYS = 3;
    public static final int MAX_SLOTS = BASE_SLOTS + 81 + 162 + 324;
    /** Ticks between power checks; the drain of that many ticks is taken at once. */
    private static final int POWER_PERIOD = 10;

    private int version;
    private boolean cacheDirty = true;
    private int usedCache;
    private int highestCache;
    private boolean signalDirty = true;
    private int lastSignal = -1;
    private int gridVersion;
    private boolean powered;

    public final ItemStackHandler items = new ItemStackHandler(MAX_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canStore(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            contentsChanged();
        }
    };

    public final ItemStackHandler upgrades = new ItemStackHandler(EXPANSION_BAYS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!(stack.getItem() instanceof StorageExpansionItem)) return false;
            for (int i = 0; i < getSlots(); i++) {
                if (i != slot && getStackInSlot(i).is(stack.getItem())) return false;
            }
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            upgradesChanged();
        }
    };

    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isBattery(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** The 3 x 3 crafting grid. Persistent: it keeps its contents when the GUI is closed. */
    public final NonNullList<ItemStack> craft = NonNullList.withSize(9, ItemStack.EMPTY);

    public final MachineEnergyStorage energy = new MachineEnergyStorage(StorageConfig.energyBuffer(), 2_000, 0, this::setChanged);

    private final IItemHandler access = new Access(false);
    private final IItemHandler restoreAccess = new Access(true);

    public StorageTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.TERMINAL_BE.get(), pos, state);
    }

    public static boolean isBattery(ItemStack stack) {
        if (stack.isEmpty()) return false;
        IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap != null && cap.canExtract();
    }

    // ------------------------------------------------------------------ capacity

    /** Slots the installed expansions add up to, plus the base. */
    public int capacity() {
        int cap = BASE_SLOTS;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (upgrades.getStackInSlot(i).getItem() instanceof StorageExpansionItem e) cap += e.slots();
        }
        return Math.min(MAX_SLOTS, cap);
    }

    public int expansionCount() {
        int n = 0;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (upgrades.getStackInSlot(i).getItem() instanceof StorageExpansionItem) n++;
        }
        return n;
    }

    /** Slots that take new items right now: all of them with power, none without. */
    public int activeCapacity() {
        return powered ? capacity() : 0;
    }

    public boolean isPowered() {
        return powered;
    }

    /** Jade: "no energy" while nothing new goes in, and how full the terminal is. */
    @Override
    public void collectInfo(net.minecraft.server.level.ServerLevel level, com.arno.robotica.compat.MachineInfo info) {
        if (!powered) info.status = "no_energy";
        if (capacity() > 0) info.progress = Math.min(100, usedSlots() * 100 / capacity());
    }

    /** FE per tick the terminal uses (before the global energy multiplier). */
    public int drainPerTick() {
        return CoreConfig.scaleEnergy(StorageConfig.idleFe() + StorageConfig.perExpansionFe() * expansionCount());
    }

    /** Number of non-empty slots. */
    public int usedSlots() {
        refreshCache();
        return usedCache;
    }

    /** Slots reachable from outside: the capacity, or more if stacks sit beyond it (never hides items). */
    private int usableSlots() {
        refreshCache();
        return Math.max(capacity(), highestCache + 1);
    }

    private void refreshCache() {
        if (!cacheDirty) return;
        int used = 0, highest = -1;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                used++;
                highest = i;
            }
        }
        usedCache = used;
        highestCache = highest;
        cacheDirty = false;
    }

    /** Bumps whenever the stored items change; menus use it to know when to rebuild their view. */
    public int version() {
        return version;
    }

    public int gridVersion() {
        return gridVersion;
    }

    public void gridChanged() {
        gridVersion++;
        setChanged();
    }

    private void contentsChanged() {
        version++;
        cacheDirty = true;
        signalDirty = true;
        setChanged();
    }

    private void upgradesChanged() {
        cacheDirty = true;
        signalDirty = true;
        compact();
        version++;
        setChanged();
    }

    /** After the capacity shrank: moves stacks that sit beyond it into free space below. */
    private void compact() {
        int cap = capacity();
        refreshCache();
        if (highestCache < cap) return;
        for (int i = cap; i < MAX_SLOTS; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = stack.copy();
            items.setStackInSlot(i, ItemStack.EMPTY);
            for (int j = 0; j < cap && !rest.isEmpty(); j++) rest = items.insertItem(j, rest, false);
            if (!rest.isEmpty()) items.setStackInSlot(i, rest);
        }
    }

    // ------------------------------------------------------------------ item access

    /** Every non-empty stack, for building the GUI view. */
    public List<ItemStack> snapshot() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /**
     * Whether a stack may go into a terminal (storage or crafting grid): not a carried terminal, a filled shulker box or a
     * filled bundle, so a carried terminal never holds storage inside storage.
     */
    public static boolean canStore(ItemStack stack) {
        if (stack.has(CoreComponents.CONTENTS.get())) return false;
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyStream().findAny().isPresent()) return false;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle == null || bundle.isEmpty();
    }

    /** Inserts into the slots that currently take new items. Returns what did not fit. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return ItemHandlerHelper.insertItemStacked(access, stack, simulate);
    }

    /**
     * Puts stacks back that were just taken out (a shift-click that did not fit into the player's inventory). Ignores the
     * power rule: that room was theirs a moment ago. Returns what did not fit.
     */
    public ItemStack restore(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return ItemHandlerHelper.insertItemStacked(restoreAccess, stack, false);
    }

    /**
     * Takes up to {@code amount} of {@code template} (same item and components) out, from the highest slots down so the
     * low slots stay full. Always works, with or without power. Returns what was really taken.
     */
    public ItemStack extract(ItemStack template, int amount) {
        if (template.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack out = ItemStack.EMPTY;
        int need = Math.min(amount, template.getMaxStackSize());
        for (int i = MAX_SLOTS - 1; i >= 0 && need > 0; i--) {
            ItemStack in = items.getStackInSlot(i);
            if (in.isEmpty() || !ItemStack.isSameItemSameComponents(in, template)) continue;
            ItemStack taken = items.extractItem(i, need, false);
            if (taken.isEmpty()) continue;
            if (out.isEmpty()) out = taken;
            else out.grow(taken.getCount());
            need -= taken.getCount();
        }
        return out;
    }

    /** A stored stack of this item with any components (a worn tool, a charged item), or empty. */
    public ItemStack findSameItem(ItemStack template) {
        for (int i = MAX_SLOTS - 1; i >= 0; i--) {
            ItemStack in = items.getStackInSlot(i);
            if (!in.isEmpty() && ItemStack.isSameItem(in, template)) return in.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    /** Total count of an item in the terminal. */
    public int count(ItemStack template) {
        int n = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack in = items.getStackInSlot(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, template)) n += in.getCount();
        }
        return n;
    }

    /** The item handler other blocks and mods see (all sides). */
    public IItemHandler access() {
        return access;
    }

    private final class Access implements IItemHandler {
        private final boolean ignorePower;

        Access(boolean ignorePower) {
            this.ignorePower = ignorePower;
        }

        private int insertLimit() {
            return ignorePower ? capacity() : activeCapacity();
        }

        @Override
        public int getSlots() {
            return usableSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < 0 || slot >= MAX_SLOTS ? ItemStack.EMPTY : items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= insertLimit()) return stack;
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= MAX_SLOTS) return ItemStack.EMPTY;
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(Mth.clamp(slot, 0, MAX_SLOTS - 1));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < insertLimit() && items.isItemValid(slot, stack);
        }
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, StorageTerminalBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.asLong()) % POWER_PERIOD == 0) updatePower(level, pos, state);
        if (signalDirty) {
            signalDirty = false;
            int signal = signal();
            if (signal != lastSignal) {
                lastSignal = signal;
                level.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }
    }

    /** Runs the power check now instead of on the next period (game tests). */
    public void updatePowerNow() {
        if (level != null) updatePower(level, worldPosition, getBlockState());
    }

    private void updatePower(Level level, BlockPos pos, BlockState state) {
        long cost = (long) drainPerTick() * POWER_PERIOD;
        ItemStack cell = battery.getStackInSlot(0);
        if (!cell.isEmpty() && energy.getSpace() > 0) {
            EnergyUtil.dischargeItem(cell, energy, 2_000);
        }
        boolean ok = cost <= 0 || energy.consume((int) Math.min(cost, Integer.MAX_VALUE));
        if (ok != powered) {
            powered = ok;
            setChanged();
            if (state.hasProperty(StorageTerminalBlock.LIT) && state.getValue(StorageTerminalBlock.LIT) != ok) {
                level.setBlock(pos, state.setValue(StorageTerminalBlock.LIT, ok), Block.UPDATE_ALL);
            }
        }
    }

    /** Comparator signal: how full the slots are (0 only when empty, 15 only when completely full). */
    public int signal() {
        float fill = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) fill += (float) s.getCount() / Math.min(items.getSlotLimit(i), s.getMaxStackSize());
        }
        fill /= capacity();
        return Mth.lerpDiscrete(fill, 0, 15);
    }

    // ------------------------------------------------------------------ misc

    /** Anything inside (items, expansions, battery, crafting grid or FE): the dropped terminal keeps it all. */
    public boolean hasContents() {
        refreshCache();
        if (usedCache > 0 || energy.getEnergyStored() > 0) return true;
        for (int i = 0; i < upgrades.getSlots(); i++) if (!upgrades.getStackInSlot(i).isEmpty()) return true;
        if (!battery.getStackInSlot(0).isEmpty()) return true;
        for (ItemStack s : craft) if (!s.isEmpty()) return true;
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.robotica.storage_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StorageMenu(id, inv, this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level != null && hasContents()) {
            CompoundTag contents = new CompoundTag();
            writeContents(contents, level.registryAccess());
            components.set(CoreComponents.CONTENTS.get(), contents);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        CompoundTag contents = input.get(CoreComponents.CONTENTS.get());
        if (contents != null && level != null) {
            readContents(contents, level.registryAccess());
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
        tag.putBoolean("powered", powered);
    }

    /** Everything inside: saved with the block, and kept by the dropped terminal item. */
    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("items", items.serializeNBT(registries));
        tag.put("expansions", upgrades.serializeNBT(registries));
        tag.put("battery", battery.serializeNBT(registries));
        CompoundTag grid = new CompoundTag();
        ContainerHelper.saveAllItems(grid, craft, registries);
        tag.put("craft", grid);
        tag.put("energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readContents(tag, registries);
        powered = tag.getBoolean("powered");
    }

    private void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        if (tag.contains("expansions")) upgrades.deserializeNBT(registries, tag.getCompound("expansions"));
        if (tag.contains("battery")) battery.deserializeNBT(registries, tag.getCompound("battery"));
        craft.clear();
        if (tag.contains("craft")) ContainerHelper.loadAllItems(tag.getCompound("craft"), craft, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        cacheDirty = true;
        signalDirty = true;
        version++;
        gridVersion++;
    }
}
