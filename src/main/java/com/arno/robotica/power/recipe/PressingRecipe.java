package com.arno.robotica.power.recipe;

import com.arno.robotica.power.PowerRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Metal Press recipe {@code robotica:pressing}: one input ingredient (a single item) to one output stack.
 * Optional {@code time} in ticks at base speed (default 100).
 */
public record PressingRecipe(Ingredient ingredient, ItemStack result, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 100;

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(PowerRegistry.METAL_PRESS.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PowerRegistry.PRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return PowerRegistry.PRESSING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<PressingRecipe> {
        private static final MapCodec<PressingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(PressingRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(PressingRecipe::result),
                com.mojang.serialization.Codec.intRange(1, 72_000).optionalFieldOf("time", DEFAULT_TIME).forGetter(PressingRecipe::time)
        ).apply(i, PressingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, PressingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, PressingRecipe::ingredient,
                ItemStack.STREAM_CODEC, PressingRecipe::result,
                ByteBufCodecs.VAR_INT, PressingRecipe::time,
                PressingRecipe::new);

        @Override
        public MapCodec<PressingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PressingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
