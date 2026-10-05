package com.arno.robotica.automation.client;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity.RigState;
import com.arno.robotica.automation.menu.SurveyRigMenu;
import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Survey Rig GUI: energy, battery and core, a small scanner screen, the ledger bar, upgrades and the buffer. */
public class SurveyRigScreen extends MachineScreen<SurveyRigMenu> {
    private static final int SCREEN_BG = 0xFF0C1B1E;
    private static final int SCREEN_GRID = 0xFF173236;
    private static final int SCAN = 0xFF5FE3F0;
    private static final int SCAN_GLOW = 0x605FE3F0;
    private static final int ORE = 0xFFF2D27A;
    private static final int DONE = 0xFF3DBE5A;
    private static final int REFUSED = 0xFFD83A30;

    public SurveyRigScreen(SurveyRigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 176;
        this.inventoryLabelY = 83;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("copper_cell");
        if (slot.index == SurveyRigMenu.SLOT_CORE) return icon("magma_core");
        if (slot.index < SurveyRigMenu.FIRST_UPGRADE + menu.be.upgradeSlotCount()) return icon("upgrade_speed");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.slot_battery");
        if (slot.index == SurveyRigMenu.SLOT_CORE) return Component.translatable("gui.robotica.survey.slot_core");
        if (slot.index < SurveyRigMenu.FIRST_UPGRADE + menu.be.upgradeSlotCount()) return Component.translatable("gui.robotica.slot_upgrade");
        return null;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    private String stateKey() {
        AreaWorkerBlockEntity.Status status = menu.status();
        RigState state = menu.rigState();
        if (status == AreaWorkerBlockEntity.Status.NO_ENERGY) return "no_energy";
        if (status == AreaWorkerBlockEntity.Status.OUTPUT_FULL) return "output_full";
        return state.name().toLowerCase(Locale.ROOT);
    }

    private Tone tone() {
        AreaWorkerBlockEntity.Status status = menu.status();
        if (status == AreaWorkerBlockEntity.Status.NO_ENERGY) return Tone.BAD;
        if (status == AreaWorkerBlockEntity.Status.OUTPUT_FULL) return Tone.WARN;
        return switch (menu.rigState()) {
            case SCANNING, MINING, FINISHED -> Tone.GOOD;
            case SURVEYED, BUSY -> Tone.BAD;
            case WAITING, NEEDS_CORE -> Tone.WARN;
        };
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 18, 12, 52, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.survey.fe_per_ore", Fmt.energy(menu.energyPerOre())));
        drawLabelRight(g, Component.translatable("gui.robotica.area", 16, 16), x + imageWidth - 8, y + 6, 40);

        // Scanner screen
        int sx = x + 64, sy = y + 17, sw = 44, sh = 18;
        drawScanner(g, sx, sy, sw, sh);
        String key = stateKey();
        addTooltip(sx, sy, sw, sh, Component.translatable("gui.robotica.survey." + key),
                Component.translatable("gui.robotica.survey_hint." + key).withStyle(net.minecraft.ChatFormatting.GRAY));

        // Ledger bar: ores mined of the total, the scan's progress while scanning
        RigState state = menu.rigState();
        int bx = x + 26, by = y + 41, bw = 82, bh = 5;
        boolean scanning = state == RigState.SCANNING || state == RigState.WAITING;
        int total = menu.total();
        int left = menu.left();
        float fill = scanning ? menu.scanPercent() / 100.0F : total <= 0 ? 1.0F : (float) left / total;
        drawProgress(g, bx, by, bw, bh, fill);
        Component tip = scanning
                ? Component.translatable("gui.robotica.survey.scan_progress", menu.scanPercent(), total)
                : Component.translatable("gui.robotica.survey.ores_left", left, total);
        addTooltip(bx - 1, by - 2, bw + 2, bh + 4, tip);

        Component statusText = Component.translatable("gui.robotica.survey." + key);
        drawStatus(g, statusText, x + 9, y + 74, 100, 1, tone());
        addTooltip(x + 9, y + 72, 100, 11, Component.translatable("gui.robotica.survey_hint." + key));
    }

    /** A small radar screen: a sweeping line while scanning, ore blips while mining, a mark when done or refused. */
    private void drawScanner(GuiGraphics g, int x, int y, int w, int h) {
        drawInset(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, SCREEN_BG);
        for (int gx = x + 4; gx < x + w; gx += 8) g.fill(gx, y, gx + 1, y + h, SCREEN_GRID);
        for (int gy = y + 4; gy < y + h; gy += 6) g.fill(x, gy, x + w, gy + 1, SCREEN_GRID);
        long t = Util.getMillis();
        RigState state = menu.rigState();
        boolean powered = menu.status() != AreaWorkerBlockEntity.Status.NO_ENERGY;
        switch (state) {
            case SCANNING -> {
                // The scan goes down through the chunk: the line sweeps top to bottom.
                int ly = y + (int) ((t / 40) % h);
                if (powered) {
                    g.fill(x, Math.max(y, ly - 2), x + w, ly, SCAN_GLOW);
                    g.fill(x, ly, x + w, ly + 1, SCAN);
                }
                blips(g, x, y, w, h, Math.min(12, menu.total()), t, false);
            }
            case MINING, NEEDS_CORE -> {
                int total = Math.max(1, menu.total());
                int shown = menu.left() <= 0 ? 0 : Math.max(1, Math.round(14.0F * menu.left() / total));
                blips(g, x, y, w, h, shown, t, powered);
                if (powered && state == RigState.MINING) {
                    int lx = x + (int) ((t / 30) % w);
                    g.fill(lx, y, lx + 1, y + h, SCAN_GLOW);
                }
            }
            case FINISHED -> {
                int cx = x + w / 2, cy = y + h / 2;
                for (int i = 0; i < 4; i++) g.fill(cx - 5 + i, cy + i - 1, cx - 4 + i, cy + i + 1, DONE);
                for (int i = 0; i < 7; i++) g.fill(cx - 1 + i, cy + 2 - i, cx + i, cy + 4 - i, DONE);
            }
            case SURVEYED, BUSY -> {
                int cx = x + w / 2, cy = y + h / 2;
                for (int i = -4; i <= 4; i++) {
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, REFUSED);
                    g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, REFUSED);
                }
            }
            case WAITING -> {
            }
        }
    }

    /** Fixed pseudo-random ore blips; one blinks while the rig works. */
    private static void blips(GuiGraphics g, int x, int y, int w, int h, int count, long t, boolean blink) {
        int lit = blink ? (int) ((t / 250) % Math.max(1, count)) : -1;
        for (int i = 0; i < count; i++) {
            int bx = x + 2 + Math.floorMod(i * 37 + 11, w - 4);
            int by = y + 2 + Math.floorMod(i * 23 + 5, h - 4);
            int color = i == lit ? 0xFFFFFFFF : ORE;
            g.fill(bx, by, bx + 2, by + 2, color);
        }
    }
}
