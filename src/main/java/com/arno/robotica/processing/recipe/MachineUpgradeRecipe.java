package com.arno.robotica.processing.recipe;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.processing.ProcessingRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.Optional;

/**
 * Shaped crafting recipe {@code robotica:machine_upgrade} for the next Mk of an item: same JSON as
 * {@code minecraft:crafting_shaped} plus an optional {@code source} ingredient (the previous Mk in the grid). The
 * result gets every data component the source carries (binding, link settings, custom name, ...). Energy of all source
 * stacks is added up and clamped to the result's capacity. Without {@code source}, every grid stack with changed
 * components counts as a source.
 */
public class MachineUpgradeRecipe extends ShapedRecipe {
    private final ItemStack output;
    private final Optional<Ingredient> source;

    public MachineUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result,
                                boolean showNotification, Optional<Ingredient> source) {
        super(group, category, pattern, result, showNotification);
        this.output = result;
        this.source = source;
    }

    public Optional<Ingredient> source() {
        return source;
    }

    private boolean isSource(ItemStack stack) {
        return !stack.isEmpty() && source.map(s -> s.test(stack)).orElseGet(() -> !stack.isComponentsPatchEmpty());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = super.assemble(input, registries);
        DataComponentType<Integer> energyType = CoreComponents.ENERGY.get();
        long energy = 0;
        boolean hasEnergy = false, copied = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack in = input.getItem(i);
            if (!isSource(in)) continue;
            Integer stored = in.get(energyType);
            if (stored != null) {
                energy += stored;
                hasEnergy = true;
            }
            if (!copied) {
                copied = true;
                for (var entry : in.getComponentsPatch().entrySet()) {
                    if (entry.getKey() != energyType && entry.getValue().isPresent()) copy(out, entry.getKey(), entry.getValue().get());
                }
            }
        }
        if (hasEnergy) {
            int capacity = ItemEnergy.capacity(out);
            out.set(energyType, (int) Math.min(energy, capacity > 0 ? capacity : Integer.MAX_VALUE));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> void copy(ItemStack out, DataComponentType<T> type, Object value) {
        out.set(type, (T) value);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ProcessingRegistry.MACHINE_UPGRADE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<MachineUpgradeRecipe> {
        private static final MapCodec<MachineUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.output),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification),
                Ingredient.CODEC_NONEMPTY.optionalFieldOf("source").forGetter(MachineUpgradeRecipe::source)
        ).apply(i, MachineUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> SOURCE_STREAM_CODEC =
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC);

        private static final StreamCodec<RegistryFriendlyByteBuf, MachineUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeUtf(r.getGroup());
                    buf.writeEnum(r.category());
                    ShapedRecipePattern.STREAM_CODEC.encode(buf, r.pattern);
                    ItemStack.STREAM_CODEC.encode(buf, r.output);
                    buf.writeBoolean(r.showNotification());
                    SOURCE_STREAM_CODEC.encode(buf, r.source);
                },
                buf -> new MachineUpgradeRecipe(buf.readUtf(), buf.readEnum(CraftingBookCategory.class),
                        ShapedRecipePattern.STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf), buf.readBoolean(),
                        SOURCE_STREAM_CODEC.decode(buf)));

        @Override
        public MapCodec<MachineUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MachineUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
