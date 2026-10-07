package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity.RigState;
import com.arno.robotica.automation.menu.SurveyRigMenu;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Survey Rig GUI: energy, battery and core, a scanner screen showing the last ore, the progress bar, upgrades and the buffer. */
public class SurveyRigScreen extends MachineScreen<SurveyRigMenu> {
    private static final int SCREEN_BG = 0xFF0C1B1E;
    private static final int SCREEN_GRID = 0xFF173236;
    private static final int SCAN_GLOW = 0x605FE3F0;

    public SurveyRigScreen(SurveyRigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 176;
        this.inventoryLabelY = 83;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("copper_cell");
        if (slot.index == SurveyRigMenu.SLOT_CORE) return icon("magma_core");
        if (slot.index < SurveyRigMenu.FIRST_UPGRADE + menu.be.upgrades.getSlots()) return icon("upgrade_speed");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.slot_battery");
        if (slot.index == SurveyRigMenu.SLOT_CORE) return Component.translatable("gui.robotica.survey.slot_core");
        if (slot.index < SurveyRigMenu.FIRST_UPGRADE + menu.be.upgrades.getSlots()) return Component.translatable("gui.robotica.slot_upgrade");
        return null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    private String stateKey() {
        AreaWorkerBlockEntity.Status status = menu.status();
        if (status == AreaWorkerBlockEntity.Status.NO_ENERGY) return "no_energy";
        if (status == AreaWorkerBlockEntity.Status.OUTPUT_FULL) return "output_full";
        return menu.rigState().name().toLowerCase(Locale.ROOT);
    }

    private Tone tone() {
        AreaWorkerBlockEntity.Status status = menu.status();
        if (status == AreaWorkerBlockEntity.Status.NO_ENERGY) return Tone.BAD;
        if (status == AreaWorkerBlockEntity.Status.OUTPUT_FULL) return Tone.WARN;
        return switch (menu.rigState()) {
            case MINING -> Tone.GOOD;
            case NO_ORES -> Tone.BAD;
            case WAITING -> Tone.WARN;
        };
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 18, 12, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.survey.fe_per_tick", Fmt.energy(menu.energyPerTick())));

        // Scanner screen with the last ore
        int sx = x + 64, sy = y + 17, sw = 44, sh = 18;
        drawScanner(g, sx, sy, sw, sh);
        Item last = menu.lastOre();
        if (last != null) {
            g.renderItem(new ItemStack(last), sx + (sw - 16) / 2, sy + 1);
            addTooltip(sx, sy, sw, sh, Component.translatable("gui.robotica.survey.last_ore", last.getDescription()),
                    Component.translatable("gui.robotica.survey.chance", String.format(Locale.ROOT, "%.2f", menu.lastOreChance() / 100.0))
                            .withStyle(ChatFormatting.GRAY));
        }

        // Progress to the next ore
        int bx = x + 26, by = y + 41, bw = 82, bh = 5;
        drawProgress(g, bx, by, bw, bh, menu.progress() / 100.0F);
        addTooltip(bx - 1, by - 2, bw + 2, bh + 4, Component.translatable("gui.robotica.survey.per_ore",
                String.format(Locale.ROOT, "%.1f", menu.interval() / 20.0), Fmt.energy((long) menu.energyPerTick() * menu.interval())));

        String key = stateKey();
        drawStatus(g, Component.translatable("gui.robotica.survey." + key), x + 9, y + 74, 100, 1, tone());
        addTooltip(x + 9, y + 72, 100, 11, Component.translatable("gui.robotica.survey_hint." + key));
    }

    /** A small radar screen with a sweeping line while the rig works. */
    private void drawScanner(GuiGraphics g, int x, int y, int w, int h) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, SCREEN_BG);
        for (int gx = x + 4; gx < x + w; gx += 8) g.fill(gx, y, gx + 1, y + h, SCREEN_GRID);
        for (int gy = y + 4; gy < y + h; gy += 6) g.fill(x, gy, x + w, gy + 1, SCREEN_GRID);
        if (menu.rigState() == RigState.MINING && menu.status() == AreaWorkerBlockEntity.Status.WORKING) {
            int lx = x + (int) ((Util.getMillis() / 30) % w);
            g.fill(lx, y, lx + 1, y + h, SCAN_GLOW);
        }
    }
}
