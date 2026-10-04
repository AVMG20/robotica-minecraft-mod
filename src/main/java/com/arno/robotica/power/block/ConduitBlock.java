package com.arno.robotica.power.block;

import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.conduit.ConduitManager;
import com.arno.robotica.power.conduit.ConduitTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Thin energy cable. Has no logic of its own: the {@link ConduitManager} keeps a cached network per connected
 * group of conduits and moves the energy with one ticker per network. The connection properties are only
 * for rendering and are written by the manager. Different tiers connect; the network runs at the lowest tier's rate.
 */
public class ConduitBlock extends Block implements EntityBlock {
    public enum Conn implements StringRepresentable {
        NONE("none"), CONDUIT("conduit"), BLOCK("block");

        private final String name;

        Conn(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Map<Direction, EnumProperty<Conn>> CONN = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values()) {
            CONN.put(dir, EnumProperty.create(dir.getName(), Conn.class));
        }
    }

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    private final ConduitTier tier;

    public ConduitBlock(Properties props, ConduitTier tier) {
        super(props);
        this.tier = tier;
        BlockState state = stateDefinition.any();
        for (Direction dir : Direction.values()) state = state.setValue(CONN.get(dir), Conn.NONE);
        registerDefaultState(state);
    }

    public ConduitTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction dir : Direction.values()) builder.add(CONN.get(dir));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(CONN.get(dir)) != Conn.NONE) mask |= 1 << dir.ordinal();
        }
        VoxelShape shape = SHAPES[mask];
        if (shape == null) {
            shape = CORE;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) != 0) shape = Shapes.or(shape, arm(dir));
            }
            SHAPES[mask] = shape;
        }
        return shape;
    }

    private static VoxelShape arm(Direction dir) {
        return switch (dir) {
            case NORTH -> Block.box(6, 6, 0, 10, 10, 6);
            case SOUTH -> Block.box(6, 6, 10, 10, 10, 16);
            case WEST -> Block.box(0, 6, 6, 6, 10, 10);
            case EAST -> Block.box(10, 6, 6, 16, 10, 10);
            case DOWN -> Block.box(6, 0, 6, 10, 6, 10);
            case UP -> Block.box(6, 10, 6, 10, 16, 10);
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBlockEntity(pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !(neighborBlock instanceof ConduitBlock)) {
            ConduitManager.neighborChanged(serverLevel, pos, neighborPos, neighborBlock);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.conduit", tier.rate()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.conduit_network").withStyle(ChatFormatting.DARK_GRAY));
    }
}
