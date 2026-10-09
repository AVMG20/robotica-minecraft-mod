package com.arno.robotica.energy;

import com.arno.robotica.Robotica;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The energy module's data maps. Datapacks (and the industry module) fill them:
 * <ul>
 *   <li>{@code data/<ns>/data_maps/block/spire_conductor.json}: {@code {"power": 30, "efficiency": 1.0}}: FE/t a block
 *       adds to a Tesla Spire column, and how much FE the spire gets out of its fuel (x efficiency).</li>
 *   <li>{@code data/<ns>/data_maps/item/spire_fuel.json}: {@code {"energy": 4000000, "waste": "..."}}: FE an item gives
 *       a Tesla Spire (before the efficiency), waste optional.</li>
 *   <li>{@code data/<ns>/data_maps/item/reactor_core.json}: {@code {"power": 2.0, "life": 12096000}}: Core Reactor output
 *       multiplier and how many ticks the core lasts at burn x1.</li>
 *   <li>{@code data/<ns>/data_maps/item/reactor_fuel.json}: {@code {"power": 600, "ticks": 12000, "waste": "..."}}:
 *       Core Reactor FE/t of a pellet (before the core) and how long it lasts at burn x1, waste optional.</li>
 *   <li>{@code data/<ns>/data_maps/block/core_modulator.json}: {@code {"power": 0.08, "burn": 0.12}}: what one block
 *       inside a Core Reactor adds to its power and burn rate (negative for stabilizers).</li>
 *   <li>{@code data/<ns>/data_maps/item/collider_fuel.json}: {@code {"ticks": 6000}}: how long one unit feeds a 64-block
 *       Ring Collider.</li>
 * </ul>
 * Keys may be tags ({@code "#c:..."}) as usual for data maps. All are synced to clients (optional sync, so vanilla
 * clients are not refused) for tooltips and JEI.
 */
public final class EnergyDataMaps {
    private EnergyDataMaps() {}

    public record SpireConductor(int power, float efficiency) {
        public static final Codec<SpireConductor> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 1_000_000).fieldOf("power").forGetter(SpireConductor::power),
                Codec.floatRange(0.01F, 100.0F).fieldOf("efficiency").forGetter(SpireConductor::efficiency)
        ).apply(i, SpireConductor::new));
    }

    public record SpireFuel(int energy, Optional<Item> waste) {
        public static final Codec<SpireFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy").forGetter(SpireFuel::energy),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("waste").forGetter(SpireFuel::waste)
        ).apply(i, SpireFuel::new));
    }

    public record ReactorCore(float power, int life) {
        public static final Codec<ReactorCore> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.01F, 10_000.0F).fieldOf("power").forGetter(ReactorCore::power),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("life").forGetter(ReactorCore::life)
        ).apply(i, ReactorCore::new));
    }

    public record ReactorFuel(int power, int ticks, Optional<Item> waste) {
        public static final Codec<ReactorFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 100_000_000).fieldOf("power").forGetter(ReactorFuel::power),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(ReactorFuel::ticks),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("waste").forGetter(ReactorFuel::waste)
        ).apply(i, ReactorFuel::new));
    }

    public record CoreModulator(float power, float burn) {
        public static final Codec<CoreModulator> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(-10.0F, 10.0F).fieldOf("power").forGetter(CoreModulator::power),
                Codec.floatRange(-10.0F, 10.0F).fieldOf("burn").forGetter(CoreModulator::burn)
        ).apply(i, CoreModulator::new));
    }

    public record ColliderFuel(int ticks) {
        public static final Codec<ColliderFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(ColliderFuel::ticks)
        ).apply(i, ColliderFuel::new));
    }

    public static final DataMapType<Block, SpireConductor> SPIRE_CONDUCTOR = DataMapType.builder(
            Robotica.id("spire_conductor"), Registries.BLOCK, SpireConductor.CODEC).synced(SpireConductor.CODEC, false).build();
    public static final DataMapType<Item, SpireFuel> SPIRE_FUEL = DataMapType.builder(
            Robotica.id("spire_fuel"), Registries.ITEM, SpireFuel.CODEC).synced(SpireFuel.CODEC, false).build();
    public static final DataMapType<Item, ReactorCore> REACTOR_CORE = DataMapType.builder(
            Robotica.id("reactor_core"), Registries.ITEM, ReactorCore.CODEC).synced(ReactorCore.CODEC, false).build();
    public static final DataMapType<Item, ReactorFuel> REACTOR_FUEL = DataMapType.builder(
            Robotica.id("reactor_fuel"), Registries.ITEM, ReactorFuel.CODEC).synced(ReactorFuel.CODEC, false).build();
    public static final DataMapType<Block, CoreModulator> CORE_MODULATOR = DataMapType.builder(
            Robotica.id("core_modulator"), Registries.BLOCK, CoreModulator.CODEC).synced(CoreModulator.CODEC, false).build();
    public static final DataMapType<Item, ColliderFuel> COLLIDER_FUEL = DataMapType.builder(
            Robotica.id("collider_fuel"), Registries.ITEM, ColliderFuel.CODEC).synced(ColliderFuel.CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(SPIRE_CONDUCTOR);
        event.register(SPIRE_FUEL);
        event.register(REACTOR_CORE);
        event.register(REACTOR_FUEL);
        event.register(CORE_MODULATOR);
        event.register(COLLIDER_FUEL);
    }

    // Game tests run without the industry module's pellets or the boss cores: they register stand-ins here. Checked
    // before the data maps.
    private static final Map<Item, SpireFuel> TEST_SPIRE_FUEL = new ConcurrentHashMap<>();
    private static final Map<Item, ReactorCore> TEST_CORE = new ConcurrentHashMap<>();
    private static final Map<Item, ReactorFuel> TEST_REACTOR_FUEL = new ConcurrentHashMap<>();
    private static final Map<Item, ColliderFuel> TEST_COLLIDER_FUEL = new ConcurrentHashMap<>();

    public static void registerTestSpireFuel(Item item, SpireFuel fuel) {
        TEST_SPIRE_FUEL.put(item, fuel);
    }

    public static void registerTestCore(Item item, ReactorCore core) {
        TEST_CORE.put(item, core);
    }

    /** A datapack dropping a core from robotica:reactor_core, for game tests. */
    public static void unregisterTestCore(Item item) {
        TEST_CORE.remove(item);
    }

    public static void registerTestFuel(Item item, ReactorFuel fuel) {
        TEST_REACTOR_FUEL.put(item, fuel);
    }

    public static void registerTestColliderFuel(Item item, ColliderFuel fuel) {
        TEST_COLLIDER_FUEL.put(item, fuel);
    }

    @Nullable
    public static SpireFuel spireFuel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        SpireFuel test = TEST_SPIRE_FUEL.get(stack.getItem());
        return test != null ? test : stack.getItemHolder().getData(SPIRE_FUEL);
    }

    @Nullable
    public static ReactorCore reactorCore(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ReactorCore test = TEST_CORE.get(stack.getItem());
        return test != null ? test : stack.getItemHolder().getData(REACTOR_CORE);
    }

    @Nullable
    public static ReactorFuel reactorFuel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ReactorFuel test = TEST_REACTOR_FUEL.get(stack.getItem());
        return test != null ? test : stack.getItemHolder().getData(REACTOR_FUEL);
    }

    @Nullable
    public static ColliderFuel colliderFuel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ColliderFuel test = TEST_COLLIDER_FUEL.get(stack.getItem());
        return test != null ? test : stack.getItemHolder().getData(COLLIDER_FUEL);
    }

    /** Spire conductor of a block, or null when it is none. */
    @Nullable
    public static SpireConductor conductor(BlockState state) {
        return state.getBlockHolder().getData(SPIRE_CONDUCTOR);
    }

    /** Core Reactor modulator of a block, or null when it is none. */
    @Nullable
    public static CoreModulator modulator(BlockState state) {
        return state.getBlockHolder().getData(CORE_MODULATOR);
    }
}
