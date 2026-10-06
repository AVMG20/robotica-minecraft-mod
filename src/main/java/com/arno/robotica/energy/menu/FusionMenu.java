package com.arno.robotica.energy.menu;

import com.arno.robotica.core.menu.MachineSlot;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.FusionControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Fusion Reactor GUI. Slots: 0-2 fusion fuel, then the player inventory. */
public class FusionMenu extends ControllerMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int PLAYER_Y = 130;
    public static final int FUEL_X = 152, SLOT_Y = 18;

    /** Client side. */
    public FusionMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, FusionControllerBlockEntity.newFuelHandler(() -> {}), null);
    }

    /** Server side. */
    public FusionMenu(int id, Inventory inv, FusionControllerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.fuel, be);
    }

    private FusionMenu(int id, Inventory inv, BlockPos pos, IItemHandler fuel, @Nullable FusionControllerBlockEntity be) {
        super(EnergyRegistry.FUSION_MENU.get(), id, inv, pos, be);
        for (int i = 0; i < FusionControllerBlockEntity.FUEL_SLOTS; i++) addSlot(new MachineSlot(fuel, i, FUEL_X, SLOT_Y + i * 18));
        addPlayerInventory(inv, 8, PLAYER_Y);
    }

    public boolean enabled() { return data().getBoolean("enabled"); }
    public boolean ignited() { return data().getBoolean("ignited"); }
    public int charge() { return data().getInt("charge"); }
    public int ignition() { return data().getInt("ignition"); }
    public int plasma() { return data().getInt("plasma"); }
    public int fePerTick() { return data().getInt("fe"); }
    public int energy() { return data().getInt("energy"); }
    public int capacity() { return data().getInt("capacity"); }
    public float burn() { return data().getFloat("burn"); }
    public int burnPower() { return data().getInt("burnPower"); }

    public FusionControllerBlockEntity.State state() {
        int i = data().getInt("state");
        FusionControllerBlockEntity.State[] all = FusionControllerBlockEntity.State.values();
        return i >= 0 && i < all.length ? all[i] : FusionControllerBlockEntity.State.NOT_FORMED;
    }
}
