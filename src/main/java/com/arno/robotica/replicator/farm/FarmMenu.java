package com.arno.robotica.replicator.farm;

import com.arno.robotica.energy.menu.ControllerMenu;
import com.arno.robotica.replicator.ReplicatorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Mob farm GUI. Slots: the vials (3 or 4), the boost, 2 upgrades, 18 output (take only), then the player inventory.
 */
public class FarmMenu extends ControllerMenu {
    public static final int IMAGE_HEIGHT = 226;
    public static final int PLAYER_Y = 144, OUTPUT_Y = 96;
    public static final int VIAL_X = 26, VIAL_Y = 20, SIDE_X = 152;

    private final FarmKind kind;

    /** Client side. */
    public FarmMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, FarmKind.of(inv.player.level().getBlockState(pos)), null);
    }

    /** Server side. */
    public FarmMenu(int id, Inventory inv, FarmControllerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.kind(), be);
    }

    private FarmMenu(int id, Inventory inv, BlockPos pos, FarmKind kind, @Nullable FarmControllerBlockEntity be) {
        super(ReplicatorRegistry.FARM_MENU.get(), id, inv, pos, be);
        this.kind = kind;
        IItemHandler vials = be != null ? be.vials : FarmControllerBlockEntity.newVialHandler(kind, () -> {});
        IItemHandler boost = be != null ? be.boost : FarmControllerBlockEntity.newBoostHandler(() -> {});
        IItemHandler upgrades = be != null ? be.upgrades : FarmControllerBlockEntity.newUpgrades(() -> {});
        IItemHandler output = be != null ? be.output : FarmControllerBlockEntity.newOutputHandler(() -> {});
        for (int i = 0; i < kind.vialSlots(); i++) addSlot(new ControlledSlot(vials, i, VIAL_X + i * 18, VIAL_Y));
        addSlot(new ControlledSlot(boost, 0, SIDE_X, 20));
        for (int i = 0; i < FarmControllerBlockEntity.UPGRADE_SLOTS; i++) addSlot(new ControlledSlot(upgrades, i, SIDE_X, 38 + i * 18));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new ControlledSlot(output, col + row * 9, 8 + col * 18, OUTPUT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }
        addPlayerInventory(inv, 8, PLAYER_Y);
    }

    public FarmKind kind() {
        return kind;
    }

    /** Index of the boost slot; the vials come before it, the upgrades and the output after. */
    public int boostSlot() {
        return kind.vialSlots();
    }

    public int energy() { return data().getInt("energy"); }
    public int capacity() { return data().getInt("capacity"); }
    public int fePerTick() { return data().getInt("fe"); }
    public int needed() { return data().getInt("needed"); }
    public int speed() { return Math.max(1, data().getInt("speed")); }
    public int cycles() { return data().getInt("cycles"); }

    public float progress() {
        int needed = needed();
        return needed <= 0 ? 0 : Math.min(1.0F, (float) data().getInt("progress") / needed);
    }

    public FarmControllerBlockEntity.State state() {
        int i = data().getInt("state");
        FarmControllerBlockEntity.State[] all = FarmControllerBlockEntity.State.values();
        return i >= 0 && i < all.length ? all[i] : FarmControllerBlockEntity.State.NOT_FORMED;
    }
}
