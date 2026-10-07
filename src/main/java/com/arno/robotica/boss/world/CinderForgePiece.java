package com.arno.robotica.boss.world;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
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

/** The single template piece of the Cinder Forge. Rotates around the altar so the altar stays on the chunk centre. */
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
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }
}
