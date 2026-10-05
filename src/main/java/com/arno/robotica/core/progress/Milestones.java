package com.arno.robotica.core.progress;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Advancement trigger {@code robotica:milestone} for things vanilla triggers can not see: winding a Mainspring, a robot
 * starting work, a vial filling up, a replicator forming, a warp. Advancements use it as
 * {@code {"trigger": "robotica:milestone", "conditions": {"milestone": "wind_spring"}}}.
 * Machines award their owner (when online), never a fake player.
 */
public final class Milestones {
    private Milestones() {}

    public static final String WIND_SPRING = "wind_spring";
    public static final String ROBOT_WORKING = "robot_working";
    public static final String FARM_KIT = "farm_kit";
    public static final String VIAL_COMPLETE = "vial_complete";
    public static final String REPLICATOR_FORMED = "replicator_formed";
    public static final String WARP = "warp";
    public static final String PORTAL = "portal";

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Robotica.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, Trigger> MILESTONE = TRIGGERS.register("milestone", Trigger::new);

    /** Awards a milestone to a real player. Safe to call often: done criteria are ignored by vanilla. */
    public static void award(@Nullable ServerPlayer player, String milestone) {
        if (player == null || player instanceof FakePlayer) return;
        MILESTONE.get().trigger(player, milestone);
    }

    /** Awards a milestone to the owner of a machine if they are online and in the same dimension within 64 blocks. */
    public static void awardOwner(Level level, BlockPos pos, @Nullable UUID owner, String milestone) {
        ServerPlayer player = nearbyOwner(level, pos, owner, 64);
        if (player != null) award(player, milestone);
    }

    /** The online owner of a machine when they are in the same dimension within {@code range} blocks, else null. */
    @Nullable
    public static ServerPlayer nearbyOwner(Level level, BlockPos pos, @Nullable UUID owner, double range) {
        if (owner == null || !(level instanceof ServerLevel sl)) return null;
        ServerPlayer player = sl.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level) return null;
        return player.blockPosition().distSqr(pos) <= range * range ? player : null;
    }

    public static final class Trigger extends SimpleCriterionTrigger<Trigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String milestone) {
            trigger(player, instance -> instance.milestone().equals(milestone));
        }

        public record Instance(Optional<ContextAwarePredicate> player, String milestone) implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.fieldOf("milestone").forGetter(Instance::milestone)
            ).apply(i, Instance::new));
        }
    }
}
