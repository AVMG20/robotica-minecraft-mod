package com.arno.robotica.automation.block;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Digs a hole below itself. */
public class ExcavatorBlock extends AreaWorkerBlock {
    public static final MapCodec<ExcavatorBlock> CODEC = simpleCodec(ExcavatorBlock::new);

    public ExcavatorBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<ExcavatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AreaWorkerBlockEntity> beType() {
        return AutomationContent.EXCAVATOR_BE.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExcavatorBlockEntity(pos, state);
    }
}
