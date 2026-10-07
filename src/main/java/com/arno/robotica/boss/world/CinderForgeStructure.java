package com.arno.robotica.boss.world;

import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cinder Forge: a ruined 23x23 Nether forge hall of blackstone and basalt around a Forge Altar (template
 * robotica:cinder_forge, written by scripts/data/boss_structure.py). One template piece, centred on the chunk, random
 * rotation. The Nether has a roof, so it looks for a cave floor between the lava sea and the ceiling: solid ground with
 * air above in the centre, and floors within {@value #MAX_SLOPE} blocks at the four corners. Skipped when the server
 * config turns it off.
 */
public class CinderForgeStructure extends Structure {
    public static final MapCodec<CinderForgeStructure> CODEC = simpleCodec(CinderForgeStructure::new);
    /** Template size; the altar sits at (HALF, 1, HALF). */
    public static final int SIZE = 23;
    public static final int HALF = SIZE / 2;
    private static final int MAX_SLOPE = 5;
    /** Just above the lava sea (31) up to well under the bedrock roof. */
    private static final int MIN_FLOOR = 33;
    private static final int MAX_FLOOR = 100;

    public CinderForgeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!BossConfig.cinderForgeEnabled()) return Optional.empty();
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        ChunkGenerator gen = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState random = context.randomState();
        NoiseColumn centre = gen.getBaseColumn(cx, cz, height, random);
        List<Integer> floors = new ArrayList<>();
        for (int y = Math.max(MIN_FLOOR, height.getMinBuildHeight() + 1); y <= Math.min(MAX_FLOOR, height.getMaxBuildHeight() - 5); y++) {
            if (isFloor(centre, y)) floors.add(y);
        }
        if (floors.isEmpty()) return Optional.empty();
        int y = floors.get(context.random().nextInt(floors.size()));
        int[][] corners = {{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}};
        for (int[] c : corners) {
            NoiseColumn column = gen.getBaseColumn(cx + c[0], cz + c[1], height, random);
            boolean found = false;
            for (int dy = -MAX_SLOPE; dy <= MAX_SLOPE && !found; dy++) found = isFloor(column, y + dy);
            if (!found) return Optional.empty();
        }
        BlockPos origin = new BlockPos(cx - HALF, y, cz - HALF);
        Rotation rotation = Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(new BlockPos(cx, y, cz),
                builder -> builder.addPiece(new CinderForgePiece(context.structureTemplateManager(), origin, rotation))));
    }

    /** Solid ground (no lava) with three blocks of air above it. */
    private static boolean isFloor(NoiseColumn column, int y) {
        BlockState ground = column.getBlock(y);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) return false;
        for (int up = 1; up <= 3; up++) {
            if (!column.getBlock(y + up).isAir()) return false;
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return BossRegistry.CINDER_FORGE.get();
    }
}
