package com.arno.robotica.processing.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.menu.MachineSlot;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.MachineUpgrades;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.arno.robotica.processing.block.ProcessingMachineBlockEntity;
import com.arno.robotica.processing.media.GrindingMedia;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Grinder GUI: input, media, three outputs, battery, upgrade slots of its Mk. */
public class GrinderMenu extends MachineMenu {
    public static final int UPGRADE_X = 80;
    public static final int UPGRADE_Y = 53;

    private final ContainerLevelAccess access;
    private final Block block;
    private final int tier;
    private final int energyIndex, capacityIndex, progressIndex, neededIndex, statusIndex, useIndex, mediaLeftIndex, neededTierIndex;

    /** Client side. */
    public GrinderMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(GrinderBlockEntity.SLOTS), null);
    }

    /** Server side. */
    public GrinderMenu(int id, Inventory inv, GrinderBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.items, be);
    }

    private GrinderMenu(int id, Inventory inv, BlockPos pos, IItemHandler items, @Nullable GrinderBlockEntity be) {
        super(ProcessingRegistry.GRINDER_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.block = inv.player.level().getBlockState(pos).getBlock();
        this.tier = block instanceof ProcessingMachineBlock m ? m.tier() : 1;
        IItemHandler upgrades = be != null ? be.upgrades
                : new MachineUpgrades(GrinderBlockEntity.KINDS, k -> ProcessingMachineBlockEntity.cap(k, tier), () -> ProcessingConfig.upgradeSlots(tier), () -> {});

        addSlot(new SlotItemHandler(items, GrinderBlockEntity.INPUT, 26, 17));
        addSlot(new SlotItemHandler(items, GrinderBlockEntity.MEDIA, 26, 41));
        for (int i = 0; i < GrinderBlockEntity.OUT_COUNT; i++) {
            addSlot(new MachineSlot(items, GrinderBlockEntity.OUT_FIRST + i, 80 + i * 18, 17) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addSlot(new SlotItemHandler(items, GrinderBlockEntity.BATTERY, 152, 17));
        int active = ProcessingConfig.upgradeSlots(tier);
        for (int i = 0; i < MachineUpgrades.MAX_SLOTS; i++) {
            final boolean on = i < active;
            addSlot(new MachineSlot(upgrades, i, UPGRADE_X + i * 18, UPGRADE_Y) {
                @Override
                public boolean isActive() {
                    return on;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return on && super.mayPlace(stack);
                }
            });
        }
        addPlayerInventory(inv, 8, 84);
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        progressIndex = track(be == null ? () -> 0 : be::progress);
        neededIndex = track(be == null ? () -> 0 : be::needed);
        statusIndex = track(be == null ? () -> 0 : () -> be.status().ordinal());
        useIndex = track(be == null ? () -> 0 : be::lastUse);
        mediaLeftIndex = track(be == null ? () -> 0 : be::mediaLeft);
        neededTierIndex = track(be == null ? () -> 0 : be::neededTier);
    }

    public int tier() {
        return tier;
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    public float progress() {
        int needed = synced(neededIndex);
        return needed <= 0 ? 0 : Math.min(1.0F, (float) synced(progressIndex) / needed);
    }

    public ProcessingMachineBlockEntity.Status status() {
        ProcessingMachineBlockEntity.Status[] all = ProcessingMachineBlockEntity.Status.values();
        int i = synced(statusIndex);
        return i >= 0 && i < all.length ? all[i] : ProcessingMachineBlockEntity.Status.IDLE;
    }

    public int lastUse() {
        return synced(useIndex);
    }

    public int mediaLeft() {
        return synced(mediaLeftIndex);
    }

    public int neededTier() {
        return synced(neededTierIndex);
    }

    /** Uses of the media item in the slot (client: from the synced data map), 0 when none. */
    public int mediaUses() {
        GrindingMedia media = GrindingMedia.of(slots.get(1).getItem());
        return media == null ? 0 : media.uses();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
