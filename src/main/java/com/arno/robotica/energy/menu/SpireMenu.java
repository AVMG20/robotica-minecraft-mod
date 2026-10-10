package com.arno.robotica.energy.menu;

import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.SpireBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Tesla Spire GUI. Slots: 0-1 fuel, 2 waste (take only), then the player inventory. */
public class SpireMenu extends ControllerMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int PLAYER_Y = 130;
    public static final int FUEL_X = 134, WASTE_X = 152, SLOT_Y = 18;

    /** Client side. */
    public SpireMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, SpireBlockEntity.newFuelHandler(() -> {}), SpireBlockEntity.newWasteHandler(() -> {}), null);
    }

    /** Server side. */
    public SpireMenu(int id, Inventory inv, SpireBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.fuel, be.waste, be);
    }

    private SpireMenu(int id, Inventory inv, BlockPos pos, IItemHandler fuel, IItemHandler waste, @Nullable SpireBlockEntity be) {
        super(EnergyRegistry.SPIRE_MENU.get(), id, inv, pos, be);
        for (int i = 0; i < SpireBlockEntity.FUEL_SLOTS; i++) addSlot(new ControlledSlot(fuel, i, FUEL_X, SLOT_Y + i * 18));
        addSlot(new ControlledSlot(waste, 0, WASTE_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(inv, 8, PLAYER_Y);
        trackSides(be == null ? null : be.sides);
    }

    public int fePerTick() { return data().getInt("fe"); }
    public int potential() { return data().getInt("potential"); }
    public int energy() { return data().getInt("energy"); }
    public int capacity() { return data().getInt("capacity"); }
    public float burn() { return data().getFloat("burn"); }
    public int conductors() { return data().getInt("conductors"); }
    public int basePower() { return data().getInt("basePower"); }
    /** Efficiency in percent. */
    public int efficiency() { return data().getInt("eff"); }
    /** Altitude bonus in percent. */
    public int altitude() { return data().getInt("altitude"); }
    /** 0 clear, 1 rain, 2 thunder. */
    public int weather() { return data().getInt("weather"); }
    /** Output share left after neighbouring spires, in percent. */
    public int interference() { return data().getInt("interference"); }
    public int neighbours() { return data().getInt("neighbours"); }
    public boolean sky() { return data().getBoolean("sky"); }
    public boolean muted() { return data().getBoolean("muted"); }
    /** Seconds since the last strike, -1 for never. */
    public long strikeAgo() { return data().getLong("strikeAgo"); }

    public SpireBlockEntity.State state() {
        int i = data().getInt("state");
        SpireBlockEntity.State[] all = SpireBlockEntity.State.values();
        return i >= 0 && i < all.length ? all[i] : SpireBlockEntity.State.NOT_FORMED;
    }

    /** Nothing built yet: the column goes straight up from the base. */
    @Override
    public net.minecraft.world.level.levelgen.structure.BoundingBox previewBox(net.minecraft.world.level.block.state.BlockState controllerState) {
        return net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(pos(), pos().above(Math.max(3, data().getInt("minH")) - 1));
    }
}
