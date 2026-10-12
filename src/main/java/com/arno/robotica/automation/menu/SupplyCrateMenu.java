package com.arno.robotica.automation.menu;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.SupplyCrateBlock;
import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Chest style menu of the Supply Crate: its Mk's slot grid above the player inventory, both centred. */
public class SupplyCrateMenu extends MachineMenu {
    public final SupplyCrateBlockEntity be;
    public final int columns;
    public final int rows;

    public SupplyCrateMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf));
    }

    private static SupplyCrateBlockEntity resolve(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof SupplyCrateBlockEntity crate) return crate;
        throw new IllegalStateException("No supply crate at the menu position");
    }

    public SupplyCrateMenu(int id, Inventory inv, SupplyCrateBlockEntity be) {
        super(AutomationContent.CRATE_MENU.get(), id);
        this.be = be;
        SupplyCrateBlock crate = be.getBlockState().getBlock() instanceof SupplyCrateBlock b ? b : null;
        this.columns = crate != null ? crate.columns() : 9;
        this.rows = crate != null ? Math.min(crate.rows(), be.items.getSlots() / columns) : 3;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                addSlot(new SlotItemHandler(be.items, c + r * columns, 8 + c * 18, 18 + r * 18));
            }
        }
        addPlayerInventory(inv, inventoryX(), inventoryY());
    }

    /** GUI width: the slot grid plus 8 px margins, at least the standard 176. */
    public int width() {
        return Math.max(176, columns * 18 + 16);
    }

    /** GUI height: the 3 row crate is the standard 166 high, each extra row adds 18. */
    public int height() {
        return inventoryY() + 82;
    }

    public int inventoryX() {
        return (width() - 162) / 2 + 1;
    }

    public int inventoryY() {
        return 30 + rows * 18;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be.isRemoved() || be.getLevel() == null) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
