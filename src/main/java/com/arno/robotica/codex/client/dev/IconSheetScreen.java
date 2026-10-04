package com.arno.robotica.codex.client.dev;

import com.arno.robotica.Robotica;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only: every Robotica item with its name, split over pages. Used by {@link Showcase}.
 * The layout is computed in a virtual space at {@link #SCALE} of the GUI scale, from the real GUI-scaled window size and the widest
 * item name, so names never overlap whatever the window or GUI scale is.
 */
public class IconSheetScreen extends Screen {
    private static final float SCALE = 0.5F;
    private static final int CELL_H = 20, PAD = 6;
    private final int page;

    public IconSheetScreen(int page) {
        super(Component.literal("Robotica items"));
        this.page = page;
    }

    private static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Robotica.MODID)) items.add(item);
        }
        return items;
    }

    private static int cellWidth() {
        int widest = 0;
        for (Item item : items()) widest = Math.max(widest, Minecraft.getInstance().font.width(new ItemStack(item).getHoverName()));
        return 22 + widest + 10;
    }

    private static int cols(int guiWidth) {
        return Math.max(1, (int) ((guiWidth / SCALE - PAD) / cellWidth()));
    }

    private static int rows(int guiHeight) {
        return Math.max(1, (int) ((guiHeight / SCALE - PAD) / CELL_H));
    }

    /** Number of pages the sheet needs for a window of the given GUI-scaled size. */
    public static int pageCount(int guiWidth, int guiHeight) {
        int per = cols(guiWidth) * rows(guiHeight);
        return Math.max(1, (items().size() + per - 1) / per);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF2B3133);
        List<Item> items = items();
        int cols = cols(width), rows = rows(height), per = cols * rows, cellW = cellWidth();
        g.pose().pushPose();
        g.pose().scale(SCALE, SCALE, 1.0F);
        for (int i = page * per; i < Math.min(items.size(), (page + 1) * per); i++) {
            int slot = i - page * per;
            int x = PAD + (slot % cols) * cellW, y = PAD + (slot / cols) * CELL_H;
            ItemStack stack = new ItemStack(items.get(i));
            g.renderItem(stack, x, y);
            g.drawString(font, stack.getHoverName(), x + 22, y + 4, 0xFFE1E8E5, false);
        }
        g.pose().popPose();
    }
}
