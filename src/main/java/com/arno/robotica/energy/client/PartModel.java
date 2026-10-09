package com.arno.robotica.energy.client;

import com.arno.robotica.Robotica;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.ArrayList;
import java.util.List;

/** A standalone block model (scripts/data/energy_models.py) drawn by an energy renderer; its quads refresh on reload. */
final class PartModel {
    static final PartModel SPIRE_COIL = new PartModel("spire_coil");
    static final PartModel SPIRE_COIL_GLOW = new PartModel("spire_coil_glow");
    private static final RandomSource RANDOM = RandomSource.create();

    private final ModelResourceLocation id;
    private BakedModel model;
    private List<BakedQuad> quads = List.of();

    private PartModel(String name) {
        this.id = ModelResourceLocation.standalone(Robotica.id("block/" + name));
    }

    static void register(ModelEvent.RegisterAdditional event) {
        event.register(SPIRE_COIL.id);
        event.register(SPIRE_COIL_GLOW.id);
    }

    List<BakedQuad> quads() {
        BakedModel current = Minecraft.getInstance().getModelManager().getModel(id);
        if (current != model) {
            model = current;
            List<BakedQuad> all = new ArrayList<>();
            for (Direction dir : Direction.values()) all.addAll(current.getQuads(null, dir, RANDOM));
            all.addAll(current.getQuads(null, null, RANDOM));
            quads = all;
        }
        return quads;
    }
}
