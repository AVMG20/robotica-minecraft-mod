package com.arno.robotica.processing.recipe;

import com.arno.robotica.processing.ProcessingRegistry;
import com.mojang.serialization.Codec;
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

import java.util.List;

/**
 * Grinder recipe {@code robotica:grinding} for the special cases the tag rules do not cover:
 * <pre>
 * {"type": "robotica:grinding", "ingredient": &lt;Ingredient&gt;, "result": &lt;ItemStack&gt;,
 *  "extras": [{"result": &lt;ItemStack&gt;, "chance": 0.25}], "min_tier": 1}
 * </pre>
 * {@code extras} (each rolled on its own, chance 0-1) and {@code min_tier} (lowest Grinder Mk, 1-4) are optional.
 * Optional {@code time} overrides the Grinder's base ticks (0 = the config value). A recipe beats the tag rules for
 * the same input. {@code "by_tag": true} marks a recipe that only documents a generic c: tag rule (so recipe lists and
 * the Codex show where a dust comes from): the Grinder then computes the output from the tag rule and the config.
 */
public record GrindingRecipe(Ingredient ingredient, ItemStack result, List<Extra> extras, int minTier, int time, boolean byTag)
        implements Recipe<SingleRecipeInput> {

    /** A chance output. */
    public record Extra(ItemStack result, float chance) {
        public static final Codec<Extra> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Extra::result),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Extra::chance)
        ).apply(i, Extra::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Extra> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Extra::result,
                ByteBufCodecs.FLOAT, Extra::chance,
                Extra::new);
    }

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
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ProcessingRegistry.GRINDER_MK1.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ProcessingRegistry.GRINDING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ProcessingRegistry.GRINDING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<GrindingRecipe> {
        private static final MapCodec<GrindingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(GrindingRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(GrindingRecipe::result),
                Extra.CODEC.listOf().optionalFieldOf("extras", List.of()).forGetter(GrindingRecipe::extras),
                Codec.intRange(1, 4).optionalFieldOf("min_tier", 1).forGetter(GrindingRecipe::minTier),
                Codec.intRange(0, 72_000).optionalFieldOf("time", 0).forGetter(GrindingRecipe::time),
                Codec.BOOL.optionalFieldOf("by_tag", false).forGetter(GrindingRecipe::byTag)
        ).apply(i, GrindingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, GrindingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, GrindingRecipe::ingredient,
                ItemStack.STREAM_CODEC, GrindingRecipe::result,
                Extra.STREAM_CODEC.apply(ByteBufCodecs.list()), GrindingRecipe::extras,
                ByteBufCodecs.VAR_INT, GrindingRecipe::minTier,
                ByteBufCodecs.VAR_INT, GrindingRecipe::time,
                ByteBufCodecs.BOOL, GrindingRecipe::byTag,
                GrindingRecipe::new);

        @Override
        public MapCodec<GrindingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GrindingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
