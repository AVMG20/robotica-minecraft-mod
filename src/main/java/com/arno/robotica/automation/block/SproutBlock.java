package com.arno.robotica.automation.block;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SproutBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Crop bot. */
public class SproutBlock extends FarmBotBlock {
    public static final MapCodec<SproutBlock> CODEC = simpleCodec(SproutBlock::new);

    public SproutBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<SproutBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AreaWorkerBlockEntity> beType() {
        return AutomationContent.SPROUT_BE.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SproutBlockEntity(pos, state);
    }
}
