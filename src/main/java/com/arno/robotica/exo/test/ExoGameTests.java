package com.arno.robotica.exo.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.ExoActions;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoItems;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoRules;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.ExoTicker;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Arrays;
import java.util.List;

@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class ExoGameTests {

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
        return player;
    }

    private static ItemStack mod(ExoModuleKind kind, int level) {
        return new ItemStack(ExoItems.module(kind, level).get());
    }

    private static ItemStack mod(ExoModuleKind kind) {
        return mod(kind, 1);
    }

    /** An Exo piece with the given modules installed and the given energy. */
    private static ItemStack pieceM(DeferredItem<ExoArmorItem> item, int energy, ItemStack... modules) {
        ItemStack stack = new ItemStack(item.get());
        ExoData.setModules(stack, Arrays.asList(modules));
        ItemEnergy.set(stack, energy);
        return stack;
    }

    private static ItemStack piece(DeferredItem<ExoArmorItem> item, int energy, ExoModuleKind... kinds) {
        return pieceM(item, energy, Arrays.stream(kinds).map(ExoGameTests::mod).toArray(ItemStack[]::new));
    }

    private static void ticks(ServerPlayer player, int n) {
        for (int i = 0; i < n; i++) ExoTicker.tick(player);
        ExoTicker.flush(player);
    }

    private static int energy(ServerPlayer player, EquipmentSlot slot) {
        return ItemEnergy.get(player.getItemBySlot(slot));
    }

    // ---------------------------------------------------------------- module screen

    @GameTest(template = "empty")
    public static void moduleInstallAndRemoveKeepEnergy(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack chest = piece(ExoItems.CHESTPLATE_MK3, 123_456);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ExoMenu menu = new ExoMenu(1, player.getInventory(), List.of(new ExoMenu.Section(EquipmentSlot.CHEST, 0, 3, true)), true);

        helper.assertTrue(!menu.slots.get(0).mayPlace(mod(ExoModuleKind.NIGHT_VISION)), "a helmet module must not fit the chestplate");
        ItemStack flight = mod(ExoModuleKind.FLIGHT);
        helper.assertTrue(menu.slots.get(0).mayPlace(flight), "a chest module fits the chestplate");

        menu.slots.get(0).set(flight);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        helper.assertTrue(ExoData.kind(worn, 0) == ExoModuleKind.FLIGHT, "module should be installed in the worn piece");
        helper.assertTrue(ItemEnergy.get(worn) == 123_456, "installing a module must keep the energy, got " + ItemEnergy.get(worn));
        helper.assertTrue(ExoData.isEnabled(worn, 0), "a new module starts switched on");
        helper.assertTrue(!menu.slots.get(1).mayPlace(mod(ExoModuleKind.FLIGHT)), "the same kind twice in one piece is refused");

        helper.assertTrue(menu.clickMenuButton(player, 0), "toggle click should work");
        helper.assertTrue(!ExoData.isEnabled(worn, 0), "toggle should switch the module off");
        helper.assertTrue(!ExoSuit.active(player).has(ExoModuleKind.FLIGHT), "a switched off module is not active");

        ItemStack taken = menu.slots.get(0).remove(1);
        helper.assertTrue(taken.getItem() == ExoItems.module(ExoModuleKind.FLIGHT).get(), "the module comes back out");
        helper.assertTrue(ExoData.kind(worn, 0) == null, "slot should be empty again");
        helper.assertTrue(ExoData.isEnabled(worn, 0), "removing clears the off bit");
        helper.assertTrue(ItemEnergy.get(worn) == 123_456, "removing a module must keep the energy");

        // Core socket: only boss cores, not consumed, removable.
        int coreSlot = 3;
        helper.assertTrue(!menu.slots.get(coreSlot).mayPlace(new ItemStack(Items.DIAMOND)), "the socket takes only cores");
        menu.slots.get(coreSlot).set(new ItemStack(CoreItems.MAGMA_CORE.get()));
        helper.assertTrue(ExoData.coreKind(ExoData.core(worn)) == ExoData.Core.MAGMA, "the core is stored on the chestplate");
        menu.slots.get(coreSlot).remove(1);
        helper.assertTrue(ExoData.core(worn).isEmpty(), "the core comes back out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void marksSlotsAndSmithingKeepEverything(GameTestHelper helper) {
        for (int mk = 1; mk <= 4; mk++) {
            for (EquipmentSlot slot : ExoSuit.SLOTS) {
                ExoArmorItem item = ExoItems.piece(mk, slot).get();
                helper.assertTrue(item.mk == mk && item.moduleSlots() == mk, "Mk" + mk + " has " + mk + " module slots");
                helper.assertTrue(item.getEquipmentSlot() == slot, "piece " + slot + " Mk" + mk);
                helper.assertTrue(item.hasCoreSocket() == (mk >= 2 && slot == EquipmentSlot.CHEST), "core socket only on Mk2+ chestplates");
            }
        }
        int mult = ExoConfig.markMultiplier();
        helper.assertTrue(ExoItems.CHESTPLATE_MK3.get().baseCapacity() == ExoConfig.baseCapacity(1) * mult * mult, "Mk3 battery is x16");
        helper.assertTrue(ExoItems.CHESTPLATE_MK4.get().baseCapacity() == ExoConfig.baseCapacity(1) * mult * mult * mult, "Mk4 battery is x64");
        helper.assertTrue(ExoItems.CHESTPLATE_MK3.get().getDefense() == 8 && ExoItems.CHESTPLATE_MK3.get().getToughness() == 3.0F, "Mk3 is netherite-like");
        helper.assertTrue(ExoItems.CHESTPLATE_MK4.get().getDefense() == 9 && ExoItems.CHESTPLATE_MK4.get().getToughness() == 4.0F, "Mk4 is above netherite");
        helper.assertTrue(new ItemStack(ExoItems.BOOTS_MK4.get()).has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT), "Mk4 does not burn");

        // Smithing Mk2 -> Mk3 -> Mk4 keeps energy, modules, switches and the core.
        ServerLevel level = helper.getLevel();
        ItemStack mk2 = pieceM(ExoItems.CHESTPLATE_MK2, 777_000, mod(ExoModuleKind.MED_INJECTOR), mod(ExoModuleKind.JET_ASSIST));
        ExoData.setEnabled(mk2, 1, false);
        ExoData.setCore(mk2, new ItemStack(CoreItems.SERVO_CORE.get()));
        SmithingRecipeInput in3 = new SmithingRecipeInput(new ItemStack(CoreItems.BLAZING_CASING.get()), mk2, part("superconductor_coil"));
        var r3 = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, in3, level);
        helper.assertTrue(r3.isPresent(), "a Mk3 smithing recipe exists");
        ItemStack mk3 = r3.get().value().assemble(in3, level.registryAccess());
        helper.assertTrue(mk3.is(ExoItems.CHESTPLATE_MK3.get()), "smithing makes the Mk3 chestplate");
        helper.assertTrue(ItemEnergy.get(mk3) == 777_000, "energy survives smithing");
        helper.assertTrue(ExoData.kind(mk3, 0) == ExoModuleKind.MED_INJECTOR && ExoData.kind(mk3, 1) == ExoModuleKind.JET_ASSIST, "modules survive smithing");
        helper.assertTrue(!ExoData.isEnabled(mk3, 1), "switches survive smithing");
        helper.assertTrue(ExoData.coreKind(ExoData.core(mk3)) == ExoData.Core.SERVO, "the core survives smithing");
        SmithingRecipeInput in4 = new SmithingRecipeInput(new ItemStack(CoreItems.NULL_CASING.get()), mk3, part("resonant_lattice"));
        var r4 = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, in4, level);
        helper.assertTrue(r4.isPresent(), "a Mk4 smithing recipe exists");
        ItemStack mk4 = r4.get().value().assemble(in4, level.registryAccess());
        helper.assertTrue(mk4.is(ExoItems.CHESTPLATE_MK4.get()) && ItemEnergy.get(mk4) == 777_000 && ExoData.slotCount(mk4) == 4,
                "Mk4 chestplate with four slots keeps its energy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markGatingAndOnePerSuit(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack mk2Chest = new ItemStack(ExoItems.CHESTPLATE_MK2.get());
        helper.assertTrue(ExoRules.moduleRefusal(mk2Chest, 0, mod(ExoModuleKind.FLIGHT), List.of()) != null, "Flight needs a Mk3 chestplate");
        helper.assertTrue(ExoRules.moduleRefusal(new ItemStack(ExoItems.CHESTPLATE_MK3.get()), 0, mod(ExoModuleKind.FLIGHT), List.of()) == null, "Mk3 takes Flight");
        helper.assertTrue(ExoRules.moduleRefusal(mk2Chest, 0, mod(ExoModuleKind.KINETIC_SHIELD), List.of()) != null, "Kinetic Shield needs Mk3");
        helper.assertTrue(ExoRules.moduleRefusal(new ItemStack(ExoItems.CHESTPLATE_MK3.get()), 0, mod(ExoModuleKind.KINETIC_SHIELD, 2), List.of()) != null,
                "Kinetic Shield II needs Mk4");
        helper.assertTrue(ExoRules.moduleRefusal(new ItemStack(ExoItems.BOOTS_MK1.get()), 0, mod(ExoModuleKind.MAGNET, 2), List.of()) != null, "level II needs Mk2");
        // Step Assist sits on the boots, from Mk1.
        helper.assertTrue(ExoRules.moduleRefusal(new ItemStack(ExoItems.BOOTS_MK1.get()), 0, mod(ExoModuleKind.STEP_ASSIST), List.of()) == null, "Step Assist fits Mk1 boots");
        helper.assertTrue(ExoRules.moduleRefusal(new ItemStack(ExoItems.LEGGINGS_MK4.get()), 0, mod(ExoModuleKind.STEP_ASSIST), List.of()) != null, "Step Assist is not for leggings");

        // Same kind twice in one piece (Stride I + II) is refused.
        ItemStack legs = pieceM(ExoItems.LEGGINGS_MK2, 0, mod(ExoModuleKind.SERVO_STRIDE, 1));
        helper.assertTrue(ExoRules.moduleRefusal(legs, 1, mod(ExoModuleKind.SERVO_STRIDE, 2), List.of()) != null, "Stride I and II in one piece is refused");

        // One per suit across pieces: Dash Thrusters fit legs or boots, but only once.
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK3, 50_000, ExoModuleKind.DASH_THRUSTERS));
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK3, 50_000));
        ExoMenu menu = new ExoMenu(2, player.getInventory(), List.of(new ExoMenu.Section(EquipmentSlot.LEGS, 0, 3, false)), true);
        helper.assertTrue(!menu.slots.get(0).mayPlace(mod(ExoModuleKind.DASH_THRUSTERS)), "a second Dash Thrusters in the suit is refused");
        helper.assertTrue(menu.slots.get(0).mayPlace(mod(ExoModuleKind.CAPACITOR_PLATING)), "Capacitor Plating fits any piece");
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK3, 50_000, ExoModuleKind.CAPACITOR_PLATING));
        helper.assertTrue(menu.slots.get(0).mayPlace(mod(ExoModuleKind.CAPACITOR_PLATING)), "Capacitor Plating works per piece, one in every piece is fine");
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK3, 50_000, ExoModuleKind.POWER_REGULATOR));
        helper.assertTrue(!menu.slots.get(0).mayPlace(mod(ExoModuleKind.POWER_REGULATOR)), "the Power Regulator works for the whole suit, once");

        // Duplicates that got in anyway (two pieces put on) never stack: the highest level counts.
        player.setItemSlot(EquipmentSlot.HEAD, pieceM(ExoItems.HELMET_MK4, 50_000, mod(ExoModuleKind.POWER_REGULATOR, 3)));
        helper.assertTrue(ExoSuit.active(player).level(ExoModuleKind.POWER_REGULATOR) == 3, "the highest level counts once");
        helper.succeed();
    }

    // ---------------------------------------------------------------- modules

    @GameTest(template = "empty")
    public static void abilitiesConsumeEnergyAndStopWhenEmpty(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK1, 100_000, ExoModuleKind.NIGHT_VISION));
        player.setItemSlot(EquipmentSlot.LEGS, pieceM(ExoItems.LEGGINGS_MK2, 100_000, mod(ExoModuleKind.SERVO_STRIDE, 2)));
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 100_000, ExoModuleKind.STEP_ASSIST));

        ticks(player, 40);
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "night vision should be applied while charged");
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Robotica.id("exo_servo_stride"));
        helper.assertTrue(speed != null && Math.abs(speed.amount() - ExoConfig.servoSpeed(2)) < 1.0E-9, "servo stride II gives +40% speed");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.STEP_HEIGHT) - 1.0) < 1.0E-6,
                "step assist on the boots walks up full blocks, step height " + player.getAttributeValue(Attributes.STEP_HEIGHT));
        int nvUsed = 100_000 - energy(player, EquipmentSlot.HEAD);
        helper.assertTrue(nvUsed > 0 && nvUsed <= ExoConfig.cost(ExoModuleKind.NIGHT_VISION, 1) * 3, "night vision costs a little FE, used " + nvUsed);

        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.HEAD), 0);
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.LEGS), 0);
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.FEET), 0);
        ticks(player, 2);
        helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION), "empty helmet: night vision is removed");
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Robotica.id("exo_servo_stride")) == null, "empty leggings: no speed bonus");
        helper.assertTrue(player.getAttribute(Attributes.STEP_HEIGHT).getModifier(Robotica.id("exo_step_assist")) == null, "empty boots: no step bonus");
        helper.assertTrue(energy(player, EquipmentSlot.HEAD) == 0, "energy never goes below zero");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getMaxDamage() == 0, "exo armor has no durability");

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        ExoTicker.tick(player);
        helper.assertTrue(!ExoTicker.hasState(player), "state is dropped when the suit comes off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flightIsGrantedAndRevokedCleanly(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        int start = 500_000;
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK3, start, ExoModuleKind.FLIGHT));
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "flight module grants mayfly");

        player.getAbilities().flying = true;
        ticks(player, 20);
        int used = start - energy(player, EquipmentSlot.CHEST);
        int expected = ExoConfig.cost(ExoModuleKind.FLIGHT, 1);
        helper.assertTrue(used >= expected * 0.8 && used <= expected * 1.3, "one second of flight costs about " + expected + " FE, used " + used);
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "still flying while charged");

        ExoActions.toggleFlight(player);
        ticks(player, 1);
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "toggle off revokes flight");
        ExoActions.toggleFlight(player);
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "toggle on grants it again");

        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.CHEST), 0);
        ticks(player, 1);
        helper.assertTrue(!player.getAbilities().mayfly, "an empty suit cannot fly");
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.CHEST), 100_000);
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "recharged: flight is back");

        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ticks(player, 1);
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "unequipping revokes flight");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flightNeverTouchesCreativeFlight(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(player.getAbilities().mayfly, "creative flies");
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK3, 0, ExoModuleKind.FLIGHT));
        ticks(player, 3);
        helper.assertTrue(player.getAbilities().mayfly, "empty suit does not revoke creative flight");
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ticks(player, 3);
        helper.assertTrue(player.getAbilities().mayfly, "unequipping does not revoke creative flight");

        player.setGameMode(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK3, 100_000, ExoModuleKind.FLIGHT));
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "suit grants flight in survival");
        player.setGameMode(GameType.CREATIVE);
        ticks(player, 1);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "creative flight survives the suit coming off");
        helper.succeed();
    }

    private static ItemEntity drop(ServerLevel level, ServerPlayer player, double dx) {
        ItemEntity item = new ItemEntity(level, player.getX() + dx, player.getY(), player.getZ(), new ItemStack(Items.COBBLESTONE));
        item.setNoPickUpDelay();
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        return item;
    }

    @GameTest(template = "empty")
    public static void magnetRadiusGrowsWithLevel(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        int start = 50_000;
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, start, ExoModuleKind.MAGNET));
        ServerLevel level = helper.getLevel();
        ItemEntity near = drop(level, player, 4.0);
        ItemEntity far = drop(level, player, 12.0);
        ticks(player, 4);
        Vec3 toPlayer = player.position().subtract(near.position());
        helper.assertTrue(near.getDeltaMovement().dot(toPlayer) > 0.05, "a nearby item is pulled toward the player, motion " + near.getDeltaMovement());
        helper.assertTrue(far.getDeltaMovement().lengthSqr() < 1.0E-6, "an item out of Magnet I range stays put");
        helper.assertTrue(energy(player, EquipmentSlot.FEET) < start, "pulling costs energy");

        player.setItemSlot(EquipmentSlot.FEET, pieceM(ExoItems.BOOTS_MK3, start, mod(ExoModuleKind.MAGNET, 3)));
        ticks(player, 4);
        helper.assertTrue(ExoConfig.magnetRadius(3) >= 12 && far.getDeltaMovement().lengthSqr() > 1.0E-4, "Magnet III reaches " + ExoConfig.magnetRadius(3) + " blocks");
        near.discard();
        far.discard();
        helper.succeed();
    }

    /** Posts a fall of {@code distance} blocks on the event bus and returns the resulting damage multiplier. */
    private static float fall(ServerPlayer player, float distance) {
        LivingFallEvent event = new LivingFallEvent(player, distance, 1.0F);
        NeoForge.EVENT_BUS.post(event);
        return event.getDamageMultiplier();
    }

    @GameTest(template = "empty")
    public static void fallDampenerCancelsFallDamageWhileCharged(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 100_000, ExoModuleKind.FALL_DAMPENER));
        ticks(player, 1);
        int perBlock = ExoConfig.cost(ExoModuleKind.FALL_DAMPENER, 1);
        helper.assertTrue(fall(player, 10.0F) == 0.0F, "a charged fall dampener removes the fall damage");
        ExoTicker.flush(player);
        int used = 100_000 - energy(player, EquipmentSlot.FEET);
        helper.assertTrue(used == 7 * perBlock, "absorbing 7 damaging blocks costs " + 7 * perBlock + " FE, used " + used);
        helper.assertTrue(fall(player, 2.0F) == 1.0F, "a harmless fall costs nothing and changes nothing");

        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.FEET), 3 * perBlock);
        ticks(player, 1);
        float partial = fall(player, 10.0F);
        helper.assertTrue(partial > 0.4F && partial < 0.7F, "3 of 7 blocks paid leaves about 4/7 of the damage, multiplier " + partial);

        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.FEET), 0);
        ticks(player, 1);
        helper.assertTrue(fall(player, 10.0F) == 1.0F, "empty boots give no protection");

        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 100_000));
        ticks(player, 1);
        helper.assertTrue(fall(player, 10.0F) == 1.0F, "no module, no protection");

        // Power Regulator III (helmet) cuts every cost by 30%.
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 100_000, ExoModuleKind.FALL_DAMPENER));
        player.setItemSlot(EquipmentSlot.HEAD, pieceM(ExoItems.HELMET_MK4, 0, mod(ExoModuleKind.POWER_REGULATOR, 3)));
        ticks(player, 1);
        helper.assertTrue(fall(player, 10.0F) == 0.0F, "still absorbed with the regulator");
        ExoTicker.flush(player);
        int reg = 100_000 - energy(player, EquipmentSlot.FEET);
        int want = (int) Math.round(7 * perBlock * (1.0 - ExoConfig.regulatorSaving(3)));
        helper.assertTrue(Math.abs(reg - want) <= 1, "the regulator saves " + ExoConfig.regulatorSaving(3) + ": " + want + " FE, used " + reg);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSetSharesEnergyAndKineticShieldBlocksHits(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK1, 10_000));
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK3, 0, ExoModuleKind.KINETIC_SHIELD));
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK1, 0));
        helper.assertTrue(!ExoSuit.fullSet(player), "three pieces are not a full set");
        helper.assertTrue(ExoSuit.energyFor(player, EquipmentSlot.CHEST) == 0, "a partial set uses only the module's own piece");
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 50_000));
        helper.assertTrue(ExoSuit.fullSet(player), "four pieces are a full set");
        helper.assertTrue(ExoSuit.energyFor(player, EquipmentSlot.CHEST) == 60_000, "full set pools all batteries");

        ticks(player, 1);
        LivingDamageEvent.Pre hit = shieldHit(player, player.damageSources().generic(), 5.0F);
        float through = (float) (5.0 * (1.0 - ExoConfig.shieldAbsorb(1)));
        helper.assertTrue(Math.abs(hit.getNewDamage() - through) < 1.0E-3F,
                "the shield absorbs 75% of a 5 damage hit, " + through + " should go through, got " + hit.getNewDamage());
        int perPoint = ExoConfig.cost(ExoModuleKind.KINETIC_SHIELD, 1);
        int cost = (int) Math.round(5.0 * ExoConfig.shieldAbsorb(1) * perPoint);
        helper.assertTrue(Math.abs(ExoSuit.totalEnergy(player) - (60_000 - cost)) <= 1, "the hit cost " + cost + " FE from the pool, left " + ExoSuit.totalEnergy(player));
        helper.assertTrue(Math.abs(energy(player, EquipmentSlot.FEET) - (50_000 - cost)) <= 1, "most charged piece pays first");

        for (EquipmentSlot slot : ExoSuit.SLOTS) ItemEnergy.set(player.getItemBySlot(slot), 0);
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.FEET), perPoint);
        ticks(player, 1);
        LivingDamageEvent.Pre weak = shieldHit(player, player.damageSources().generic(), 5.0F);
        helper.assertTrue(Math.abs(weak.getNewDamage() - 4.0F) < 1.0E-3F, "one paid point leaves 4 damage, got " + weak.getNewDamage());

        LivingDamageEvent.Pre voidHit = shieldHit(player, player.damageSources().genericKill(), 5.0F);
        helper.assertTrue(voidHit.getNewDamage() == 5.0F, "damage that bypasses invulnerability is never absorbed");

        // Kinetic Shield III absorbs more of each hit for less FE per point.
        player.setItemSlot(EquipmentSlot.CHEST, pieceM(ExoItems.CHESTPLATE_MK4, 1_000_000, mod(ExoModuleKind.KINETIC_SHIELD, 3)));
        ticks(player, 1);
        LivingDamageEvent.Pre strong = shieldHit(player, player.damageSources().generic(), 10.0F);
        float through3 = (float) (10.0 * (1.0 - ExoConfig.shieldAbsorb(3)));
        helper.assertTrue(Math.abs(strong.getNewDamage() - through3) < 1.0E-3F, "Kinetic Shield III lets " + through3 + " through, got " + strong.getNewDamage());
        helper.assertTrue(ExoConfig.cost(ExoModuleKind.KINETIC_SHIELD, 3) < perPoint, "higher levels pay less per point");

        for (EquipmentSlot slot : ExoSuit.SLOTS) ItemEnergy.set(player.getItemBySlot(slot), 0);
        ticks(player, 1);
        LivingDamageEvent.Pre late = shieldHit(player, player.damageSources().generic(), 5.0F);
        helper.assertTrue(late.getNewDamage() == 5.0F, "an empty shield absorbs nothing");
        helper.succeed();
    }

    /** Posts the damage stage the Kinetic Shield works in (after i-frames and armor) and returns the event. */
    private static LivingDamageEvent.Pre shieldHit(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        LivingDamageEvent.Pre event = new LivingDamageEvent.Pre(player, new DamageContainer(source, amount));
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    /** Real hits through vanilla's hurt(): hits inside the invulnerability frames never reach the shield, so they cost nothing. */
    @GameTest(template = "empty")
    public static void kineticShieldDoesNotPayForInvulnerabilityFrames(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.CHEST, pieceM(ExoItems.CHESTPLATE_MK4, 1_000_000, mod(ExoModuleKind.KINETIC_SHIELD, 3)));
        for (int i = 0; i < 61; i++) player.tick(); // past the spawn protection of a new player
        ticks(player, 1);
        player.invulnerableTime = 0;
        float health = player.getHealth();
        int before = energy(player, EquipmentSlot.CHEST);
        player.hurt(player.damageSources().cactus(), 6.0F);
        int afterFirst = energy(player, EquipmentSlot.CHEST);
        helper.assertTrue(afterFirst < before, "the first hit is absorbed and paid at once, energy " + before + " -> " + afterFirst);
        helper.assertTrue(player.invulnerableTime > 10, "an absorbed hit still starts the invulnerability frames, " + player.invulnerableTime);
        helper.assertTrue(player.getHealth() > health - 6.0F * 0.5F, "most of the hit is absorbed, health " + health + " -> " + player.getHealth());
        for (int i = 0; i < 9; i++) {
            player.tick();
            player.hurt(player.damageSources().cactus(), 6.0F);
        }
        ExoTicker.flush(player);
        helper.assertTrue(energy(player, EquipmentSlot.CHEST) == afterFirst,
                "hits inside the i-frames cost nothing, energy " + afterFirst + " -> " + energy(player, EquipmentSlot.CHEST));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capacitorPlatingGrowsTheBattery(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack chest = new ItemStack(ExoItems.CHESTPLATE_MK1.get());
        int base = ExoItems.CHESTPLATE_MK1.get().baseCapacity();
        helper.assertTrue(ItemEnergy.capacity(chest) == base, "no plating: base battery");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ExoMenu menu = new ExoMenu(3, player.getInventory(), List.of(new ExoMenu.Section(EquipmentSlot.CHEST, 0, 1, false)), true);
        menu.slots.get(0).set(mod(ExoModuleKind.CAPACITOR_PLATING));
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        int plated = (int) Math.round(base * (1.0 + ExoConfig.capacitorBonus(1)));
        helper.assertTrue(ItemEnergy.capacity(worn) == plated, "Capacitor Plating I adds 50%: " + plated + ", got " + ItemEnergy.capacity(worn));
        ItemEnergy.set(worn, plated);
        helper.assertTrue(ItemEnergy.get(worn) == plated, "the bigger battery fills up");
        menu.slots.get(0).remove(1);
        helper.assertTrue(ItemEnergy.capacity(worn) == base && ItemEnergy.get(worn) == base, "taking the plating out caps the energy at the old battery");

        ItemStack mk3 = pieceM(ExoItems.BOOTS_MK3, 0, mod(ExoModuleKind.CAPACITOR_PLATING, 3));
        helper.assertTrue(ItemEnergy.capacity(mk3) == (int) Math.round(ExoItems.BOOTS_MK3.get().baseCapacity() * (1.0 + ExoConfig.capacitorBonus(3))),
                "Capacitor Plating III triples the battery");
        helper.succeed();
    }

    private static void fullMk2Set(ServerPlayer player, ItemStack core) {
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK2, 500_000));
        ItemStack chest = piece(ExoItems.CHESTPLATE_MK2, 2_000_000);
        ExoData.setCore(chest, core);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK2, 500_000));
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK2, 500_000));
    }

    @GameTest(template = "empty")
    public static void coreSocketSetBonuses(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        // Magma Core: fire immunity, burning hits.
        fullMk2Set(player, new ItemStack(CoreItems.MAGMA_CORE.get()));
        helper.assertTrue(ExoSuit.setBonus(player) == ExoData.Core.MAGMA, "four Mk2 pieces and a Magma Core give its bonus");
        ticks(player, 2);
        helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "Magma Core: fire resistance");
        LivingIncomingDamageEvent burn = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().lava(), 4.0F));
        NeoForge.EVENT_BUS.post(burn);
        helper.assertTrue(burn.isCanceled(), "Magma Core: lava does not hurt");
        var target = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        target.moveTo(player.getX() + 1, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(target);
        LivingIncomingDamageEvent hit = new LivingIncomingDamageEvent(target, new DamageContainer(player.damageSources().playerAttack(player), 3.0F));
        NeoForge.EVENT_BUS.post(hit);
        helper.assertTrue(target.getRemainingFireTicks() > 0, "Magma Core: melee hits set the target on fire");
        target.discard();

        // One piece below the needed mark: no bonus, and the effect goes away.
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 100_000));
        helper.assertTrue(ExoSuit.setBonus(player) == ExoData.Core.NONE, "a Mk1 piece breaks the set bonus");
        ticks(player, 1);
        helper.assertTrue(!player.hasEffect(MobEffects.FIRE_RESISTANCE), "no bonus: fire resistance is removed");

        // Antigrav Core: no fall damage, no module needed.
        fullMk2Set(player, new ItemStack(CoreItems.ANTIGRAV_CORE.get()));
        ticks(player, 1);
        helper.assertTrue(fall(player, 30.0F) == 0.0F, "Antigrav Core: no fall damage");

        // Servo Core: Overclock gives Haste and Speed, costs FE and then cools down.
        fullMk2Set(player, new ItemStack(CoreItems.SERVO_CORE.get()));
        ticks(player, 1);
        int before = ExoSuit.totalEnergy(player);
        helper.assertTrue(ExoTicker.overclock(player), "Overclock fires");
        ExoTicker.flush(player);
        MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
        helper.assertTrue(haste != null && haste.getAmplifier() == ExoConfig.overclockHaste() - 1, "Overclock gives Haste II");
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Overclock gives Speed");
        helper.assertTrue(before - ExoSuit.totalEnergy(player) == ExoConfig.overclockCost(), "Overclock costs " + ExoConfig.overclockCost() + " FE");
        helper.assertTrue(!ExoTicker.overclock(player), "Overclock cools down");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void medInjectorHazardSealAndAutoFeeder(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK3, 1_000_000, ExoModuleKind.MED_INJECTOR, ExoModuleKind.HAZARD_SEAL));
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK1, 100_000, ExoModuleKind.AUTO_FEEDER));
        player.setHealth(4.0F);
        ticks(player, 1);
        helper.assertTrue(Math.abs(player.getHealth() - (4.0F + ExoConfig.medHeal(1))) < 1.0E-3F, "the injector heals " + ExoConfig.medHeal(1) + ", health " + player.getHealth());
        player.setHealth(4.0F);
        ticks(player, 1);
        helper.assertTrue(player.getHealth() == 4.0F, "then it cools down");
        helper.assertTrue(1_000_000 - energy(player, EquipmentSlot.CHEST) == ExoConfig.cost(ExoModuleKind.MED_INJECTOR, 1), "one shot cost");

        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200));
        ticks(player, 10);
        helper.assertTrue(!player.hasEffect(MobEffects.POISON), "the Hazard Seal clears poison");
        helper.assertTrue(player.hasEffect(MobEffects.DIG_SLOWDOWN), "but leaves other effects alone");

        player.getFoodData().setFoodLevel(8);
        player.getInventory().add(new ItemStack(Items.BREAD, 3));
        player.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 3));
        ticks(player, 20);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 8 + 5, "the Auto-Feeder eats a bread, food " + player.getFoodData().getFoodLevel());
        helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 2 && player.getInventory().countItem(Items.ROTTEN_FLESH) == 3,
                "bread is eaten, rotten flesh never");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void kineticGeneratorAndDash(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK3, 10_000, ExoModuleKind.KINETIC_GENERATOR, ExoModuleKind.DASH_THRUSTERS));
        ticks(player, 1);
        for (int i = 0; i < 20; i++) {
            player.setPos(player.getX() + 0.5, player.getY(), player.getZ());
            player.setOnGround(true);
            ExoTicker.tick(player);
        }
        ExoTicker.flush(player);
        int made = energy(player, EquipmentSlot.LEGS) - 10_000;
        int expect = 10 * ExoConfig.cost(ExoModuleKind.KINETIC_GENERATOR, 1);
        helper.assertTrue(Math.abs(made - expect) <= 2, "walking 10 blocks makes about " + expect + " FE, made " + made);

        int before = energy(player, EquipmentSlot.LEGS);
        player.setYRot(0);
        helper.assertTrue(ExoTicker.dash(player), "the dash fires");
        helper.assertTrue(player.getDeltaMovement().z > 1.0, "the dash pushes where the player looks, motion " + player.getDeltaMovement());
        helper.assertTrue(!ExoTicker.dash(player), "then it cools down");
        ExoTicker.flush(player);
        helper.assertTrue(before - energy(player, EquipmentSlot.LEGS) == ExoConfig.cost(ExoModuleKind.DASH_THRUSTERS, 1), "a dash costs its FE");
        helper.succeed();
    }

    // ---------------------------------------------------------------- audit fixes

    /** Index in the menu of the slot showing player inventory slot {@code invSlot}. */
    private static int menuSlotOf(ExoMenu menu, ServerPlayer player, int invSlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == invSlot) return i;
        }
        throw new IllegalStateException("no menu slot for inventory slot " + invSlot);
    }

    private static int moduleCount(ItemStack piece) {
        int n = 0;
        for (ItemStack m : ExoData.modules(piece)) n += m.isEmpty() ? 0 : 1;
        return n;
    }

    /** The hand-held module screen never writes into a piece that was swapped into the hand: no module copies. */
    @GameTest(template = "empty")
    public static void handMenuCannotCopyModulesOntoASwappedPiece(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        var inv = player.getInventory();
        inv.selected = 0;
        ItemStack a = piece(ExoItems.CHESTPLATE_MK3, 1_000, ExoModuleKind.FLIGHT, ExoModuleKind.HAZARD_SEAL);
        ItemStack b = piece(ExoItems.CHESTPLATE_MK3, 1_000);
        inv.setItem(0, a);
        inv.setItem(9, b);
        ExoMenu menu = new ExoMenu(4, inv, List.of(new ExoMenu.Section(EquipmentSlot.CHEST, 1, 3, true)), true);

        // Normal use still works: take a module out of the held piece and put it back.
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ExoItems.module(ExoModuleKind.HAZARD_SEAL).get()) && moduleCount(a) == 1, "a module comes out of the held piece");
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && moduleCount(a) == 2, "and goes back in");

        // Hover piece B and press the held slot's number key: refused, nothing moves.
        menu.clicked(menuSlotOf(menu, player, 9), 0, ClickType.SWAP, player);
        helper.assertTrue(inv.getItem(0) == a && inv.getItem(9) == b, "the number key swap of the held slot is refused");
        // Picking up, shift-clicking or throwing the held piece itself: refused.
        int held = menuSlotOf(menu, player, 0);
        menu.clicked(held, 0, ClickType.PICKUP, player);
        menu.clicked(held, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(held, 0, ClickType.THROW, player);
        helper.assertTrue(inv.getItem(0) == a && menu.getCarried().isEmpty(), "the held piece cannot be picked up while its screen is open");

        // The swap happens anyway (any other path): the menu notices, refuses clicks and writes nothing into B.
        inv.setItem(0, b);
        inv.setItem(9, a);
        helper.assertTrue(!menu.stillValid(player), "a swapped piece invalidates the menu");
        helper.assertTrue(!menu.slots.get(0).mayPickup(player) && !menu.slots.get(3).mayPickup(player), "module and core slots lock");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "no module comes out after the swap");
        helper.assertTrue(menu.clickMenuButton(player, 0) == false, "switches do nothing after the swap");
        helper.assertTrue(moduleCount(b) == 0 && moduleCount(a) == 2, "B gets no modules and A keeps its two: no copies");

        // Offhand piece: the offhand key (F) is refused.
        ItemStack c = piece(ExoItems.CHESTPLATE_MK3, 1_000, ExoModuleKind.FLIGHT);
        player.setItemSlot(EquipmentSlot.OFFHAND, c);
        ExoMenu off = new ExoMenu(5, inv, List.of(new ExoMenu.Section(EquipmentSlot.CHEST, 2, 3, true)), true);
        off.clicked(menuSlotOf(off, player, 9), Inventory.SLOT_OFFHAND, ClickType.SWAP, player);
        helper.assertTrue(player.getOffhandItem() == c && off.stillValid(player), "the offhand swap key is refused");
        helper.succeed();
    }

    /** A module in the wrong piece or below its mark does nothing, and the screen marks it. */
    @GameTest(template = "empty")
    public static void misplacedModulesDoNothing(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.LEGS, pieceM(ExoItems.LEGGINGS_MK1, 100_000, mod(ExoModuleKind.SERVO_STRIDE, 3)));
        player.setItemSlot(EquipmentSlot.FEET, pieceM(ExoItems.BOOTS_MK3, 100_000, mod(ExoModuleKind.NIGHT_VISION)));
        helper.assertTrue(!ExoSuit.active(player).has(ExoModuleKind.SERVO_STRIDE), "Stride III in Mk1 leggings does nothing");
        helper.assertTrue(!ExoSuit.active(player).has(ExoModuleKind.NIGHT_VISION), "Night Vision in boots does nothing");
        ticks(player, 2);
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Robotica.id("exo_servo_stride")) == null, "no speed bonus");
        helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION), "no night vision");
        ExoMenu menu = new ExoMenu(6, player.getInventory(), List.of(new ExoMenu.Section(EquipmentSlot.FEET, 0, 3, false)), true);
        helper.assertTrue(menu.isMisplaced(0, 0), "the screen marks it");
        helper.succeed();
    }

    /** One-shot costs are paid from the worn stacks at once; cooldowns and the flight grant survive a relog. */
    @GameTest(template = "empty")
    public static void costsCooldownsAndFlightSurviveSwapsAndRelogs(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK3, 100_000, ExoModuleKind.DASH_THRUSTERS));
        ticks(player, 1);
        helper.assertTrue(ExoTicker.dash(player), "the dash fires");
        helper.assertTrue(energy(player, EquipmentSlot.LEGS) == 100_000 - ExoConfig.cost(ExoModuleKind.DASH_THRUSTERS, 1),
                "the dash is paid at once, before any piece swap could dodge it");
        Item dash = ExoItems.module(ExoModuleKind.DASH_THRUSTERS).get();
        player.getCooldowns().removeCooldown(dash);
        ExoTicker.onLogin(player);
        helper.assertTrue(player.getCooldowns().isOnCooldown(dash), "the dash cooldown comes back after a relog");

        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK3, 500_000, ExoModuleKind.FLIGHT));
        ticks(player, 1);
        player.getAbilities().flying = true;
        helper.assertTrue(player.getPersistentData().getBoolean(ExoTicker.FLIGHT_KEY), "the grant is saved with the player");
        ExoTicker.onLogout(player);
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "logging out does not drop a flying player");
        ExoTicker.onLogin(player);
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "rejoining with a working Flight module keeps flying");

        ExoTicker.onLogout(player);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ExoTicker.onLogin(player);
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "a stale grant without the module is revoked on login");
        helper.assertTrue(!player.getPersistentData().getBoolean(ExoTicker.FLIGHT_KEY), "and forgotten");

        // A grant saved before a crash, no suit at all: the first tick takes it back.
        ExoTicker.onLogout(player);
        player.getAbilities().mayfly = true;
        player.getPersistentData().putBoolean(ExoTicker.FLIGHT_KEY, true);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        ExoTicker.tick(player);
        helper.assertTrue(!player.getAbilities().mayfly, "a stale grant is revoked without a suit");
        helper.succeed();
    }

    /** The Auto-Feeder gives bowls back and never eats suspicious stew. */
    @GameTest(template = "empty")
    public static void autoFeederKeepsContainers(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK1, 100_000, ExoModuleKind.AUTO_FEEDER));
        player.getFoodData().setFoodLevel(4);
        player.getInventory().add(new ItemStack(Items.SUSPICIOUS_STEW));
        player.getInventory().add(new ItemStack(Items.MUSHROOM_STEW));
        ticks(player, 20);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 10, "the stew is eaten, food " + player.getFoodData().getFoodLevel());
        helper.assertTrue(player.getInventory().countItem(Items.BOWL) == 1 && player.getInventory().countItem(Items.MUSHROOM_STEW) == 0,
                "the bowl comes back");
        helper.assertTrue(player.getInventory().countItem(Items.SUSPICIOUS_STEW) == 1, "suspicious stew is never eaten");
        helper.succeed();
    }

    /** An Assembler-only part from the industry module, looked up by id (the smithing addition of Mk3 / Mk4). */
    private static ItemStack part(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(com.arno.robotica.Robotica.id(id)));
    }
}
