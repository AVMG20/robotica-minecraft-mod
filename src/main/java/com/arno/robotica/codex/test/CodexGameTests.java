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
}
