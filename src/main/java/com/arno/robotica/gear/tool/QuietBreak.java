package com.arno.robotica.gear.tool;

import com.arno.robotica.gear.GearFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Breaks the extra blocks of an area break without vanilla's per-block break effect (a sound and up to 64 particles
 * for every nearby player, dozens of times a tick). Plain blocks (no block entity, vanilla's {@code playerWillDestroy})
 * take the same steps as {@code ServerPlayerGameMode.destroyBlock}, BreakEvent included, minus that effect; the blocks
 * are collected and each nearby client gets one packet per call and draws a few crumbs per block (GearFx). Any other
 * block goes through {@code destroyBlock} unchanged.
 */
final class QuietBreak {
    private QuietBreak() {}

    private static final Map<Class<?>, Boolean> PLAIN = new IdentityHashMap<>();
    private static final int MAX_CRUMBS = 128;
    private static final int[] crumbs = new int[4 * MAX_CRUMBS];
    private static int crumbCount;
    private static double cx, cy, cz;

    /** True when the block's class keeps vanilla's playerWillDestroy (only the break effect, piglins and a game event). */
    private static boolean plain(Block block) {
        return PLAIN.computeIfAbsent(block.getClass(), c -> {
            try {
                return c.getMethod("playerWillDestroy", Level.class, BlockPos.class, BlockState.class, Player.class).getDeclaringClass() == Block.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        });
    }

    static void begin() {
        crumbCount = 0;
    }

    /** Breaks one block of an area break as the player. */
    static void destroy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!plain(block) || state.hasBlockEntity() || block instanceof GameMasterBlock) {
            player.gameMode.destroyBlock(pos);
            return;
        }
        var gameType = player.gameMode.getGameModeForPlayer();
        if (CommonHooks.fireBlockBreak(level, gameType, player, pos, state).isCanceled()) return;
        if (player.blockActionRestricted(level, pos, gameType)) return;
        // Block.playerWillDestroy without spawnDestroyParticles
        if (state.is(BlockTags.GUARDED_BY_PIGLINS)) PiglinAi.angerNearbyPiglins(player, false);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        crumb(pos, state);
        if (player.gameMode.isCreative()) {
            remove(player, level, pos, state, false);
            return;
        }
        ItemStack held = player.getMainHandItem();
        ItemStack before = held.copy();
        boolean harvest = state.canHarvestBlock(level, pos, player);
        held.mineBlock(level, state, pos, player);
        boolean removed = remove(player, level, pos, state, harvest);
        if (harvest && removed) block.playerDestroy(level, player, pos, state, null, before);
        if (held.isEmpty() && !before.isEmpty()) EventHooks.onPlayerDestroyItem(player, before, InteractionHand.MAIN_HAND);
    }

    private static boolean remove(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, boolean harvest) {
        boolean removed = state.onDestroyedByPlayer(level, pos, player, harvest, level.getFluidState(pos));
        if (removed) state.getBlock().destroy(level, pos, state);
        return removed;
    }

    private static void crumb(BlockPos pos, BlockState state) {
        if (crumbCount >= MAX_CRUMBS) return;
        if (crumbCount == 0) {
            cx = pos.getX();
            cy = pos.getY();
            cz = pos.getZ();
        }
        int i = crumbCount++ * 4;
        crumbs[i] = pos.getX();
        crumbs[i + 1] = pos.getY();
        crumbs[i + 2] = pos.getZ();
        crumbs[i + 3] = Block.getId(state);
    }

    /** Sends the crumbs of the blocks broken since {@link #begin} in one packet per nearby player. */
    static void end(ServerLevel level) {
        if (crumbCount == 0) return;
        GearFxPayload.send(level, cx + 0.5, cy + 0.5, cz + 0.5,
                new GearFxPayload(GearFxPayload.CRUMBS, new float[0], java.util.Arrays.copyOf(crumbs, crumbCount * 4)));
        crumbCount = 0;
    }
}
