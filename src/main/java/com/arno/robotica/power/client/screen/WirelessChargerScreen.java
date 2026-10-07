package com.arno.robotica.power.client.screen;

import com.arno.robotica.core.client.MachineScreen;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.power.block.WirelessChargerBlockEntity;
import com.arno.robotica.power.menu.WirelessChargerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Wireless Charger GUI: energy bar, range and rate, FE/t delivered, the players in range and two card slots. */
public class WirelessChargerScreen extends MachineScreen<WirelessChargerMenu> {
    private static final int ROWS = 3;

    public WirelessChargerScreen(WirelessChargerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int titleMaxWidth() {
        return 140;
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return icon(slot.index == 0 ? "upgrade_speed" : "upgrade_range");
    }

    @Override
    protected Component slotHint(Slot slot) {
        return Component.translatable("gui.robotica.wireless.slot");
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + 9, y + 17, 12, 52, menu.energy(), menu.capacity());
        drawLabel(g, Component.translatable("gui.robotica.wireless.stats", menu.range(), Fmt.energy(menu.rate())), x + 26, y + 18, 120);

        WirelessChargerBlockEntity be = menu.charger();
        int[] ids = be == null ? new int[0] : be.targetIds();
        byte[] states = be == null ? new byte[0] : be.targetStates();
        int charging = 0;
        for (byte s : states) if (s == WirelessChargerBlockEntity.CHARGING) charging++;
        Component status;
        Tone tone;
        if (charging > 0) {
            status = Component.translatable("gui.robotica.wireless.charging", charging, Fmt.energy(menu.fePerTick()));
            tone = Tone.GOOD;
        } else if (menu.energy() <= 0) {
            status = Component.translatable("gui.robotica.status.no_energy");
            tone = Tone.BAD;
        } else {
            status = Component.translatable(ids.length == 0 ? "gui.robotica.wireless.nobody" : "gui.robotica.status.idle");
            tone = Tone.WARN;
        }
        drawStatus(g, status, x + 26, y + 29, 120, 1, tone);

        // players in range, nearest first
        drawInset(g, x + 26, y + 40, 120, ROWS * 9 + 2);
        if (ids.length == 0) {
            drawLabel(g, Component.translatable("gui.robotica.wireless.empty"), x + 30, y + 46, 112);
        }
        for (int i = 0; i < Math.min(ROWS, ids.length); i++) {
            byte state = i < states.length ? states[i] : WirelessChargerBlockEntity.IDLE;
            Entity entity = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(ids[i]);
            Component name = entity == null ? Component.literal("?") : entity.getName();
            Tone rowTone = switch (state) {
                case WirelessChargerBlockEntity.CHARGING -> Tone.GOOD;
                case WirelessChargerBlockEntity.DENIED, WirelessChargerBlockEntity.NO_POWER -> Tone.BAD;
                default -> Tone.WARN;
            };
            String key = switch (state) {
                case WirelessChargerBlockEntity.CHARGING -> "charging_one";
                case WirelessChargerBlockEntity.DENIED -> "denied";
                case WirelessChargerBlockEntity.NO_POWER -> "no_power";
                default -> "full";
            };
            int ry = y + 42 + i * 9;
            drawStatus(g, name, x + 29, ry, 66, 1, rowTone);
            drawLabelRight(g, Component.translatable("gui.robotica.wireless." + key), x + 144, ry, 46);
        }
        if (ids.length > ROWS) {
            addTooltip(x + 26, y + 40, 120, ROWS * 9 + 2, Component.translatable("gui.robotica.wireless.more", ids.length - ROWS));
        }
    }
}
