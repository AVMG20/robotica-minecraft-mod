package com.arno.robotica.architect.block;

import net.minecraft.world.level.block.TransparentBlock;

/** Glass-like window block: neighbouring windows hide their shared faces, light passes through. */
public class StyleWindowBlock extends TransparentBlock implements BuildingBlock {
    public StyleWindowBlock(Properties props) {
        super(props);
    }
}
