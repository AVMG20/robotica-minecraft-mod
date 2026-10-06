package com.arno.robotica.industry.client.jei;

import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.recipe.ChanceResult;
import com.arno.robotica.industry.recipe.Machine;
import com.arno.robotica.industry.recipe.ProcessingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One JEI category per processing machine: inputs (with counts), arrow, results (with chances), time and FE at Mk1. */
class ProcessingCategory implements IRecipeCategory<RecipeHolder<ProcessingRecipe>> {
    private static final int WIDTH = 150;
    private static final int TEXT_COLOR = 0xFF808080;

    private final Machine machine;
    private final RecipeType<RecipeHolder<ProcessingRecipe>> type;
    private final IDrawable icon;
    private final IGuiHelper helper;
    private final Map<Integer, IDrawableAnimated> arrows = new HashMap<>();

    ProcessingCategory(IGuiHelper helper, Machine machine, RecipeType<RecipeHolder<ProcessingRecipe>> type) {
        this.helper = helper;
        this.machine = machine;
        this.type = type;
        this.icon = helper.createDrawableItemStack(new ItemStack(IndustryRegistry.machineItem(machine, 1).get()));
    }

    private int rows() {
        return machine == Machine.ASSEMBLER || machine == Machine.CENTRIFUGE ? 2 : 1;
    }

    private int arrowX() {
        return machine == Machine.CENTRIFUGE ? 28 : 60;
    }

    @Override
    public RecipeType<RecipeHolder<ProcessingRecipe>> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.robotica.category." + machine.recipeId);
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
        return rows() * 18 + 14;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ProcessingRecipe> holder, IFocusGroup focuses) {
        ProcessingRecipe recipe = holder.value();
        int top = 1;
        for (int i = 0; i < recipe.inputs().size(); i++) {
            SizedIngredient in = recipe.inputs().get(i);
            int x = machine == Machine.ASSEMBLER ? 1 + 18 * (i % 3) : 1 + 18 * i;
            int y = machine == Machine.ASSEMBLER ? top + 18 * (i / 3) : top + (rows() - 1) * 9;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y).setStandardSlotBackground().addItemStacks(Arrays.asList(in.getItems()));
        }
        for (int i = 0; i < recipe.results().size(); i++) {
            ChanceResult r = recipe.results().get(i);
            int x = machine == Machine.CENTRIFUGE ? 66 + 18 * (i % 2) : 98;
            int y = machine == Machine.CENTRIFUGE ? top + 18 * (i / 2) : top + (rows() - 1) * 9;
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).setOutputSlotBackground().addItemStack(r.stack());
            if (!r.guaranteed()) {
                String pct = String.format(Locale.ROOT, "%.0f", r.chance() * 100);
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.robotica.chance", pct).withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    @Override
    public void draw(RecipeHolder<ProcessingRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        ProcessingRecipe recipe = holder.value();
        int ticks = Math.max(1, CoreConfig.scaleInterval(recipe.time()));
        arrows.computeIfAbsent(ticks, t -> helper.createAnimatedRecipeArrow(t)).draw(graphics, arrowX(), 1 + (rows() - 1) * 9);
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < recipe.results().size(); i++) {
            ChanceResult r = recipe.results().get(i);
            if (r.guaranteed()) continue;
            int x = machine == Machine.CENTRIFUGE ? 66 + 18 * (i % 2) : 98;
            int y = machine == Machine.CENTRIFUGE ? 1 + 18 * (i / 2) : 1 + (rows() - 1) * 9;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.pose().scale(0.5F, 0.5F, 1);
            graphics.drawString(font, Math.round(r.chance() * 100) + "%", (x + 1) * 2, (y + 1) * 2, 0xFFFFD060, true);
            graphics.pose().popPose();
        }
        int perTick = CoreConfig.scaleEnergy(recipe.effectivePower());
        Component time = Component.translatable("jei.robotica.pressing.time", String.format(Locale.ROOT, "%.1f", ticks / 20.0));
        Component energy = Component.translatable("jei.robotica.pressing.energy", (long) perTick * ticks, perTick);
        int ty = getHeight() - 10;
        graphics.drawString(font, time, 1, ty, TEXT_COLOR, false);
        graphics.drawString(font, energy, WIDTH - 1 - font.width(energy), ty, TEXT_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<ProcessingRecipe> holder) {
        return holder.id();
    }
}
