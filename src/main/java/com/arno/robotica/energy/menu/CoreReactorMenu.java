package com.arno.robotica.energy.menu;

import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.CoreReactorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Core Reactor GUI. Slots: 0 next core, 1-3 fuel, 4-6 waste (take only), then the player inventory. */
public class CoreReactorMenu extends ControllerMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int PLAYER_Y = 130;
    public static final int CORE_X = 110, CORE_Y = 18, FUEL_X = 134, WASTE_X = 152, SLOT_Y = 18;

    /** Client side. */
    public CoreReactorMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, CoreReactorBlockEntity.newCoreHandler(() -> {}), CoreReactorBlockEntity.newFuelHandler(() -> {}),
                CoreReactorBlockEntity.newWasteHandler(() -> {}), null);
    }

    /** Server side. */
    public CoreReactorMenu(int id, Inventory inv, CoreReactorBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.cores, be.fuel, be.waste, be);
    }

    private CoreReactorMenu(int id, Inventory inv, BlockPos pos, IItemHandler cores, IItemHandler fuel, IItemHandler waste,
                            @Nullable CoreReactorBlockEntity be) {
        super(EnergyRegistry.REACTOR_MENU.get(), id, inv, pos, be);
        addSlot(new ControlledSlot(cores, 0, CORE_X, CORE_Y));
        for (int i = 0; i < CoreReactorBlockEntity.FUEL_SLOTS; i++) addSlot(new ControlledSlot(fuel, i, FUEL_X, SLOT_Y + i * 18));
        for (int i = 0; i < CoreReactorBlockEntity.WASTE_SLOTS; i++) {
            addSlot(new ControlledSlot(waste, i, WASTE_X, SLOT_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addPlayerInventory(inv, 8, PLAYER_Y);
    }

    public int fePerTick() { return data().getInt("fe"); }
    public int energy() { return data().getInt("energy"); }
    public int capacity() { return data().getInt("capacity"); }
    public float burn() { return data().getFloat("burn"); }
    /** FE/t of the burning pellet before the core and modulators, 0 when none burns. */
    public int burnPower() { return data().getInt("burnPower"); }
    /** Power and burn multipliers in percent. */
    public int powerPct() { return data().getInt("powerPct"); }
    public int burnPct() { return data().getInt("burnPct"); }
    public int amplifiers() { return data().getInt("amps"); }
    public int dampers() { return data().getInt("dampers"); }
    public float corePower() { return data().getFloat("corePower"); }
    public float integrity() { return data().getFloat("integrity"); }
    public long coreTicks() { return data().getLong("coreTicks"); }

    /** The active core, empty when none burns. */
    public ItemStack core() {
        if (!data().contains("core")) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(data().getString("core"))).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public CoreReactorBlockEntity.State state() {
        int i = data().getInt("state");
        CoreReactorBlockEntity.State[] all = CoreReactorBlockEntity.State.values();
        return i >= 0 && i < all.length ? all[i] : CoreReactorBlockEntity.State.NOT_FORMED;
    }
}
