package com.arno.robotica.replicator.farm;

import com.arno.robotica.replicator.ReplicatorConfig;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.logic.Essence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a mob farm makes: the mob it builds and strikes down, the vials its controller holds (one rule per slot), the
 * matrix that goes in the middle of its roof and its base numbers (server config).
 * <ul>
 *   <li>Iron Golem Farmer: three Villager vials and one zombie vial (zombie, husk, drowned or zombie villager).</li>
 *   <li>Wither Farmer: three Wither Skeleton vials. Each Wither gives a Nether Star.</li>
 * </ul>
 */
public enum FarmKind {
    GOLEM(EntityType.IRON_GOLEM, ReplicatorRegistry.IRON_GOLEM_FARMER, ReplicatorRegistry.GOLEM_MATRIX,
            ReplicatorConfig::golemEnergyPerTick, ReplicatorConfig::golemCycleTicks, 0xE8493A, "golem_farm_formed",
            new VialRule[]{VialRule.VILLAGER, VialRule.VILLAGER, VialRule.VILLAGER, VialRule.ZOMBIE}),
    WITHER(EntityType.WITHER, ReplicatorRegistry.WITHER_FARMER, ReplicatorRegistry.WITHER_MATRIX,
            ReplicatorConfig::witherEnergyPerTick, ReplicatorConfig::witherCycleTicks, 0x9A7CFF, "wither_farm_formed",
            new VialRule[]{VialRule.WITHER_SKELETON, VialRule.WITHER_SKELETON, VialRule.WITHER_SKELETON});

    /** What a vial slot takes: a complete vial of a mob this rule accepts. */
    public enum VialRule {
        VILLAGER(t -> t == EntityType.VILLAGER, Items.VILLAGER_SPAWN_EGG),
        ZOMBIE(Essence::isZombie, Items.ZOMBIE_SPAWN_EGG),
        WITHER_SKELETON(t -> t == EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SPAWN_EGG);

        private final Predicate<EntityType<?>> types;
        private final net.minecraft.world.item.Item icon;

        VialRule(Predicate<EntityType<?>> types, net.minecraft.world.item.Item icon) {
            this.types = types;
            this.icon = icon;
        }

        public boolean accepts(ItemStack stack) {
            if (!Essence.isComplete(stack)) return false;
            EntityType<?> type = Essence.type(stack);
            return type != null && types.test(type);
        }

        /** Ghost icon of the empty slot. */
        public ItemStack icon() {
            return new ItemStack(icon);
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final EntityType<?> mob;
    private final Supplier<? extends Block> controller;
    private final Supplier<? extends Block> matrix;
    private final IntSupplier energy;
    private final IntSupplier cycle;
    private final int color;
    private final String milestone;
    private final VialRule[] vials;

    FarmKind(EntityType<?> mob, Supplier<? extends Block> controller, Supplier<? extends Block> matrix, IntSupplier energy,
             IntSupplier cycle, int color, String milestone, VialRule[] vials) {
        this.mob = mob;
        this.controller = controller;
        this.matrix = matrix;
        this.energy = energy;
        this.cycle = cycle;
        this.color = color;
        this.milestone = milestone;
        this.vials = vials;
    }

    /** The farm whose controller is {@code state}'s block (the Iron Golem Farmer when it is neither). */
    public static FarmKind of(BlockState state) {
        return of(state.getBlock());
    }

    public static FarmKind of(Block block) {
        return block == WITHER.controller.get() ? WITHER : GOLEM;
    }

    public EntityType<?> mob() {
        return mob;
    }

    public net.minecraft.sounds.SoundEvent hurtSound() {
        return this == WITHER ? net.minecraft.sounds.SoundEvents.WITHER_HURT : net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT;
    }

    public Block matrix() {
        return matrix.get();
    }

    public int baseEnergyPerTick() {
        return energy.getAsInt();
    }

    public int baseCycleTicks() {
        return cycle.getAsInt();
    }

    /** Frame light when the farm forms (0xRRGGBB). */
    public int color() {
        return color;
    }

    public String milestone() {
        return milestone;
    }

    public int vialSlots() {
        return vials.length;
    }

    public VialRule vial(int slot) {
        return vials[slot];
    }
}
