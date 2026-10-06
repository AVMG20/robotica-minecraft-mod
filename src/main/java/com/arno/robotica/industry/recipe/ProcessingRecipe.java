package com.arno.robotica.industry.recipe;

import com.arno.robotica.industry.IndustryRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Recipe of an industry machine: {@code robotica:crushing}, {@code alloying}, {@code centrifuging}, {@code assembling}.
 * <pre>{@code
 * {"type": "robotica:alloying",
 *  "inputs": [{"ingredient": {"tag": "c:ingots/iron"}, "count": 1}, {"ingredient": [{"tag": "c:ingots/thorium"}, {"tag": "c:dusts/thorium"}]}],
 *  "results": [{"id": "robotica:ferrothorium_ingot", "count": 2}],
 *  "time": 200, "power": 0, "fortune": true}
 * }</pre>
 * Inputs are shapeless: every ingredient takes its own input slot (with at least {@code count} items). Results may
 * carry a {@code chance}. {@code time} is in ticks at base speed (default per machine), {@code power} the FE/t
 * (0 = the machine's config value). Both are base values: the machine's Mk and its cards scale them.
 * {@code fortune} says whether Fortune cards can add a main result (default: only for alloying, so loops like
 * centrifuging magma cream or extra casings from one assembler craft cannot multiply items).
 */
public record ProcessingRecipe(Machine machine, List<SizedIngredient> inputs, List<ChanceResult> results, int time, int power, boolean fortune)
        implements Recipe<ProcessingInput> {

    /** Fortune default of a machine's recipes when the JSON does not say. */
    public static boolean defaultFortune(Machine machine) {
        return machine == Machine.ALLOY_SMELTER;
    }

    /** True if every ingredient finds its own slot and no slot holds anything else. */
    @Override
    public boolean matches(ProcessingInput input, Level level) {
        return assign(input) != null;
    }

    /** For each ingredient the input slot it takes, or null when the input does not match. */
    @Nullable
    public int[] assign(ProcessingInput input) {
        int[] slotOf = new int[inputs.size()];
        boolean[] used = new boolean[input.size()];
        // Every filled slot must be used by some ingredient, so stray items block the machine instead of being ignored.
        int filled = 0;
        for (int i = 0; i < input.size(); i++) if (!input.getItem(i).isEmpty()) filled++;
        if (filled != inputs.size()) return null;
        return search(input, 0, slotOf, used) ? slotOf : null;
    }

    private boolean search(ProcessingInput input, int ing, int[] slotOf, boolean[] used) {
        if (ing == inputs.size()) return true;
        SizedIngredient wanted = inputs.get(ing);
        for (int s = 0; s < input.size(); s++) {
            if (used[s]) continue;
            ItemStack stack = input.getItem(s);
            if (stack.isEmpty() || !wanted.test(stack)) continue;
            used[s] = true;
            slotOf[ing] = s;
            if (search(input, ing + 1, slotOf, used)) return true;
            used[s] = false;
        }
        return false;
    }

    /** True if the stack is (part of) some ingredient of this recipe. */
    public boolean usesItem(ItemStack stack) {
        for (SizedIngredient in : inputs) {
            if (in.ingredient().test(stack)) return true;
        }
        return false;
    }

    @Override
    public ItemStack assemble(ProcessingInput input, HolderLookup.Provider registries) {
        return results.get(0).stack().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /** The first listed result (machines roll every result, see {@link ChanceResult}). */
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.get(0).stack();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (SizedIngredient in : inputs) list.add(in.ingredient());
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(IndustryRegistry.machineItem(machine, 1).get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return IndustryRegistry.serializer(machine).get();
    }

    @Override
    public RecipeType<?> getType() {
        return IndustryRegistry.recipeType(machine).get();
    }

    /** Base FE/t of this recipe: its own {@code power}, else the machine's config value. */
    public int effectivePower() {
        return power > 0 ? power : machine.basePower();
    }

    public static final class Serializer implements RecipeSerializer<ProcessingRecipe> {
        private final MapCodec<ProcessingRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> streamCodec;

        public Serializer(Machine machine) {
            Codec<List<SizedIngredient>> inputCodec = SizedIngredient.NESTED_CODEC.listOf().validate(list ->
                    list.isEmpty() || list.size() > machine.inputs
                            ? DataResult.error(() -> machine.recipeId + " takes 1 to " + machine.inputs + " inputs, got " + list.size())
                            : DataResult.success(list));
            Codec<List<ChanceResult>> resultCodec = ChanceResult.CODEC.listOf().validate(list ->
                    list.isEmpty() || list.size() > machine.outputs
                            ? DataResult.error(() -> machine.recipeId + " gives 1 to " + machine.outputs + " results, got " + list.size())
                            : DataResult.success(list));
            this.codec = RecordCodecBuilder.mapCodec(i -> i.group(
                    inputCodec.fieldOf("inputs").forGetter(ProcessingRecipe::inputs),
                    resultCodec.fieldOf("results").forGetter(ProcessingRecipe::results),
                    Codec.intRange(1, 72_000).optionalFieldOf("time", machine.defaultTime).forGetter(ProcessingRecipe::time),
                    Codec.intRange(0, 10_000_000).optionalFieldOf("power", 0).forGetter(ProcessingRecipe::power),
                    Codec.BOOL.optionalFieldOf("fortune", defaultFortune(machine)).forGetter(ProcessingRecipe::fortune)
            ).apply(i, (in, out, time, power, fortune) -> new ProcessingRecipe(machine, in, out, time, power, fortune)));
            this.streamCodec = StreamCodec.composite(
                    SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::inputs,
                    ChanceResult.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::results,
                    ByteBufCodecs.VAR_INT, ProcessingRecipe::time,
                    ByteBufCodecs.VAR_INT, ProcessingRecipe::power,
                    ByteBufCodecs.BOOL, ProcessingRecipe::fortune,
                    (in, out, time, power, fortune) -> new ProcessingRecipe(machine, in, out, time, power, fortune));
        }

        @Override
        public MapCodec<ProcessingRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
