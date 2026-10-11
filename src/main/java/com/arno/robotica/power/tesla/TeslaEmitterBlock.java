package com.arno.robotica.power.tesla;

import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tesla Emitter: puts the FE of the block it sits on into a Tesla network, through one link to a relay, at the tier's
 * rate and range. A quarter of a relay (1 relay crafts into 4, 4 craft back).
 */
public class TeslaEmitterBlock extends TeslaCoilBlock {
    /** Arcs start in the middle of the floating crystal, which spans about 3.8..10. */
    public static final double EMITTER_TIP = 6.0 / 16.0;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values()) {
            SHAPES.put(dir, Shapes.or(box(dir, 4, 12, 0, 1), box(dir, 5, 11, 1, 2.2), box(dir, 6.5, 9.5, 2.2, 10.5)));
        }
    }

    public TeslaEmitterBlock(Properties props, TeslaTier tier) {
        super(props, tier);
    }

    @Override
    public int maxLinks() {
        return 1;
    }

    @Override
    public double tip() {
        return EMITTER_TIP;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.tesla_emitter", tier().range(), Fmt.compact(tier().rate()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.tesla_emitter_source").withStyle(ChatFormatting.DARK_GRAY));
    }
}
