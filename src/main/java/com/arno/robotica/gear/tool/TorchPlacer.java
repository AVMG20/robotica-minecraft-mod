package com.arno.robotica.gear.tool;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Torch Placer module (drills): a moment after you mine a block, if the spot is dark (light at or below
 * {@link GearConfig#torchLight}, sky or block light), a torch from your inventory goes on the floor or the nearest wall
 * there, or at your feet. Costs a little FE per torch, at most one torch per {@link GearConfig#torchCooldown} ticks.
 * Placement fires the normal place event, so claim mods can refuse it. State is per player UUID and dropped on logout.
 */
public final class TorchPlacer {
    private TorchPlacer() {}

    /** Ticks between the break and the light check (lets an area break and the light engine finish). */
    public static final int DELAY = 2;

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, long due) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** Called when the player breaks a block with a drill (origin of the break only). */
    public static void schedule(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos pos) {
        if (Modules.active(tool, ModuleKind.TORCH_PLACER) <= 0) return;
        PENDING.put(player.getUUID(), new Pending(level.dimension(), pos.immutable(), level.getGameTime() + DELAY));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Pending p = e.getValue();
            if (player == null || player.level().dimension() != p.dimension()) {
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (level.getGameTime() < p.due()) continue;
            it.remove();
            ItemStack tool = player.getMainHandItem();
            if (Modules.active(tool, ModuleKind.TORCH_PLACER) <= 0 || !level.isLoaded(p.pos())) continue;
            if (isDark(level, p.pos())) tryPlace(player, level, tool, p.pos());
        }
    }

    /** Light at the spot (sky light without the time of day, or block light) is at or below the configured level. */
    public static boolean isDark(Level level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) <= GearConfig.torchLight();
    }

    /**
     * Places a torch near {@code pos} without looking at the light (the caller did): the spot itself, the block below it,
     * then the player's feet; on the floor first, else on a wall (facing the player first). Returns true when placed.
     */
    public static boolean tryPlace(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos pos) {
        if (!player.mayBuild()) return false;
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < GearConfig.torchCooldown() && now >= last) return false;
        boolean creative = player.getAbilities().instabuild;
        ItemStack torches = findTorch(player.getInventory());
        if (torches.isEmpty() && !creative) return false;
        int cost = Modules.regulated(tool, GearConfig.torchCost());
        if (!creative && ItemEnergy.get(tool) < cost) return false;
        for (BlockPos spot : List.of(pos, pos.below(), player.blockPosition())) {
            for (BlockState state : candidates(player, spot)) {
                if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir() || !state.canSurvive(level, spot)) continue;
                if (!level.mayInteract(player, spot) || !level.getWorldBorder().isWithinBounds(spot)) continue;
                BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, spot);
                if (!level.setBlock(spot, state, Block.UPDATE_ALL)) continue;
                if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
                    snapshot.restore(Block.UPDATE_ALL);
                    return false;
                }
                if (!creative) {
                    torches.shrink(1);
                    ItemEnergy.drain(tool, cost);
                }
                LAST.put(player.getUUID(), now);
                level.playSound(null, spot, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
                level.sendParticles(ParticleTypes.SMALL_FLAME, spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.01);
                return true;
            }
        }
        return false;
    }

    private static List<BlockState> candidates(ServerPlayer player, BlockPos spot) {
        List<BlockState> out = new ArrayList<>();
        out.add(Blocks.TORCH.defaultBlockState());
        Direction toPlayer = Direction.getNearest(player.getX() - (spot.getX() + 0.5), 0, player.getZ() - (spot.getZ() + 0.5));
        if (toPlayer.getAxis().isHorizontal()) out.add(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, toPlayer));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != toPlayer) out.add(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, d));
        }
        return out;
    }

    private static ItemStack findTorch(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.TORCH)) return s;
        }
        return ItemStack.EMPTY;
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
        LAST.remove(player);
    }

    public static void clearAll() {
        PENDING.clear();
        LAST.clear();
    }
}
