package com.arno.robotica.processing.client.jei;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.recipe.GrindingRecipe;
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

import java.util.HashMap;
import java.util.Map;

/** Grinder: input, main output, up to three chance extras with their odds, the Mk it needs and its cost at Mk1. */
class GrindingCategory implements IRecipeCategory<GrindingDisplay> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 44;
    private static final int TEXT_COLOR = 0xFF808080;

    private final IDrawable icon;
    private final IGuiHelper helper;
    private final Map<Integer, IDrawableAnimated> arrows = new HashMap<>();

    GrindingCategory(IGuiHelper helper) {
        this.helper = helper;
        this.icon = helper.createDrawableItemStack(new ItemStack(ProcessingRegistry.GRINDER_MK1.get()));
    }

    @Override
    public RecipeType<GrindingDisplay> getRecipeType() {
        return ProcessingJeiPlugin.GRINDING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.robotica.category.grinding");
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
    public void setRecipe(IRecipeLayoutBuilder builder, GrindingDisplay display, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 5).setStandardSlotBackground().addIngredients(display.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 62, 5).setOutputSlotBackground().addItemStack(display.main());
        int i = 0;
        for (GrindingRecipe.Extra extra : display.extras()) {
            if (i >= 3) break;
            builder.addSlot(RecipeIngredientRole.OUTPUT, 88 + i * 20, 5).setStandardSlotBackground().addItemStack(extra.result());
            i++;
        }
    }

    @Override
    public void draw(GrindingDisplay display, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        int ticks = Math.max(1, CoreConfig.scaleInterval(display.time() > 0 ? display.time() : ProcessingConfig.grinderTicks()));
        arrows.computeIfAbsent(ticks, t -> helper.createAnimatedRecipeArrow(t)).draw(graphics, 28, 5);
        var font = Minecraft.getInstance().font;
        int i = 0;
        for (GrindingRecipe.Extra extra : display.extras()) {
            if (i >= 3) break;
            String pct = Math.round(extra.chance() * 100) + "%";
            graphics.drawString(font, pct, 88 + i * 20 + 9 - font.width(pct) / 2, 25, TEXT_COLOR, false);
            i++;
        }
        int perTick = CoreConfig.scaleEnergy(ProcessingConfig.grinderPower());
        Component cost = Component.translatable("jei.robotica.grinding.energy", (long) perTick * ticks);
        graphics.drawString(font, cost, 2, 34, TEXT_COLOR, false);
        Component right = display.minTier() > 1 ? Component.translatable("jei.robotica.grinding.min_tier", display.minTier())
                : display.boostable() ? Component.translatable("jei.robotica.grinding.boostable") : Component.empty();
        graphics.drawString(font, right, WIDTH - 2 - font.width(right), 34, TEXT_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(GrindingDisplay display) {
        return display.id();
    }
}
