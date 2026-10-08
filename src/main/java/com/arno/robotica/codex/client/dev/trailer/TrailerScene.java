package com.arno.robotica.codex.client.dev.trailer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One piece of trailer footage: a flattened site, a setup, a warm-up, then {@code durationTicks} of recording along a
 * camera path. Dev-only, see {@link Trailer}.
 */
public final class TrailerScene {
    /** Server-thread work. {@code site} is the centre of the pad, one block above the grass. */
    @FunctionalInterface
    public interface Action {
        void run(ServerLevel level, ServerPlayer player, BlockPos site);
    }

    public record Timed(int tick, Action action) {}

    public final String id;
    /** Pad size in blocks (x by z). */
    public final int width, depth;
    public final int timeOfDay, warmupTicks, durationTicks;
    public final Difficulty difficulty;
    /** First person: the player is in survival with the hand visible (set an item in {@code setup}) instead of a spectator. */
    public final boolean firstPerson;
    /** First person only: the hand swings every this many ticks while recording (0 = never). */
    public final int swingEvery;
    public final Action setup;
    public final Function<BlockPos, CameraPath> camera;
    public final List<Timed> actions;

    private TrailerScene(Builder b) {
        this.id = b.id;
        this.width = b.width;
        this.depth = b.depth;
        this.timeOfDay = b.timeOfDay;
        this.warmupTicks = b.warmupTicks;
        this.durationTicks = b.durationTicks;
        this.difficulty = b.difficulty;
        this.firstPerson = b.firstPerson;
        this.swingEvery = b.swingEvery;
        this.setup = b.setup;
        this.camera = b.camera;
        this.actions = List.copyOf(b.actions);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private int width = 40, depth = 40, timeOfDay = 4000, warmupTicks = 40, durationTicks = 120;
        private Difficulty difficulty = Difficulty.PEACEFUL;
        private boolean firstPerson;
        private int swingEvery;
        private Action setup = (level, player, site) -> {};
        private Function<BlockPos, CameraPath> camera;
        private final List<Timed> actions = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder site(int width, int depth) {
            this.width = width;
            this.depth = depth;
            return this;
        }

        /** Time of day (0 sunrise, 6000 noon, 12500 sunset, 18000 midnight). */
        public Builder time(int timeOfDay) {
            this.timeOfDay = timeOfDay;
            return this;
        }

        /** Game ticks (normal speed) between setup and the first recorded frame. */
        public Builder warmup(int ticks) {
            this.warmupTicks = ticks;
            return this;
        }

        public Builder duration(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        /** Peaceful by default; scenes with mobs or bosses ask for NORMAL. */
        public Builder difficulty(Difficulty difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        /**
         * First-person camera: the player is a flying, invulnerable survival player and the hand with the held item shows
         * (give the item in {@code setup}, e.g. {@code player.setItemInHand}). The hand swings every {@code swingEvery}
         * ticks while recording (0 = not at all). The camera path still places the player, keep it out of blocks.
         */
        public Builder firstPerson(int swingEvery) {
            this.firstPerson = true;
            this.swingEvery = swingEvery;
            return this;
        }

        public Builder setup(Action setup) {
            this.setup = setup;
            return this;
        }

        public Builder camera(Function<BlockPos, CameraPath> camera) {
            this.camera = camera;
            return this;
        }

        /** Runs {@code action} on the server thread when the scene clock passes {@code tick} (recording starts at 0). */
        public Builder at(int tick, Action action) {
            actions.add(new Timed(tick, action));
            return this;
        }

        public TrailerScene build() {
            if (camera == null) throw new IllegalStateException("Scene " + id + " has no camera");
            return new TrailerScene(this);
        }
    }
}
