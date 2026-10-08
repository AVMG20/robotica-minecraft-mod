package com.arno.robotica.energy.menu;

import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.ColliderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Ring Collider GUI. Slots: 0-2 fuel, 3-5 Strange Matter (take only), then the player inventory. */
public class ColliderMenu extends ControllerMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int PLAYER_Y = 130;
    public static final int FUEL_X = 134, OUTPUT_X = 152, SLOT_Y = 18;

    /** Client side. */
    public ColliderMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, ColliderBlockEntity.newFuelHandler(() -> {}), ColliderBlockEntity.newOutputHandler(() -> {}), null);
    }

    /** Server side. */
    public ColliderMenu(int id, Inventory inv, ColliderBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.fuel, be.output, be);
    }

    private ColliderMenu(int id, Inventory inv, BlockPos pos, IItemHandler fuel, IItemHandler output, @Nullable ColliderBlockEntity be) {
        super(EnergyRegistry.COLLIDER_MENU.get(), id, inv, pos, be);
        for (int i = 0; i < ColliderBlockEntity.FUEL_SLOTS; i++) addSlot(new ControlledSlot(fuel, i, FUEL_X, SLOT_Y + i * 18));
        for (int i = 0; i < ColliderBlockEntity.OUTPUT_SLOTS; i++) {
            addSlot(new ControlledSlot(output, i, OUTPUT_X, SLOT_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addPlayerInventory(inv, 8, PLAYER_Y);
    }

    public boolean enabled() { return data().getBoolean("enabled"); }
    public boolean beamOn() { return data().getBoolean("beamOn"); }
    public long charge() { return data().getLong("charge"); }
    public long chargeNeeded() { return data().getLong("chargeNeed"); }
    /** Beam strength in percent. */
    public int beam() { return data().getInt("beam"); }
    public int fePerTick() { return data().getInt("fe"); }
    public long fullPower() { return data().getLong("full"); }
    public int energy() { return data().getInt("energy"); }
    public int capacity() { return data().getInt("capacity"); }
    public float burn() { return data().getFloat("burn"); }
    public int length() { return data().getInt("length"); }
    public int segments() { return data().getInt("seg1"); }
    public int resonantSegments() { return data().getInt("seg2"); }
    /** Progress to the next Strange Matter, 0-1. */
    public float matter() { return data().getFloat("matter"); }

    public ColliderBlockEntity.State state() {
        int i = data().getInt("state");
        ColliderBlockEntity.State[] all = ColliderBlockEntity.State.values();
        return i >= 0 && i < all.length ? all[i] : ColliderBlockEntity.State.NOT_FORMED;
    }

    /** Nothing built yet: the smallest ring, a 7x7 square outline behind the controller. */
    @Override
    public net.minecraft.world.level.levelgen.structure.BoundingBox previewBox(net.minecraft.world.level.block.state.BlockState controllerState) {
        net.minecraft.core.Direction out = controllerState.hasProperty(com.arno.robotica.energy.block.ControllerBlock.FACING)
                ? controllerState.getValue(com.arno.robotica.energy.block.ControllerBlock.FACING) : net.minecraft.core.Direction.NORTH;
        net.minecraft.core.Direction right = out.getClockWise();
        return net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(pos().relative(right, -3), pos().relative(right, 3).relative(out, -6));
    }
}
