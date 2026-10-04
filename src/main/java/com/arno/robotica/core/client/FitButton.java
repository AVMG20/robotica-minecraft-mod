package com.arno.robotica.core.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A vanilla looking button whose label never gets clipped or scrolls: a label wider than the button is shrunk
 * (down to 65 %) and, as a last resort, shortened with "...". Use it for every button in Robotica screens.
 */
public class FitButton extends Button {
    public FitButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public FitButton(int x, int y, int width, int height, Component message, OnPress onPress, Component tooltip) {
        this(x, y, width, height, message, onPress);
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int color) {
        MachineScreen.drawFitted(g, font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, width - 6, color, 0, true, 1.0F);
    }
}
