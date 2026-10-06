package com.arno.robotica.core.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Why a multiblock is not formed: a sentence for the GUI and, when one block is to blame, its position (for the
 * "first wrong block" readout and the client outline). Messages use the {@code multiblock.robotica.*} lang keys of core
 * or module keys; block names are passed as components, so they translate on the client.
 */
public record StructureProblem(Component message, @Nullable BlockPos pos) {
    public static StructureProblem of(@Nullable BlockPos pos, String key, Object... args) {
        return new StructureProblem(Component.translatable(key, args), pos == null ? null : pos.immutable());
    }

    /** "x, y, z" for messages. */
    public static Component at(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** The block's display name, or "Air" / "nothing" for an empty position. */
    public static Component name(BlockState state) {
        return state.isAir() ? Component.translatable("multiblock.robotica.nothing") : state.getBlock().getName();
    }
}
