package com.arno.robotica.gear.tool;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.gear.GearItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sounds of the mining tools, played on the server so everyone nearby hears them. 3x3 adds a chunky crunch to the
 * vanilla break sounds; 5x5 and bigger only play the vanilla ones. Vein and tree breaks keep clattering debris while
 * the queue drains. Mode switches tick higher for bigger modes. Start sounds are limited to one every half second per
 * player.
 */
public final class GearSounds {
    private GearSounds() {}

    private static final Map<UUID, Long> LAST_START = new HashMap<>();

    /** Called once per area/vein/tree break with the number of blocks it removes (origin included). */
    public static void breakStarted(ServerPlayer player, ServerLevel level, GearToolItem tool, AreaMode mode, BlockPos origin, int blocks) {
        if (blocks <= 1) return;
        long now = level.getGameTime();
        Long last = LAST_START.get(player.getUUID());
        if (last != null && now - last < 10 && now >= last) return;
        LAST_START.put(player.getUUID(), now);
        float jitter = 0.95F + level.random.nextFloat() * 0.1F;
        if (mode == AreaMode.TREE) {
            boolean saw = tool == GearItems.CHAINSAW.get();
            CoreSounds.play(level, origin, saw ? CoreSounds.CHAINSAW_REV : CoreSounds.STUMPY_CHOP, SoundSource.PLAYERS, 0.8F, jitter);
            if (blocks >= 5) {
                BlockPos top = origin.above(Math.min(blocks, 8) / 2);
                CoreSounds.play(level, top, CoreSounds.TREE_FALL, SoundSource.PLAYERS, Math.min(1.2F, 0.6F + blocks / 40F), jitter);
            }
            return;
        }
        if (mode == AreaMode.VEIN) {
            CoreSounds.play(level, origin, CoreSounds.AREA_BREAK, SoundSource.PLAYERS, 0.7F, 1.15F * jitter);
            return;
        }
        if (mode.width * mode.height * mode.depth <= 9) CoreSounds.play(level, origin, CoreSounds.AREA_BREAK, SoundSource.PLAYERS, 0.7F, jitter);
    }

    /** Debris clatter while a queued break drains; call with the block just broken, every few ticks. */
    public static void debris(ServerLevel level, BlockPos pos, int remaining) {
        float volume = Math.min(1.0F, 0.4F + remaining / 300F);
        CoreSounds.play(level, pos, CoreSounds.AREA_DEBRIS, SoundSource.PLAYERS, volume, 0.85F + level.random.nextFloat() * 0.3F);
    }

    /** Tick of a mode switch: 1x1 is the lowest pitch, 12x12x12 the highest (vein and tree count as 27 blocks). */
    public static void modeSwitch(ServerPlayer player, AreaMode mode) {
        CoreSounds.play(player, CoreSounds.TOOL_MODE, SoundSource.PLAYERS, 0.5F, modePitch(mode));
    }

    public static float modePitch(AreaMode mode) {
        int size = mode.isBox() ? mode.width * mode.height * mode.depth : mode == AreaMode.SINGLE ? 1 : 27;
        double t = Math.min(1.0, Math.log(size) / Math.log(1728));
        return (float) (0.75 + 0.75 * t);
    }

    public static void forget(UUID player) {
        LAST_START.remove(player);
    }

    public static void clearAll() {
        LAST_START.clear();
    }
}
