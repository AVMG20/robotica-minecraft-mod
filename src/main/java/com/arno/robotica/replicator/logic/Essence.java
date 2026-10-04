package com.arno.robotica.replicator.logic;

import com.arno.robotica.Robotica;
import com.arno.robotica.replicator.ReplicatorRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the Essence Vial and of what the replicator may copy. No world access, so it is unit testable:
 * sampling mutates only the stack it is given.
 */
public final class Essence {
    private Essence() {}

    public static final int SAMPLES_REQUIRED = 8;

    /** Entity types that can never be sampled (data pack extensible). */
    public static final TagKey<EntityType<?>> BLACKLIST = TagKey.create(Registries.ENTITY_TYPE, Robotica.id("replicator_blacklist"));
    /** Common tag of boss mobs. */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    /** Items the replicator never puts in its output. */
    public static final TagKey<Item> OUTPUT_BLACKLIST = TagKey.create(Registries.ITEM, Robotica.id("replicator_output_blacklist"));

    public enum Result {
        /** First sample: the vial is now bound to the mob type. */
        BOUND(true),
        SAMPLED(true),
        /** The sample just taken was the last one. */
        COMPLETED(true),
        WRONG_TYPE(false),
        ALREADY_COMPLETE(false),
        REFUSED(false);

        public final boolean success;

        Result(boolean success) {
            this.success = success;
        }
    }

    /** Hostile means Enemy (slimes, ghasts, phantoms...) or the MONSTER category. Players never count. */
    public static boolean isHostile(Entity entity) {
        return !(entity instanceof Player) && (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER);
    }

    /** Bosses and everything in the blacklist tag. The explicit list keeps working if the tags fail to load. */
    public static boolean isBlacklisted(EntityType<?> type) {
        return type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.WARDEN
                || type == EntityType.ELDER_GUARDIAN || type == EntityType.PLAYER
                || type.is(BOSSES) || type.is(BLACKLIST);
    }

    public static boolean canSample(Entity entity) {
        return isHostile(entity) && !isBlacklisted(entity.getType());
    }

    public static boolean isOutputBlacklisted(ItemStack stack) {
        return stack.is(Items.NETHER_STAR) || stack.is(Items.DRAGON_EGG) || stack.is(OUTPUT_BLACKLIST);
    }

    // ---- vial state (data components) ----

    public static boolean isVial(ItemStack stack) {
        return stack.is(ReplicatorRegistry.ESSENCE_VIAL.get());
    }

    @Nullable
    public static ResourceLocation typeId(ItemStack stack) {
        return stack.get(ReplicatorRegistry.ESSENCE_TYPE.get());
    }

    /** The bound entity type, or null for an empty vial or a type that no longer exists. */
    @Nullable
    public static EntityType<?> type(ItemStack stack) {
        ResourceLocation id = typeId(stack);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public static int samples(ItemStack stack) {
        return stack.getOrDefault(ReplicatorRegistry.ESSENCE_SAMPLES.get(), 0);
    }

    public static boolean isBound(ItemStack stack) {
        return typeId(stack) != null;
    }

    public static boolean isComplete(ItemStack stack) {
        return isVial(stack) && isBound(stack) && samples(stack) >= SAMPLES_REQUIRED;
    }

    /** A complete vial of a mob the replicator may still copy. This is what the controller's vial slot accepts. */
    public static boolean isUsable(ItemStack stack) {
        if (!isComplete(stack)) return false;
        EntityType<?> type = type(stack);
        return type != null && !isBlacklisted(type);
    }

    /** What taking a sample of {@code type} would do to this vial, without changing it. */
    public static Result test(ItemStack vial, EntityType<?> type) {
        if (isBlacklisted(type)) return Result.REFUSED;
        if (!isBound(vial)) return Result.BOUND;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (!key.equals(typeId(vial))) return Result.WRONG_TYPE;
        int samples = samples(vial);
        if (samples >= SAMPLES_REQUIRED) return Result.ALREADY_COMPLETE;
        return samples + 1 >= SAMPLES_REQUIRED ? Result.COMPLETED : Result.SAMPLED;
    }

    /**
     * Takes one sample on this exact stack. The caller must hand in a single vial (split stacks first):
     * a bound vial no longer stacks, so its max stack size becomes 1.
     */
    public static Result sample(ItemStack vial, EntityType<?> type) {
        Result result = test(vial, type);
        if (!result.success) return result;
        if (result == Result.BOUND) {
            vial.set(ReplicatorRegistry.ESSENCE_TYPE.get(), BuiltInRegistries.ENTITY_TYPE.getKey(type));
            vial.set(DataComponents.MAX_STACK_SIZE, 1);
        }
        vial.set(ReplicatorRegistry.ESSENCE_SAMPLES.get(), samples(vial) + 1);
        return result;
    }

    /** A finished vial for tests and creative use. */
    public static ItemStack completeVial(EntityType<?> type) {
        ItemStack stack = new ItemStack(ReplicatorRegistry.ESSENCE_VIAL.get());
        stack.set(ReplicatorRegistry.ESSENCE_TYPE.get(), BuiltInRegistries.ENTITY_TYPE.getKey(type));
        stack.set(ReplicatorRegistry.ESSENCE_SAMPLES.get(), SAMPLES_REQUIRED);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }
}
