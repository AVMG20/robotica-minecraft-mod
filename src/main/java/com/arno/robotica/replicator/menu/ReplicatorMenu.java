package com.arno.robotica.replicator.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Mode;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity.Pause;
import com.arno.robotica.replicator.logic.ReplicatorStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of the Replicator Controller. Slots: 0 vial, 1 boost, 2-4 upgrades, 5-22 output (take only), 23 catalyst, then the player inventory.
 */
public class ReplicatorMenu extends MachineMenu {
    public static final int IMAGE_HEIGHT = 226;
    /** Row of the experience controls, between the machine and the output. */
    public static final int XP_ROW_Y = 79;
    private static final int OUTPUT_Y = 96;
    private static final int PLAYER_Y = 144;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final int energyIndex;
    private final int capacityIndex;
    private final int progressIndex;
    private final int neededIndex;
    private final int statusIndex;
    private final int speedIndex;
    private final int costIndex;
    private final int xpIndex;
    @Nullable
    private final ReplicatorControllerBlockEntity be;

    /** Client side. */
    public ReplicatorMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, ReplicatorControllerBlockEntity.newVialHandler(() -> {}), ReplicatorControllerBlockEntity.newBoostHandler(() -> {}),
                ReplicatorControllerBlockEntity.newUpgrades(() -> {}), new ItemStackHandler(ReplicatorControllerBlockEntity.OUTPUT_SLOTS),
                ReplicatorControllerBlockEntity.newCatalystHandler(() -> {}), null);
    }

    /** Server side. */
    public ReplicatorMenu(int id, Inventory inv, ReplicatorControllerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.vial, be.boost, be.upgrades, be.output, be.catalyst, be);
    }

    private ReplicatorMenu(int id, Inventory inv, BlockPos pos, IItemHandler vial, IItemHandler boost, IItemHandler upgrades,
                           IItemHandler output, IItemHandler catalyst, @Nullable ReplicatorControllerBlockEntity be) {
        super(ReplicatorRegistry.REPLICATOR_MENU.get(), id);
        this.pos = pos;
        this.be = be;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        addSlot(new SlotItemHandler(vial, 0, 38, 20));
        addSlot(new SlotItemHandler(boost, 0, 38, 48));
        for (int i = 0; i < ReplicatorControllerBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new SlotItemHandler(upgrades, i, 134, 20 + i * 18));
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(output, col + row * 9, 8 + col * 18, OUTPUT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }
        addSlot(new SlotItemHandler(catalyst, 0, 154, 20));
        addPlayerInventory(inv, 8, PLAYER_Y);
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        progressIndex = track(be == null ? () -> 0 : be::progress);
        neededIndex = track(be == null ? () -> 0 : be::needed);
        // bit 0 formed, bit 1 mode, bits 2-4 structure status, bits 5-8 pause reason, bits 9-10 looting level
        statusIndex = track(be == null ? () -> 0 : () -> (be.isFormed() ? 1 : 0) | (be.mode().ordinal() << 1)
                | (be.status().ordinal() << 2) | (be.pause().ordinal() << 5) | (be.lootingLevel() << 9));
        speedIndex = track(be == null ? () -> 0 : be::speedMultiplier);
        costIndex = track(be == null ? () -> 0 : be::energyPerTick);
        xpIndex = track(be == null ? () -> 0 : be::xpStored);
        trackSides(be == null ? null : be.sides);
    }

    public BlockPos pos() {
        return pos;
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

    public int cycleTicks() {
        return synced(neededIndex);
    }

    public boolean formed() {
        return (synced(statusIndex) & 1) != 0;
    }

    public Mode mode() {
        return Mode.byId((synced(statusIndex) >> 1) & 1);
    }

    public ReplicatorStructure.Status structure() {
        int id = (synced(statusIndex) >> 2) & 7;
        ReplicatorStructure.Status[] all = ReplicatorStructure.Status.values();
        return id < all.length ? all[id] : ReplicatorStructure.Status.INCOMPLETE;
    }

    public Pause pause() {
        return Pause.byId((synced(statusIndex) >> 5) & 15);
    }

    public int looting() {
        return (synced(statusIndex) >> 9) & 3;
    }

    public int speedMultiplier() {
        return synced(speedIndex);
    }

    public int energyPerTick() {
        return synced(costIndex);
    }

    public int xpStored() {
        return synced(xpIndex);
    }

    /** Button id that claims all stored experience. */
    public static final int CLAIM_ALL = 0;

    /** Button id that claims experience up to {@code level}. */
    public static int claimToLevel(int level) {
        return Math.max(1, Math.min(ReplicatorControllerBlockEntity.MAX_CLAIM_LEVEL, level));
    }

    /** Server side, after vanilla checked the menu is open and still valid: 0 claims all, 1-1000 up to that level. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null || be.isRemoved() || id < 0 || id > ReplicatorControllerBlockEntity.MAX_CLAIM_LEVEL) return false;
        if (!stillValid(player)) return false;
        be.claimXp(player, id == CLAIM_ALL ? -1 : id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ReplicatorRegistry.REPLICATOR_CONTROLLER.get());
    }
}
