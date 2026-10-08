package com.arno.robotica.codex.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.codex.CodexLayout;
import com.arno.robotica.codex.LabActions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class CodexGameTests {

    /** Every id in the Creative Lab age kits must exist, so kits never silently shrink after a rename. */
    @GameTest(template = "empty")
    public static void labKitsReferenceRealItems(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (Map<String, Integer> kit : LabActions.KITS) {
            for (String id : kit.keySet()) {
                ResourceLocation key = ResourceLocation.parse(id.contains(":") ? id : Robotica.MODID + ":" + id);
                if (!BuiltInRegistries.ITEM.containsKey(key)) missing.add(id);
            }
        }
        if (!missing.isEmpty()) helper.fail("Lab kit ids without an item: " + missing);
        helper.succeed();
    }

    /**
     * The guide advancements load, form one tree under robotica:guide/root, and together unlock every Robotica recipe in
     * the recipe book (except the Architect's, which the architect module owns).
     */
    @GameTest(template = "empty")
    public static void guideCoversEveryRecipe(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        java.util.Set<ResourceLocation> unlocked = new java.util.HashSet<>();
        int steps = 0;
        for (var holder : server.getAdvancements().getAllAdvancements()) {
            if (!com.arno.robotica.codex.Guide.isGuide(holder.id())) continue;
            steps++;
            unlocked.addAll(holder.value().rewards().recipes());
            var node = server.getAdvancements().tree().get(holder.id());
            helper.assertTrue(node != null && node.root().holder().id().getPath().equals("guide/root"), holder.id() + " hangs off the guide root");
        }
        helper.assertTrue(steps >= 25, "the guide should have its steps, found " + steps);
        List<String> missing = new ArrayList<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation id = holder.id();
            if (!id.getNamespace().equals(Robotica.MODID) || unlocked.contains(id)) continue;
            String path = id.getPath();
            if (path.startsWith("timberframe_") || path.startsWith("copper_works_") || path.startsWith("steel_lab_")
                    || path.startsWith("null_spire_") || path.startsWith("architect_")) continue;
            missing.add(path);
        }
        if (!missing.isEmpty()) helper.fail("recipes no guide step unlocks: " + missing);
        helper.succeed();
    }

    private static com.google.gson.JsonObject readModJson(String... path) {
        java.nio.file.Path file = net.neoforged.fml.ModList.get().getModFileById(Robotica.MODID).getFile().findResource(path);
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }

    private static String str(com.google.gson.JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    /**
     * Lays out the whole Codex with the book's real geometry ({@link CodexLayout}) and the vanilla font widths, and fails
     * on anything that would leave the page: the chapter list (any length), chapter and page titles, item rows, page
     * text (every page must fit on one page: the book is short on purpose), the guide steps, and the book itself on the
     * smallest screen the automatic GUI scale allows (320x240).
     */
    @GameTest(template = "empty")
    public static void codexFitsTheBook(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        java.util.function.ToIntFunction<String> plain = s -> CodexLayout.estimateWidth(s, false);

        for (int rows = 1; rows <= 80; rows++) {
            CodexLayout.ListLayout list = CodexLayout.list(rows);
            int bottom = list.paged() ? CodexLayout.LIST_BOTTOM_PAGED : CodexLayout.LIST_BOTTOM_SINGLE;
            if (CodexLayout.LIST_TOP + list.perPage() * list.rowH() > bottom) problems.add("chapter list of " + rows + " rows runs past the page");
            if (list.perPage() * list.pages() < rows) problems.add("chapter list of " + rows + " rows loses rows");
        }
        float s = CodexLayout.fitScale(320, 240);
        if ((CodexLayout.W + 12) * s > 320 || (CodexLayout.H + 12) * s > 240) problems.add("book does not fit a 320x240 screen");
        if (CodexLayout.recipeMaxRows() < 3) problems.add("a 3x3 recipe does not fit");

        var chapters = readModJson("assets", "robotica", "codex", "chapters.json").getAsJsonArray("chapters");
        var layouts = readModJson("assets", "robotica", "codex", "multiblocks.json").getAsJsonObject("multiblocks");
        int titleRoom = CodexLayout.PAGE_W - CodexLayout.ROW_PAGED - 8;
        for (var ce : chapters) {
            var c = ce.getAsJsonObject();
            String chapter = str(c, "title");
            if (CodexLayout.estimateWidth(chapter, false) > titleRoom) problems.add("chapter title too wide: " + chapter);
            if (!c.has("pages")) continue;
            for (var pe : c.getAsJsonArray("pages")) {
                var p = pe.getAsJsonObject();
                String where = chapter + " / " + str(p, "title");
                int items = p.has("items") ? p.getAsJsonArray("items").size() : 0;
                if (items > CodexLayout.MAX_ITEMS) problems.add(where + ": " + items + " items, at most " + CodexLayout.MAX_ITEMS);
                if (p.has("items")) for (var ie : p.getAsJsonArray("items")) {
                    ResourceLocation key = ResourceLocation.tryParse(ie.getAsString());
                    if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) problems.add(where + ": unknown item " + ie.getAsString());
                }
                if (CodexLayout.estimateWidth(str(p, "title"), true) > CodexLayout.PAGE_W) problems.add(where + ": page title too wide");
                int lines = CodexLayout.wrap(str(p, "text"), CodexLayout.TEXT_W, plain).size();
                if (p.has("layout")) {
                    String id = str(p, "layout");
                    if (!layouts.has(id)) {
                        problems.add(where + ": unknown layout " + id);
                        continue;
                    }
                    var mb = layouts.getAsJsonObject(id);
                    var legend = mb.getAsJsonObject("legend");
                    for (var le : legend.entrySet()) {
                        String item = le.getValue().getAsString();
                        ResourceLocation key = ResourceLocation.tryParse(item);
                        if (!item.isEmpty() && (key == null || !BuiltInRegistries.ITEM.containsKey(key))) problems.add(where + ": layout item " + item + " does not exist");
                    }
                    int width = 0, depth = 0;
                    for (var layer : mb.getAsJsonArray("layers")) {
                        depth = Math.max(depth, layer.getAsJsonArray().size());
                        for (var row : layer.getAsJsonArray()) {
                            String r = row.getAsString();
                            width = Math.max(width, r.length());
                            for (char ch : r.toCharArray()) if (!legend.has(String.valueOf(ch))) problems.add(where + ": layout letter " + ch + " has no legend entry");
                        }
                    }
                    if (width > CodexLayout.LAYOUT_MAX_SIDE || depth > CodexLayout.LAYOUT_MAX_SIDE) problems.add(where + ": layout wider than " + CodexLayout.LAYOUT_MAX_SIDE);
                    int room = CodexLayout.layoutLines(depth, width);
                    if (lines > room) problems.add(where + ": " + lines + " lines under the layout, room for " + room);
                    continue;
                }
                int room = CodexLayout.linesPerSubPage(items);
                if (lines > room) problems.add(where + ": " + lines + " lines, the page holds " + room);
            }
        }

        var lang = readModJson("assets", "robotica", "lang", "en_us.json");
        for (var se : readModJson("assets", "robotica", "codex", "guide.json").getAsJsonArray("steps")) {
            String id = se.getAsJsonObject().get("id").getAsString();
            String key = "advancements.robotica." + id.substring(id.indexOf(':') + 1).replace('/', '.');
            String title = str(lang, key + ".title"), desc = str(lang, key + ".description");
            if (title.isEmpty() || desc.isEmpty()) {
                problems.add(id + ": missing title or description");
                continue;
            }
            int titleLines = CodexLayout.wrap(title, CodexLayout.GUIDE_TITLE_W, t -> CodexLayout.estimateWidth(t, true)).size();
            int descLines = CodexLayout.wrap(desc, CodexLayout.TEXT_W, plain).size();
            if (CodexLayout.stepHeight(titleLines, descLines) > CodexLayout.guideSpace()) problems.add(id + ": guide step taller than the page");
            if (CodexLayout.estimateWidth("• " + title, false) > CodexLayout.TEXT_W) problems.add(id + ": checklist line too wide");
        }
        if (!problems.isEmpty()) helper.fail("Codex layout: " + String.join("; ", problems));
        helper.succeed();
    }

    /** The milestone trigger completes its guide step: winding a spring finishes "Wind It Up". */
    @GameTest(template = "empty")
    public static void milestoneTriggerCompletesStep(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        var holder = server.getAdvancements().get(Robotica.id("guide/mainspring"));
        helper.assertTrue(holder != null, "guide/mainspring exists");
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(), "not done yet");
        com.arno.robotica.core.progress.Milestones.award(player, com.arno.robotica.core.progress.Milestones.WIND_SPRING);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(), "winding a spring completes the step");
        helper.succeed();
    }
}
