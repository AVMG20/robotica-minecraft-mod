package com.arno.robotica.exo.menu;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoRegistry;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.ExoTicker;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.core.module.ModuleItem;
import com.arno.robotica.core.module.Modules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Module screen of the Exo-Frame: one row per armor piece with up to four module slots, an on/off switch per module
 * and, on chestplates from Mk2 on, the core socket. Opened for all four worn pieces with the J key (absent pieces get
 * an empty row), or for one piece by sneak + right-click with the piece in hand. The switches are menu buttons
 * ({@link #clickMenuButton}), so the server validates every click. The slots write straight into the piece's data
 * components; the energy and everything else on the piece is never touched (except that removing Capacitor Plating
 * caps the stored energy at the smaller battery).
 *
 * <p>The server menu remembers the exact piece stacks it was opened for and only ever reads or writes those. When a
 * piece is swapped, moved or replaced (hotbar number keys, the offhand key, another selected slot) the menu refuses
 * every further click and closes, so a module list can never be copied onto a second piece. Clicks that would move
 * the held piece itself are refused outright.
 */
public class ExoMenu extends MachineMenu {
    /** Where a section's armor piece lives: 0 worn in its slot, 1 main hand, 2 off hand. {@code count} 0 = no piece. */
    public record Section(EquipmentSlot slot, int source, int count, boolean core) {
        public ItemStack resolve(Player player) {
            ItemStack stack = switch (source) {
                case 1 -> player.getMainHandItem();
                case 2 -> player.getOffhandItem();
                default -> player.getItemBySlot(slot);
            };
            return stack.getItem() instanceof ExoArmorItem item && item.getEquipmentSlot() == slot ? stack : ItemStack.EMPTY;
        }

        /** The other worn pieces, for the one-per-suit rule. Empty for a piece held in the hand. */
        public List<ItemStack> others(Player player) {
            if (source != 0) return List.of();
            List<ItemStack> list = new ArrayList<>(3);
            for (EquipmentSlot s : ExoSuit.SLOTS) {
                if (s != slot) list.add(ExoSuit.piece(player, s));
            }
            return list;
        }

        boolean hasPiece() {
            return count > 0 || core;
        }
    }

    // Layout, shared with the screen.
    public static final int WIDTH = 196;
    public static final int ROW_HEIGHT = 24;
    public static final int FIRST_ROW_Y = 18;
    public static final int SLOT_X = 35;
    public static final int SLOT_STEP = 30;
    public static final int CORE_X = 157;

    public final List<Section> sections;
    /** Index of the first module slot of each section in {@link #slots}. */
    public final int[] firstSlot;
    private final Player player;
    /** Server only (null on the client): the piece stack of each row at open time. Reads and writes require it. */
    @Nullable
    private final ItemStack[] expected;
    /** Server only: what the client last got for each row's piece (see {@link #broadcastChanges}). */
    @Nullable
    private final ItemStack[] sent;

    public ExoMenu(int id, Inventory inv, List<Section> sections, boolean server) {
        super(ExoRegistry.EXO_MENU.get(), id);
        this.player = inv.player;
        this.sections = List.copyOf(sections);
        this.firstSlot = new int[sections.size()];
        this.expected = server ? new ItemStack[sections.size()] : null;
        this.sent = server ? new ItemStack[sections.size()] : null;
        if (expected != null) {
            for (int r = 0; r < sections.size(); r++) {
                Section section = sections.get(r);
                expected[r] = section.hasPiece() ? section.resolve(inv.player) : ItemStack.EMPTY;
            }
        }
        int index = 0;
        for (int r = 0; r < sections.size(); r++) {
            Section section = sections.get(r);
            firstSlot[r] = index;
            int y = rowY(r) + 3;
            int row = r;
            Supplier<ItemStack> piece = () -> piece(row);
            Container container = server ? new ModuleContainer(inv.player, section, piece) : new SimpleContainer(Math.max(1, section.count()));
            for (int i = 0; i < section.count(); i++) {
                addSlot(new ModuleSlot(container, i, inv.player, section, piece, SLOT_X + i * SLOT_STEP, y));
                index++;
            }
            if (section.core()) {
                Container core = server ? new CoreContainer(inv.player, piece) : new SimpleContainer(1);
                addSlot(new CoreSlot(core, piece, CORE_X, y));
                index++;
            }
        }
        addPlayerInventory(inv, (WIDTH - 162) / 2, inventoryY(sections.size()));
    }

    /**
     * The piece of a row. On the server only the exact stack the menu was opened for, EMPTY once that stack moved, was
     * replaced or used up. On the client whatever sits where the section points.
     */
    public ItemStack piece(int row) {
        ItemStack now = sections.get(row).resolve(player);
        if (expected == null) return now;
        ItemStack want = expected[row];
        return !want.isEmpty() && now == want ? now : ItemStack.EMPTY;
    }

    public static int rowY(int row) {
        return FIRST_ROW_Y + row * ROW_HEIGHT;
    }

    public static int inventoryY(int rows) {
        return rowY(rows) + 15;
    }

    // ---------------------------------------------------------------- opening

    /** J key: every armor slot gets a row, worn pieces with their slots. */
    public static void openWorn(ServerPlayer player) {
        List<Section> list = new ArrayList<>();
        for (EquipmentSlot slot : ExoSuit.SLOTS) {
            ItemStack piece = ExoSuit.piece(player, slot);
            list.add(piece.getItem() instanceof ExoArmorItem item
                    ? new Section(slot, 0, Modules.slots(piece), item.hasCoreSocket())
                    : new Section(slot, 0, 0, false));
        }
        open(player, list);
    }

    public static void openForHand(ServerPlayer player, InteractionHand hand) {
        ItemStack piece = player.getItemInHand(hand);
        if (!(piece.getItem() instanceof ExoArmorItem item)) return;
        open(player, List.of(new Section(item.getEquipmentSlot(), hand == InteractionHand.MAIN_HAND ? 1 : 2, Modules.slots(piece), item.hasCoreSocket())));
    }

    private static void open(ServerPlayer player, List<Section> sections) {
        if (sections.stream().allMatch(s -> s.count() == 0)) return;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ExoMenu(id, inv, sections, true),
                Component.translatable("container.robotica.exo")), buf -> writeSections(buf, sections));
    }

    private static void writeSections(RegistryFriendlyByteBuf buf, List<Section> sections) {
        buf.writeVarInt(sections.size());
        for (Section s : sections) {
            buf.writeVarInt(s.slot().ordinal());
            buf.writeVarInt(s.source());
            buf.writeVarInt(s.count());
            buf.writeBoolean(s.core());
        }
    }

    public static List<Section> readSections(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4);
        List<Section> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            EquipmentSlot slot = EquipmentSlot.values()[Math.floorMod(buf.readVarInt(), EquipmentSlot.values().length)];
            int source = buf.readVarInt();
            int count = Math.max(0, Math.min(buf.readVarInt(), ExoArmorItem.MAX_SLOTS));
            list.add(new Section(slot, source, count, buf.readBoolean()));
        }
        return list;
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    public boolean stillValid(Player player) {
        for (int r = 0; r < sections.size(); r++) {
            if (sections.get(r).hasPiece() && piece(r).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Worn armor and the offhand are not slots of this menu, and vanilla only syncs them through the player's own
     * inventory menu, which is idle while this one is open. Without this the client keeps the piece it had at open
     * time: switches and new modules would not show until the screen is reopened. The main hand is a slot here already.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (sent == null || !(player instanceof ServerPlayer sp)) return;
        for (int r = 0; r < sections.size(); r++) {
            Section section = sections.get(r);
            if (section.source() == 1) continue;
            ItemStack piece = piece(r);
            if (piece.isEmpty() || (sent[r] != null && ItemStack.matches(sent[r], piece))) continue;
            sent[r] = piece.copy();
            int index = section.source() == 2 ? Inventory.SLOT_OFFHAND : Inventory.INVENTORY_SIZE + section.slot().getIndex();
            // Container id -2 writes straight into the client's inventory, whatever menu is open.
            sp.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, index, piece.copy()));
        }
    }

    /** Refuses every click that would move a held piece the menu edits (its hotbar slot, number keys, the offhand key). */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (movesSource(slotId, button, type, player)) return;
        super.clicked(slotId, button, type, player);
    }

    /** True when the click would take, swap or throw the held piece of a hand section. */
    public boolean movesSource(int slotId, int button, ClickType type, Player player) {
        Inventory inv = player.getInventory();
        for (Section section : sections) {
            if (section.source() == 1) {
                if (type == ClickType.SWAP && button == inv.selected) return true;
                if (slotId >= 0 && slotId < slots.size()) {
                    Slot slot = slots.get(slotId);
                    if (slot.container == inv && slot.getContainerSlot() == inv.selected) return true;
                }
            } else if (section.source() == 2 && type == ClickType.SWAP && button == Inventory.SLOT_OFFHAND) {
                return true;
            }
        }
        return false;
    }

    /** Button id = row * 4 + module slot: switches that module on or off. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int row = id / ExoArmorItem.MAX_SLOTS;
        int slot = id % ExoArmorItem.MAX_SLOTS;
        if (id < 0 || row >= sections.size() || slot >= sections.get(row).count()) return false;
        ItemStack piece = piece(row);
        if (piece.isEmpty() || Modules.kind(piece, slot) == null) return false;
        boolean on = !Modules.enabled(piece, slot);
        Modules.setEnabled(piece, slot, on);
        if (player instanceof ServerPlayer sp) ExoTicker.notifySound(sp, CoreSounds.TOOL_MODE.get(), 0.5F, on ? 1.3F : 0.8F);
        return true;
    }

    /**
     * True when the module in this row and slot is a duplicate of a kind that already counts elsewhere in the suit
     * (so it does nothing). Used by the screen.
     */
    public boolean isShadowed(int row, int slot) {
        Section section = sections.get(row);
        ItemStack piece = piece(row);
        if (!(Modules.module(piece, slot).getItem() instanceof ModuleItem m) || m.kind.perPiece() || section.source() != 0) return false;
        if (!Modules.works(piece, slot)) return false;
        ExoSuit.Active active = ExoSuit.active(player);
        int best = active.level(m.kind);
        if (best <= 0 || !Modules.enabled(piece, slot)) return false;
        if (best > m.level) return true;
        // Same level elsewhere: the first piece in head-to-feet order counts.
        int counted = active.piece(m.kind);
        return counted != ExoSuit.index(section.slot()) || Modules.slotOf(piece, m.kind) != slot;
    }

    /** True when the module in this row and slot sits in the wrong piece or below its mark, so it does nothing. */
    public boolean isMisplaced(int row, int slot) {
        ItemStack piece = piece(row);
        return Modules.module(piece, slot).getItem() instanceof ModuleItem && !Modules.works(piece, slot);
    }

    /** One slot for one module. The rules (piece, mark, one per suit) are in {@link Modules#refusal}. */
    public static class ModuleSlot extends Slot {
        private final Player player;
        private final Section section;
        private final Supplier<ItemStack> piece;

        public ModuleSlot(Container container, int index, Player player, Section section, Supplier<ItemStack> piece, int x, int y) {
            super(container, index, x, y);
            this.player = player;
            this.section = section;
            this.piece = piece;
        }

        public EquipmentSlot piece() {
            return section.slot();
        }

        /** Why the stack cannot go here, or null. */
        @Nullable
        public Component refusal(ItemStack stack) {
            return Modules.refusal(piece.get(), getContainerSlot(), stack, section.others(player));
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !piece.get().isEmpty();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /** The chestplate's core socket: one Servo, Magma or Antigrav Core. */
    public static class CoreSlot extends Slot {
        private final Supplier<ItemStack> piece;

        public CoreSlot(Container container, Supplier<ItemStack> piece, int x, int y) {
            super(container, 0, x, y);
            this.piece = piece;
        }

        @Nullable
        public Component refusal(ItemStack stack) {
            return ExoData.coreRefusal(piece.get(), stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !piece.get().isEmpty();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /**
     * Server side backing of the module slots: a small container loaded from the piece's component and written back
     * on every change, always into the stack the menu was opened for. Switching state follows the module (a freshly
     * installed module starts on).
     */
    public static final class ModuleContainer extends SimpleContainer {
        private final Player player;
        private final Section section;
        private final Supplier<ItemStack> source;

        public ModuleContainer(Player player, Section section, Supplier<ItemStack> source) {
            super(Math.max(1, section.count()));
            this.player = player;
            this.section = section;
            this.source = source;
            ItemStack piece = source.get();
            for (int i = 0; i < section.count(); i++) setItem(i, Modules.module(piece, i).copy());
            addListener(this::writeBack);
        }

        private void writeBack(Container container) {
            ItemStack piece = source.get();
            if (piece.isEmpty()) return;
            boolean installed = false;
            for (int i = 0; i < section.count(); i++) {
                ItemStack stack = getItem(i);
                if (ItemStack.isSameItem(Modules.module(piece, i), stack)) continue;
                // A changed slot starts on; removing Capacitor Plating caps the stored energy (both in setModule).
                Modules.setModule(piece, i, stack);
                installed |= !stack.isEmpty();
            }
            if (installed && !player.level().isClientSide) {
                CoreSounds.play(player, CoreSounds.UPGRADE_INSTALL, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
    }

    /** Server side backing of the core socket, written into the stack the menu was opened for. */
    public static final class CoreContainer extends SimpleContainer {
        private final Player player;
        private final Supplier<ItemStack> source;

        public CoreContainer(Player player, Supplier<ItemStack> source) {
            super(1);
            this.player = player;
            this.source = source;
            setItem(0, ExoData.core(source.get()).copy());
            addListener(this::writeBack);
        }

        private void writeBack(Container container) {
            ItemStack piece = source.get();
            if (piece.isEmpty()) return;
            boolean had = !ExoData.core(piece).isEmpty();
            ExoData.setCore(piece, getItem(0));
            if (!had && !getItem(0).isEmpty() && !player.level().isClientSide) {
                CoreSounds.play(player, CoreSounds.UPGRADE_INSTALL, SoundSource.PLAYERS, 0.7F, 0.7F);
            }
        }
    }

    /** Double-click collecting never pulls installed modules out of the item. */
    @Override
    public boolean canTakeItemForPickAll(net.minecraft.world.item.ItemStack stack, net.minecraft.world.inventory.Slot slot) {
        return !(slot instanceof ModuleSlot || slot instanceof CoreSlot) && super.canTakeItemForPickAll(stack, slot);
    }
}
