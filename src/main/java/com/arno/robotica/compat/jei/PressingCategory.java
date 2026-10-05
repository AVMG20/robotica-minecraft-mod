package com.arno.robotica.compat.jei;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.recipe.PressingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.Map;

/** Metal Press: one input, one output, plus the time and the FE it takes at base speed (no upgrade cards). */
class PressingCategory implements IRecipeCategory<RecipeHolder<PressingRecipe>> {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 36;
    private static final int TEXT_COLOR = 0xFF808080;

    private final IDrawable icon;
    private final IGuiHelper helper;
    private final Map<Integer, IDrawableAnimated> arrows = new HashMap<>();

    PressingCategory(IGuiHelper helper) {
        this.helper = helper;
        this.icon = helper.createDrawableItemStack(new ItemStack(PowerRegistry.METAL_PRESS_ITEM.get()));
    }

    @Override
    public RecipeType<RecipeHolder<PressingRecipe>> getRecipeType() {
        return RoboticaJeiPlugin.PRESSING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.robotica.category.pressing");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<PressingRecipe> holder, IFocusGroup focuses) {
        PressingRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 5).setStandardSlotBackground().addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 5).setOutputSlotBackground().addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<PressingRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        PressingRecipe recipe = holder.value();
        int ticks = Math.max(1, CoreConfig.scaleInterval(recipe.time()));
        arrows.computeIfAbsent(ticks, t -> helper.createAnimatedRecipeArrow(t)).draw(graphics, 36, 5);

        int perTick = CoreConfig.scaleEnergy(PowerConfig.pressPower());
        var font = Minecraft.getInstance().font;
        Component time = Component.translatable("jei.robotica.pressing.time", String.format("%.1f", ticks / 20.0));
        Component energy = Component.translatable("jei.robotica.pressing.energy", (long) perTick * ticks, perTick);
        graphics.drawString(font, time, 2, 26, TEXT_COLOR, false);
        graphics.drawString(font, energy, WIDTH - 2 - font.width(energy), 26, TEXT_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<PressingRecipe> holder) {
        return holder.id();
    }
}
