package com.arno.robotica.processing.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.link.LinkRegistry;
import com.arno.robotica.link.block.LinkPriority;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.block.TransferMode;
import com.arno.robotica.link.network.LinkNetwork;
import com.arno.robotica.link.network.LinkNetworks;
import com.arno.robotica.link.network.Visibility;
import com.arno.robotica.processing.recipe.MachineUpgradeRecipe;
import com.arno.robotica.storage.StorageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** {@code robotica:machine_upgrade}: crafting the next Mk keeps the data of the Mk in the grid. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeRecipeGameTests {

    /** Fills the grid of an upgrade recipe with the first item of every ingredient and {@code sources} in the source slots. */
    private static ItemStack craft(GameTestHelper helper, String recipeId, List<ItemStack> sources) {
        var level = helper.getLevel();
        var holder = level.getRecipeManager().byKey(Robotica.id(recipeId)).orElseThrow();
        if (!(holder.value() instanceof MachineUpgradeRecipe recipe)) throw new IllegalStateException(recipeId + " is not an upgrade recipe");
        Ingredient source = recipe.source().orElseThrow();
        NonNullList<ItemStack> grid = NonNullList.withSize(recipe.getWidth() * recipe.getHeight(), ItemStack.EMPTY);
        int next = 0;
        for (int i = 0; i < grid.size(); i++) {
            Ingredient ing = recipe.getIngredients().get(i);
            if (ing.isEmpty()) continue;
            ItemStack first = ing.getItems()[0];
            grid.set(i, source.test(first) ? sources.get(next++).copy() : first.copy());
        }
        helper.assertTrue(next == sources.size(), recipeId + ": " + sources.size() + " source stacks used, got " + next);
        CraftingInput input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), grid);
        helper.assertTrue(recipe.matches(input, level), recipeId + " matches");
        return recipe.assemble(input, level.registryAccess());
    }

    /** A charged, bound, renamed Terminal Link Mk1 crafts into a Mk2 with the same charge, binding and name. */
    @GameTest(template = "empty")
    public static void terminalLinkUpgradeKeepsChargeAndBinding(GameTestHelper helper) {
        ItemStack mk1 = new ItemStack(StorageContent.TERMINAL_LINK.get());
        ItemEnergy.fill(mk1);
        int charge = ItemEnergy.get(mk1);
        GlobalPos bound = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 64, -40));
        mk1.set(StorageContent.LINKED_TERMINAL.get(), bound);
        mk1.set(DataComponents.CUSTOM_NAME, Component.literal("Base"));

        ItemStack mk2 = craft(helper, "terminal_link_mk2", List.of(mk1));
        helper.assertTrue(mk2.is(StorageContent.TERMINAL_LINK_MK2.get()), "makes a Terminal Link Mk2");
        helper.assertTrue(charge > 0 && ItemEnergy.get(mk2) == charge, "charge kept: " + ItemEnergy.get(mk2) + " of " + charge);
        helper.assertTrue(bound.equals(mk2.get(StorageContent.LINKED_TERMINAL.get())), "binding kept");
        helper.assertTrue("Base".equals(mk2.getHoverName().getString()), "name kept");
        helper.succeed();
    }

    /** A picked-up Quantum Link Mk1 keeps its network and settings when crafted into a Mk2, and the placed Mk2 has them. */
    @GameTest(template = "empty")
    public static void quantumLinkUpgradeKeepsSettings(GameTestHelper helper) {
        LinkNetwork net = LinkNetworks.get(helper.getLevel().getServer())
                .create(UUID.randomUUID(), "Tester", "Upgrade", 9, Visibility.PUBLIC, helper.getLevel().dimension());
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(3, 1, 1);
        helper.setBlock(a, LinkRegistry.LINK_MK1.get().defaultBlockState());
        QuantumLinkBlockEntity old = (QuantumLinkBlockEntity) helper.getBlockEntity(a);
        old.joinNetwork(net);
        old.setEnergyMode(TransferMode.SEND);
        old.setItemMode(TransferMode.RECEIVE);
        old.setPriority(LinkPriority.HIGH);

        ItemStack mk1 = new ItemStack(LinkRegistry.LINK_MK1_ITEM.get());
        mk1.applyComponents(old.collectComponents());
        mk1.set(CoreComponents.ENERGY.get(), 500);
        helper.assertTrue(mk1.has(LinkRegistry.LINK_SETTINGS.get()), "picked-up Mk1 carries its settings");

        ItemStack mk2 = craft(helper, "quantum_link_mk2", List.of(mk1));
        helper.assertTrue(mk2.is(LinkRegistry.LINK_MK2_ITEM.get()), "makes a Quantum Link Mk2");
        helper.assertTrue(mk1.get(LinkRegistry.LINK_SETTINGS.get()).equals(mk2.get(LinkRegistry.LINK_SETTINGS.get())), "settings kept");
        helper.assertTrue(mk2.getOrDefault(CoreComponents.ENERGY.get(), 0) == 500, "energy kept");

        helper.setBlock(b, LinkRegistry.LINK_MK2.get().defaultBlockState());
        QuantumLinkBlockEntity fresh = (QuantumLinkBlockEntity) helper.getBlockEntity(b);
        fresh.applyComponents(mk2.getComponents(), mk2.getComponentsPatch());
        helper.assertTrue(net.id().equals(fresh.networkId()), "placed Mk2 is on the network");
        helper.assertTrue(fresh.energyMode() == TransferMode.SEND && fresh.itemMode() == TransferMode.RECEIVE, "modes kept");
        helper.assertTrue(fresh.priority() == LinkPriority.HIGH, "priority kept");
        LinkNetworks.get(helper.getLevel().getServer()).delete(net);
        helper.succeed();
    }

    /** Two cells add their energy up, clamped to the new cell's capacity. */
    @GameTest(template = "empty")
    public static void cellUpgradeAddsAndClampsEnergy(GameTestHelper helper) {
        ItemStack half = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.set(half, 300_000);
        ItemStack out = craft(helper, "redstone_cell", List.of(half, half));
        helper.assertTrue(ItemEnergy.get(out) == 600_000, "energy added up: " + ItemEnergy.get(out));

        ItemStack over = new ItemStack(CoreItems.COPPER_CELL.get());
        over.set(CoreComponents.ENERGY.get(), 2_000_000_000);
        out = craft(helper, "redstone_cell", List.of(over, over));
        helper.assertTrue(ItemEnergy.get(out) == ItemEnergy.capacity(out), "clamped to capacity: " + ItemEnergy.get(out));
        helper.succeed();
    }
}
