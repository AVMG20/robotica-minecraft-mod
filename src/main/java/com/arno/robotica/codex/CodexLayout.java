package com.arno.robotica.codex;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Geometry of the Codex book in GUI pixels (before the fit-to-screen scale). Pure math without client classes, shared
 * by {@code CodexScreen} and the layout game test, so what the test checks is what the screen draws.
 * Every y below is relative to the top of the book, every x to the left edge of a page's text column.
 */
public final class CodexLayout {
    private CodexLayout() {}

    /** Book (paper) size, text column width on each page, page margin, line height. */
    public static final int W = 320, H = 210, PAGE_W = 140, MARGIN = 10, LINE_H = 10;
    /** The cover around the paper is 6 px; keep 2 px of screen around it. */
    public static final int FRAME = 16;
    /** Page arrows, the hint line just above them, and the lowest pixel text or lists may use. */
    public static final int NAV_Y = H - 24, NAV_H = 16, HINT_Y = H - 36, CONTENT_BOTTOM = HINT_Y - 2;
    public static final int TITLE_Y = 10;

    // ------------------------------------------------------------------ left page: chapter list

    /** First row under the "ROBOTICA CODEX" heading; full rows; compact rows when the list is paged. */
    public static final int LIST_TOP = 26, ROW_FULL = 16, ROW_MIN = 14, ROW_PAGED = 14;
    /** Without arrows the list may run to just above the bottom edge; with arrows it stops above them. */
    public static final int LIST_BOTTOM_SINGLE = H - 8, LIST_BOTTOM_PAGED = NAV_Y - 4;

    public record ListLayout(int rowH, int perPage, int pages) {
        public boolean paged() {
            return pages > 1;
        }
    }

    /** Lays out {@code rows} list rows: one page of 16 px rows if they fit, else 14 px rows, else pages with arrows. */
    public static ListLayout list(int rows) {
        rows = Math.max(1, rows);
        int single = LIST_BOTTOM_SINGLE - LIST_TOP;
        int rowH = Math.min(ROW_FULL, single / rows);
        if (rowH >= ROW_MIN) return new ListLayout(rowH, rows, 1);
        int per = (LIST_BOTTOM_PAGED - LIST_TOP) / ROW_PAGED;
        return new ListLayout(ROW_PAGED, per, (rows + per - 1) / per);
    }

    // ------------------------------------------------------------------ right page: text pages

    /** Item icons under the page title: 18 px slots every 20 px, wrapping after a full row. */
    public static final int ITEMS_Y = 24, SLOT = 18, SLOT_STEP = 20, ITEM_ROW_H = 22;
    public static final int ITEM_COLS = (PAGE_W - SLOT) / SLOT_STEP + 1;
    public static final int MAX_ITEM_ROWS = 2, MAX_ITEMS = ITEM_COLS * MAX_ITEM_ROWS;

    public static int itemRows(int items) {
        return (Math.min(items, MAX_ITEMS) + ITEM_COLS - 1) / ITEM_COLS;
    }

    public static int itemX(int index) {
        return (index % ITEM_COLS) * SLOT_STEP;
    }

    public static int itemY(int index) {
        return ITEMS_Y + (index / ITEM_COLS) * ITEM_ROW_H;
    }

    public static int textTop(int items) {
        return ITEMS_Y + itemRows(items) * ITEM_ROW_H;
    }

    /** Text lines that fit on one sub-page under the icons; longer text continues on the next sub-page. */
    public static int linesPerSubPage(int items) {
        return Math.max(1, (CONTENT_BOTTOM - textTop(items)) / LINE_H);
    }

    public static int subPages(int lines, int items) {
        int per = linesPerSubPage(items);
        return Math.max(1, (lines + per - 1) / per);
    }

    // ------------------------------------------------------------------ guide ("Next steps")

    /** Steps start under the heading and the progress line; the step title sits next to an 18 px icon. */
    public static final int GUIDE_TOP = 36, GUIDE_TITLE_W = PAGE_W - 24, TEXT_W = PAGE_W - 4;

    public static int stepHeight(int titleLines, int descLines) {
        return Math.max(20, titleLines * LINE_H + 2) + descLines * LINE_H + 6;
    }

    public static int guideSpace() {
        return CONTENT_BOTTOM - GUIDE_TOP;
    }

    /** Checklist lines per page (under its heading). */
    public static int checklistLines() {
        return (CONTENT_BOTTOM - (TITLE_Y + 14)) / LINE_H;
    }

    /**
     * Splits blocks of the given heights into pages of at most {@code space} pixels, in order. Returns [from, to)
     * index pairs; a block taller than a page gets a page of its own (the screen clips it).
     */
    public static List<int[]> paginate(int[] heights, int space) {
        List<int[]> pages = new ArrayList<>();
        int from = 0, used = 0;
        for (int i = 0; i < heights.length; i++) {
            if (i > from && used + heights[i] > space) {
                pages.add(new int[]{from, i});
                from = i;
                used = 0;
            }
            used += heights[i];
        }
        if (heights.length > from) pages.add(new int[]{from, heights.length});
        return pages;
    }

    // ------------------------------------------------------------------ recipe view

    /** The ingredient grid starts under the item name and the recipe kind; rows of 19 px; the hint takes 2 lines. */
    public static final int RECIPE_GRID_Y = TITLE_Y + 16 + 12, RECIPE_STEP = 19;

    public static int recipeMaxRows() {
        return (NAV_Y - 4 - 2 * LINE_H - 8 - RECIPE_GRID_Y) / RECIPE_STEP;
    }

    // ------------------------------------------------------------------ fit to screen

    /** Scale that keeps the whole book (with its cover) on screen; 1 when it fits at the current GUI scale. */
    public static float fitScale(int screenW, int screenH) {
        return Math.min(1F, Math.min(screenW / (float) (W + FRAME), screenH / (float) (H + FRAME)));
    }

    // ------------------------------------------------------------------ text measuring without a client

    /** Advance widths of the vanilla default font for ASCII 32..126 (from minecraft:textures/font/ascii.png). */
    private static final String ASCII_ADVANCE =
            "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";

    /** Width of a string in the default font, for checks on a server; other characters count as a wide 8 px. */
    public static int estimateWidth(String s, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += (c >= 32 && c < 127 ? ASCII_ADVANCE.charAt(c - 32) - '0' : 8) + (bold ? 1 : 0);
        }
        return w;
    }

    /** Greedy word wrap like the game's: paragraphs split on new lines, blank lines kept, long words broken. */
    public static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsInt(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) lines.add(line.toString());
                line.setLength(0);
                String rest = word;
                while (measure.applyAsInt(rest) > width && rest.length() > 1) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && measure.applyAsInt(rest.substring(0, cut)) > width) cut--;
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
