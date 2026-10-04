package com.arno.robotica.architect.block;

import net.minecraft.world.level.block.RotatedPillarBlock;

/** Pillar / trim block of a style. Placed along the clicked axis like a log. */
public class StylePillarBlock extends RotatedPillarBlock implements BuildingBlock {
    public StylePillarBlock(Properties props) {
        super(props);
    }
}
