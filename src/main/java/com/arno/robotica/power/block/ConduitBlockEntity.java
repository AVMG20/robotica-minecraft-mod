package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.conduit.ConduitManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Never ticks. Only announces the conduit to the {@link ConduitManager} while its chunk is loaded. */
public class ConduitBlockEntity extends BlockEntity {
    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.CONDUIT_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) ConduitManager.register(serverLevel, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) ConduitManager.unregister(serverLevel, worldPosition);
    }
}
