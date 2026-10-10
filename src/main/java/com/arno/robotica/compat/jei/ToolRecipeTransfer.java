package com.arno.robotica.compat.jei;

import com.arno.robotica.storage.menu.StorageMenu;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI's crafting table and inventory grid handlers, except that a recipe with a tool (the Tinker's Hammer) does not
 * need complete sets. JEI caps a shift-click with complete sets at the smallest stack size in the recipe, so one
 * hammer meant one ingot per slot. These replace JEI's own handlers for the same menus (mod plugins load after JEI's).
 */
final class ToolRecipeTransfer {
    private ToolRecipeTransfer() {
    }

    static void register(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(helper.createUnregisteredRecipeTransferHandler(
                new Info<>(helper.createBasicRecipeTransferInfo(CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1, 9, 10, 36))),
                RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new PlayerGrid(helper), RecipeTypes.CRAFTING);
    }

    static boolean hasTool(CraftingRecipe recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0 && StorageMenu.isCraftingTool(items[0])) return true;
        }
        return false;
    }

    /** JEI's basic transfer info, with complete sets only for recipes without a tool. */
    private record Info<C extends AbstractContainerMenu>(IRecipeTransferInfo<C, RecipeHolder<CraftingRecipe>> base)
            implements IRecipeTransferInfo<C, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends C> getContainerClass() {
            return base.getContainerClass();
        }

        @Override
        public Optional<MenuType<C>> getMenuType() {
            return base.getMenuType();
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return base.getRecipeType();
        }

        @Override
        public boolean canHandle(C container, RecipeHolder<CraftingRecipe> recipe) {
            return base.canHandle(container, recipe);
        }

        @Override
        public List<Slot> getRecipeSlots(C container, RecipeHolder<CraftingRecipe> recipe) {
            return base.getRecipeSlots(container, recipe);
        }

        @Override
        public List<Slot> getInventorySlots(C container, RecipeHolder<CraftingRecipe> recipe) {
            return base.getInventorySlots(container, recipe);
        }

        @Override
        public boolean requireCompleteSets(C container, RecipeHolder<CraftingRecipe> recipe) {
            return !hasTool(recipe.value());
        }
    }

    /** The inventory's 2x2 grid: like JEI's own handler, it takes recipes that fit the top left 2x2 of the table grid. */
    private static final class PlayerGrid implements IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> {
        private final IRecipeTransferHandlerHelper helper;
        private final IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> handler;

        PlayerGrid(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
            this.handler = helper.createUnregisteredRecipeTransferHandler(
                    new Info<>(helper.createBasicRecipeTransferInfo(InventoryMenu.class, null, RecipeTypes.CRAFTING, 1, 4, 9, 36)));
        }

        @Override
        public Class<? extends InventoryMenu> getContainerClass() {
            return InventoryMenu.class;
        }

        @Override
        public Optional<MenuType<InventoryMenu>> getMenuType() {
            return Optional.empty();
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(InventoryMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                                             Player player, boolean maxTransfer, boolean doTransfer) {
            if (!helper.recipeTransferHasServerSupport()) {
                return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.no.server"));
            }
            // the table's top left 2x2 (inputs 0, 1, 3, 4); anything outside it does not fit
            List<IRecipeSlotView> views = slots.getSlotViews(RecipeIngredientRole.INPUT);
            List<IRecipeSlotView> grid = new ArrayList<>(4);
            for (int i = 0; i < views.size(); i++) {
                if (i < 6 && i % 3 < 2) {
                    grid.add(views.get(i));
                } else if (!views.get(i).isEmpty()) {
                    return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.too.large.player.inventory"));
                }
            }
            return handler.transferRecipe(menu, recipe, helper.createRecipeSlotsView(grid), player, maxTransfer, doTransfer);
        }
    }
}
