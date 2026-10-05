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

/** The single template piece of the Rusted Foundry. Rotates around the altar so the altar stays on the chunk centre. */
public class RustedFoundryPiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = Robotica.id("rusted_foundry");

    public RustedFoundryPiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation) {
        super(BossRegistry.RUSTED_FOUNDRY_PIECE.get(), 0, templates, TEMPLATE, TEMPLATE.toString(), settings(rotation), origin);
    }

    public RustedFoundryPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(BossRegistry.RUSTED_FOUNDRY_PIECE.get(), tag, templates, id -> settings(Rotation.valueOf(tag.getString("Rot"))));
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(RustedFoundryStructure.HALF, 0, RustedFoundryStructure.HALF))
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
