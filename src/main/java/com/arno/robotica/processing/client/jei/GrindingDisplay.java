package com.arno.robotica.processing.client.jei;

import com.arno.robotica.processing.recipe.GrindingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** One Grinder line in JEI: an explicit robotica:grinding recipe or a generic c: tag rule. */
record GrindingDisplay(ResourceLocation id, Ingredient input, ItemStack main, List<GrindingRecipe.Extra> extras, int minTier, int time,
                       boolean boostable) {}
