package com.arno.robotica.replicator.block;

import net.minecraft.world.level.block.TransparentBlock;

/** Window of the replicator frame. Behaves like glass so the hologram inside stays visible. */
public class ReplicatorGlassBlock extends TransparentBlock {
    public ReplicatorGlassBlock(Properties props) {
        super(props);
    }
}
