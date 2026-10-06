package com.arno.robotica.processing.recipe;

import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Shaped crafting recipe {@code robotica:machine_upgrade} for the next Mk of a processing machine: same JSON as
 * {@code minecraft:crafting_shaped}, but the result keeps the energy and the carried contents (Carry card) of the
 * machine in the grid, so upgrading a Grinder or Electric Furnace loses nothing.
 */
public class MachineUpgradeRecipe extends ShapedRecipe {
    private final ItemStack output;

    public MachineUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.output = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = super.assemble(input, registries);
        for (int i = 0; i < input.size(); i++) {
            ItemStack in = input.getItem(i);
            if (in.getItem() instanceof BlockItem block && block.getBlock() instanceof ProcessingMachineBlock) {
                Integer energy = in.get(CoreComponents.ENERGY.get());
                if (energy != null) out.set(CoreComponents.ENERGY.get(), energy);
                var contents = in.get(CoreComponents.CONTENTS.get());
                if (contents != null) out.set(CoreComponents.CONTENTS.get(), contents.copy());
                break;
            }
        }
        return out;
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
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
        ).apply(i, MachineUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, MachineUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeUtf(r.getGroup());
                    buf.writeEnum(r.category());
                    ShapedRecipePattern.STREAM_CODEC.encode(buf, r.pattern);
                    ItemStack.STREAM_CODEC.encode(buf, r.output);
                    buf.writeBoolean(r.showNotification());
                },
                buf -> new MachineUpgradeRecipe(buf.readUtf(), buf.readEnum(CraftingBookCategory.class),
                        ShapedRecipePattern.STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf), buf.readBoolean()));

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
