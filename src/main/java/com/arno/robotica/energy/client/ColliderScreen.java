package com.arno.robotica.energy.client;

import com.arno.robotica.core.client.FitButton;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.block.ColliderBlockEntity.State;
import com.arno.robotica.energy.menu.ColliderMenu;
import com.arno.robotica.energy.net.ControllerActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Ring Collider GUI: spin-up charge or beam strength, the ring, Strange Matter progress, output and buffer, fuel and
 * matter slots, an on/off switch, status and structure.
 */
public class ColliderScreen extends ControllerScreen<ColliderMenu> {
    private static final int W = 120;
    private FitButton powerButton;
    private Boolean shownEnabled;
    private boolean shownAllowed;

    public ColliderScreen(ColliderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = ColliderMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = ColliderMenu.PLAYER_Y - 11;
    }

    @Override
    protected int titleMaxWidth() {
        return 110;
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
        powerButton.setMessage(Component.translatable(on ? "gui.robotica.energy.collider_on" : "gui.robotica.energy.collider_off"));
        powerButton.setTooltip(Tooltip.create(Component.translatable(!allowed ? "gui.robotica.energy.not_allowed"
                : on ? "gui.robotica.energy.collider_power_tip_on" : "gui.robotica.energy.collider_power_tip_off")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (powerButton != null) updateButton();
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index < 3 ? icon("fusion_fuel_pellet") : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index < 3) return Component.translatable("gui.robotica.energy.slot_collider_fuel");
        if (slot.index < 6) return Component.translatable("gui.robotica.energy.slot_matter");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int tx = x + 8;
        if (menu.beamOn()) {
            small(g, Component.translatable("gui.robotica.energy.collider.beam_line", menu.beam()), tx, y + 19, W);
            drawBeam(g, tx, y + 28, W, 8, menu.beam() / 100.0F);
            addTooltip(tx, y + 18, W, 19, Component.translatable("gui.robotica.energy.collider.beam_tip"));
        } else {
            small(g, Component.translatable("gui.robotica.energy.collider.charge_line", Fmt.energy(menu.charge()), Fmt.energy(menu.chargeNeeded())), tx, y + 19, W);
            drawEnergyBarWide(g, tx, y + 28, W, 8, menu.charge(), Math.max(1, menu.chargeNeeded()));
            addTooltip(tx, y + 18, W, 19, Component.translatable("gui.robotica.energy.collider.charge_tip"));
        }

        small(g, Component.translatable("gui.robotica.energy.collider.ring", menu.length(), menu.segments(), menu.resonantSegments(),
                Fmt.compact(menu.fullPower())), tx, y + 40, W);
        addTooltip(tx, y + 39, W, 8, Component.translatable("gui.robotica.energy.collider.ring_tip"));

        small(g, Component.translatable("gui.robotica.energy.collider.matter", Math.round(menu.matter() * 100)), tx, y + 49, W);
        drawMatter(g, tx, y + 57, W, 3, menu.matter());
        addTooltip(tx, y + 48, W, 13, Component.translatable("gui.robotica.energy.collider.matter_tip"));

        drawFitted(g, font, Component.literal(Fmt.compact(menu.fePerTick()) + " FE/t"), tx, y + 63, W, 0xFF1E6B2A, -1, false, 1.0F);
        drawEnergyBarWide(g, tx, y + 74, W, 5, menu.energy(), menu.capacity());
        addTooltip(tx, y + 62, W, 18, Component.translatable("gui.robotica.energy.output_tip"),
                Component.translatable("gui.robotica.energy.collider.buffer_tip").withStyle(ChatFormatting.GRAY));

        drawProgress(g, x + ColliderMenu.FUEL_X, y + 74, 16, 3, menu.burn());
        addTooltip(x + ColliderMenu.FUEL_X - 1, y + 73, 18, 5, menu.burn() > 0
                ? Component.translatable("gui.robotica.energy.fuel_left", Math.round(menu.burn() * 100))
                : Component.translatable("gui.robotica.energy.not_burning"));
        smallRight(g, Component.translatable("gui.robotica.energy.fuel_matter"), x + 168, y + 8, 40);

        State state = menu.state();
        Tone tone = switch (state) {
            case RUNNING -> Tone.GOOD;
            case NOT_FORMED, NO_FUEL, STARVING -> Tone.BAD;
            default -> Tone.WARN;
        };
        String key = "gui.robotica.energy.collider." + state.name().toLowerCase(Locale.ROOT);
        drawStatus(g, Component.translatable(key), x + 8, y + 82, 136, 1, tone);
        addTooltip(x + 8, y + 81, 136, 10, Component.translatable(key + ".tip"));
        drawStructure(g, x + 8, y + 95, 88, 2);
    }

    /** Beam bar: deep blue to white-hot cyan. */
    private void drawBeam(GuiGraphics g, int bx, int by, int w, int h, float level) {
        drawInset(g, bx, by, w, h);
        g.fill(bx, by, bx + w, by + h, 0xFF0F1A3A);
        int filled = Math.round(w * Math.max(0, Math.min(1, level)));
        if (filled > 0) g.fillGradient(bx, by, bx + filled, by + h, 0xFFE2FBFF, 0xFF3F8FD0);
    }

    /** Strange Matter progress: violet. */
    private void drawMatter(GuiGraphics g, int bx, int by, int w, int h, float level) {
        drawInset(g, bx, by, w, h);
        g.fill(bx, by, bx + w, by + h, 0xFF1E0F3A);
        int filled = Math.round(w * Math.max(0, Math.min(1, level)));
        if (filled > 0) g.fillGradient(bx, by, bx + filled, by + h, 0xFFE0B8FF, 0xFF7A3FD0);
    }
}
