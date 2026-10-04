package com.arno.robotica.power.block;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Age 0 charger for Mainsprings. Right-click with an empty hand to wind (rate limited per player),
 * right-click with a Mainspring to insert it, sneak right-click with an empty hand to take it out.
 * The ROTATION property advances with every click, which gives a turning crank without a renderer.
 */
public class WindingCrankBlock extends PowerBlock {
    public static final BooleanProperty SPRING = BooleanProperty.create("spring");
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    /** Server tick of each player's last accepted click. Cleared on logout. */
    private static final Map<UUID, Integer> LAST_CLICK = new ConcurrentHashMap<>();

    public WindingCrankBlock(Properties props) {
        super(props, PowerRegistry.WINDING_CRANK_BE::get);
        registerDefaultState(stateDefinition.any().setValue(SPRING, false).setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPRING, ROTATION);
    }

    /** The crank needs no ticker: winding happens on click and from capability calls. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.is(CoreItems.MAINSPRING.get()) && level.getBlockEntity(pos) instanceof WindingCrankBlockEntity crank) {
            if (!crank.hasSpring()) {
                if (!level.isClientSide) {
                    ItemStack one = stack.copyWithCount(1);
                    crank.spring.setStackInSlot(0, one);
                    stack.consume(1, player);
                    CoreSounds.play(level, pos, CoreSounds.SPRING_INSERT, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WindingCrankBlockEntity crank)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (crank.hasSpring()) {
                ItemStack out = crank.spring.getStackInSlot(0).copy();
                crank.spring.setStackInSlot(0, ItemStack.EMPTY);
                CoreSounds.play(level, pos, CoreSounds.SPRING_REMOVE, SoundSource.BLOCKS, 0.7F, 1.0F);
                player.getInventory().placeItemBackInInventory(out);
            }
            return InteractionResult.CONSUME;
        }
        if (!crank.hasSpring()) {
            player.displayClientMessage(Component.translatable("message.robotica.crank_empty"), true);
            return InteractionResult.CONSUME;
        }
        int now = level.getServer().getTickCount();
        Integer last = LAST_CLICK.get(player.getUUID());
        if (last != null && now - last < PowerConfig.crankClickGapTicks()) return InteractionResult.CONSUME;
        LAST_CLICK.put(player.getUUID(), now);

        int added = crank.wind(PowerConfig.crankFePerClick());
        ItemStack spring = crank.spring.getStackInSlot(0);
        int stored = ItemEnergy.get(spring);
        int capacity = ItemEnergy.capacity(spring);
        if (added > 0) {
            float fraction = capacity <= 0 ? 0 : (float) stored / capacity;
            // Ratchet clicks rise in pitch as the spring tightens; a chime when it reaches full.
            CoreSounds.play(level, pos, CoreSounds.CRANK_WIND, SoundSource.BLOCKS, 0.8F, 0.6F + 1.0F * fraction);
            if (stored >= capacity) CoreSounds.play(level, pos, CoreSounds.CRANK_FULL, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.setBlock(pos, state.setValue(ROTATION, (state.getValue(ROTATION) + 1) & 3), Block.UPDATE_CLIENTS);
        } else {
            CoreSounds.play(level, pos, CoreSounds.CRANK_FULL, SoundSource.BLOCKS, 0.4F, 1.2F);
        }
        int percent = capacity <= 0 ? 0 : (int) (100L * stored / capacity);
        player.displayClientMessage(Component.translatable(added > 0 ? "message.robotica.crank_wound" : "message.robotica.crank_full",
                percent, stored, capacity), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.winding_crank", PowerConfig.crankFePerClick()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.winding_crank_auto", PowerConfig.crankAutoRate()).withStyle(ChatFormatting.GRAY));
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CLICK.remove(event.getEntity().getUUID());
    }
}
