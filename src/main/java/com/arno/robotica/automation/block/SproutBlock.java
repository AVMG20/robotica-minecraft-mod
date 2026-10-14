package com.arno.robotica.automation.block;

import com.mojang.serialization.MapCodec;

/** Sprout as a block, from before the robots walked: turns into the walking Sprout on its first tick. */
public class SproutBlock extends FarmBotBlock {
    public static final MapCodec<SproutBlock> CODEC = simpleCodec(SproutBlock::new);

    public SproutBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<SproutBlock> codec() {
        return CODEC;
    }
}
