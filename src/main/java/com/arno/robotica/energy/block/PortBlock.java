package com.arno.robotica.energy.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A port in a multiblock wall. It has no logic of its own: its block entity forwards the standard FE / item
 * capabilities to the controller that formed it, so cables, pipes and Tesla Coils of any mod work on it.
 * Right-click opens the controller's GUI. A Bank Port switches between input and output with sneak + right-click.
 */
public class PortBlock extends Block implements EntityBlock {
    public enum Kind {
        /** Reactor Power Port: FE out (the Fusion Reactor also takes its ignition charge in here). */
        REACTOR_POWER,
        /** Reactor Access Port: fuel in, waste out. */
        REACTOR_ACCESS,
        /** Bank Port: FE in or out, see {@link #OUTPUT}. */
        BANK
    }

    /** Bank Port mode: false takes FE in, true sends it out. */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");

    private final Kind kind;

    public PortBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static boolean isOutput(BlockState state) {
        return state.hasProperty(OUTPUT) && state.getValue(OUTPUT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (kind == Kind.BANK && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean output = !state.getValue(OUTPUT);
                level.setBlock(pos, state.setValue(OUTPUT, output), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6F, output ? 0.6F : 0.5F);
                player.displayClientMessage(Component.translatable(output ? "message.robotica.bank_port_output" : "message.robotica.bank_port_input"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof PortBlockEntity port && player instanceof ServerPlayer serverPlayer) {
            StructureControllerBlockEntity controller = port.controller();
            if (controller != null) {
                serverPlayer.openMenu(controller, buf -> buf.writeBlockPos(controller.getBlockPos()));
            } else {
                player.displayClientMessage(Component.translatable("message.robotica.port_unlinked"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        String id = switch (kind) {
            case REACTOR_POWER -> "reactor_power_port";
            case REACTOR_ACCESS -> "reactor_access_port";
            case BANK -> "bank_port";
        };
        tooltip.add(Component.translatable("tooltip.robotica." + id).withStyle(ChatFormatting.GRAY));
    }

    /** Bank Port: the same block with an input/output switch. */
    public static class Bank extends PortBlock {
        public Bank(Properties props) {
            super(props, Kind.BANK);
            registerDefaultState(stateDefinition.any().setValue(OUTPUT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OUTPUT);
        }
    }
}
