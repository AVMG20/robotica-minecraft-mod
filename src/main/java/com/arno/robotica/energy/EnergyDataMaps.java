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
 *   <li>{@code data/<ns>/data_maps/item/reactor_fuel.json}: {@code {"values": {"<item>": {"heat": 400, "ticks": 12000,
 *       "waste": "robotica:depleted_fuel_pellet"}}}}. {@code heat} is reactor heat per tick while one unit burns in one rod
 *       (about FE/t at efficiency 1), {@code ticks} how long one unit lasts, {@code waste} optional.</li>
 *   <li>{@code data/<ns>/data_maps/item/fusion_fuel.json}: {@code {"power": 200000, "ticks": 2400}}: FE/t while one unit
 *       burns.</li>
 *   <li>{@code data/<ns>/data_maps/block/reactor_coolant.json}: {@code {"cooling": 1.5}} per block.</li>
 * </ul>
 * Item keys may be tags ({@code "#c:..."}) as usual for data maps. All three are synced to clients (optional sync, so
 * vanilla clients are not refused) for tooltips and JEI.
 */
public final class EnergyDataMaps {
    private EnergyDataMaps() {}

    public record ReactorFuel(int heat, int ticks, Optional<Item> waste) {
        public static final Codec<ReactorFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 100_000_000).fieldOf("heat").forGetter(ReactorFuel::heat),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(ReactorFuel::ticks),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("waste").forGetter(ReactorFuel::waste)
        ).apply(i, ReactorFuel::new));
    }

    public record FusionFuel(int power, int ticks) {
        public static final Codec<FusionFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("power").forGetter(FusionFuel::power),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(FusionFuel::ticks)
        ).apply(i, FusionFuel::new));
    }

    public record ReactorCoolant(float cooling) {
        public static final Codec<ReactorCoolant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0F, 1000.0F).fieldOf("cooling").forGetter(ReactorCoolant::cooling)
        ).apply(i, ReactorCoolant::new));
    }

    public static final DataMapType<Item, ReactorFuel> REACTOR_FUEL = DataMapType.builder(
            Robotica.id("reactor_fuel"), Registries.ITEM, ReactorFuel.CODEC).synced(ReactorFuel.CODEC, false).build();
    public static final DataMapType<Item, FusionFuel> FUSION_FUEL = DataMapType.builder(
            Robotica.id("fusion_fuel"), Registries.ITEM, FusionFuel.CODEC).synced(FusionFuel.CODEC, false).build();
    public static final DataMapType<Block, ReactorCoolant> REACTOR_COOLANT = DataMapType.builder(
            Robotica.id("reactor_coolant"), Registries.BLOCK, ReactorCoolant.CODEC).synced(ReactorCoolant.CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(REACTOR_FUEL);
        event.register(FUSION_FUEL);
        event.register(REACTOR_COOLANT);
    }

    // Game tests run without the industry module's pellets: they register a stand-in fuel here. Checked before the data map.
    private static final Map<Item, ReactorFuel> TEST_REACTOR_FUEL = new ConcurrentHashMap<>();
    private static final Map<Item, FusionFuel> TEST_FUSION_FUEL = new ConcurrentHashMap<>();

    /** Test hook: treat {@code item} as reactor fuel without a data map entry. */
    public static void registerTestFuel(Item item, ReactorFuel fuel) {
        TEST_REACTOR_FUEL.put(item, fuel);
    }

    /** Test hook: treat {@code item} as fusion fuel without a data map entry. */
    public static void registerTestFusionFuel(Item item, FusionFuel fuel) {
        TEST_FUSION_FUEL.put(item, fuel);
    }

    @Nullable
    public static ReactorFuel reactorFuel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ReactorFuel test = TEST_REACTOR_FUEL.get(stack.getItem());
        return test != null ? test : stack.getItemHolder().getData(REACTOR_FUEL);
    }

    @Nullable
    public static FusionFuel fusionFuel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        FusionFuel test = TEST_FUSION_FUEL.get(stack.getItem());
        return test != null ? test : stack.getItemHolder().getData(FUSION_FUEL);
    }

    /** Cooling value of a block for the Fission Reactor, 0 when it is no coolant. */
    public static float cooling(BlockState state) {
        ReactorCoolant coolant = state.getBlockHolder().getData(REACTOR_COOLANT);
        return coolant == null ? 0.0F : coolant.cooling();
    }

    public static boolean isCoolant(BlockState state) {
        return state.getBlockHolder().getData(REACTOR_COOLANT) != null;
    }
}
