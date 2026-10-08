package com.arno.robotica.energy.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.block.FusionControllerBlockEntity.State;
import com.arno.robotica.energy.menu.FusionMenu;
import com.arno.robotica.energy.net.ControllerActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fusion Reactor GUI: ignition charge, plasma, output and buffer, fuel slots, an on/off switch, status and structure. */
public class FusionScreen extends ControllerScreen<FusionMenu> {
    private FitButton powerButton;
    private Boolean shownEnabled;
    private boolean shownAllowed;

    public FusionScreen(FusionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = FusionMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = FusionMenu.PLAYER_Y - 11;
    }

    @Override
    protected int titleMaxWidth() {
        return 130;
    }

    @Override
    protected void init() {
        super.init();
        addShowButton(150, 92);
        powerButton = addRenderableWidget(new FitButton(leftPos + 98, topPos + 92, 48, 18, Component.empty(),
                b -> PacketDistributor.sendToServer(new ControllerActionPayload(menu.pos(), ControllerActionPayload.SET_ENABLED, menu.enabled() ? 0 : 1))));
        shownEnabled = null;
        shownAllowed = false;
        updateButton();
    }

    private void updateButton() {
        boolean on = menu.enabled();
        boolean allowed = menu.canControl();
        if (shownEnabled != null && shownEnabled == on && shownAllowed == allowed) return;
        shownEnabled = on;
        shownAllowed = allowed;
        powerButton.active = allowed;
        powerButton.setMessage(Component.translatable(on ? "gui.robotica.energy.fusion_on" : "gui.robotica.energy.fusion_off"));
        powerButton.setTooltip(Tooltip.create(Component.translatable(!allowed ? "gui.robotica.energy.not_allowed"
                : on ? "gui.robotica.energy.fusion_power_tip_on" : "gui.robotica.energy.fusion_power_tip_off")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (powerButton != null) updateButton();
    }

    @Override
    protected Component slotHint(Slot slot) {
        return slot.index < 3 ? Component.translatable("gui.robotica.energy.slot_fusion_fuel") : null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int w = 136;
        boolean ignited = menu.ignited();
        if (ignited) {
            small(g, Component.translatable("gui.robotica.energy.fusion_ignited"), x + 8, y + 19, w);
        } else {
            small(g, Component.translatable("gui.robotica.energy.fusion_charge", Fmt.energy(menu.charge()), Fmt.energy(menu.ignition())), x + 8, y + 19, w);
        }
        drawEnergyBarWide(g, x + 8, y + 28, w, 8, ignited ? menu.ignition() : menu.charge(), Math.max(1, menu.ignition()));
        addTooltip(x + 8, y + 18, w, 9, Component.translatable("gui.robotica.energy.fusion_charge_tip"));

        small(g, Component.translatable("gui.robotica.energy.fusion_plasma", menu.plasma()), x + 8, y + 41, w);
        drawPlasma(g, x + 8, y + 50, w, 8, menu.plasma() / 100.0F);
        addTooltip(x + 8, y + 40, w, 19, Component.translatable("gui.robotica.energy.fusion_plasma_tip"));

        drawFitted(g, font, Component.literal(Fmt.compact(menu.fePerTick()) + " FE/t"), x + 8, y + 63, w, 0xFF1E6B2A, -1, false, 1.0F);
        drawEnergyBarWide(g, x + 8, y + 74, w, 5, menu.energy(), menu.capacity());

        drawProgress(g, x + FusionMenu.FUEL_X, y + 74, 16, 3, menu.burn());
        addTooltip(x + FusionMenu.FUEL_X - 1, y + 73, 18, 5, menu.burnPower() > 0
                ? Component.translatable("gui.robotica.energy.fusion_burning", Fmt.compact(menu.burnPower()), Math.round(menu.burn() * 100))
                : Component.translatable("gui.robotica.energy.not_burning"));

        State state = menu.state();
        Tone tone = switch (state) {
            case RUNNING -> Tone.GOOD;
            case NOT_FORMED, NO_FUEL, STARVING -> Tone.BAD;
            default -> Tone.WARN;
        };
        String key = "gui.robotica.energy.fusion." + state.name().toLowerCase(java.util.Locale.ROOT);
        drawStatus(g, Component.translatable(key), x + 8, y + 82, 136, 1, tone);
        addTooltip(x + 8, y + 81, 136, 10, Component.translatable(key + ".tip"), Component.translatable("gui.robotica.energy.fusion_shape").withStyle(ChatFormatting.GRAY));
        drawStructure(g, x + 8, y + 95, 88, 2);
    }

    /** Plasma bar: violet to white-hot. */
    private void drawPlasma(GuiGraphics g, int px, int py, int w, int h, float level) {
        drawInset(g, px, py, w, h);
        g.fill(px, py, px + w, py + h, 0xFF1E0F3A);
        int filled = Math.round(w * Math.max(0, Math.min(1, level)));
        if (filled > 0) g.fillGradient(px, py, px + filled, py + h, 0xFFECE2FF, 0xFF6A3FB0);
    }
}
