package com.arno.robotica.exo.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.exo.ExoActions;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoItems;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.ExoTicker;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

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

    /** An Exo piece with the given modules installed and the given energy. */
    private static ItemStack piece(DeferredItem<ExoArmorItem> item, int energy, ExoModuleKind... kinds) {
        ItemStack stack = new ItemStack(item.get());
        ItemEnergy.set(stack, energy);
        ExoData.setModules(stack, java.util.Arrays.stream(kinds).map(k -> new ItemStack(ExoItems.module(k).get())).toList());
        return stack;
    }

    private static void ticks(ServerPlayer player, int n) {
        for (int i = 0; i < n; i++) ExoTicker.tick(player);
        ExoTicker.flush(player);
    }

    @GameTest(template = "empty")
    public static void moduleInstallAndRemoveKeepEnergy(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack chest = piece(ExoItems.CHESTPLATE_MK1, 123_456);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ExoMenu menu = new ExoMenu(1, player.getInventory(), List.of(new ExoMenu.Section(EquipmentSlot.CHEST, 0, 1)), true);

        ItemStack helmetModule = new ItemStack(ExoItems.module(ExoModuleKind.NIGHT_VISION).get());
        helper.assertTrue(!menu.slots.get(0).mayPlace(helmetModule), "a helmet module must not fit the chestplate");
        ItemStack flight = new ItemStack(ExoItems.module(ExoModuleKind.FLIGHT).get());
        helper.assertTrue(menu.slots.get(0).mayPlace(flight), "a chest module fits the chestplate");

        menu.slots.get(0).set(flight);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        helper.assertTrue(ExoData.kind(worn, 0) == ExoModuleKind.FLIGHT, "module should be installed in the worn piece");
        helper.assertTrue(ItemEnergy.get(worn) == 123_456, "installing a module must keep the energy, got " + ItemEnergy.get(worn));
        helper.assertTrue(ExoData.isEnabled(worn, 0), "a new module starts switched on");

        helper.assertTrue(menu.clickMenuButton(player, 0), "toggle click should work");
        helper.assertTrue(!ExoData.isEnabled(worn, 0), "toggle should switch the module off");
        helper.assertTrue(ExoData.activeMask(worn) == 0, "a switched off module is not active");

        ItemStack taken = menu.slots.get(0).remove(1);
        helper.assertTrue(taken.getItem() == ExoItems.module(ExoModuleKind.FLIGHT).get(), "the module comes back out");
        helper.assertTrue(ExoData.kind(worn, 0) == null, "slot should be empty again");
        helper.assertTrue(ExoData.isEnabled(worn, 0), "removing clears the off bit");
        helper.assertTrue(ItemEnergy.get(worn) == 123_456, "removing a module must keep the energy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void abilitiesConsumeEnergyAndStopWhenEmpty(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK1, 100_000, ExoModuleKind.NIGHT_VISION));
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK2, 100_000, ExoModuleKind.SERVO_STRIDE_2, ExoModuleKind.STEP_ASSIST));

        ticks(player, 40);
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "night vision should be applied while charged");
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Robotica.id("exo_servo_stride"));
        helper.assertTrue(speed != null && Math.abs(speed.amount() - 0.4) < 1.0E-9, "servo stride II gives +40% speed");
        var step = player.getAttribute(Attributes.STEP_HEIGHT).getModifier(Robotica.id("exo_step_assist"));
        helper.assertTrue(step != null, "step assist raises the step height");
        int nvUsed = 100_000 - ItemEnergy.get(player.getItemBySlot(EquipmentSlot.HEAD));
        helper.assertTrue(nvUsed > 0 && nvUsed <= ExoConfig.cost(ExoModuleKind.NIGHT_VISION) * 3, "night vision costs a little FE, used " + nvUsed);

        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.HEAD), 0);
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.LEGS), 0);
        ticks(player, 2);
        helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION), "empty helmet: night vision is removed");
        helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Robotica.id("exo_servo_stride")) == null, "empty leggings: no speed bonus");
        helper.assertTrue(player.getAttribute(Attributes.STEP_HEIGHT).getModifier(Robotica.id("exo_step_assist")) == null, "empty leggings: no step bonus");
        helper.assertTrue(ItemEnergy.get(player.getItemBySlot(EquipmentSlot.HEAD)) == 0, "energy never goes below zero");
        // The armor itself is untouched: still there, never damaged.
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getMaxDamage() == 0, "exo armor has no durability");

        // Taking the suit off removes everything.
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        ExoTicker.tick(player);
        helper.assertTrue(!ExoTicker.hasState(player), "state is dropped when the suit comes off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flightIsGrantedAndRevokedCleanly(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        int start = 500_000;
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK2, start, ExoModuleKind.FLIGHT));
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "flight module grants mayfly");

        player.getAbilities().flying = true;
        ticks(player, 20);
        int used = start - ItemEnergy.get(player.getItemBySlot(EquipmentSlot.CHEST));
        int expected = ExoConfig.cost(ExoModuleKind.FLIGHT);
        helper.assertTrue(used >= expected * 0.8 && used <= expected * 1.3, "one second of flight costs about " + expected + " FE, used " + used);
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "still flying while charged");

        // Switched off: revoked.
        ExoActions.toggleFlight(player);
        ticks(player, 1);
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "toggle off revokes flight");
        ExoActions.toggleFlight(player);
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "toggle on grants it again");

        // Empty: revoked.
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.CHEST), 0);
        ticks(player, 1);
        helper.assertTrue(!player.getAbilities().mayfly, "an empty suit cannot fly");
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.CHEST), 100_000);
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "recharged: flight is back");

        // Unequipped: revoked.
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
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK1, 0, ExoModuleKind.FLIGHT));
        ticks(player, 3);
        helper.assertTrue(player.getAbilities().mayfly, "empty suit does not revoke creative flight");
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ticks(player, 3);
        helper.assertTrue(player.getAbilities().mayfly, "unequipping does not revoke creative flight");

        // Survival to creative while the suit grants flight, then back.
        player.setGameMode(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK1, 100_000, ExoModuleKind.FLIGHT));
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "suit grants flight in survival");
        player.setGameMode(GameType.CREATIVE);
        ticks(player, 1);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ticks(player, 1);
        helper.assertTrue(player.getAbilities().mayfly, "creative flight survives the suit coming off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void magnetPullsItems(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        int start = 50_000;
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, start, ExoModuleKind.MAGNET));
        ServerLevel level = helper.getLevel();
        ItemEntity item = new ItemEntity(level, player.getX() + 4.0, player.getY(), player.getZ(), new ItemStack(Items.COBBLESTONE));
        item.setNoPickUpDelay();
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        ItemEntity far = new ItemEntity(level, player.getX() + 12.0, player.getY(), player.getZ(), new ItemStack(Items.COBBLESTONE));
        far.setNoPickUpDelay();
        far.setNoGravity(true);
        far.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(far);

        ticks(player, 4);
        Vec3 toPlayer = player.position().subtract(item.position());
        helper.assertTrue(item.getDeltaMovement().dot(toPlayer) > 0.05, "a nearby item is pulled toward the player, motion " + item.getDeltaMovement());
        helper.assertTrue(far.getDeltaMovement().lengthSqr() < 1.0E-6, "an item out of range stays put");
        helper.assertTrue(ItemEnergy.get(player.getItemBySlot(EquipmentSlot.FEET)) < start, "pulling costs energy");
        item.discard();
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
        helper.assertTrue(fall(player, 10.0F) == 0.0F, "a charged fall dampener removes the fall damage");
        ExoTicker.flush(player);
        int used = 100_000 - ItemEnergy.get(player.getItemBySlot(EquipmentSlot.FEET));
        helper.assertTrue(used == 7 * ExoConfig.cost(ExoModuleKind.FALL_DAMPENER), "absorbing 7 damaging blocks costs " + 7 * ExoConfig.cost(ExoModuleKind.FALL_DAMPENER) + " FE, used " + used);
        helper.assertTrue(fall(player, 2.0F) == 1.0F, "a harmless fall costs nothing and changes nothing");

        // Almost empty: only part of the fall is absorbed.
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.FEET), 3 * ExoConfig.cost(ExoModuleKind.FALL_DAMPENER));
        ticks(player, 1);
        float partial = fall(player, 10.0F);
        helper.assertTrue(partial > 0.4F && partial < 0.7F, "3 of 7 blocks paid leaves about 4/7 of the damage, multiplier " + partial);

        // Empty: no protection.
        ItemEnergy.set(player.getItemBySlot(EquipmentSlot.FEET), 0);
        ticks(player, 1);
        helper.assertTrue(fall(player, 10.0F) == 1.0F, "empty boots give no protection");

        // Charged but without the module: no protection either.
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 100_000));
        ticks(player, 1);
        helper.assertTrue(fall(player, 10.0F) == 1.0F, "no module, no protection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSetSharesEnergyAndKineticShieldBlocksHits(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.HEAD, piece(ExoItems.HELMET_MK1, 10_000));
        player.setItemSlot(EquipmentSlot.CHEST, piece(ExoItems.CHESTPLATE_MK1, 0, ExoModuleKind.KINETIC_SHIELD));
        player.setItemSlot(EquipmentSlot.LEGS, piece(ExoItems.LEGGINGS_MK1, 0));
        helper.assertTrue(!ExoSuit.fullSet(player), "three pieces are not a full set");
        helper.assertTrue(ExoSuit.energyFor(player, EquipmentSlot.CHEST) == 0, "a partial set uses only the module's own piece");
        player.setItemSlot(EquipmentSlot.FEET, piece(ExoItems.BOOTS_MK1, 50_000));
        helper.assertTrue(ExoSuit.fullSet(player), "four pieces are a full set");
        helper.assertTrue(ExoSuit.energyFor(player, EquipmentSlot.CHEST) == 60_000, "full set pools all batteries");

        ticks(player, 1);
        LivingIncomingDamageEvent hit = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().generic(), 5.0F));
        NeoForge.EVENT_BUS.post(hit);
        helper.assertTrue(hit.isCanceled(), "the shield absorbs a 5 damage hit completely");
        ExoTicker.flush(player);
        int perPoint = ExoConfig.cost(ExoModuleKind.KINETIC_SHIELD);
        helper.assertTrue(ExoSuit.totalEnergy(player) == 60_000 - 5 * perPoint, "the hit cost " + 5 * perPoint + " FE from the pool, left " + ExoSuit.totalEnergy(player));
        // Drained from the most charged piece first (boots).
        helper.assertTrue(ItemEnergy.get(player.getItemBySlot(EquipmentSlot.FEET)) == 50_000 - 5 * perPoint, "most charged piece pays first");

        // Void damage bypasses the shield.
        LivingIncomingDamageEvent voidHit = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().genericKill(), 5.0F));
        NeoForge.EVENT_BUS.post(voidHit);
        helper.assertTrue(!voidHit.isCanceled(), "damage that bypasses invulnerability is never absorbed");

        // Empty suit: the hit goes through.
        for (EquipmentSlot slot : ExoSuit.SLOTS) ItemEnergy.set(player.getItemBySlot(slot), 0);
        ticks(player, 1);
        LivingIncomingDamageEvent late = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().generic(), 5.0F));
        NeoForge.EVENT_BUS.post(late);
        helper.assertTrue(!late.isCanceled() && late.getAmount() == 5.0F, "an empty shield absorbs nothing");
        helper.succeed();
    }
}
