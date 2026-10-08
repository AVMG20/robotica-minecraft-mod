package com.arno.robotica.energy.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.block.ReactorControllerBlockEntity.State;
import com.arno.robotica.energy.menu.ReactorMenu;
import com.arno.robotica.energy.net.ControllerActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fission Reactor GUI: temperature gauge with safe and SCRAM marks, FE buffer, output and heat balance, a control rod
 * slider (click, drag or scroll), fuel and waste slots with the burn bar, status and structure lines.
 */
public class ReactorScreen extends ControllerScreen<ReactorMenu> {
    private static final int GAUGE_X = 8, GAUGE_Y = 18, GAUGE_W = 12, GAUGE_H = 58;
    private static final int BUFFER_X = 24;
    private static final int TEXT_X = 42, TEXT_W = 86;
    private static final int SLIDER_X = 42, SLIDER_Y = 66, SLIDER_W = 86, SLIDER_H = 8;

    private FitButton resetButton;
    private int dragValue = -1;
    private long lastSent;

    public ReactorScreen(ReactorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = ReactorMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = ReactorMenu.PLAYER_Y - 11;
    }

    @Override
    protected int titleMaxWidth() {
        return 110;
    }

    @Override
    protected void init() {
        super.init();
        addShowButton(150, 92);
        resetButton = addRenderableWidget(new FitButton(leftPos + 98, topPos + 92, 48, 18, Component.translatable("gui.robotica.energy.reset"),
                b -> PacketDistributor.sendToServer(new ControllerActionPayload(menu.pos(), ControllerActionPayload.RESET_SCRAM, 0)),
                Component.translatable("gui.robotica.energy.reset_tip")));
        resetButton.visible = false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (resetButton != null) {
            resetButton.visible = menu.scrammed();
            resetButton.active = menu.canControl() && menu.temperature() <= menu.resetTemp();
        }
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index < 3) return Component.translatable("gui.robotica.energy.slot_fuel");
        if (slot.index < 6) return Component.translatable("gui.robotica.energy.slot_waste");
        return null;
    }

    private int shownRods() {
        return dragValue >= 0 ? dragValue : menu.rodInsertion();
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawTemperature(g, x + GAUGE_X, y + GAUGE_Y);
        Component bufferTip = Component.translatable("gui.robotica.energy.buffer_tip").withStyle(ChatFormatting.GRAY);
        drawEnergyBar(g, x + BUFFER_X, y + GAUGE_Y, 10, GAUGE_H, menu.energy(), menu.capacity(), false, bufferTip);

        // Output and heat balance
        int tx = x + TEXT_X;
        drawFitted(g, font, Component.literal(Fmt.compact(menu.fePerTick()) + " FE/t"), tx, y + 18, TEXT_W, 0xFF1E6B2A, -1, false, 1.0F);
        addTooltip(tx, y + 17, TEXT_W, 10, Component.translatable("gui.robotica.energy.output_tip"),
                Component.translatable("gui.robotica.energy.efficiency", menu.efficiency()).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.robotica.energy.throttle", menu.throttle()).withStyle(ChatFormatting.GRAY));
        small(g, Component.translatable("gui.robotica.energy.heat_line", Fmt.compact(menu.heat()), Fmt.compact(menu.cooling())), tx, y + 30, TEXT_W);
        addTooltip(tx, y + 29, TEXT_W, 8, Component.translatable("gui.robotica.energy.heat_tip"),
                Component.translatable("gui.robotica.energy.heat_tip2").withStyle(ChatFormatting.GRAY));
        small(g, Component.translatable("gui.robotica.energy.rods_line", menu.rods(), String.format(java.util.Locale.ROOT, "%.1f", menu.coolant()), menu.efficiency()), tx, y + 39, TEXT_W);
        addTooltip(tx, y + 38, TEXT_W, 8, Component.translatable("gui.robotica.energy.rods_tip"),
                Component.translatable("gui.robotica.energy.coolant_tip").withStyle(ChatFormatting.GRAY));

        // Control rods
        small(g, Component.translatable("gui.robotica.energy.control_rods", shownRods()), x + SLIDER_X, y + 56, SLIDER_W);
        drawSlider(g, x + SLIDER_X, y + SLIDER_Y);
        if (menu.canControl()) {
            addTooltip(x + SLIDER_X, y + SLIDER_Y - 1, SLIDER_W, SLIDER_H + 2, Component.translatable("gui.robotica.energy.rods_slider_tip"),
                    Component.translatable("gui.robotica.energy.rods_slider_tip2").withStyle(ChatFormatting.GRAY));
        } else {
            addTooltip(x + SLIDER_X, y + SLIDER_Y - 1, SLIDER_W, SLIDER_H + 2, Component.translatable("gui.robotica.energy.rods_slider_tip"),
                    Component.translatable("gui.robotica.energy.not_allowed").withStyle(ChatFormatting.RED));
        }

        // Fuel burn bar under the fuel column, labels above the slots
        drawProgress(g, x + ReactorMenu.FUEL_X, y + 74, 16, 3, menu.burn());
        Component burnTip = menu.burnHeat() > 0
                ? Component.translatable("gui.robotica.energy.burning", menu.burnHeat(), Math.round(menu.burn() * 100))
                : Component.translatable("gui.robotica.energy.not_burning");
        addTooltip(x + ReactorMenu.FUEL_X - 1, y + 73, 18, 5, burnTip);
        smallRight(g, Component.translatable("gui.robotica.energy.fuel_waste"), x + 168, y + 8, 40);

        // Status and structure
        State state = menu.state();
        Tone tone = switch (state) {
            case RUNNING -> menu.temperature() > menu.safeTemp() ? Tone.WARN : Tone.GOOD;
            case SCRAM, NOT_FORMED, NO_FUEL, WASTE_FULL -> Tone.BAD;
            default -> Tone.WARN;
        };
        String key = "gui.robotica.energy.reactor." + state.name().toLowerCase(java.util.Locale.ROOT);
        if (state == State.RUNNING && menu.temperature() > menu.safeTemp()) key = "gui.robotica.energy.reactor.hot";
        drawStatus(g, Component.translatable(key), x + 8, y + 82, 120, 1, tone);
        addTooltip(x + 8, y + 81, 120, 10, Component.translatable(key + ".tip"));
        drawStructure(g, x + 8, y + 95, 88, 2);
    }

    private void drawTemperature(GuiGraphics g, int gx, int gy) {
        int temp = menu.temperature(), safe = menu.safeTemp(), scram = menu.scramTemp();
        drawInset(g, gx, gy, GAUGE_W, GAUGE_H);
        g.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, 0xFF20262A);
        int filled = (int) Math.min(GAUGE_H, Math.round((double) GAUGE_H * Math.max(0, temp) / scram));
        if (filled > 0) {
            int top = temp > safe ? 0xFFFF6A3A : temp > safe * 0.6 ? 0xFFF2B33D : 0xFF6FD3F0;
            int bottom = temp > safe ? 0xFFC9302A : temp > safe * 0.6 ? 0xFFD2812F : 0xFF2594A8;
            g.fillGradient(gx, gy + GAUGE_H - filled, gx + GAUGE_W, gy + GAUGE_H, top, bottom);
        }
        int safeY = gy + GAUGE_H - Math.round((float) GAUGE_H * safe / scram);
        g.fill(gx, safeY, gx + GAUGE_W, safeY + 1, 0xFFF2B33D);
        g.fill(gx, gy, gx + GAUGE_W, gy + 1, 0xFFD83A30);
        addTooltip(gx - 1, gy - 1, GAUGE_W + 2, GAUGE_H + 2,
                Component.translatable("gui.robotica.energy.temperature", temp),
                Component.translatable("gui.robotica.energy.temperature_marks", safe, scram).withStyle(ChatFormatting.GRAY));
    }

    private void drawSlider(GuiGraphics g, int sx, int sy) {
        int value = shownRods();
        drawInset(g, sx, sy, SLIDER_W, SLIDER_H);
        g.fill(sx, sy, sx + SLIDER_W, sy + SLIDER_H, SLOT);
        int filled = Math.round(SLIDER_W * value / 100.0F);
        if (filled > 0) g.fillGradient(sx, sy, sx + filled, sy + SLIDER_H, 0xFF8D9599, 0xFF4D5558);
        for (int i = 1; i < 4; i++) g.fill(sx + SLIDER_W * i / 4, sy + SLIDER_H - 2, sx + SLIDER_W * i / 4 + 1, sy + SLIDER_H, 0x66000000);
        int knob = sx + Math.min(SLIDER_W - 3, Math.max(0, filled - 1));
        g.fill(knob - 1, sy - 2, knob + 3, sy + SLIDER_H + 2, BORDER);
        g.fill(knob, sy - 1, knob + 2, sy + SLIDER_H + 1, PANEL_LIGHT);
    }

    private boolean onSlider(double mx, double my) {
        int sx = leftPos + SLIDER_X, sy = topPos + SLIDER_Y;
        return mx >= sx - 2 && mx <= sx + SLIDER_W + 2 && my >= sy - 3 && my <= sy + SLIDER_H + 3;
    }

    private int sliderValue(double mx) {
        double t = (mx - (leftPos + SLIDER_X)) / SLIDER_W;
        return (int) Math.round(Math.max(0, Math.min(1, t)) * 100);
    }

    private void send(int value) {
        menu.data().putInt("rodsIn", value);   // keeps the knob in place until the next sync
        PacketDistributor.sendToServer(new ControllerActionPayload(menu.pos(), ControllerActionPayload.SET_RODS, value));
        lastSent = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && onSlider(mx, my) && menu.canControl()) {
            dragValue = sliderValue(mx);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragValue >= 0) {
            dragValue = sliderValue(mx);
            if (System.currentTimeMillis() - lastSent > 150) send(dragValue);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragValue >= 0) {
            send(dragValue);
            dragValue = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (onSlider(mx, my) && scrollY != 0 && menu.canControl()) {
            int step = hasShiftDown() ? 1 : 5;
            int next = Math.max(0, Math.min(100, menu.rodInsertion() + (scrollY > 0 ? step : -step)));
            menu.data().putInt("rodsIn", next);
            send(next);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}
