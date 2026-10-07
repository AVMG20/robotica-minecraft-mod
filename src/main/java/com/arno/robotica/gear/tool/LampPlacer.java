package com.arno.robotica.gear.tool;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.lamp.SparkLamps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lamp Placer module (drills): a moment after you mine a block, if the spot is dark (light at or below
 * {@link GearConfig#lampLight}, sky or block light), a Spark Lamp goes on the floor, a wall or the ceiling there, or at
 * your feet. For an area break the spot is the bottom row of the mined area (nearest the origin), so the lamp sits on
 * the floor instead of at eye level. Paid in FE from the drill, at most one lamp per {@link GearConfig#lampCooldown}
 * ticks. Placement goes through {@link SparkLamps} (build rights, spawn protection, world border, place event). State is per player UUID and
 * dropped on logout.
 */
public final class LampPlacer {
    private LampPlacer() {}

    /** Ticks between the break and the light check (lets an area break and the light engine finish). */
    public static final int DELAY = 2;

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, BlockPos origin, long due) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** Called when the player breaks a block with a drill (origin of the break only, plus the extra area blocks). */
    public static void schedule(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos origin, List<BlockPos> targets) {
        if (Modules.active(tool, ModuleKind.LAMP_PLACER) <= 0) return;
        BlockPos spot = spotFor(origin, targets, player.blockPosition().getY());
        PENDING.put(player.getUUID(), new Pending(level.dimension(), spot, origin.immutable(), level.getGameTime() + DELAY));
    }

    /**
     * The lowest mined position nearest the origin, not below the player's feet: bottom-centre of a wall area, the origin
     * itself for 1x1 or a floor.
     */
    public static BlockPos spotFor(BlockPos origin, List<BlockPos> targets, int feetY) {
        int floorY = Math.min(origin.getY(), feetY);
        BlockPos best = origin;
        for (BlockPos p : targets) {
            if (p.getY() < floorY) continue;
            if (p.getY() < best.getY() || (p.getY() == best.getY() && p.distSqr(origin) < best.distSqr(origin))) best = p;
        }
        return best.immutable();
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
            if (Modules.active(tool, ModuleKind.LAMP_PLACER) <= 0 || !level.isLoaded(p.pos())) continue;
            if (isDark(level, p.pos())) tryPlace(player, level, tool, p.pos(), p.origin());
        }
    }

    /** Light at the spot (sky light without the time of day, or block light) is at or below the configured level. */
    public static boolean isDark(Level level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) <= GearConfig.lampLight();
    }

    public static boolean tryPlace(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos pos) {
        return tryPlace(player, level, tool, pos, pos);
    }

    /**
     * Places a lamp near {@code pos} without looking at the light (the caller did): the spot itself, the block below it,
     * then the origin of the break, then the player's feet; on the floor first, then a wall (facing the player first),
     * then the ceiling. Returns true when placed.
     */
    public static boolean tryPlace(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos pos, BlockPos origin) {
        if (!player.mayBuild()) return false;
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < GearConfig.lampCooldown() && now >= last) return false;
        boolean creative = player.getAbilities().instabuild;
        int cost = Modules.regulated(tool, GearConfig.lampCost());
        if (!creative && ItemEnergy.get(tool) < cost) return false;
        for (BlockPos spot : new LinkedHashSet<>(List.of(pos, pos.below(), origin, player.blockPosition()))) {
            for (Direction facing : facings(player, spot)) {
                if (!SparkLamps.canPlace(player, level, spot, facing)) continue;
                if (!SparkLamps.place(player, level, spot, facing)) return false;
                if (!creative) ItemEnergy.drain(tool, cost);
                LAST.put(player.getUUID(), now);
                return true;
            }
        }
        return false;
    }

    private static List<Direction> facings(ServerPlayer player, BlockPos spot) {
        List<Direction> out = new ArrayList<>();
        out.add(Direction.UP);
        Direction toPlayer = Direction.getNearest(player.getX() - (spot.getX() + 0.5), 0, player.getZ() - (spot.getZ() + 0.5));
        if (toPlayer.getAxis().isHorizontal()) out.add(toPlayer);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != toPlayer) out.add(d);
        }
        out.add(Direction.DOWN);
        return out;
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
