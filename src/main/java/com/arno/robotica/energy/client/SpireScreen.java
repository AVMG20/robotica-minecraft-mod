package com.arno.robotica.energy.client;

import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.energy.block.SpireBlockEntity.State;
import com.arno.robotica.energy.menu.SpireMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Tesla Spire GUI: FE buffer, output, the column (conductors, base power, efficiency), the crown's altitude, weather
 * and neighbours, the last lightning strike, fuel and waste slots with the burn bar, status and structure lines.
 */
public class SpireScreen extends ControllerScreen<SpireMenu> {
    private static final int BUFFER_X = 8, BUFFER_Y = 18, BUFFER_H = 58;
    private static final int TEXT_X = 24, TEXT_W = 104;

    public SpireScreen(SpireMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = SpireMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = SpireMenu.PLAYER_Y - 11;
    }

    @Override
    protected int titleMaxWidth() {
        return 110;
    }

    @Override
    protected void init() {
        super.init();
        addShowButton(150, 92);
    }

    @Override
    protected ItemStack ghostIcon(Slot slot) {
        return slot.index < 2 ? icon("thorium_fuel_pellet") : ItemStack.EMPTY;
    }

    @Override
    protected Component slotHint(Slot slot) {
        if (slot.index < 2) return Component.translatable("gui.robotica.energy.slot_spire_fuel");
        if (slot.index < 3) return Component.translatable("gui.robotica.energy.slot_waste");
        return null;
    }

    @Override
    protected void renderMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawEnergyBar(g, x + BUFFER_X, y + BUFFER_Y, 10, BUFFER_H, menu.energy(), menu.capacity(), false,
                Component.translatable("gui.robotica.energy.spire.buffer_tip").withStyle(ChatFormatting.GRAY));

        int tx = x + TEXT_X;
        int fe = menu.fePerTick();
        drawFitted(g, font, Component.literal(Fmt.compact(fe) + " FE/t"), tx, y + 18, TEXT_W, 0xFF1E6B2A, -1, false, 1.0F);
        addTooltip(tx, y + 17, TEXT_W, 10, Component.translatable("gui.robotica.energy.output_tip"),
                Component.translatable("gui.robotica.energy.spire.potential", Fmt.compact(menu.potential())).withStyle(ChatFormatting.GRAY));

        small(g, Component.translatable("gui.robotica.energy.spire.column", menu.conductors(), Fmt.compact(menu.basePower())), tx, y + 30, TEXT_W);
        addTooltip(tx, y + 29, TEXT_W, 8, Component.translatable("gui.robotica.energy.spire.column_tip"));

        small(g, Component.translatable("gui.robotica.energy.spire.efficiency", menu.efficiency()), tx, y + 39, TEXT_W);
        addTooltip(tx, y + 38, TEXT_W, 8, Component.translatable("gui.robotica.energy.spire.efficiency_tip"));

        String weather = switch (menu.weather()) {
            case 2 -> "thunder";
            case 1 -> "rain";
            default -> "clear";
        };
        small(g, Component.translatable("gui.robotica.energy.spire.sky_line", menu.altitude(),
                Component.translatable("gui.robotica.energy.spire.weather." + weather)), tx, y + 48, TEXT_W);
        addTooltip(tx, y + 47, TEXT_W, 8, Component.translatable("gui.robotica.energy.spire.altitude_tip"),
                Component.translatable("gui.robotica.energy.spire.weather_tip").withStyle(ChatFormatting.GRAY));

        int n = menu.neighbours();
        Component near = n == 0 ? Component.translatable("gui.robotica.energy.spire.alone")
                : Component.translatable("gui.robotica.energy.spire.crowded", n, 100 - menu.interference());
        drawFitted(g, font, near, tx, y + 57, TEXT_W, n == 0 ? TEXT : 0xFFB0452B, -1, false, 0.75F);
        addTooltip(tx, y + 56, TEXT_W, 8, Component.translatable("gui.robotica.energy.spire.spacing_tip"));

        long ago = menu.strikeAgo();
        small(g, ago < 0 ? Component.translatable("gui.robotica.energy.spire.no_strike")
                : Component.translatable("gui.robotica.energy.spire.last_strike", Fmt.duration(ago * 20)), tx, y + 66, TEXT_W);
        addTooltip(tx, y + 65, TEXT_W, 8, Component.translatable("gui.robotica.energy.spire.strike_tip"));

        drawProgress(g, x + SpireMenu.FUEL_X, y + 56, 16, 3, menu.burn());
        addTooltip(x + SpireMenu.FUEL_X - 1, y + 55, 18, 5, menu.burn() > 0
                ? Component.translatable("gui.robotica.energy.fuel_left", Math.round(menu.burn() * 100))
                : Component.translatable("gui.robotica.energy.not_burning"));
        smallRight(g, Component.translatable("gui.robotica.energy.fuel_waste"), x + 168, y + 8, 40);

        State state = menu.state();
        Tone tone = switch (state) {
            case RUNNING -> Tone.GOOD;
            case NOT_FORMED, NO_SKY, NO_FUEL, WASTE_FULL -> Tone.BAD;
            default -> Tone.WARN;
        };
        String key = "gui.robotica.energy.spire." + state.name().toLowerCase(Locale.ROOT);
        drawStatus(g, Component.translatable(key), x + 8, y + 82, 136, 1, tone);
        addTooltip(x + 8, y + 81, 136, 10, Component.translatable(key + ".tip"));
        drawStructure(g, x + 8, y + 95, 136, 2);
    }
}
