package com.arno.robotica.codex.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.codex.CodexModule;
import com.arno.robotica.codex.LabActionPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Two-page technical manual. Left page: chapter list. Right page: chapter text, item icons
 * (click one to see its recipe; click recipe ingredients to follow the ladder down).
 * Operators also get the Creative Lab: an item grid (click = 1, shift-click = stack) and test actions.
 * Content: assets/robotica/codex/chapters.json (resource pack overridable).
 */
public class CodexScreen extends Screen {
    private static final int W = 320, H = 210, PAGE_W = 140;
    private static final int COVER = 0xFF23292B, TRIM = 0xFFC87533, PAPER = 0xFFE9EDEA, GRID = 0xFFDCE3E0;
    private static final int INK = 0xFF1E2A2A, HEAD = 0xFFA4521C, MUTED = 0xFF5D6B67, LINK = 0xFF0F7488, SLOT = 0xFFC9D2CE;

    private record Page(String title, String text, List<ItemStack> items) {}
    /** {@code guide}: the "Next steps" chapter, drawn from the guide advancements instead of text. */
    private record Chapter(String title, ItemStack icon, List<Page> pages, boolean guide) {}
    /** One step of the guide (assets/robotica/codex/guide.json, written with the advancements by codex_guide.py). */
    private record Step(String id, String parent, ItemStack icon, int age) {
        String key() {
            return "advancements.robotica." + id.substring(id.indexOf(':') + 1).replace('/', '.');
        }
    }

    private final List<Step> steps = new ArrayList<>();
    /** Clickable icons drawn on the guide page this frame: x, y and the stack. */
    private final List<Object[]> guideHits = new ArrayList<>();
    private static final int GUIDE_LIST_LINES = 14;

    private final List<Chapter> chapters = new ArrayList<>();
    private int chapter = 0, page = 0, subPage = 0, labScroll = 0;
    private boolean lab = false;
    private ItemStack recipeItem = ItemStack.EMPTY;
    private final Deque<ItemStack> recipeHistory = new ArrayDeque<>();
    private int left, top;

    // Caches (rebuilt per screen; layout caches are cleared in init, which also runs on resize)
    private Map<Item, List<RecipeHolder<?>>> recipeCache;
    private final Map<ResourceLocation, List<Ingredient>> smithingCache = new HashMap<>();
    private final Map<Long, List<FormattedCharSequence>> wrapCache = new HashMap<>();
    private List<Item> labItemList = List.of();
    /** Animation values computed once per frame, so what is drawn and what a click hits always agree. */
    private int frameTick = 0;
    private long frameRecipeSlot = 0;

    public CodexScreen() {
        super(Component.translatable("item.robotica.codex"));
        loadChapters();
        loadGuide();
    }

    private void loadGuide() {
        try {
            Optional<Resource> res = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(Robotica.id("codex/guide.json"));
            if (res.isEmpty()) return;
            try (Reader reader = res.get().openAsReader()) {
                for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("steps")) {
                    JsonObject o = e.getAsJsonObject();
                    String parent = o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null;
                    steps.add(new Step(o.get("id").getAsString(), parent, stackOf(o.get("icon").getAsString()), o.has("age") ? o.get("age").getAsInt() : 0));
                }
            }
        } catch (Exception e) {
            Robotica.LOGGER.error("Could not read the Robotica guide steps", e);
        }
    }

    private boolean stepDone(Step step) {
        return com.arno.robotica.codex.GuideProgressPayload.clientDone().contains(step.id());
    }

    /** Steps you can do now: not done, and the step before them is done. */
    private List<Step> nextSteps() {
        List<Step> next = new ArrayList<>();
        for (Step step : steps) {
            if (stepDone(step)) continue;
            boolean open = step.parent() == null || com.arno.robotica.codex.GuideProgressPayload.clientDone().contains(step.parent());
            if (open) next.add(step);
        }
        return next;
    }

    private void loadChapters() {
        try {
            Optional<Resource> res = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(Robotica.id("codex/chapters.json"));
            if (res.isEmpty()) return;
            try (Reader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement ce : root.getAsJsonArray("chapters")) {
                    try {
                        Chapter chapter = readChapter(ce.getAsJsonObject());
                        if (chapter != null) chapters.add(chapter);
                    } catch (RuntimeException e) {
                        Robotica.LOGGER.error("Skipping a broken codex chapter", e);
                    }
                }
            }
        } catch (Exception e) {
            Robotica.LOGGER.error("Could not read Robotica codex", e);
        }
    }

    /** Returns null for a chapter without pages. A page without text gets an empty text, so it never takes the rest down. */
    private static Chapter readChapter(JsonObject c) {
        List<Page> pages = new ArrayList<>();
        if (c.has("pages")) {
            for (JsonElement pe : c.getAsJsonArray("pages")) {
                JsonObject p = pe.getAsJsonObject();
                List<ItemStack> items = new ArrayList<>();
                if (p.has("items")) {
                    for (JsonElement ie : p.getAsJsonArray("items")) {
                        ItemStack s = stackOf(ie.getAsString());
                        if (!s.isEmpty()) items.add(s);
                    }
                }
                pages.add(new Page(p.has("title") ? p.get("title").getAsString() : "",
                        p.has("text") ? p.get("text").getAsString() : "", items));
            }
        }
        boolean guide = c.has("special") && "guide".equals(c.get("special").getAsString());
        if (guide && pages.isEmpty()) pages.add(new Page("", "", List.of()));
        if (pages.isEmpty()) return null;
        String title = c.has("title") ? c.get("title").getAsString() : "?";
        ItemStack icon = c.has("icon") ? stackOf(c.get("icon").getAsString()) : ItemStack.EMPTY;
        return new Chapter(title, icon, pages, guide);
    }

    private static ItemStack stackOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private boolean labAllowed() {
        return minecraft != null && minecraft.player != null && CodexModule.canUseLab(minecraft.player);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        wrapCache.clear();
        labItemList = buildLabItems();
        chapter = Math.min(chapter, Math.max(0, chapters.size() - 1));
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int rx = left + W / 2 + 8;
        if (lab) {
            String[][] actions = {
                    {"Kit: Age 0", "kit", "0", "Give the starter kit of age 0"}, {"Kit: Age 1", "kit", "1", "Give the starter kit of age 1"},
                    {"Kit: Age 2", "kit", "2", "Give the starter kit of age 2"}, {"Kit: Age 3", "kit", "3", "Give the starter kit of age 3"},
                    {"Kit: Age 4", "kit", "4", "Give the starter kit of age 4"}, {"Charge items", "charge", "", "Fill every energy item in your inventory"},
                    {"Charge target", "charge_target", "", "Fill the block you are looking at"}, {"Set day", "day", "", "Set the time to day"},
                    {"Clear weather", "clear_weather", "", "Stop rain and thunder"}, {"Heal + feed", "heal", "", "Restore health and hunger"},
                    {"Kill hostiles", "kill_hostiles", "", "Kill hostile mobs within 48 blocks"}, {"Spawn zombie", "spawn", "minecraft:zombie", "Spawn a zombie in front of you"},
                    {"Spawn skeleton", "spawn", "minecraft:skeleton", "Spawn a skeleton in front of you"}, {"Creative mode", "gamemode", "", "Toggle creative mode"}};
            for (int i = 0; i < actions.length; i++) {
                String[] a = actions[i];
                int bx = rx + (i % 2) * 71, by = top + 28 + (i / 2) * 22;
                addRenderableWidget(new BookButton(bx, by, 69, 20, Component.literal(a[0]),
                        b -> sendLab(a[1], a[2], a[1].equals("kit") ? Integer.parseInt(a[2]) : 1), Component.literal(a[3])));
            }
            addRenderableWidget(new BookButton(rx, top + H - 24, PAGE_W, 16, Component.literal("Back to the manual"), b -> {
                lab = false;
                rebuild();
            }, BookButton.Kind.LABEL));
            return;
        }
        int ny = top + H - 24;
        BookButton prev = addRenderableWidget(new BookButton(rx, ny, 22, 16, Component.literal("Previous page"), b -> turn(-1), BookButton.Kind.PREV));
        BookButton next = addRenderableWidget(new BookButton(rx + PAGE_W - 22, ny, 22, 16, Component.literal("Next page"), b -> turn(1), BookButton.Kind.NEXT));
        prev.active = !chapters.isEmpty() && (subPage > 0 || page > 0 || chapter > 0);
        next.active = !chapters.isEmpty() && (subPage + 1 < subPageCount() || page + 1 < chapters.get(chapter).pages().size() || chapter + 1 < chapters.size());
        if (!recipeItem.isEmpty()) {
            addRenderableWidget(new BookButton(rx + PAGE_W / 2 - 22, ny, 44, 16, Component.literal("Back"), b -> back(), BookButton.Kind.LABEL));
        }
    }

    private void sendLab(String action, String arg, int count) {
        PacketDistributor.sendToServer(new LabActionPayload(action, arg, count));
    }

    private void turn(int dir) {
        if (chapters.isEmpty()) return;
        recipeItem = ItemStack.EMPTY;
        recipeHistory.clear();
        int subs = subPageCount();
        Chapter c = chapters.get(chapter);
        if (dir > 0) {
            if (subPage + 1 < subs) subPage++;
            else if (page + 1 < c.pages().size()) { page++; subPage = 0; }
            else if (chapter + 1 < chapters.size()) { chapter++; page = 0; subPage = 0; }
        } else {
            if (subPage > 0) subPage--;
            else if (page > 0) { page--; subPage = 0; }
            else if (chapter > 0) { chapter--; page = chapters.get(chapter).pages().size() - 1; subPage = 0; }
        }
        rebuild();
    }

    private void back() {
        recipeItem = recipeHistory.isEmpty() ? ItemStack.EMPTY : recipeHistory.pop();
        rebuild();
    }

    /** Dev hooks for the screenshot showcase. */
    public void devSelect(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size()) return;
        lab = false;
        chapter = chapterIndex;
        page = 0;
        subPage = 0;
        recipeItem = ItemStack.EMPTY;
        recipeHistory.clear();
        rebuild();
    }

    public void devShowRecipe(ItemStack stack) {
        lab = false;
        showRecipe(stack);
    }

    public void devLab() {
        lab = true;
        rebuild();
    }

    private void showRecipe(ItemStack stack) {
        if (!recipeItem.isEmpty()) recipeHistory.push(recipeItem);
        recipeItem = stack.copyWithCount(1);
        rebuild();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        frameTick = (int) (now / 1000);
        frameRecipeSlot = now / 3000;
        super.render(g, mx, my, pt); // draws the book (renderBackground) first, then the buttons on top of it
        ItemStack hovered = lab ? renderLab(g, mx, my) : renderManual(g, mx, my);
        if (!lab) renderChapterList(g, mx, my);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        drawBook(g);
    }

    private void drawBook(GuiGraphics g) {
        g.fill(left - 6, top - 6, left + W + 6, top + H + 6, COVER);
        g.fill(left - 6, top - 6, left + 2, top + 2, TRIM);
        g.fill(left + W - 2, top - 6, left + W + 6, top + 2, TRIM);
        g.fill(left - 6, top + H - 2, left + 2, top + H + 6, TRIM);
        g.fill(left + W - 2, top + H - 2, left + W + 6, top + H + 6, TRIM);
        g.fill(left, top, left + W, top + H, PAPER);
        for (int x = left + 8; x < left + W; x += 8) g.fill(x, top, x + 1, top + H, GRID);
        for (int y = top + 8; y < top + H; y += 8) g.fill(left, y, left + W, y + 1, GRID);
        // page edges and the shaded spine
        g.fill(left, top, left + W, top + 1, 0xFFB9C4C0);
        g.fill(left, top + H - 1, left + W, top + H, 0xFFB9C4C0);
        for (int i = 0; i < 6; i++) {
            int shade = (6 - i) * 0x07;
            g.fill(left + W / 2 - 2 - i, top, left + W / 2 - 1 - i, top + H, shade << 24);
            g.fill(left + W / 2 + 1 + i, top, left + W / 2 + 2 + i, top + H, shade << 24);
        }
        g.fill(left + W / 2 - 1, top, left + W / 2 + 1, top + H, COVER);
    }

    private void renderChapterList(GuiGraphics g, int mx, int my) {
        int x = left + 10, y = top + 10;
        g.drawString(font, Component.literal("ROBOTICA CODEX").withStyle(ChatFormatting.BOLD), x, y, HEAD, false);
        y += 16;
        for (int i = 0; i < chapters.size(); i++) {
            Chapter c = chapters.get(i);
            boolean active = !lab && i == chapter && recipeItem.isEmpty();
            boolean hover = mx >= x && mx < x + PAGE_W && my >= y && my < y + 16;
            if (active || hover) g.fill(x - 2, y - 1, x + PAGE_W - 4, y + 15, active ? SLOT : GRID);
            g.renderItem(c.icon(), x, y - 1);
            g.drawString(font, c.title(), x + 20, y + 3, active ? HEAD : INK, false);
            y += 16;
        }
        if (labAllowed()) {
            y += 4;
            boolean hover = mx >= x && mx < x + PAGE_W && my >= y && my < y + 16;
            if (lab || hover) g.fill(x - 2, y - 1, x + PAGE_W - 4, y + 15, lab ? SLOT : GRID);
            g.drawString(font, Component.literal("Creative Lab (OP)"), x + 20, y + 3, 0xFFB8860B, false);
        }
    }

    private int listY(int index) {
        return top + 26 + index * 16;
    }

    private List<FormattedCharSequence> wrappedText() {
        if (chapters.isEmpty()) return List.of();
        return wrapCache.computeIfAbsent(((long) chapter << 32) | page, key -> {
            Page p = chapters.get(chapter).pages().get(page);
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String para : p.text().split("\n")) {
                if (para.isBlank()) { lines.add(FormattedCharSequence.EMPTY); continue; }
                lines.addAll(font.split(FormattedText.of(para), PAGE_W - 4));
            }
            return lines;
        });
    }

    private int linesPerSubPage() {
        Page p = chapters.get(chapter).pages().get(page);
        int textTop = 24 + (p.items().isEmpty() ? 0 : 22);
        return (H - textTop - 30) / 10;
    }

    private int subPageCount() {
        if (chapters.isEmpty()) return 1;
        if (chapters.get(chapter).guide()) return 1 + Math.max(1, (steps.size() + GUIDE_LIST_LINES - 1) / GUIDE_LIST_LINES);
        return Math.max(1, (wrappedText().size() + linesPerSubPage() - 1) / linesPerSubPage());
    }

    private ItemStack renderManual(GuiGraphics g, int mx, int my) {
        int x = left + W / 2 + 10, y = top + 10;
        if (chapters.isEmpty()) {
            g.drawString(font, "Codex content missing.", x, y, INK, false);
            return ItemStack.EMPTY;
        }
        if (!recipeItem.isEmpty()) return renderRecipe(g, x, y, mx, my);
        if (chapters.get(chapter).guide()) return renderGuide(g, x, y, mx, my);
        Page p = chapters.get(chapter).pages().get(page);
        ItemStack hovered = ItemStack.EMPTY;
        g.drawString(font, Component.literal(p.title().isEmpty() ? chapters.get(chapter).title() : p.title()).withStyle(ChatFormatting.BOLD), x, y, HEAD, false);
        y += 14;
        if (!p.items().isEmpty()) {
            int ix = x;
            for (ItemStack s : p.items()) {
                if (ix + 18 > x + PAGE_W) break;
                g.fill(ix, y, ix + 18, y + 18, SLOT);
                g.renderItem(s, ix + 1, y + 1);
                if (mx >= ix && mx < ix + 18 && my >= y && my < y + 18) hovered = s;
                ix += 20;
            }
            y += 22;
        }
        List<FormattedCharSequence> lines = wrappedText();
        int per = linesPerSubPage();
        for (int i = subPage * per; i < Math.min(lines.size(), (subPage + 1) * per); i++) {
            g.drawString(font, lines.get(i), x, y, INK, false);
            y += 10;
        }
        String counter = (page + 1) + "/" + chapters.get(chapter).pages().size() + (subPageCount() > 1 ? " (" + (subPage + 1) + "/" + subPageCount() + ")" : "");
        g.drawCenteredString(font, Component.literal(counter).withStyle(s -> s.withColor(MUTED)), x + PAGE_W / 2, top + H - 20, MUTED);
        if (!hovered.isEmpty()) {
            g.drawString(font, Component.literal("Click an item to see its recipe"), x, top + H - 36, MUTED, false);
        }
        return hovered;
    }

    // ---------------------------------------------------------------- guide

    /** Sub-page 0: the steps you can do now, with icon, title and description. Then a checklist of every step. */
    private ItemStack renderGuide(GuiGraphics g, int x, int y, int mx, int my) {
        guideHits.clear();
        ItemStack hovered = ItemStack.EMPTY;
        int done = 0;
        for (Step step : steps) if (stepDone(step)) done++;
        if (subPage == 0) {
            g.drawString(font, Component.translatable("codex.robotica.guide.title").withStyle(ChatFormatting.BOLD), x, y, HEAD, false);
            g.drawString(font, Component.translatable("codex.robotica.guide.progress", done, steps.size()), x, y + 11, MUTED, false);
            y += 26;
            List<Step> next = nextSteps();
            int bottom = top + H - 30;
            if (steps.isEmpty()) {
                drawWrapped(g, Component.translatable("codex.robotica.guide.missing"), x, y, INK);
            } else if (next.isEmpty()) {
                drawWrapped(g, Component.translatable("codex.robotica.guide.all_done"), x, y, INK);
            }
            for (Step step : next) {
                List<FormattedCharSequence> title = font.split(Component.translatable(step.key() + ".title").withStyle(ChatFormatting.BOLD), PAGE_W - 24);
                List<FormattedCharSequence> desc = font.split(Component.translatable(step.key() + ".description"), PAGE_W - 4);
                int needed = Math.max(18, title.size() * 10) + desc.size() * 10 + 6;
                if (y + needed > bottom) break;
                g.fill(x, y, x + 18, y + 18, SLOT);
                g.renderItem(step.icon(), x + 1, y + 1);
                guideHits.add(new Object[]{x, y, step.icon()});
                if (mx >= x && mx < x + 18 && my >= y && my < y + 18) hovered = step.icon();
                int ty = y + (title.size() == 1 ? 5 : 0);
                for (FormattedCharSequence line : title) {
                    g.drawString(font, line, x + 22, ty, HEAD, false);
                    ty += 10;
                }
                y += Math.max(20, title.size() * 10 + 2);
                for (FormattedCharSequence line : desc) {
                    g.drawString(font, line, x, y, INK, false);
                    y += 10;
                }
                y += 6;
            }
            if (!next.isEmpty()) g.drawString(font, Component.translatable("codex.robotica.guide.click"), x, top + H - 36, MUTED, false);
        } else {
            g.drawString(font, Component.translatable("codex.robotica.guide.checklist").withStyle(ChatFormatting.BOLD), x, y, HEAD, false);
            y += 14;
            int from = (subPage - 1) * GUIDE_LIST_LINES;
            for (int i = from; i < Math.min(steps.size(), from + GUIDE_LIST_LINES); i++) {
                Step step = steps.get(i);
                boolean ok = stepDone(step);
                Component line = Component.literal(ok ? "\u2714 " : "\u2022 ").append(Component.translatable(step.key() + ".title"));
                MachineScreenFit.draw(g, font, line, x, y, PAGE_W - 4, ok ? 0xFF2E7D32 : INK);
                y += 10;
            }
        }
        String counter = (subPage + 1) + "/" + subPageCount();
        g.drawCenteredString(font, Component.literal(counter), x + PAGE_W / 2, top + H - 20, MUTED);
        return hovered;
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : font.split(text, PAGE_W - 4)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
    }

    /** Draws one line shrunk to fit, through the core helper. */
    private static final class MachineScreenFit {
        static void draw(GuiGraphics g, net.minecraft.client.gui.Font font, Component text, int x, int y, int maxWidth, int color) {
            com.arno.robotica.core.client.MachineScreen.drawFitted(g, font, text, x, y, maxWidth, color, -1, false, 1.0F);
        }
    }

    // ---------------------------------------------------------------- recipes

    /** All recipes by result item, built once per screen (the recipe manager never changes while the codex is open). */
    private List<RecipeHolder<?>> recipesFor(ItemStack stack) {
        if (minecraft == null || minecraft.level == null) return List.of();
        if (recipeCache == null) {
            recipeCache = new HashMap<>();
            RegistryAccess access = minecraft.level.registryAccess();
            for (RecipeHolder<?> holder : minecraft.level.getRecipeManager().getRecipes()) {
                try {
                    recipeCache.computeIfAbsent(holder.value().getResultItem(access).getItem(), i -> new ArrayList<>()).add(holder);
                } catch (RuntimeException ignored) {
                }
            }
            for (List<RecipeHolder<?>> list : recipeCache.values()) list.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
        }
        return recipeCache.getOrDefault(stack.getItem(), List.of());
    }

    private int gridWidth(Recipe<?> recipe) {
        return recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
    }

    private List<Ingredient> ingredientsOf(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof ShapedRecipe shaped) return shaped.getIngredients();
        if (recipe instanceof SmithingRecipe smithing) {
            return smithingCache.computeIfAbsent(holder.id(), id ->
                    List.of(findSmithing(smithing, 0), findSmithing(smithing, 1), findSmithing(smithing, 2)));
        }
        return recipe.getIngredients();
    }

    private ItemStack renderRecipe(GuiGraphics g, int x, int y, int mx, int my) {
        ItemStack hovered = ItemStack.EMPTY;
        g.drawString(font, recipeItem.getHoverName().copy().withStyle(ChatFormatting.BOLD), x, y, HEAD, false);
        y += 16;
        List<RecipeHolder<?>> recipes = recipesFor(recipeItem);
        if (recipes.isEmpty()) {
            for (FormattedCharSequence line : font.split(FormattedText.of("No recipe. This item drops from a boss or is found in the world."), PAGE_W - 4)) {
                g.drawString(font, line, x, y, INK, false);
                y += 10;
            }
            return ItemStack.EMPTY;
        }
        int index = (int) (frameRecipeSlot % recipes.size());
        Recipe<?> recipe = recipes.get(index).value();
        int tick = frameTick;
        List<Ingredient> ingredients = ingredientsOf(recipes.get(index));
        int gw = gridWidth(recipe);
        String kind = recipe instanceof SmithingRecipe ? "Smithing table" :
                BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) == null ? "" : BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).getPath().replace('_', ' ');
        g.drawString(font, Component.literal(kind + (recipes.size() > 1 ? "  (" + (index + 1) + "/" + recipes.size() + ")" : "")), x, y, MUTED, false);
        y += 12;
        int gx = x + 4, gy = y;
        for (int i = 0; i < Math.max(ingredients.size(), 1); i++) {
            int sx = gx + (i % gw) * 19, sy = gy + (i / gw) * 19;
            g.fill(sx, sy, sx + 18, sy + 18, SLOT);
            if (i >= ingredients.size()) continue;
            ItemStack[] options = ingredients.get(i).getItems();
            if (options.length == 0) continue;
            ItemStack s = options[tick % options.length];
            g.renderItem(s, sx + 1, sy + 1);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) hovered = s;
        }
        int rows = (ingredients.size() + gw - 1) / gw;
        int ax = gx + gw * 19 + 6, ay = gy + Math.max(0, rows - 1) * 19 / 2 + 5;
        g.drawString(font, "->", ax, ay, INK, false);
        ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
        g.fill(ax + 18, ay - 5, ax + 36, ay + 13, SLOT);
        g.renderItem(result, ax + 19, ay - 4);
        g.renderItemDecorations(font, result, ax + 19, ay - 4);
        int ty = gy + Math.max(rows, 1) * 19 + 8;
        for (FormattedCharSequence line : font.split(FormattedText.of("Click an ingredient to see how it is made."), PAGE_W - 4)) {
            g.drawString(font, line, x, ty, MUTED, false);
            ty += 10;
        }
        return hovered;
    }

    /** SmithingTransformRecipe keeps its ingredients package-private; probe Robotica and vanilla items instead. */
    private Ingredient findSmithing(SmithingRecipe recipe, int slot) {
        List<ItemStack> matches = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(item);
            boolean ok = switch (slot) {
                case 0 -> recipe.isTemplateIngredient(s);
                case 1 -> recipe.isBaseIngredient(s);
                default -> recipe.isAdditionIngredient(s);
            };
            if (ok) matches.add(s);
            if (matches.size() >= 8) break;
        }
        return matches.isEmpty() ? Ingredient.EMPTY : Ingredient.of(matches.stream());
    }

    // ---------------------------------------------------------------- creative lab

    private List<Item> labItems() {
        return labItemList;
    }

    private static List<Item> buildLabItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Robotica.MODID)) items.add(item);
        }
        return items;
    }

    private static final int LAB_COLS = 7;

    private int labX() {
        return left + 12;
    }

    private int labY() {
        return top + 24;
    }

    private int labRows() {
        return Math.max(1, (top + H - 16 - labY()) / 18);
    }

    private ItemStack renderLab(GuiGraphics g, int mx, int my) {
        int rx = left + W / 2 + 10;
        g.drawString(font, Component.literal("Test actions").withStyle(ChatFormatting.BOLD), rx, top + 10, 0xFFB8860B, false);
        g.drawString(font, Component.literal("Creative Lab").withStyle(ChatFormatting.BOLD), left + 10, top + 10, 0xFFB8860B, false);
        List<Item> items = labItems();
        int gx = labX(), gy = labY();
        int cols = LAB_COLS, visibleRows = labRows();
        int maxScroll = Math.max(0, (items.size() + cols - 1) / cols - visibleRows);
        labScroll = Math.min(labScroll, maxScroll);
        com.arno.robotica.core.client.MachineScreen.drawFitted(g, font, Component.literal("Click: 1  Shift: stack  Wheel: scroll"), gx, top + H - 12, PAGE_W - 6, MUTED, -1, false, 1.0F);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = labScroll * cols; i < Math.min(items.size(), (labScroll + visibleRows) * cols); i++) {
            int slot = i - labScroll * cols;
            int sx = gx + (slot % cols) * 18, sy = gy + (slot / cols) * 18;
            g.fill(sx, sy, sx + 17, sy + 17, SLOT);
            ItemStack s = new ItemStack(items.get(i));
            g.renderItem(s, sx, sy);
            if (mx >= sx && mx < sx + 17 && my >= sy && my < sy + 17) hovered = s;
        }
        return hovered;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int x = left + 10;
        for (int i = 0; i < chapters.size() && !lab; i++) {
            int y = listY(i);
            if (mx >= x && mx < x + PAGE_W && my >= y && my < y + 16) {
                lab = false;
                chapter = i;
                page = 0;
                subPage = 0;
                recipeItem = ItemStack.EMPTY;
                recipeHistory.clear();
                rebuild();
                return true;
            }
        }
        if (labAllowed() && !lab) {
            int y = listY(chapters.size()) + 4;
            if (mx >= x && mx < x + PAGE_W && my >= y && my < y + 16) {
                lab = true;
                rebuild();
                return true;
            }
        }
        if (lab) {
            List<Item> items = labItems();
            int gx = labX(), gy = labY(), cols = LAB_COLS;
            int visibleRows = labRows();
            int col = (int) Math.floor((mx - gx) / 18), row = (int) Math.floor((my - gy) / 18);
            if (col >= 0 && col < cols && row >= 0 && row < visibleRows) {
                int i = (labScroll + row) * cols + col;
                if (i < items.size()) {
                    Item item = items.get(i);
                    sendLab("give", BuiltInRegistries.ITEM.getKey(item).toString(), hasShiftDown() ? item.getDefaultMaxStackSize() : 1);
                    return true;
                }
            }
            return false;
        }
        // Item icons on a manual page, or ingredients in the recipe view.
        ItemStack clicked = hoveredManualItem((int) mx, (int) my);
        if (!clicked.isEmpty()) {
            showRecipe(clicked);
            return true;
        }
        return false;
    }

    private ItemStack hoveredManualItem(int mx, int my) {
        if (chapters.isEmpty() || minecraft == null) return ItemStack.EMPTY;
        GuiGraphicsProbe probe = new GuiGraphicsProbe();
        if (!recipeItem.isEmpty()) return probe.recipeHit(mx, my);
        if (chapters.get(chapter).guide()) {
            for (Object[] hit : guideHits) {
                int hx = (int) hit[0], hy = (int) hit[1];
                if (mx >= hx && mx < hx + 18 && my >= hy && my < hy + 18) return (ItemStack) hit[2];
            }
            return ItemStack.EMPTY;
        }
        Page p = chapters.get(chapter).pages().get(page);
        int x = left + W / 2 + 10, y = top + 24;
        int ix = x;
        for (ItemStack s : p.items()) {
            if (ix + 18 > x + PAGE_W) break;
            if (mx >= ix && mx < ix + 18 && my >= y && my < y + 18) return s;
            ix += 20;
        }
        return ItemStack.EMPTY;
    }

    /** Recomputes the recipe grid layout to find the clicked ingredient (same math as renderRecipe). */
    private class GuiGraphicsProbe {
        ItemStack recipeHit(int mx, int my) {
            List<RecipeHolder<?>> recipes = recipesFor(recipeItem);
            if (recipes.isEmpty()) return ItemStack.EMPTY;
            RecipeHolder<?> holder = recipes.get((int) (frameRecipeSlot % recipes.size()));
            List<Ingredient> ingredients = ingredientsOf(holder);
            int gw = gridWidth(holder.value());
            int gx = left + W / 2 + 14, gy = top + 10 + 16 + 12;
            int tick = frameTick;
            for (int i = 0; i < ingredients.size(); i++) {
                int sx = gx + (i % gw) * 19, sy = gy + (i / gw) * 19;
                if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                    ItemStack[] options = ingredients.get(i).getItems();
                    return options.length == 0 ? ItemStack.EMPTY : options[tick % options.length];
                }
            }
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (lab) {
            labScroll = Math.max(0, labScroll - (int) Math.signum(scrollY));
            return true;
        }
        turn(scrollY < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
