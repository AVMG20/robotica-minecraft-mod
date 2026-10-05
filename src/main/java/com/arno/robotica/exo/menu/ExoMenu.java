package com.arno.robotica.exo.menu;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoRegistry;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Module screen of the Exo-Frame: one row per armor piece with its module slots. Opened for one piece by sneak +
 * right-click with the piece in hand, or for all worn pieces with the J key. The switches are menu buttons
 * ({@link #clickMenuButton}), so the server validates every click. The module slots write straight into the piece's
 * data component; the energy and everything else on the piece is never touched.
 */
public class ExoMenu extends MachineMenu {
    /** Where a section's armor piece lives: 0 worn in its slot, 1 main hand, 2 off hand. */
    public record Section(EquipmentSlot slot, int source, int count) {
        public ItemStack resolve(Player player) {
            ItemStack stack = switch (source) {
                case 1 -> player.getMainHandItem();
                case 2 -> player.getOffhandItem();
                default -> player.getItemBySlot(slot);
            };
            return stack.getItem() instanceof ExoArmorItem item && item.getEquipmentSlot() == slot ? stack : ItemStack.EMPTY;
        }
    }

    public static final int ROW_HEIGHT = 22;
    public static final int FIRST_ROW_Y = 18;

    public final List<Section> sections;
    /** Index of the first module slot of each section in {@link #slots}. */
    public final int[] firstSlot;

    public ExoMenu(int id, Inventory inv, List<Section> sections, boolean server) {
        super(ExoRegistry.EXO_MENU.get(), id);
        this.sections = List.copyOf(sections);
        this.firstSlot = new int[sections.size()];
        int index = 0;
        for (int r = 0; r < sections.size(); r++) {
            Section section = sections.get(r);
            firstSlot[r] = index;
            Container container = server ? new ModuleContainer(inv.player, section) : new SimpleContainer(section.count());
            for (int i = 0; i < section.count(); i++) {
                addSlot(new ModuleSlot(container, i, section.slot(), 33 + i * 56, FIRST_ROW_Y + r * ROW_HEIGHT + 2));
                index++;
            }
        }
        addPlayerInventory(inv, 8, 35 + ROW_HEIGHT * sections.size());
    }

    // ---------------------------------------------------------------- opening

    public static void openWorn(ServerPlayer player) {
        List<Section> list = new ArrayList<>();
        for (EquipmentSlot slot : ExoSuit.SLOTS) {
            ItemStack piece = ExoSuit.piece(player, slot);
            if (!piece.isEmpty()) list.add(new Section(slot, 0, ExoData.slotCount(piece)));
        }
        open(player, list);
    }

    public static void openForHand(ServerPlayer player, InteractionHand hand) {
        ItemStack piece = player.getItemInHand(hand);
        if (!(piece.getItem() instanceof ExoArmorItem item)) return;
        open(player, List.of(new Section(item.getEquipmentSlot(), hand == InteractionHand.MAIN_HAND ? 1 : 2, item.moduleSlots())));
    }

    private static void open(ServerPlayer player, List<Section> sections) {
        if (sections.isEmpty()) return;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ExoMenu(id, inv, sections, true),
                Component.translatable("container.robotica.exo")), buf -> writeSections(buf, sections));
    }

    private static void writeSections(RegistryFriendlyByteBuf buf, List<Section> sections) {
        buf.writeVarInt(sections.size());
        for (Section s : sections) {
            buf.writeVarInt(s.slot().ordinal());
            buf.writeVarInt(s.source());
            buf.writeVarInt(s.count());
        }
    }

    public static List<Section> readSections(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4);
        List<Section> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            EquipmentSlot slot = EquipmentSlot.values()[Math.floorMod(buf.readVarInt(), EquipmentSlot.values().length)];
            list.add(new Section(slot, buf.readVarInt(), Math.min(buf.readVarInt(), 2)));
        }
        return list;
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    public boolean stillValid(Player player) {
        for (Section section : sections) {
            if (section.resolve(player).isEmpty()) return false;
        }
        return true;
    }

    /** Button id = section * 2 + module slot: switches that module on or off. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int row = id / 2;
        int slot = id % 2;
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

    /** One slot for one module. Accepts only modules of the piece's kind and never two of the same kind in one piece. */
    public static class ModuleSlot extends Slot {
        private final EquipmentSlot piece;

        public ModuleSlot(Container container, int index, EquipmentSlot piece, int x, int y) {
            super(container, index, x, y);
            this.piece = piece;
        }

        public EquipmentSlot piece() {
            return piece;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof ExoModuleItem module) || module.kind.slot != piece) return false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (i != getContainerSlot() && container.getItem(i).getItem() == stack.getItem()) return false;
            }
            return true;
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
            super(section.count());
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
                ExoModuleKind was = before.get(i).getItem() instanceof ExoModuleItem m ? m.kind : null;
                ExoModuleKind is = stack.getItem() instanceof ExoModuleItem m ? m.kind : null;
                if (was != is) {
                    ExoData.setEnabled(piece, i, true);
                    installed |= is != null;
                }
            }
            ExoData.setModules(piece, now);
            if (installed && !player.level().isClientSide) {
                CoreSounds.play(player, CoreSounds.UPGRADE_INSTALL, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
    }
}
