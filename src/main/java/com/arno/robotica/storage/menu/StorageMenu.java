package com.arno.robotica.storage.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.block.StorageTerminalBlockEntity;
import com.arno.robotica.storage.item.StorageExpansionItem;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Menu of the Storage Terminal.
 *
 * <p>The 9 x 6 grid is a window of {@value #VIEW_SLOTS} "view slots" onto a sorted, compacted, filtered picture of the
 * inventory ({@link StorageView}). View slots are not real storage: every click on them is handled in
 * {@link #clicked} on the server against the real inventory (extract / insert), so nothing can be duplicated. The
 * client never changes the inventory itself; it only sends scroll, sort and search text ({@code StorageViewPayload}).
 *
 * <p>Slot order: result (0), crafting grid (1-9), view (10-63), expansions (64-66), battery (67), player (68-103).
 */
public class StorageMenu extends MachineMenu {
    public static final int COLS = 9;
    public static final int ROWS = 6;
    public static final int VIEW_SLOTS = COLS * ROWS;

    public static final int RESULT = 0;
    public static final int GRID_START = 1;
    public static final int GRID_END = 10;
    public static final int VIEW_START = 10;
    public static final int VIEW_END = VIEW_START + VIEW_SLOTS;
    public static final int EXPANSION_START = VIEW_END;
    public static final int EXPANSION_END = EXPANSION_START + StorageTerminalBlockEntity.EXPANSION_BAYS;
    public static final int BATTERY = EXPANSION_END;
    public static final int PLAYER_START = BATTERY + 1;
    public static final int PLAYER_END = PLAYER_START + 36;
    /**
     * Changes that did not come from this menu (hoppers, pipes, another player) rebuild the picture at most this often,
     * so a terminal fed every tick does not sort its whole inventory every tick for every viewer. Own clicks and view
     * changes rebuild right away. A stale picture is harmless: clicks are resolved against the real inventory.
     */
    public static final int EXTERNAL_REFRESH_TICKS = 4;

    // Layout (relative to the GUI corner), shared with the screen.
    public static final int VIEW_X = 8, VIEW_Y = 18;
    public static final int INV_X = 8, INV_Y = 144;
    public static final int CRAFT_X = 196, CRAFT_Y = 28;
    public static final int RESULT_X = 282, RESULT_Y = 46;
    public static final int EXPANSION_X = 196, EXPANSION_Y = 100;
    public static final int BATTERY_X = 258, BATTERY_Y = 100;

    /** Null on the client: the client only mirrors what the server syncs. */
    @Nullable
    private final StorageTerminalBlockEntity be;
    private final Player player;
    private final SimpleContainer view = new SimpleContainer(VIEW_SLOTS);
    private final Grid grid;
    private final ResultContainer result = new ResultContainer();

    private String filter = "";
    private StorageView.Sort sort = StorageView.Sort.NAME;
    private int scrollRow;
    private int viewTotal;
    private boolean viewDirty = true;
    private int viewVersion = -1;
    private long lastRefresh = -EXTERNAL_REFRESH_TICKS;
    private int seenGridVersion = -1;

    private final int idxEnergy, idxEnergyMax, idxUsed, idxCapacity, idxPowered, idxViewTotal, idxDrain;

    /** Client side. */
    public StorageMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (StorageTerminalBlockEntity) null);
        buf.readBlockPos();
    }

    /** Server side ({@code be} non-null), or client side with null. */
    public StorageMenu(int id, Inventory inv, @Nullable StorageTerminalBlockEntity be) {
        super(StorageContent.MENU.get(), id);
        this.be = be;
        this.player = inv.player;
        this.grid = new Grid(be != null ? be.craft : NonNullList.withSize(9, ItemStack.EMPTY));

        addSlot(new ResultSlot(inv.player, grid, result, 0, RESULT_X, RESULT_Y));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) addSlot(new GridSlot(grid, c + r * 3, CRAFT_X + c * 18, CRAFT_Y + r * 18));
        }
        for (int i = 0; i < VIEW_SLOTS; i++) {
            addSlot(new ViewSlot(view, i, VIEW_X + (i % COLS) * 18, VIEW_Y + (i / COLS) * 18));
        }
        ItemStackHandler expansions = be != null ? be.upgrades : new ItemStackHandler(StorageTerminalBlockEntity.EXPANSION_BAYS);
        for (int i = 0; i < StorageTerminalBlockEntity.EXPANSION_BAYS; i++) {
            addSlot(new ExpansionSlot(expansions, i, EXPANSION_X + i * 18, EXPANSION_Y));
        }
        addSlot(new BatterySlot(be != null ? be.battery : new ItemStackHandler(1), 0, BATTERY_X, BATTERY_Y));
        addPlayerInventory(inv, INV_X, INV_Y);

        idxEnergy = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        idxEnergyMax = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        idxUsed = track(be == null ? () -> 0 : be::usedSlots);
        idxCapacity = track(be == null ? () -> 0 : be::capacity);
        idxPowered = track(be == null ? () -> 0 : () -> be.isPowered() ? 1 : 0);
        idxViewTotal = track(be == null ? () -> 0 : () -> viewTotal);
        idxDrain = track(be == null ? () -> 0 : be::drainPerTick);
        // so the very first sync to the client already carries the picture
        refreshView();
        updateResult();
    }

    // ------------------------------------------------------------------ synced values (client reads these)

    public int energy() {
        return synced(idxEnergy);
    }

    public int energyMax() {
        return synced(idxEnergyMax);
    }

    /** Non-empty terminal slots. */
    public int used() {
        return synced(idxUsed);
    }

    /** Total slots of the terminal (base plus expansions). */
    public int capacity() {
        return synced(idxCapacity);
    }

    public boolean powered() {
        return synced(idxPowered) != 0;
    }

    /** Number of entries in the current (filtered) view, in stacks. */
    public int viewTotal() {
        return synced(idxViewTotal);
    }

    public int drain() {
        return synced(idxDrain);
    }

    public int maxScroll() {
        return Math.max(0, (viewTotal() + COLS - 1) / COLS - ROWS);
    }

    public ItemStack resultItem() {
        return slots.get(RESULT).getItem();
    }

    public String filter() {
        return filter;
    }

    public StorageView.Sort sort() {
        return sort;
    }

    public int scrollRow() {
        return scrollRow;
    }

    /** Sets what the view shows. On the server this is validated and the view is rebuilt on the next tick. */
    public void setView(int scrollRow, StorageView.Sort sort, String filter) {
        String f = filter == null ? "" : filter;
        if (f.length() > StorageView.MAX_FILTER) f = f.substring(0, StorageView.MAX_FILTER);
        this.filter = f;
        this.sort = sort == null ? StorageView.Sort.NAME : sort;
        this.scrollRow = Math.max(0, Math.min(scrollRow, 100_000));
        viewDirty = true;
    }

    // ------------------------------------------------------------------ view (server)

    /** Rebuilds the view slots from the real inventory. Server only. */
    public void refreshView() {
        if (be == null) return;
        viewDirty = false;
        viewVersion = be.version();
        if (be.getLevel() != null) lastRefresh = be.getLevel().getGameTime();
        List<ItemStack> list = StorageView.build(be.snapshot(), filter, sort);
        viewTotal = list.size();
        int maxScroll = Math.max(0, (viewTotal + COLS - 1) / COLS - ROWS);
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll);
        for (int i = 0; i < VIEW_SLOTS; i++) {
            int index = scrollRow * COLS + i;
            view.setItem(i, index < viewTotal ? list.get(index) : ItemStack.EMPTY);
        }
    }

    /** The picture of the inventory as it is now (what the view slots hold), for tests. */
    public ItemStack viewStack(int index) {
        return view.getItem(index);
    }

    private void refreshIfStale() {
        if (be == null) return;
        if (viewDirty) {
            refreshView();
        } else if (viewVersion != be.version()) {
            long now = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
            if (now - lastRefresh >= EXTERNAL_REFRESH_TICKS) refreshView();
        }
        if (seenGridVersion != be.gridVersion()) updateResult();
    }

    @Override
    public void broadcastChanges() {
        refreshIfStale();
        super.broadcastChanges();
    }

    @Override
    public void broadcastFullState() {
        refreshIfStale();
        super.broadcastFullState();
    }

    // ------------------------------------------------------------------ crafting

    /** Recomputes the crafting result from the grid. Server only. */
    private void updateResult() {
        if (be == null || !(player instanceof ServerPlayer sp)) return;
        seenGridVersion = be.gridVersion();
        Level level = sp.level();
        CraftingInput input = grid.asCraftInput();
        ItemStack out = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = found.get();
            if (result.setRecipeUsed(level, sp, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) out = assembled;
            }
        }
        result.setItem(0, out);
    }

    /**
     * Fills the grid for a recipe (JEI transfer): what is in the grid goes back first, then each slot takes the first
     * accepted item it can find, from the terminal and then the player's inventory. {@code max} fills as many crafts as
     * the items allow (one stack at most), otherwise one craft. Missing items just leave their slot empty. Server only.
     */
    public void fillGrid(List<List<ItemStack>> wanted, boolean max) {
        if (be == null || wanted.size() > 9) return;
        for (int i = 0; i < 9; i++) {
            ItemStack old = grid.removeItemNoUpdate(i);
            if (old.isEmpty()) continue;
            intoTerminalThenPlayer(old);
            if (!old.isEmpty()) player.drop(old, false);
        }
        List<ItemStack> items = grid.getItems();
        for (int i = 0; i < wanted.size(); i++) {
            for (ItemStack option : wanted.get(i)) {
                if (!StorageTerminalBlockEntity.canStore(option)) continue;
                ItemStack got = take(option, 1);
                if (!got.isEmpty()) {
                    items.set(i, got);
                    break;
                }
            }
        }
        // Shift: add one more craft at a time while every filled slot can grow, so the slots stay even.
        while (max) {
            ItemStack[] round = new ItemStack[9];
            boolean any = false, complete = true;
            for (int i = 0; i < 9 && complete; i++) {
                ItemStack in = items.get(i);
                if (in.isEmpty()) continue;
                any = true;
                round[i] = in.getCount() < in.getMaxStackSize() ? take(in, 1) : ItemStack.EMPTY;
                complete = !round[i].isEmpty();
            }
            complete &= any;
            for (int i = 0; i < 9; i++) {
                if (round[i] == null || round[i].isEmpty()) continue;
                if (complete) {
                    items.get(i).grow(1);
                } else {
                    intoTerminalThenPlayer(round[i]);
                    if (!round[i].isEmpty()) player.drop(round[i], false);
                }
            }
            if (!complete) break;
        }
        grid.setChanged();
        viewDirty = true;
    }

    /** Up to {@code amount} of the item, from the terminal first, then from the player's inventory. */
    private ItemStack take(ItemStack template, int amount) {
        ItemStack got = be.extract(template, amount);
        int need = Math.min(amount, template.getMaxStackSize()) - got.getCount();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size() && need > 0; i++) {
            ItemStack in = inv.items.get(i);
            if (in.isEmpty() || !ItemStack.isSameItemSameComponents(in, template)) continue;
            ItemStack part = in.split(need);
            need -= part.getCount();
            if (got.isEmpty()) got = part;
            else got.grow(part.getCount());
        }
        return got;
    }

    /** The persistent 3 x 3 grid of the block entity (a throwaway one on the client). */
    /** A crafting grid slot: the grid travels with a carried terminal, so it takes what the storage takes. */
    private static final class GridSlot extends Slot {
        GridSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return StorageTerminalBlockEntity.canStore(stack);
        }
    }

    private final class Grid implements CraftingContainer {
        private final NonNullList<ItemStack> list;

        Grid(NonNullList<ItemStack> list) {
            this.list = list;
        }

        @Override
        public int getContainerSize() {
            return 9;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : list) if (!s.isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < 9 ? list.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack out = ContainerHelper.removeItem(list, slot, amount);
            if (!out.isEmpty()) setChanged();
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(list, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            list.set(slot, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            if (be != null) be.gridChanged();
            updateResult();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            list.clear();
            setChanged();
        }

        @Override
        public int getWidth() {
            return 3;
        }

        @Override
        public int getHeight() {
            return 3;
        }

        @Override
        public List<ItemStack> getItems() {
            return list;
        }

        @Override
        public void fillStackedContents(net.minecraft.world.entity.player.StackedContents contents) {
            for (ItemStack s : list) contents.accountSimpleStack(s);
        }
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= VIEW_START && slotId < VIEW_END && type != ClickType.QUICK_CRAFT) {
            // View slots are a picture: handled against the real inventory, on the server only.
            if (be != null && player instanceof ServerPlayer) {
                clickView(slotId - VIEW_START, button, type, player);
                viewDirty = true;
            }
            return;
        }
        if (be != null && seenGridVersion != be.gridVersion()) updateResult();
        super.clicked(slotId, button, type, player);
        // the player's own click may have moved items in or out: show it right away
        if (be != null) viewDirty = true;
    }

    private void clickView(int index, int button, ClickType type, Player player) {
        ItemStack shown = view.getItem(index);
        ItemStack carried = getCarried();
        switch (type) {
            case PICKUP -> {
                if (button != 0 && button != 1) return;
                if (carried.isEmpty()) {
                    if (shown.isEmpty()) return;
                    int want = button == 0 ? shown.getCount() : (shown.getCount() + 1) / 2;
                    ItemStack taken = be.extract(shown, want);
                    if (!taken.isEmpty()) setCarried(taken);
                } else if (button == 0) {
                    setCarried(be.insert(carried, false));
                } else {
                    ItemStack rest = be.insert(carried.copyWithCount(1), false);
                    if (rest.isEmpty()) {
                        carried.shrink(1);
                        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    }
                }
            }
            case QUICK_MOVE -> {
                if (shown.isEmpty()) return;
                ItemStack taken = be.extract(shown, shown.getCount());
                if (taken.isEmpty()) return;
                moveItemStackTo(taken, PLAYER_START, PLAYER_END, true);
                giveBack(player, taken);
            }
            case THROW -> {
                if (!carried.isEmpty() || shown.isEmpty()) return;
                ItemStack taken = be.extract(shown, button == 0 ? 1 : shown.getCount());
                if (!taken.isEmpty()) player.drop(taken, true);
            }
            case SWAP -> {
                if (shown.isEmpty() || !(button >= 0 && button < 9 || button == 40)) return;
                Inventory inv = player.getInventory();
                if (!inv.getItem(button).isEmpty()) return;
                ItemStack taken = be.extract(shown, shown.getCount());
                if (!taken.isEmpty()) inv.setItem(button, taken);
            }
            default -> {
            }
        }
    }

    /** Puts leftovers of an extraction back into the terminal (or drops them as a last resort). */
    private void giveBack(Player player, ItemStack rest) {
        if (rest.isEmpty()) return;
        ItemStack left = be.restore(rest);
        if (!left.isEmpty()) player.drop(left, false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (be == null || !(player instanceof ServerPlayer)) return ItemStack.EMPTY;
        if (index < 0 || index >= slots.size() || (index >= VIEW_START && index < VIEW_END)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == RESULT) {
            if (!hasRoomFor(stack)) return ItemStack.EMPTY;
            stack.getItem().onCraftedBy(stack, player.level(), player);
            intoTerminalThenPlayer(stack);
            if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
        } else if (index < PLAYER_START) {
            // grid, expansion or battery slot: terminal first for the grid, otherwise back to the player
            if (index < GRID_END) intoTerminalThenPlayer(stack);
            else if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof StorageExpansionItem) {
            if (!moveItemStackTo(stack, EXPANSION_START, EXPANSION_END, false)) return ItemStack.EMPTY;
        } else if (StorageTerminalBlockEntity.isBattery(stack) && slots.get(BATTERY).getItem().isEmpty()
                && moveItemStackTo(stack, BATTERY, BATTERY + 1, false)) {
            // moved into the battery slot
        } else {
            ItemStack rest = be.insert(stack.copy(), false);
            stack.setCount(rest.getCount());
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == RESULT && !stack.isEmpty()) player.drop(stack, false);
        return original;
    }

    /** Moves as much of the stack as possible into the terminal, then into the player's inventory. Shrinks the stack. */
    private void intoTerminalThenPlayer(ItemStack stack) {
        ItemStack rest = be.insert(stack.copy(), false);
        stack.setCount(rest.getCount());
        if (!stack.isEmpty()) moveItemStackTo(stack, PLAYER_START, PLAYER_END, true);
    }

    /** True when the terminal and the player's inventory together can take the whole stack. */
    private boolean hasRoomFor(ItemStack stack) {
        ItemStack rest = be.insert(stack.copy(), true);
        int left = rest.getCount();
        for (int i = PLAYER_START; i < PLAYER_END && left > 0; i++) {
            ItemStack in = slots.get(i).getItem();
            if (in.isEmpty()) left -= Math.min(stack.getMaxStackSize(), slots.get(i).getMaxStackSize(stack));
            else if (ItemStack.isSameItemSameComponents(in, stack)) left -= Math.max(0, Math.min(stack.getMaxStackSize(), slots.get(i).getMaxStackSize(stack)) - in.getCount());
        }
        return left <= 0;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof ViewSlot) && slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof ViewSlot) && slot.container != result && super.canDragTo(slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return true;
        if (be.isRemoved() || be.getLevel() == null) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    // ------------------------------------------------------------------ expansions

    /** One of each expansion Mk only. */
    private boolean expansionAllowed(int slotIndex, ItemStack stack) {
        if (!(stack.getItem() instanceof StorageExpansionItem)) return false;
        for (int i = EXPANSION_START; i < EXPANSION_END; i++) {
            if (i != slotIndex && slots.get(i).getItem().is(stack.getItem())) return false;
        }
        return true;
    }

    /** An expansion may only leave when the remaining capacity still holds all stored stacks (nothing is ever lost). */
    public boolean canRemoveExpansion(Slot slot) {
        if (!(slot.getItem().getItem() instanceof StorageExpansionItem e)) return true;
        return used() <= capacity() - e.slots();
    }

    /** True when the expansion in this slot cannot be removed because the storage is too full. Client display. */
    public boolean expansionLocked(int slotIndex) {
        Slot slot = slots.get(slotIndex);
        return slot.hasItem() && !canRemoveExpansion(slot);
    }

    private final class ExpansionSlot extends SlotItemHandler {
        ExpansionSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return expansionAllowed(this.index, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (be != null) {
                // authoritative check from the real inventory (the synced values lag a little)
                if (getItem().getItem() instanceof StorageExpansionItem e) return be.usedSlots() <= be.capacity() - e.slots();
                return true;
            }
            return canRemoveExpansion(this);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private static final class BatterySlot extends SlotItemHandler {
        BatterySlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return StorageTerminalBlockEntity.isBattery(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /** A cell of the picture grid. Never takes or gives items by the normal slot code. */
    public static final class ViewSlot extends Slot {
        ViewSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
