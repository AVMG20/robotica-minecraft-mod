package com.arno.robotica.automation.menu;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.SupplyCrateBlockEntity;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/** 3 x 9 chest style menu of the Supply Crate. */
public class SupplyCrateMenu extends MachineMenu {
    public final SupplyCrateBlockEntity be;

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
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new SlotItemHandler(be.items, c + r * 9, 8 + c * 18, 18 + r * 18));
            }
        }
        addPlayerInventory(inv, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        if (be.isRemoved() || be.getLevel() == null) return false;
        return player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
