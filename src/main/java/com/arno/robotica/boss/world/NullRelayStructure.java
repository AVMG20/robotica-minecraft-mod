package com.arno.robotica.boss.world;

import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Null Relay: a ruined 27x27 relay station of end stone, purpur and obsidian around a Null Altar on the outer End islands
 * (template robotica:null_relay, written by scripts/data/boss_structure.py). One template piece, centred on the chunk,
 * random rotation. It needs island under the centre, the four corners and the four edge midpoints (no void under the
 * arena), with at most {@value #MAX_SLOPE} blocks between them. Skipped when the server config turns it off.
 */
public class NullRelayStructure extends Structure {
    public static final MapCodec<NullRelayStructure> CODEC = simpleCodec(NullRelayStructure::new);
    /** Template size; the altar sits at (HALF, 1, HALF). */
    public static final int SIZE = 27;
    public static final int HALF = SIZE / 2;
    private static final int MAX_SLOPE = 6;
    /** Lowest island surface that counts as ground (the End's islands sit around y 50 to 75). */
    private static final int MIN_GROUND = 30;

    public NullRelayStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!BossConfig.nullRelayEnabled()) return Optional.empty();
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        ChunkGenerator gen = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        int[][] samples = {{0, 0}, {-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}, {0, -HALF}, {0, HALF}, {-HALF, 0}, {HALF, 0}};
        for (int[] s : samples) {
            int surface = gen.getFirstOccupiedHeight(cx + s[0], cz + s[1], Heightmap.Types.WORLD_SURFACE_WG, height, context.randomState());
            if (surface < Math.max(MIN_GROUND, height.getMinBuildHeight() + 1)) return Optional.empty();
            min = Math.min(min, surface);
            max = Math.max(max, surface);
            sum += surface;
        }
        if (max - min > MAX_SLOPE) return Optional.empty();
        int y = Math.round(sum / (float) samples.length);
        BlockPos origin = new BlockPos(cx - HALF, y, cz - HALF);
        Rotation rotation = Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(new BlockPos(cx, y, cz),
                builder -> builder.addPiece(new NullRelayPiece(context.structureTemplateManager(), origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return BossRegistry.NULL_RELAY.get();
    }
}
