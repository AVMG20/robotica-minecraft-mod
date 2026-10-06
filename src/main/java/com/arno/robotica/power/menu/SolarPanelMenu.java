package com.arno.robotica.power.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.SolarPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Map;

/** Solar Panel: charge, live FE/t, sun state and two card slots (speed: more daylight output, efficiency: moonlight). */
public class SolarPanelMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final Block block;
    private final int storedIdx, capacityIdx, rateIdx, flagIdx;

    /** Client side. */
    public SolarPanelMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new Upgrades(2, Map.of(UpgradeKind.SPEED, 4, UpgradeKind.EFFICIENCY, 4), () -> {}), null);
    }

    /** Server side. */
    public SolarPanelMenu(int id, Inventory inv, SolarPanelBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.upgrades, be);
    }

    private SolarPanelMenu(int id, Inventory inv, BlockPos pos, IItemHandler upgrades, SolarPanelBlockEntity be) {
        super(PowerRegistry.SOLAR_PANEL_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.block = inv.player.level().getBlockState(pos).getBlock();
        addSlot(new SlotItemHandler(upgrades, 0, 134, 46));
        addSlot(new SlotItemHandler(upgrades, 1, 152, 46));
        addPlayerInventory(inv, 8, 84);
        storedIdx = track(be == null ? () -> 0 : be::stored);
        capacityIdx = track(be == null ? () -> 0 : be::capacity);
        rateIdx = track(be == null ? () -> 0 : be::rate);
        flagIdx = track(be == null ? () -> 0 : be::flag);
    }

    public int stored() {
        return synced(storedIdx);
    }

    public int capacity() {
        return synced(capacityIdx);
    }

    public int rate() {
        return synced(rateIdx);
    }

    public boolean sun() {
        return synced(flagIdx) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
