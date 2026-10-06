package com.arno.robotica.power.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CombustionGeneratorMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final int energyIndex;
    private final int capacityIndex;
    private final int burnIndex;
    private final int totalIndex;
    private final int outputIndex;

    /** Client side. */
    public CombustionGeneratorMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(1), new com.arno.robotica.core.upgrade.Upgrades(2,
                java.util.Map.of(com.arno.robotica.core.upgrade.UpgradeKind.SPEED, 3, com.arno.robotica.core.upgrade.UpgradeKind.EFFICIENCY, 4), () -> {}), null);
    }

    /** Server side. */
    public CombustionGeneratorMenu(int id, Inventory inv, CombustionGeneratorBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.fuel, be.upgrades, be);
    }

    private CombustionGeneratorMenu(int id, Inventory inv, BlockPos pos, IItemHandler fuel, IItemHandler upgrades, CombustionGeneratorBlockEntity be) {
        super(PowerRegistry.COMBUSTION_GENERATOR_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        addSlot(new SlotItemHandler(fuel, 0, 80, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CombustionGeneratorBlockEntity.isFuel(stack);
            }
        });
        addSlot(new SlotItemHandler(upgrades, 0, 152, 20));
        addSlot(new SlotItemHandler(upgrades, 1, 152, 44));
        addPlayerInventory(inv, 8, 84);
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        burnIndex = track(be == null ? () -> 0 : be::burnTime);
        totalIndex = track(be == null ? () -> 0 : be::burnTotal);
        outputIndex = track(be == null ? () -> 0 : be::output);
    }

    /** FE/t while burning, with the installed speed cards. */
    public int output() {
        return synced(outputIndex);
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    public int burnTime() {
        return synced(burnIndex);
    }

    public int burnTotal() {
        return synced(totalIndex);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PowerRegistry.COMBUSTION_GENERATOR.get());
    }
}
