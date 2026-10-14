package com.arno.robotica.automation.block;

import com.mojang.serialization.MapCodec;

/** Stumpy as a block, from before the robots walked: turns into the walking Stumpy on its first tick. */
public class StumpyBlock extends FarmBotBlock {
    public static final MapCodec<StumpyBlock> CODEC = simpleCodec(StumpyBlock::new);

    public StumpyBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<StumpyBlock> codec() {
        return CODEC;
    }
}
