package com.arno.robotica.exo.menu;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoRegistry;
import com.arno.robotica.exo.ExoRules;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Module screen of the Exo-Frame: one row per armor piece with up to four module slots, an on/off switch per module
 * and, on chestplates from Mk2 on, the core socket. Opened for all four worn pieces with the J key (absent pieces get
 * an empty row), or for one piece by sneak + right-click with the piece in hand. The switches are menu buttons
 * ({@link #clickMenuButton}), so the server validates every click. The slots write straight into the piece's data
 * components; the energy and everything else on the piece is never touched (except that removing Capacitor Plating
 * caps the stored energy at the smaller battery).
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

    public ExoMenu(int id, Inventory inv, List<Section> sections, boolean server) {
        super(ExoRegistry.EXO_MENU.get(), id);
        this.player = inv.player;
        this.sections = List.copyOf(sections);
        this.firstSlot = new int[sections.size()];
        int index = 0;
        for (int r = 0; r < sections.size(); r++) {
            Section section = sections.get(r);
            firstSlot[r] = index;
            int y = rowY(r) + 3;
            Container container = server ? new ModuleContainer(inv.player, section) : new SimpleContainer(Math.max(1, section.count()));
            for (int i = 0; i < section.count(); i++) {
                addSlot(new ModuleSlot(container, i, inv.player, section, SLOT_X + i * SLOT_STEP, y));
                index++;
            }
            if (section.core()) {
                Container core = server ? new CoreContainer(inv.player, section) : new SimpleContainer(1);
                addSlot(new CoreSlot(core, inv.player, section, CORE_X, y));
                index++;
            }
        }
        addPlayerInventory(inv, (WIDTH - 162) / 2, inventoryY(sections.size()));
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
                    ? new Section(slot, 0, item.moduleSlots(), item.hasCoreSocket())
                    : new Section(slot, 0, 0, false));
        }
        open(player, list);
    }

    public static void openForHand(ServerPlayer player, InteractionHand hand) {
        ItemStack piece = player.getItemInHand(hand);
        if (!(piece.getItem() instanceof ExoArmorItem item)) return;
        open(player, List.of(new Section(item.getEquipmentSlot(), hand == InteractionHand.MAIN_HAND ? 1 : 2, item.moduleSlots(), item.hasCoreSocket())));
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
        for (Section section : sections) {
            if (section.count() > 0 && section.resolve(player).isEmpty()) return false;
        }
        return true;
    }

    /** Button id = row * 4 + module slot: switches that module on or off. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int row = id / ExoArmorItem.MAX_SLOTS;
        int slot = id % ExoArmorItem.MAX_SLOTS;
        if (id < 0 || row >= sections.size() || slot >= sections.get(row).count()) return false;
        ItemStack piece = sections.get(row).resolve(player);
        if (piece.isEmpty() || ExoData.kind(piece, slot) == null) return false;
        boolean on = !ExoData.isEnabled(piece, slot);
        ExoData.setEnabled(piece, slot, on);
        if (!player.level().isClientSide) {
            CoreSounds.play(player, CoreSounds.TOOL_MODE, SoundSource.PLAYERS, 0.5F, on ? 1.3F : 0.8F);
        }
        return true;
    }

    /**
     * True when the module in this row and slot is a duplicate of a kind that already counts elsewhere in the suit
     * (so it does nothing). Used by the screen.
     */
    public boolean isShadowed(int row, int slot) {
        Section section = sections.get(row);
        ItemStack piece = section.resolve(player);
        if (!(ExoData.module(piece, slot).getItem() instanceof ExoModuleItem m) || m.kind.perPiece() || section.source() != 0) return false;
        ExoSuit.Active active = ExoSuit.active(player);
        int best = active.level(m.kind);
        if (best <= 0 || !ExoData.isEnabled(piece, slot)) return false;
        if (best > m.level) return true;
        // Same level elsewhere: the first piece in head-to-feet order counts.
        int counted = active.piece(m.kind);
        return counted != ExoModuleKind.slotIndex(section.slot()) || ExoData.slotOf(piece, m.kind) != slot;
    }

    /** One slot for one module. The rules (piece, mark, one per suit) are in {@link ExoRules}. */
    public static class ModuleSlot extends Slot {
        private final Player player;
        private final Section section;

        public ModuleSlot(Container container, int index, Player player, Section section, int x, int y) {
            super(container, index, x, y);
            this.player = player;
            this.section = section;
        }

        public EquipmentSlot piece() {
            return section.slot();
        }

        /** Why the stack cannot go here, or null. */
        @Nullable
        public Component refusal(ItemStack stack) {
            return ExoRules.moduleRefusal(section.resolve(player), getContainerSlot(), stack, section.others(player));
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
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
        private final Player player;
        private final Section section;

        public CoreSlot(Container container, Player player, Section section, int x, int y) {
            super(container, 0, x, y);
            this.player = player;
            this.section = section;
        }

        @Nullable
        public Component refusal(ItemStack stack) {
            return ExoRules.coreRefusal(section.resolve(player), stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return refusal(stack) == null;
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
     * on every change. Switching state follows the module (a freshly installed module starts on).
     */
    public static final class ModuleContainer extends SimpleContainer {
        private final Player player;
        private final Section section;

        public ModuleContainer(Player player, Section section) {
            super(Math.max(1, section.count()));
            this.player = player;
            this.section = section;
            ItemStack piece = section.resolve(player);
            for (int i = 0; i < section.count(); i++) setItem(i, ExoData.module(piece, i).copy());
            addListener(this::writeBack);
        }

        private void writeBack(Container container) {
            ItemStack piece = section.resolve(player);
            if (piece.isEmpty()) return;
            List<ItemStack> before = ExoData.modules(piece);
            List<ItemStack> now = new ArrayList<>();
            boolean installed = false;
            for (int i = 0; i < section.count(); i++) {
                ItemStack stack = getItem(i);
                now.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                ItemStack was = i < before.size() ? before.get(i) : ItemStack.EMPTY;
                if (!ItemStack.isSameItem(was, stack)) {
                    ExoData.setEnabled(piece, i, true);
                    installed |= !stack.isEmpty();
                }
            }
            ExoData.setModules(piece, now);
            // Removing Capacitor Plating shrinks the battery: cap the stored energy.
            if (ItemEnergy.get(piece) > ItemEnergy.capacity(piece)) ItemEnergy.set(piece, ItemEnergy.capacity(piece));
            if (installed && !player.level().isClientSide) {
                CoreSounds.play(player, CoreSounds.UPGRADE_INSTALL, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
    }

    /** Server side backing of the core socket. */
    public static final class CoreContainer extends SimpleContainer {
        private final Player player;
        private final Section section;

        public CoreContainer(Player player, Section section) {
            super(1);
            this.player = player;
            this.section = section;
            setItem(0, ExoData.core(section.resolve(player)).copy());
            addListener(this::writeBack);
        }

        private void writeBack(Container container) {
            ItemStack piece = section.resolve(player);
            if (piece.isEmpty()) return;
            boolean had = !ExoData.core(piece).isEmpty();
            ExoData.setCore(piece, getItem(0));
            if (!had && !getItem(0).isEmpty() && !player.level().isClientSide) {
                CoreSounds.play(player, CoreSounds.UPGRADE_INSTALL, SoundSource.PLAYERS, 0.7F, 0.7F);
            }
        }
    }
}
