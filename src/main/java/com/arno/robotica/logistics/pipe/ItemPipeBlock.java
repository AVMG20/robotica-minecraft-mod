package com.arno.robotica.logistics.pipe;

import com.arno.robotica.logistics.LogisticsConfig;
import com.arno.robotica.logistics.LogisticsContent;
import com.arno.robotica.logistics.menu.PipeMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Item pipe (Mk1 to Mk4). Each face links to the next pipe or to a block with an item capability on that face
 * (chests, machines as their side config allows, other mods' inventories). A link to an inventory is Insert (default),
 * Extract or Disabled with a filter and an order, set in the GUI of the right-clicked arm (sneak-right-click with an
 * empty hand switches the mode without it); see {@link ItemPipeBlockEntity}. Sneak-right-click with an empty hand toward
 * the next pipe cuts the link to it, or joins it again.
 */
public class ItemPipeBlock extends Block implements EntityBlock {
    private static final Map<Direction, EnumProperty<PipeConnection>> PROPS = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values()) PROPS.put(dir, EnumProperty.create(dir.getSerializedName(), PipeConnection.class));
    }

    /** Sizes in step with scripts/data/logistics_data.py: a 4 px tube, a 6 px node where it bends, splits or ends. */
    private static final VoxelShape NODE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape TUBE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> CONNECTORS = new EnumMap<>(Direction.class);
    /** Shape per combination of the six faces (2 bits each), built on first use. */
    private static final VoxelShape[] SHAPES = new VoxelShape[1 << 12];

    static {
        for (Direction dir : Direction.values()) {
            ARMS.put(dir, faceBox(dir, 2, 8));
            CONNECTORS.put(dir, Shapes.or(faceBox(dir, 5, 1), faceBox(dir, 3, 4)));
        }
    }

    private final int tier;

    public ItemPipeBlock(Properties props, int tier) {
        super(props);
        this.tier = tier;
        BlockState state = stateDefinition.any();
        for (EnumProperty<PipeConnection> prop : PROPS.values()) state = state.setValue(prop, PipeConnection.NONE);
        registerDefaultState(state);
    }

    public static EnumProperty<PipeConnection> prop(Direction dir) {
        return PROPS.get(dir);
    }

    /** 1 to 4: the Mk. */
    public int tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPS.values().forEach(builder::add);
    }

    // ---------------------------------------------------------------- shape

    /** A box against face {@code dir}, {@code half} px either side of the centre, {@code depth} px deep. */
    private static VoxelShape faceBox(Direction dir, double half, double depth) {
        double lo = 8 - half, hi = 8 + half;
        return switch (dir) {
            case NORTH -> Block.box(lo, lo, 0, hi, hi, depth);
            case SOUTH -> Block.box(lo, lo, 16 - depth, hi, hi, 16);
            case WEST -> Block.box(0, lo, lo, depth, hi, hi);
            case EAST -> Block.box(16 - depth, lo, lo, 16, hi, hi);
            case DOWN -> Block.box(lo, 0, lo, hi, depth, hi);
            case UP -> Block.box(lo, 16 - depth, lo, hi, 16, hi);
        };
    }

    /** True for a straight run: arms on exactly two opposite faces. Such a pipe has no node. */
    public static boolean isStraight(BlockState state) {
        int arms = 0;
        Direction first = null;
        for (Direction dir : Direction.values()) {
            if (!state.getValue(prop(dir)).hasArm()) continue;
            if (++arms > 2) return false;
            if (first == null) first = dir;
            else if (dir != first.getOpposite()) return false;
        }
        return arms == 2;
    }

    private static VoxelShape shapeFor(BlockState state) {
        int key = 0;
        for (Direction dir : Direction.values()) key |= state.getValue(prop(dir)).ordinal() << (2 * dir.ordinal());
        VoxelShape shape = SHAPES[key];
        if (shape == null) {
            shape = isStraight(state) ? TUBE : NODE;
            for (Direction dir : Direction.values()) {
                PipeConnection c = state.getValue(prop(dir));
                if (c.hasArm()) shape = Shapes.or(shape, ARMS.get(dir));
                if (c == PipeConnection.INSERT || c == PipeConnection.EXTRACT) shape = Shapes.or(shape, CONNECTORS.get(dir));
            }
            shape = shape.optimize();
            SHAPES[key] = shape;
        }
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // ---------------------------------------------------------------- connections

    /** True when the block next to {@code pos} on {@code dir} has an item capability on the face that touches the pipe. */
    public static boolean hasInventory(Level level, BlockPos pos, Direction dir) {
        BlockPos at = pos.relative(dir);
        return level.isLoaded(at) && !(level.getBlockState(at).getBlock() instanceof ItemPipeBlock)
                && level.getCapability(Capabilities.ItemHandler.BLOCK, at, dir.getOpposite()) != null;
    }

    /** True when the link from {@code pos} to the pipe on {@code dir} is cut on either end. */
    public static boolean cut(BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof ItemPipeBlockEntity a && a.isCut(dir)
                || level.getBlockEntity(pos.relative(dir)) instanceof ItemPipeBlockEntity b && b.isCut(dir.getOpposite());
    }

    private static PipeMode modeAt(LevelAccessor level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof ItemPipeBlockEntity be ? be.mode(dir) : PipeMode.INSERT;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            PipeConnection c = PipeConnection.NONE;
            if (level.getBlockState(pos.relative(dir)).getBlock() instanceof ItemPipeBlock) c = cut(level, pos, dir) ? PipeConnection.NONE : PipeConnection.PIPE;
            else if (hasInventory(level, pos, dir)) c = PipeMode.INSERT.connection;
            state = state.setValue(prop(dir), c);
        }
        return state;
    }

    /**
     * Links follow the neighbours. Inventory links are decided on the server (it knows every machine's side config);
     * the client only predicts pipe links and removals.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        PipeConnection current = state.getValue(prop(dir));
        PipeConnection next;
        if (neighbor.getBlock() instanceof ItemPipeBlock) next = cut(level, pos, dir) ? PipeConnection.NONE : PipeConnection.PIPE;
        else if (level instanceof ServerLevel server) next = hasInventory(server, pos, dir) ? modeAt(level, pos, dir).connection : PipeConnection.NONE;
        else next = neighbor.isAir() || current == PipeConnection.PIPE ? PipeConnection.NONE : current;
        return next == current ? state : state.setValue(prop(dir), next);
    }

    /** A new pipe joins the networks around it: they are rebuilt on their next use. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || oldState.is(this)) return;
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(pos.relative(dir)) instanceof ItemPipeBlockEntity pipe) pipe.invalidateNetwork();
        }
    }

    /** Removed or relinked: its network is rebuilt on its next use. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) pipe.invalidateNetwork();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ---------------------------------------------------------------- interaction

    /** The face a click means: the arm that was hit, or the clicked face of the core. */
    public static Direction sideFromHit(Vec3 hit, BlockPos pos, Direction clickedFace) {
        Vec3 d = hit.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double max = Math.max(Math.abs(d.x), Math.max(Math.abs(d.y), Math.abs(d.z)));
        return max <= 3.0 / 16 + 1e-4 ? clickedFace : Direction.getNearest(d.x, d.y, d.z);
    }

    /** Holding a block: place it against the pipe instead of opening the GUI. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof BlockItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Right-click an inventory link: opens its GUI (mode, filter, order). Sneak-right-click with an empty hand switches
     * it Insert, Extract, Off without the GUI; toward the next pipe (its arm, or the core's face where a cut arm was)
     * it cuts or joins that link. Sneaking with an item in hand never gets here.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = sideFromHit(hit.getLocation(), pos, hit.getDirection());
        if (level.getBlockState(pos.relative(side)).getBlock() instanceof ItemPipeBlock) {
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!level.mayInteract(player, pos) || !(level instanceof ServerLevel server)) return InteractionResult.FAIL;
            boolean cut = toggleLink(server, pos, side);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, cut ? 0.6F : 1.2F);
            player.displayClientMessage(Component.translatable(cut ? "message.robotica.pipe_link.cut" : "message.robotica.pipe_link.joined")
                    .withStyle(cut ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) || !hasInventory(level, pos, side)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) openMenu(serverPlayer, level, pos, side, pipe);
            return InteractionResult.CONSUME;
        }
        if (!level.mayInteract(player, pos)) return InteractionResult.FAIL;
        PipeMode mode = pipe.mode(side).next();
        pipe.changeMode(side, mode);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, mode == PipeMode.DISABLED ? 0.6F : 1.2F);
        player.displayClientMessage(Component.translatable(mode.translationKey()).withStyle(mode.color), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Cuts the link between the pipe at {@code pos} and the one on {@code side}, or joins it when it is cut. Both pipes
     * keep the cut; their arms and networks follow, and the two pipes flash. Returns true when the link is now cut.
     */
    public static boolean toggleLink(ServerLevel level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (!(level.getBlockEntity(pos) instanceof ItemPipeBlockEntity a) || !(level.getBlockEntity(other) instanceof ItemPipeBlockEntity b)) return false;
        boolean cut = !cut(level, pos, side);
        a.setCut(side, cut);
        b.setCut(side.getOpposite(), cut);
        PipeConnection link = cut ? PipeConnection.NONE : PipeConnection.PIPE;
        a.invalidateNetwork();
        b.invalidateNetwork();
        level.setBlock(pos, level.getBlockState(pos).setValue(prop(side), link), Block.UPDATE_ALL);
        level.setBlock(other, level.getBlockState(other).setValue(prop(side.getOpposite()), link), Block.UPDATE_ALL);
        PipeNetwork.flash(level, pos, side);
        return cut;
    }

    private static void openMenu(ServerPlayer player, Level level, BlockPos pos, Direction side, ItemPipeBlockEntity pipe) {
        Component title = Component.translatable("gui.robotica.pipe.title", level.getBlockState(pos.relative(side)).getBlock().getName());
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PipeMenu(id, inv, pos, side, pipe), title),
                buf -> PipeMenu.writeOpenData(buf, pos, side));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int interval = LogisticsConfig.interval(tier);
        String seconds = java.math.BigDecimal.valueOf(interval / 20.0).stripTrailingZeros().toPlainString();
        tooltip.add(Component.translatable("tooltip.robotica.item_pipe", LogisticsConfig.items(tier), seconds).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.item_pipe_modes").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---------------------------------------------------------------- block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != LogisticsContent.ITEM_PIPE_BE.get()) return null;
        return (lvl, pos, st, be) -> ((ItemPipeBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }
}
