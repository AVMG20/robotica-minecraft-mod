package com.arno.robotica.boss.world;

import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Rusted Foundry: a ruined 25x25 hall around a Colossus Altar (template robotica:rusted_foundry, written by
 * scripts/data/boss_structure.py). One template piece, centred on the chunk, random rotation. Skipped on rough ground or
 * water and when the server config turns it off. The structure JSON asks for "beard_thin" terrain adaptation, so the
 * ground is levelled under and inside the hall.
 */
public class RustedFoundryStructure extends Structure {
    public static final MapCodec<RustedFoundryStructure> CODEC = simpleCodec(RustedFoundryStructure::new);
    /** Template size; the altar sits at (HALF, 1, HALF). */
    public static final int SIZE = 25;
    public static final int HALF = SIZE / 2;
    private static final int MAX_SLOPE = 7;

    public RustedFoundryStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!BossConfig.foundryEnabled()) return Optional.empty();
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        ChunkGenerator gen = context.chunkGenerator();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        int[][] samples = {{0, 0}, {-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}};
        for (int[] s : samples) {
            int surface = gen.getFirstOccupiedHeight(cx + s[0], cz + s[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            int floor = gen.getFirstOccupiedHeight(cx + s[0], cz + s[1], Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            if (surface != floor) return Optional.empty();
            min = Math.min(min, surface);
            max = Math.max(max, surface);
            sum += surface;
        }
        if (max - min > MAX_SLOPE) return Optional.empty();
        int y = Math.round(sum / (float) samples.length);
        BlockPos origin = new BlockPos(cx - HALF, y, cz - HALF);
        Rotation rotation = Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(new BlockPos(cx, y, cz),
                builder -> builder.addPiece(new RustedFoundryPiece(context.structureTemplateManager(), origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return BossRegistry.RUSTED_FOUNDRY.get();
    }
}
