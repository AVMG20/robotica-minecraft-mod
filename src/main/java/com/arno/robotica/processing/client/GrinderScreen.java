package com.arno.robotica.processing.client;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.arno.robotica.processing.block.ProcessingMachineBlockEntity;
import com.arno.robotica.processing.menu.GrinderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class GrinderScreen extends MachineScreen<GrinderMenu> {
    public GrinderScreen(GrinderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == GrinderBlockEntity.INPUT) return new ItemStack(Items.RAW_IRON);
        if (slot.index == GrinderBlockEntity.MEDIA) return new ItemStack(Items.FLINT);
        if (slot.index >= GrinderBlockEntity.OUT_FIRST && slot.index < GrinderBlockEntity.OUT_FIRST + GrinderBlockEntity.OUT_COUNT) {
            return slot.index == GrinderBlockEntity.OUT_FIRST ? icon("iron_dust") : ItemStack.EMPTY;
        }
        if (slot.index == GrinderBlockEntity.BATTERY) return icon("copper_cell");
        return icon("upgrade_speed");
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == GrinderBlockEntity.INPUT) return Component.translatable("gui.robotica.processing.slot_grind");
        if (slot.index == GrinderBlockEntity.MEDIA) return Component.translatable("gui.robotica.processing.slot_media");
        if (slot.index == GrinderBlockEntity.BATTERY) return Component.translatable("gui.robotica.slot_battery");
        if (slot.index > GrinderBlockEntity.BATTERY) return Component.translatable("gui.robotica.processing.slot_upgrade_grinder");
        return Component.translatable("gui.robotica.slot_output");
    }

    /** Upgrade cards (in the slots or the inventory) say what they do in this Grinder. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (stack.getItem() instanceof UpgradeCardItem card) {
            lines = new ArrayList<>(lines);
            lines.addAll(ProcessingMachineBlockEntity.cardHelp(ProcessingMachineBlock.Kind.GRINDER, menu.tier(), card.getKind()));
        }
        return lines;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 8, y + 17, 10, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.processing.using", menu.lastUse()));
        drawArrow(g, x + 50, y + 18, menu.progress());

        int uses = menu.mediaUses();
        if (uses > 0) {
            int bx = x + 45, by = y + 41, h = 16;
            drawInset(g, bx, by, 3, h);
            g.fill(bx, by, bx + 3, by + h, SLOT_DARK);
            int filled = Math.round(h * Math.min(1.0F, (float) menu.mediaLeft() / uses));
            if (filled > 0) g.fillGradient(bx, by + h - filled, bx + 3, by + h, PROGRESS_TOP, PROGRESS_BOTTOM);
            addTooltip(bx - 1, by - 1, 5, h + 2, Component.translatable("gui.robotica.processing.media_left", menu.mediaLeft(), uses));
        }

        Tone tone = switch (menu.status()) {
            case WORKING -> Tone.GOOD;
            case NO_ENERGY, NEEDS_TIER -> Tone.BAD;
            default -> Tone.WARN;
        };
        Component text = menu.status() == ProcessingMachineBlockEntity.Status.NEEDS_TIER
                ? Component.translatable("gui.robotica.processing.status.needs_tier", menu.neededTier())
                : menu.status() == ProcessingMachineBlockEntity.Status.WORKING
                ? Component.translatable("gui.robotica.processing.status.working_fe", menu.lastUse())
                : Component.translatable("gui.robotica.processing.status." + menu.status().name().toLowerCase(java.util.Locale.ROOT));
        drawStatus(g, text, x + 54, y + 41, 114, 1, tone);
    }
}
