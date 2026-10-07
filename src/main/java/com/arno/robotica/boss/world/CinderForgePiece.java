package com.arno.robotica.boss.world;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The single template piece of the Cinder Forge. Rotates around the altar so the altar stays on the chunk centre. After
 * placing, lava touching the hall from the sides or from above turns to blackstone, so it does not flood the arena.
 */
public class CinderForgePiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = Robotica.id("cinder_forge");

    public CinderForgePiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation) {
        super(BossRegistry.CINDER_FORGE_PIECE.get(), 0, templates, TEMPLATE, TEMPLATE.toString(), settings(rotation), origin);
    }

    public CinderForgePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(BossRegistry.CINDER_FORGE_PIECE.get(), tag, templates, id -> settings(Rotation.valueOf(tag.getString("Rot"))));
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(CinderForgeStructure.HALF, 0, CinderForgeStructure.HALF))
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, chunkBox, chunkPos, pivot);
        sealLava(level, chunkBox);
    }

    /** Lava in the ring around the hall and in the layer above it (inside this chunk's box) becomes blackstone. */
    private void sealLava(WorldGenLevel level, BoundingBox chunkBox) {
        BoundingBox hall = getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState seal = Blocks.BLACKSTONE.defaultBlockState();
        for (int x = hall.minX() - 1; x <= hall.maxX() + 1; x++) {
            for (int z = hall.minZ() - 1; z <= hall.maxZ() + 1; z++) {
                boolean ring = x < hall.minX() || x > hall.maxX() || z < hall.minZ() || z > hall.maxZ();
                for (int y = hall.minY() + 1; y <= hall.maxY() + 1; y++) {
                    if (!ring && y <= hall.maxY()) continue;
                    p.set(x, y, z);
                    if (!chunkBox.isInside(p)) continue;
                    if (level.getFluidState(p).is(FluidTags.LAVA)) level.setBlock(p, seal, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }
}
