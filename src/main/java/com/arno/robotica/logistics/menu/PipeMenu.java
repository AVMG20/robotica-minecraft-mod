package com.arno.robotica.logistics.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.logistics.LogisticsContent;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.logistics.pipe.PipeMode;
import com.arno.robotica.logistics.pipe.PipeOrder;
import com.arno.robotica.logistics.pipe.PipePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * GUI of one pipe face (the arm that was right-clicked): mode, nine ghost filter slots, whitelist or blacklist, the
 * order an Extract link sends items in and the priority of an Insert link. Buttons 0-2 pick Insert, Extract, Off; 3 flips
 * the list; 4 switches the order; 5 and 6 step the priority down and up.
 */
public class PipeMenu extends MachineMenu {
    public static final int BTN_MODE = 0;
    public static final int BTN_LIST = 3;
    public static final int BTN_ORDER = 4;
    public static final int BTN_PRIORITY_DOWN = 5, BTN_PRIORITY_UP = 6;
    public static final int FILTER_X = 8, FILTER_Y = 42;

    public final BlockPos pos;
    public final Direction side;
    @Nullable
    private final ItemPipeBlockEntity pipe;
    private final int idxMode, idxList, idxOrder, idxPriority;

    /** A filter entry: shows an item but never holds one. Clicks copy the carried item or clear the slot. */
    public static class GhostSlot extends SlotItemHandler {
        public GhostSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
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

    public static PipeMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new PipeMenu(id, inv, buf.readBlockPos(), Direction.from3DDataValue(buf.readByte()), null);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, Direction side) {
        buf.writeBlockPos(pos);
        buf.writeByte(side.get3DDataValue());
    }

    public PipeMenu(int id, Inventory inv, BlockPos pos, Direction side, @Nullable ItemPipeBlockEntity pipe) {
        super(LogisticsContent.PIPE_MENU.get(), id);
        this.pos = pos;
        this.side = side;
        this.pipe = pipe;
        IItemHandler filter = pipe != null ? pipe.filter(side) : new ItemStackHandler(ItemPipeBlockEntity.FILTER_SLOTS);
        for (int i = 0; i < ItemPipeBlockEntity.FILTER_SLOTS; i++) addSlot(new GhostSlot(filter, i, FILTER_X + i * 18, FILTER_Y));
        addPlayerInventory(inv, 8, 98);
        idxMode = track(pipe == null ? () -> 0 : () -> pipe.mode(side).ordinal());
        idxList = track(pipe == null ? () -> 0 : () -> pipe.whitelist(side) ? 1 : 0);
        idxOrder = track(pipe == null ? () -> 0 : () -> pipe.order(side).ordinal());
        idxPriority = track(pipe == null ? () -> PipePriority.DEFAULT.ordinal() : () -> pipe.priority(side).ordinal());
    }

    public PipeMode mode() {
        return PipeMode.byId(synced(idxMode));
    }

    public boolean whitelist() {
        return synced(idxList) != 0;
    }

    public PipeOrder order() {
        return PipeOrder.byId(synced(idxOrder));
    }

    public PipePriority priority() {
        return PipePriority.byId(synced(idxPriority));
    }

    private boolean canEdit(Player player) {
        return pipe != null && !pipe.isRemoved() && player.mayBuild() && player.level().mayInteract(player, pos);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof GhostSlot ghost) {
            if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && canEdit(player)) {
                ItemStack carried = getCarried();
                if (carried.isEmpty() || button == 1 || type == ClickType.QUICK_MOVE) ghost.set(ItemStack.EMPTY);
                else ghost.set(new ItemStack(carried.getItem()));
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Shift-click in the inventory: the item becomes a filter entry and stays where it is. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < ItemPipeBlockEntity.FILTER_SLOTS || index >= slots.size() || !canEdit(player)) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int free = -1;
        for (int i = 0; i < ItemPipeBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = slots.get(i).getItem();
            if (ItemStack.isSameItem(entry, stack)) return ItemStack.EMPTY;
            if (entry.isEmpty() && free < 0) free = i;
        }
        if (free >= 0) slots.get(free).set(new ItemStack(stack.getItem()));
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || !canEdit(player)) return false;
        if (id >= BTN_MODE && id < BTN_MODE + PipeMode.values().length) pipe.changeMode(side, PipeMode.byId(id - BTN_MODE));
        else if (id == BTN_LIST) pipe.setWhitelist(side, !pipe.whitelist(side));
        else if (id == BTN_ORDER) pipe.setOrder(side, pipe.order(side).next());
        else if (id == BTN_PRIORITY_DOWN) pipe.setPriority(side, pipe.priority(side).next());
        else if (id == BTN_PRIORITY_UP) pipe.setPriority(side, pipe.priority(side).previous());
        else return false;
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (pipe == null) return true;
        return !pipe.isRemoved() && player.level().getBlockEntity(pos) == pipe && player.distanceToSqr(pos.getCenter()) <= 64.0;
    }
}
