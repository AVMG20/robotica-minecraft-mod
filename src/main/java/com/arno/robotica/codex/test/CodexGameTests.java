package com.arno.robotica.codex.test;

import com.arno.robotica.Robotica;
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
