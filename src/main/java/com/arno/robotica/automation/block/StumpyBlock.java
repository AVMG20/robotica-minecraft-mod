package com.arno.robotica.automation.block;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.StumpyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Lumber bot. */
public class StumpyBlock extends FarmBotBlock {
    public static final MapCodec<StumpyBlock> CODEC = simpleCodec(StumpyBlock::new);

    public StumpyBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<StumpyBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AreaWorkerBlockEntity> beType() {
        return AutomationContent.STUMPY_BE.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StumpyBlockEntity(pos, state);
    }
}
