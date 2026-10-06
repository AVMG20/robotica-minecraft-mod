package com.arno.robotica.energy.menu;

import com.arno.robotica.core.menu.MachineSlot;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.ReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Fission Reactor GUI. Slots: 0-2 fuel, 3-5 waste (take only), then the player inventory. */
public class ReactorMenu extends ControllerMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int PLAYER_Y = 130;
    public static final int FUEL_X = 134, WASTE_X = 152, SLOT_Y = 18;

    /** Client side. */
    public ReactorMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, ReactorControllerBlockEntity.newFuelHandler(() -> {}), ReactorControllerBlockEntity.newWasteHandler(() -> {}), null);
    }

    /** Server side. */
    public ReactorMenu(int id, Inventory inv, ReactorControllerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.fuel, be.waste, be);
    }

    private ReactorMenu(int id, Inventory inv, BlockPos pos, IItemHandler fuel, IItemHandler waste, @Nullable ReactorControllerBlockEntity be) {
        super(EnergyRegistry.REACTOR_MENU.get(), id, inv, pos, be);
        for (int i = 0; i < ReactorControllerBlockEntity.FUEL_SLOTS; i++) addSlot(new MachineSlot(fuel, i, FUEL_X, SLOT_Y + i * 18));
        for (int i = 0; i < ReactorControllerBlockEntity.WASTE_SLOTS; i++) {
            addSlot(new MachineSlot(waste, i, WASTE_X, SLOT_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addPlayerInventory(inv, 8, PLAYER_Y);
    }

    public int temperature() { return data().getInt("temp"); }
    public int safeTemp() { return Math.max(1, data().getInt("safe")); }
    public int scramTemp() { return Math.max(2, data().getInt("scramAt")); }
    public int resetTemp() { return data().getInt("resetAt"); }
    public int heat() { return data().getInt("heat"); }
    public int cooling() { return data().getInt("cooling"); }
    public int fePerTick() { return data().getInt("fe"); }
    public int efficiency() { return data().getInt("eff"); }
    public int throttle() { return data().getInt("throttle"); }
    public int rodInsertion() { return data().getInt("rodsIn"); }
    public boolean scrammed() { return data().getBoolean("scram"); }
    public int energy() { return data().getInt("energy"); }
    public int capacity() { return data().getInt("capacity"); }
    public float burn() { return data().getFloat("burn"); }
    public int burnHeat() { return data().getInt("burnHeat"); }
    public int rods() { return data().getInt("rods"); }
    public float coolant() { return data().getInt("coolant") / 100.0F; }

    public ReactorControllerBlockEntity.State state() {
        int i = data().getInt("state");
        ReactorControllerBlockEntity.State[] all = ReactorControllerBlockEntity.State.values();
        return i >= 0 && i < all.length ? all[i] : ReactorControllerBlockEntity.State.NOT_FORMED;
    }
}
