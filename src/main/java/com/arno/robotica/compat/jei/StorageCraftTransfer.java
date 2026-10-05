package com.arno.robotica.compat.jei;

import com.arno.robotica.storage.StorageContent;
import com.arno.robotica.storage.menu.StorageMenu;
import com.arno.robotica.storage.net.StorageCraftPayload;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI "+" on a crafting recipe while the Storage Terminal is open: the server fills the terminal's crafting grid from
 * the terminal and the player's inventory (shift-click: as many crafts as possible). See {@link StorageMenu#fillGrid}.
 */
final class StorageCraftTransfer implements IRecipeTransferHandler<StorageMenu, RecipeHolder<CraftingRecipe>> {
    private static final int MAX_OPTIONS = 64;

    @Override
    public Class<? extends StorageMenu> getContainerClass() {
        return StorageMenu.class;
    }

    @Override
    public Optional<MenuType<StorageMenu>> getMenuType() {
        return Optional.of(StorageContent.MENU.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(StorageMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                                         Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) return null;
        List<List<ItemStack>> grid = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            if (grid.size() == 9) break;
            grid.add(slot.getItemStacks().filter(s -> !s.isEmpty()).limit(MAX_OPTIONS).map(ItemStack::copy).toList());
        }
        PacketDistributor.sendToServer(new StorageCraftPayload(menu.containerId, grid, maxTransfer));
        return null;
    }
}
