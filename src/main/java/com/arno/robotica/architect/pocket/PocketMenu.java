package com.arno.robotica.architect.pocket;

import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.menu.MachineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI of a Pocket Architect held in {@code hand}: the building block grid (drawn by the screen, clicks go through
 * {@link PocketActionPayload}), 18 ghost slots for the junk list, a feed slot that turns items into matter at once, and
 * the player inventory. The slot holding the pocket is locked while the GUI is open.
 */
public class PocketMenu extends MachineMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 236;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 18;
    public static final int SIDE_X = 124;
    public static final int FEED_X = 150;
    public static final int FEED_Y = 60;
    public static final int JUNK_X = 8;
    public static final int JUNK_Y = 106;
    public static final int INV_X = 8;
    public static final int INV_Y = 154;

    public static final int ACTION_TAKE = 0;
    /** a = block index, -1 to clear. */
    public static final int ACTION_SELECT = 1;
    /** a = 0 / 1. */
    public static final int ACTION_EAT_JUNK = 2;

    private final Player player;
    private final InteractionHand hand;
    private final boolean server;
    private final SimpleContainer junk = new SimpleContainer(PocketSettings.JUNK_SLOTS);
    private final SimpleContainer feed;
    private final int feedSlot, playerStart, lockedSlot, lockedButton;
    private final int rusticIndex, refinedIndex, flagsIndex;

    /** A junk list entry: shows an item, never holds one. Clicks set or clear it (see {@link #clicked}). */
    public static class GhostSlot extends Slot {
        public GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /** Client side. */
    public PocketMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class), false);
    }

    /** Server side. */
    public PocketMenu(int id, Inventory inv, InteractionHand hand) {
        this(id, inv, hand, true);
    }

    private PocketMenu(int id, Inventory inv, InteractionHand hand, boolean server) {
        super(ArchitectRegistry.POCKET_MENU.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.server = server;
        this.feed = new SimpleContainer(1) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                PocketArchitectItem item = PocketArchitectItem.of(pocket());
                return item != null && item.accepts(stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (PocketMenu.this.server) absorbFeed();
            }
        };

        if (server) {
            List<Item> list = PocketArchitectItem.junkList(PocketArchitectItem.settings(pocket()));
            for (int i = 0; i < list.size() && i < PocketSettings.JUNK_SLOTS; i++) junk.setItem(i, new ItemStack(list.get(i)));
        }
        for (int i = 0; i < PocketSettings.JUNK_SLOTS; i++) addSlot(new GhostSlot(junk, i, JUNK_X + (i % 9) * 18, JUNK_Y + (i / 9) * 18));
        feedSlot = slots.size();
        addSlot(new Slot(feed, 0, FEED_X, FEED_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return feed.canPlaceItem(0, stack);
            }
        });
        playerStart = slots.size();
        addPlayerInventory(inv, INV_X, INV_Y);
        // the hotbar comes after the 27 main slots
        lockedSlot = hand == InteractionHand.MAIN_HAND ? playerStart + 27 + inv.selected : -1;
        lockedButton = hand == InteractionHand.MAIN_HAND ? inv.selected : Inventory.SLOT_OFFHAND;

        rusticIndex = track(server ? () -> PocketArchitectItem.matter(pocket()).rustic() : () -> 0);
        refinedIndex = track(server ? () -> PocketArchitectItem.matter(pocket()).refined() : () -> 0);
        flagsIndex = track(server ? this::packFlags : () -> 0);
    }

    private ItemStack pocket() {
        return player.getItemInHand(hand);
    }

    private int packFlags() {
        PocketSettings s = PocketArchitectItem.settings(pocket());
        return (s.selected() + 1) & 0xFF | (s.eatJunk() ? 1 << 8 : 0);
    }

    // ---------------------------------------------------------------- client reads

    public int matter(Matter.Grade grade) {
        return synced(grade == Matter.Grade.RUSTIC ? rusticIndex : refinedIndex);
    }

    public int selected() {
        return (synced(flagsIndex) & 0xFF) - 1;
    }

    public boolean eatJunk() {
        return (synced(flagsIndex) & (1 << 8)) != 0;
    }

    /** The pocket's item on this side (the client reads it from the synced inventory). */
    public PocketArchitectItem item() {
        PocketArchitectItem item = PocketArchitectItem.of(pocket());
        return item != null ? item : ArchitectRegistry.POCKET_ARCHITECT.get();
    }

    public int feedSlot() {
        return feedSlot;
    }

    public int lockedSlot() {
        return lockedSlot;
    }

    // ---------------------------------------------------------------- server actions

    /** Applies a GUI request (already checked to come from this menu's player). Returns a message or null. */
    public Component handleAction(int action, int a, int b) {
        ItemStack pocket = pocket();
        PocketArchitectItem item = PocketArchitectItem.of(pocket);
        if (item == null) return null;
        switch (action) {
            case ACTION_TAKE -> {
                if (!PocketArchitectItem.validIndex(a)) return null;
                int given = item.take(player, pocket, a, b != 0 ? 64 : 1);
                return given > 0 ? null : PocketArchitectItem.refusal(given, a, pocket);
            }
            case ACTION_SELECT -> {
                if (a != -1 && (!PocketArchitectItem.validIndex(a) || !item.makes(PocketArchitectItem.styleAt(a)))) return null;
                PocketSettings s = PocketArchitectItem.settings(pocket);
                PocketArchitectItem.setSettings(pocket, s.withSelected(a));
            }
            case ACTION_EAT_JUNK -> PocketArchitectItem.setSettings(pocket, PocketArchitectItem.settings(pocket).withEatJunk(a != 0));
            default -> {
            }
        }
        return null;
    }

    private void absorbFeed() {
        ItemStack stack = feed.getItem(0);
        PocketArchitectItem item = PocketArchitectItem.of(pocket());
        if (stack.isEmpty() || item == null) return;
        if (item.absorb(pocket(), stack) > 0) {
            if (stack.isEmpty()) feed.removeItemNoUpdate(0);
            CoreSounds.play(player, CoreSounds.MATTER_ABSORB, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
    }

    /** Sets or clears one junk entry. Items without matter (rustic only on the Mk1) and duplicates are refused. */
    private void setJunk(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            junk.setItem(index, ItemStack.EMPTY);
        } else {
            PocketArchitectItem item = PocketArchitectItem.of(pocket());
            if (item == null || !item.accepts(stack)) {
                if (server && item != null) {
                    boolean refinedOnMk1 = !item.mk2() && !com.arno.robotica.architect.matter.MatterTable.valueOf(stack).isZero();
                    player.displayClientMessage(Component.translatable(refinedOnMk1
                            ? "message.robotica.pocket_architect_not_junk_mk1" : "message.robotica.pocket_architect_not_junk"), true);
                }
                return;
            }
            for (int i = 0; i < junk.getContainerSize(); i++) if (junk.getItem(i).is(stack.getItem())) return;
            junk.setItem(index, stack.copyWithCount(1));
        }
        if (!server) return;
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < junk.getContainerSize(); i++) if (!junk.getItem(i).isEmpty()) list.add(junk.getItem(i).getItem());
        ItemStack pocket = pocket();
        PocketArchitectItem.setSettings(pocket, PocketArchitectItem.settings(pocket).withJunk(list));
    }

    // ---------------------------------------------------------------- clicks

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId == lockedSlot && lockedSlot >= 0) return;
        if (type == ClickType.SWAP && button == lockedButton) return;
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof GhostSlot) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                setJunk(slotId, carried.isEmpty() || button == 1 || type == ClickType.QUICK_MOVE ? ItemStack.EMPTY : carried);
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Shift-click: matter items to the feed slot and back, the rest between inventory and hotbar; the junk list never takes real items. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < feedSlot || index >= slots.size() || index == lockedSlot) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int hotbar = playerStart + 27;
        boolean moved;
        if (index == feedSlot) moved = moveItemStackTo(stack, playerStart, slots.size(), true);
        else if (slots.get(feedSlot).mayPlace(stack)) moved = moveItemStackTo(stack, feedSlot, feedSlot + 1, false);
        // anything else moves between the main inventory and the hotbar, around the locked pocket slot
        else if (index < hotbar) moved = moveItemStackTo(stack, hotbar, slots.size(), false);
        else moved = moveItemStackTo(stack, playerStart, hotbar, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) clearContainer(player, feed);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && pocket().getItem() instanceof PocketArchitectItem;
    }
}
