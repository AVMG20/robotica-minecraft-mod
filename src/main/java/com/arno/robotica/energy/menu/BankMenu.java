package com.arno.robotica.energy.menu;

import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.BankControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/** Capacitor Bank GUI: no slots, only numbers (gauge, averages, 60 s history). */
public class BankMenu extends ControllerMenu {
    /** Client side. */
    public BankMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, null);
    }

    /** Server side. */
    public BankMenu(int id, Inventory inv, BankControllerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be);
    }

    private BankMenu(int id, Inventory inv, BlockPos pos, @Nullable BankControllerBlockEntity be) {
        super(EnergyRegistry.BANK_MENU.get(), id, inv, pos, be);
    }

    public long energy() { return data().getLong("energy"); }
    public long capacity() { return data().getLong("capacity"); }
    public int rate() { return data().getInt("rate"); }
    public long averageIn() { return data().getLong("in"); }
    public long averageOut() { return data().getLong("out"); }
    public int capacitors() { return data().getInt("capacitors"); }
    public int coils() { return data().getInt("coils"); }

    /** Net FE/t per second, oldest first. */
    public long[] history() {
        long[] h = data().getLongArray("history");
        return h.length == BankControllerBlockEntity.HISTORY ? h : new long[BankControllerBlockEntity.HISTORY];
    }
}
