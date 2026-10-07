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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lamp Placer module (drills): a moment after you mine a block, if the spot is dark (light at or below
 * {@link GearConfig#lampLight}, sky or block light), a Spark Lamp goes on the floor, a wall or the ceiling there, or at
 * your feet. Paid in FE from the drill, at most one lamp per {@link GearConfig#lampCooldown} ticks. Placement goes
 * through {@link SparkLamps} (build rights, spawn protection, world border, place event). State is per player UUID and
 * dropped on logout.
 */
public final class LampPlacer {
    private LampPlacer() {}

    /** Ticks between the break and the light check (lets an area break and the light engine finish). */
    public static final int DELAY = 2;

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, long due) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** Called when the player breaks a block with a drill (origin of the break only). */
    public static void schedule(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos pos) {
        if (Modules.active(tool, ModuleKind.LAMP_PLACER) <= 0) return;
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
            if (Modules.active(tool, ModuleKind.LAMP_PLACER) <= 0 || !level.isLoaded(p.pos())) continue;
            if (isDark(level, p.pos())) tryPlace(player, level, tool, p.pos());
        }
    }

    /** Light at the spot (sky light without the time of day, or block light) is at or below the configured level. */
    public static boolean isDark(Level level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) <= GearConfig.lampLight();
    }

    /**
     * Places a lamp near {@code pos} without looking at the light (the caller did): the spot itself, the block below it,
     * then the player's feet; on the floor first, then a wall (facing the player first), then the ceiling. Returns true
     * when placed.
     */
    public static boolean tryPlace(ServerPlayer player, ServerLevel level, ItemStack tool, BlockPos pos) {
        if (!player.mayBuild()) return false;
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < GearConfig.lampCooldown() && now >= last) return false;
        boolean creative = player.getAbilities().instabuild;
        int cost = Modules.regulated(tool, GearConfig.lampCost());
        if (!creative && ItemEnergy.get(tool) < cost) return false;
        for (BlockPos spot : List.of(pos, pos.below(), player.blockPosition())) {
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
