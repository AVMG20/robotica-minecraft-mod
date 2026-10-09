package com.arno.robotica.energy.client;

import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.block.CoreReactorBlockEntity.State;
import com.arno.robotica.energy.menu.CoreReactorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Core Reactor GUI: FE buffer, output, the modulators' power and burn, the active core with its integrity bar and time
 * left, the next-core, fuel and waste slots with the burn bar, status and structure lines.
 */
public class CoreReactorScreen extends ControllerScreen<CoreReactorMenu> {
    private static final int BUFFER_X = 8, BUFFER_Y = 18, BUFFER_H = 58;
    private static final int TEXT_X = 24, TEXT_W = 82;

    public CoreReactorScreen(CoreReactorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = CoreReactorMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = CoreReactorMenu.PLAYER_Y - 11;
    }

    @Override
    protected int titleMaxWidth() {
        return 100;
    }

    @Override
    protected void init() {
        super.init();
        addShowButton(150, 92);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        if (slot.index == 0) return icon("servo_core");
        if (slot.index < 4) return icon("thorium_fuel_pellet");
        return ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index == 0) return Component.translatable("gui.robotica.energy.slot_core");
        if (slot.index < 4) return Component.translatable("gui.robotica.energy.slot_fuel");
        if (slot.index < 7) return Component.translatable("gui.robotica.energy.slot_waste");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + BUFFER_X, y + BUFFER_Y, 10, BUFFER_H, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.energy.buffer_tip").withStyle(ChatFormatting.GRAY));

        int tx = x + TEXT_X;
        drawFitted(g, font, Component.literal(Fmt.compact(menu.fePerTick()) + " FE/t"), tx, y + 18, TEXT_W, 0xFF1E6B2A, -1, false, 1.0F);
        addTooltip(tx, y + 17, TEXT_W, 10, Component.translatable("gui.robotica.energy.output_tip"));

        small(g, Component.translatable("gui.robotica.energy.core.mods", menu.powerPct(), menu.burnPct()), tx, y + 30, TEXT_W);
        small(g, Component.translatable("gui.robotica.energy.core.mod_count", menu.amplifiers(), menu.dampers()), tx, y + 39, TEXT_W);
        addTooltip(tx, y + 29, TEXT_W, 18, Component.translatable("gui.robotica.energy.core.mods_tip"),
                Component.translatable("gui.robotica.energy.core.mods_tip2").withStyle(ChatFormatting.GRAY));

        ItemStack core = menu.core();
        if (core.isEmpty()) {
            drawFitted(g, font, Component.translatable("gui.robotica.energy.core.no_core_line"), tx, y + 50, TEXT_W, 0xFFB0452B, -1, false, 0.75F);
            addTooltip(tx, y + 49, TEXT_W, 8, Component.translatable("gui.robotica.energy.core.no_core.tip"));
        } else {
            small(g, Component.translatable("gui.robotica.energy.core.active", core.getHoverName(), fmt(menu.corePower())), tx, y + 50, TEXT_W);
            drawIntegrity(g, tx, y + 59, TEXT_W, 5, menu.integrity());
            small(g, Component.translatable("gui.robotica.energy.core.left", Fmt.duration(menu.coreTicks())), tx, y + 67, TEXT_W);
            addTooltip(tx, y + 49, TEXT_W, 26, Component.translatable("gui.robotica.energy.core.integrity", Math.round(menu.integrity() * 100)),
                    Component.translatable("gui.robotica.energy.core.integrity_tip").withStyle(ChatFormatting.GRAY));
        }

        drawProgress(g, x + CoreReactorMenu.FUEL_X, y + 74, 16, 3, menu.burn());
        addTooltip(x + CoreReactorMenu.FUEL_X - 1, y + 73, 18, 5, menu.burnPower() > 0
                ? Component.translatable("gui.robotica.energy.core.burning", Fmt.compact(menu.burnPower()), Math.round(menu.burn() * 100))
                : Component.translatable("gui.robotica.energy.not_burning"));
        smallRight(g, Component.translatable("gui.robotica.energy.core_fuel_waste"), x + 168, y + 8, 60);

        State state = menu.state();
        Tone tone = switch (state) {
            case RUNNING -> Tone.GOOD;
            case NOT_FORMED, NO_CORE, NO_FUEL, WASTE_FULL -> Tone.BAD;
            default -> Tone.WARN;
        };
        String key = "gui.robotica.energy.core." + state.name().toLowerCase(Locale.ROOT);
        drawStatus(g, Component.translatable(key), x + 8, y + 82, 136, 1, tone);
        addTooltip(x + 8, y + 81, 136, 10, Component.translatable(key + ".tip"));
        drawStructure(g, x + 8, y + 95, 136, 2);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("\\.?0+$", "");
    }

    /** Core integrity: magenta when fresh, fading to red near burnout. */
    private void drawIntegrity(GuiGraphics g, int bx, int by, int w, int h, float level) {
        drawInset(g, bx, by, w, h);
        g.fill(bx, by, bx + w, by + h, 0xFF2A1420);
        int filled = Math.round(w * Math.max(0, Math.min(1, level)));
        if (filled > 0) {
            int top = level > 0.25F ? 0xFFF08CFF : 0xFFFF7A5A;
            int bottom = level > 0.25F ? 0xFF9A3FC0 : 0xFFB8302A;
            g.fillGradient(bx, by, bx + filled, by + h, top, bottom);
        }
    }
}
