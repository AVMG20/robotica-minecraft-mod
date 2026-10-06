package com.arno.robotica.core.multiblock;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Shape rules of a variable-size cuboid multiblock built around one controller that sits in a side wall.
 * <ul>
 *   <li>frame: the 12 edges and 8 corners;</li>
 *   <li>wall: the six faces without their edges (the controller, glass, ports and casing go here);</li>
 *   <li>interior: everything inside, checked by the scan's {@link CuboidVisitor}.</li>
 * </ul>
 * Sizes are exterior sizes in blocks and are read through suppliers, so they can follow a server config.
 * {@code width} is both horizontal axes, {@code height} the vertical one. A fixed shape uses min == max.
 *
 * @param frameName  what belongs on the frame, for messages ("Reactor Casing")
 * @param wallName   what belongs in the walls, for messages ("Reactor Casing, Reactor Glass or a port")
 */
public record CuboidSpec(Predicate<BlockState> frame, Predicate<BlockState> wall, Predicate<BlockState> controller,
                         IntSupplier minWidth, IntSupplier maxWidth, IntSupplier minHeight, IntSupplier maxHeight,
                         Component structureName, Component frameName, Component wallName) {

    /** A block that belongs to the shell at all (frame, wall or a controller). */
    public boolean isShell(BlockState state) {
        return frame.test(state) || wall.test(state) || controller.test(state);
    }

    public static Builder builder(Component structureName) {
        return new Builder(structureName);
    }

    public static final class Builder {
        private final Component structureName;
        private Predicate<BlockState> frame = s -> false;
        private Predicate<BlockState> wall = s -> false;
        private Predicate<BlockState> controller = s -> false;
        private IntSupplier minWidth = () -> 3, maxWidth = () -> 3, minHeight = () -> 3, maxHeight = () -> 3;
        private Component frameName = Component.empty(), wallName = Component.empty();

        private Builder(Component structureName) {
            this.structureName = structureName;
        }

        public Builder frame(Predicate<BlockState> frame, Component name) {
            this.frame = frame;
            this.frameName = name;
            return this;
        }

        /** Faces without edges. The frame block is usually allowed here too; include it in the predicate. */
        public Builder wall(Predicate<BlockState> wall, Component name) {
            this.wall = wall;
            this.wallName = name;
            return this;
        }

        public Builder controller(Predicate<BlockState> controller) {
            this.controller = controller;
            return this;
        }

        /** Same limits on all three axes. */
        public Builder size(IntSupplier min, IntSupplier max) {
            this.minWidth = this.minHeight = min;
            this.maxWidth = this.maxHeight = max;
            return this;
        }

        public Builder width(IntSupplier min, IntSupplier max) {
            this.minWidth = min;
            this.maxWidth = max;
            return this;
        }

        public Builder height(IntSupplier min, IntSupplier max) {
            this.minHeight = min;
            this.maxHeight = max;
            return this;
        }

        public CuboidSpec build() {
            return new CuboidSpec(frame, wall, controller, minWidth, maxWidth, minHeight, maxHeight, structureName, frameName, wallName);
        }
    }
}
