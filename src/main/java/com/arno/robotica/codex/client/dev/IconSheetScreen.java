package com.arno.robotica.codex.client.dev;

import com.arno.robotica.Robotica;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Dev-only: every Robotica item at 2x with its name, split over pages. Used by {@link Showcase}. */
public class IconSheetScreen extends Screen {
    private static final int COLS = 6, ROWS = 11;
    private final int page;

    public IconSheetScreen(int page) {
        super(Component.literal("Robotica items"));
        this.page = page;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF2B3133);
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Robotica.MODID)) items.add(item);
        }
        int per = COLS * ROWS, cellW = width / COLS, cellH = Math.max(20, (height - 10) / ROWS);
        for (int i = page * per; i < Math.min(items.size(), (page + 1) * per); i++) {
            int slot = i - page * per;
            int x = (slot % COLS) * cellW + 4, y = (slot / COLS) * cellH + 6;
            ItemStack stack = new ItemStack(items.get(i));
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(1.5F, 1.5F, 1F);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            g.drawString(font, stack.getHoverName(), x + 28, y + 8, 0xFFE1E8E5, false);
        }
    }
}
