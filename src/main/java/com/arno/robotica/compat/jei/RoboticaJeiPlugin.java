package com.arno.robotica.compat.jei;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.recipe.PressingRecipe;
import com.arno.robotica.replicator.ReplicatorRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI integration (optional). JEI finds this class through the {@link JeiPlugin} annotation, so it is never loaded
 * when JEI is missing. Nothing is hidden, and the Codex recipe browser is untouched.
 */
@JeiPlugin
public class RoboticaJeiPlugin implements IModPlugin {
    /** Metal Press recipes (the vanilla recipe holder, so recipe ids and recipe book transfer keep working). */
    static final RecipeType<RecipeHolder<PressingRecipe>> PRESSING = RecipeType.createFromDeferredVanilla(PowerRegistry.PRESSING_TYPE).get();

    @Override
    public ResourceLocation getPluginUid() {
        return Robotica.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PressingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(PRESSING, level.getRecipeManager().getAllRecipesFor(PowerRegistry.PRESSING_TYPE.get()));
        }

        // Items that have no crafting recipe, or are obtained or used in a special way.
        registration.addItemStackInfo(new ItemStack(CoreItems.SERVO_CORE.get()), Component.translatable("jei.robotica.info.servo_core"));
        registration.addItemStackInfo(new ItemStack(CoreItems.MAGMA_CORE.get()), Component.translatable("jei.robotica.info.magma_core"));
        registration.addItemStackInfo(new ItemStack(CoreItems.ANTIGRAV_CORE.get()), Component.translatable("jei.robotica.info.antigrav_core"));
        registration.addItemStackInfo(new ItemStack(ReplicatorRegistry.ESSENCE_VIAL.get()), Component.translatable("jei.robotica.info.essence_vial"));
        registration.addItemStackInfo(new ItemStack(CoreItems.MAINSPRING.get()), Component.translatable("jei.robotica.info.mainspring"));

        // One page per upgrade card: the shared stacking rules, then what this kind does.
        for (UpgradeKind kind : UpgradeKind.values()) {
            Component stacking = kind.stackable()
                    ? Component.translatable("jei.robotica.info.upgrade.stackable", kind.maxStack)
                    : Component.translatable("jei.robotica.info.upgrade.single");
            registration.addItemStackInfo(new ItemStack(CoreItems.card(kind).get()),
                    Component.translatable("jei.robotica.info.upgrade." + kind.id()),
                    stacking,
                    Component.translatable("jei.robotica.info.upgrade.rules"));
        }
    }

    @Override
    public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new StorageCraftTransfer(), mezz.jei.api.constants.RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(PRESSING, PowerRegistry.METAL_PRESS_ITEM.get());
    }
}
