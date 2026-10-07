package com.arno.robotica.gear.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.ExoItems;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import com.arno.robotica.gear.entity.RivetEntity;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.AreaShape;
import com.arno.robotica.gear.tool.GearActions;
import com.arno.robotica.gear.lamp.LampRodItem;
import com.arno.robotica.gear.lamp.SparkLampBlock;
import com.arno.robotica.gear.tool.LampPlacer;
import com.arno.robotica.gear.weapon.Lifesteal;
import com.arno.robotica.gear.weapon.RivetGunItem;
import com.arno.robotica.gear.weapon.WeaponModuleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Modules on tools, weapons and armor: bench and framework rules, lamp placer, weapon modules, the rivet. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class GearModuleGameTests {

    private static ItemStack module(ModuleKind kind, int level) {
        return new ItemStack(ModuleItems.get(kind, level).get());
    }

    private static TinkersBenchMenu bench(GameTestHelper helper, ServerPlayer player) {
        BlockPos benchPos = new BlockPos(1, 1, 1);
        helper.setBlock(benchPos, GearBlocks.TINKERS_BENCH.get());
        return new TinkersBenchMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(benchPos)));
    }

    private static net.minecraft.world.inventory.Slot moduleSlot(TinkersBenchMenu menu, int i) {
        return menu.getSlot(TinkersBenchMenu.FIRST_MODULE + i);
    }

    /**
     * Tinker's Bench: power tools, FE weapons and Exo pieces go in, Age 0 tools do not. Modules use the tier's slots
     * (used up, given back on removal); closing returns the item with everything installed.
     */
    @GameTest(template = "empty")
    public static void benchInstallsModules(GameTestHelper helper) {
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        player.getInventory().clearContent();
        TinkersBenchMenu menu = bench(helper, player);
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(GearItems.TINKERS_HAMMER.get())), "the hammer takes no modules");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(GearItems.GEARBLADE.get())), "the Gearblade takes no modules");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(GearItems.SHOCK_BATON.get())), "FE weapons go in");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ExoItems.HELMET_MK1.get())), "Exo pieces go in");
        ItemStack pickup = module(ModuleKind.AUTO_PICKUP, 1);
        helper.assertTrue(!moduleSlot(menu, 0).isActive() && !moduleSlot(menu, 0).mayPlace(pickup), "no tool: the module slots are closed");

        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.fill(drill);
        menu.getSlot(0).set(drill);
        pickup.setCount(3);
        helper.assertTrue(moduleSlot(menu, 0).mayPlace(pickup), "Auto-Pickup is a module now");
        moduleSlot(menu, 0).set(pickup.split(1));
        helper.assertTrue(pickup.getCount() == 2 && Modules.active(menu.tool(), ModuleKind.AUTO_PICKUP) == 1, "one module is used and installed");

        // Shift-click from the player's inventory (first inventory slot of the menu = inventory slot 9).
        player.getInventory().setItem(9, new ItemStack(ModuleItems.get(ModuleKind.LAMP_PLACER, 1).get(), 2));
        menu.quickMoveStack(player, TinkersBenchMenu.MACHINE_SLOTS);
        helper.assertTrue(Modules.level(menu.tool(), ModuleKind.LAMP_PLACER) == 1, "shift-click installs the lamp placer");
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 1, "only one lamp placer is used");

        // The Bore Drill (Age 1) has two module slots: the third is locked.
        ItemStack voidFilter = module(ModuleKind.VOID_FILTER, 1);
        helper.assertTrue(Modules.slots(menu.tool()) == 2 && !moduleSlot(menu, 2).mayPlace(voidFilter)
                && Modules.refusal(menu.tool(), 2, voidFilter, List.of()) != null, "slot 3 is locked at Age 1");

        ItemStack back = moduleSlot(menu, 0).remove(1);
        helper.assertTrue(back.is(ModuleItems.get(ModuleKind.AUTO_PICKUP, 1).get()) && Modules.level(menu.tool(), ModuleKind.AUTO_PICKUP) == 0,
                "removing the module gives it back");
        moduleSlot(menu, 0).set(voidFilter);

        menu.removed(player);
        helper.assertTrue(menu.tool().isEmpty(), "the bench keeps nothing");
        ItemStack returned = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(GearItems.BORE_DRILL.get())) returned = player.getInventory().getItem(i);
        }
        helper.assertTrue(!returned.isEmpty() && Modules.level(returned, ModuleKind.VOID_FILTER) == 1
                && Modules.level(returned, ModuleKind.LAMP_PLACER) == 1 && ItemEnergy.get(returned) == ItemEnergy.capacity(returned),
                "modules and energy survive the bench");

        // An Exo piece at the bench: armor modules by Mk, gear modules refused, Power Regulator fits all three.
        TinkersBenchMenu armorBench = bench(helper, player);
        armorBench.getSlot(0).set(new ItemStack(ExoItems.BOOTS_MK2.get()));
        helper.assertTrue(moduleSlot(armorBench, 0).mayPlace(module(ModuleKind.MAGNET, 2)), "Magnet II fits Mk2 boots");
        helper.assertTrue(!moduleSlot(armorBench, 0).mayPlace(module(ModuleKind.OVERCLOCK, 1)), "a tool module does not fit boots");
        helper.assertTrue(moduleSlot(armorBench, 0).mayPlace(module(ModuleKind.POWER_REGULATOR, 1)), "the Power Regulator fits armor");
        helper.assertTrue(Modules.slots(armorBench.tool()) == 2 && !moduleSlot(armorBench, 2).mayPlace(module(ModuleKind.STEP_ASSIST, 1)),
                "Mk2 has two slots");
        armorBench.removed(player);
        helper.succeed();
    }

    /**
     * One framework, one set of refusals for tools, weapons and armor: not a holder, not a module, wrong item type, tier
     * too low, slot locked, already installed; a forced module does nothing.
     */
    @GameTest(template = "empty")
    public static void frameworkRulesAcrossTargets(GameTestHelper helper) {
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemStack chainsaw = new ItemStack(GearItems.CHAINSAW.get());
        ItemStack nullDrill = new ItemStack(GearItems.NULL_DRILL.get());
        ItemStack baton = new ItemStack(GearItems.SHOCK_BATON.get());
        ItemStack gun = new ItemStack(GearItems.RIVET_GUN.get());
        ItemStack blade = new ItemStack(GearItems.ARC_BLADE.get());
        ItemStack lance = new ItemStack(GearItems.NULL_LANCE.get());
        ItemStack helmet = new ItemStack(ExoItems.HELMET_MK1.get());
        List<ItemStack> none = List.of();
        helper.assertTrue(Modules.slots(drill) == 2 && Modules.slots(gun) == 3 && Modules.slots(blade) == 4 && Modules.slots(lance) == 5,
                "tool and weapon slots follow the Age: 2 / 3 / 4 / 5");
        helper.assertTrue(Modules.slots(helmet) == 1 && Modules.slots(new ItemStack(ExoItems.HELMET_MK4.get())) == 4, "armor slots follow the Mk");
        helper.assertTrue(Modules.slots(new ItemStack(GearItems.TINKERS_HAMMER.get())) == 0, "Age 0 tools have none");
        helper.assertTrue(Modules.refusal(new ItemStack(Items.DIRT), 0, module(ModuleKind.OVERCLOCK, 1), none) != null, "dirt holds no modules");
        helper.assertTrue(Modules.refusal(drill, 0, new ItemStack(Items.DIRT), none) != null, "not a module");
        // tools
        helper.assertTrue(Modules.refusal(drill, 0, module(ModuleKind.OVERCLOCK, 1), none) == null, "Overclock I fits the Bore Drill");
        helper.assertTrue(Modules.refusal(drill, 0, module(ModuleKind.OVERCLOCK, 2), none) != null, "Overclock II needs Age 2");
        helper.assertTrue(Modules.refusal(chainsaw, 0, module(ModuleKind.OVERCLOCK, 1), none) == null, "Overclock fits the Chainsaw");
        helper.assertTrue(Modules.refusal(chainsaw, 0, module(ModuleKind.FORTUNE, 1), none) != null, "Fortune is for drills");
        helper.assertTrue(Modules.refusal(chainsaw, 0, module(ModuleKind.LAMP_PLACER, 1), none) != null, "the lamp placer is for drills only");
        helper.assertTrue(Modules.refusal(drill, 0, module(ModuleKind.ARMOR_PIERCE, 1), none) != null, "weapon module refused by a drill");
        // weapons
        helper.assertTrue(Modules.refusal(baton, 0, module(ModuleKind.SHARPENED_EDGE, 1), none) == null, "Sharpened Edge I fits the baton");
        helper.assertTrue(Modules.refusal(baton, 0, module(ModuleKind.LOOTING, 2), none) != null, "Looting II needs Age 2");
        helper.assertTrue(Modules.refusal(gun, 0, module(ModuleKind.THERMAL_EDGE, 1), none) == null, "Thermal Edge fits the Rivet Gun");
        helper.assertTrue(Modules.refusal(gun, 0, module(ModuleKind.CHAIN_LIGHTNING, 1), none) != null, "chain lightning is Arc Blade only");
        helper.assertTrue(Modules.refusal(blade, 0, module(ModuleKind.CHAIN_LIGHTNING, 3), none) == null, "chain lightning III fits the Arc Blade");
        helper.assertTrue(Modules.refusal(gun, 1, module(ModuleKind.RICOCHET, 2), none) == null, "ricochet fits the Rivet Gun");
        helper.assertTrue(Modules.refusal(blade, 0, module(ModuleKind.RICOCHET, 1), none) != null, "ricochet is Rivet Gun only");
        helper.assertTrue(Modules.refusal(blade, 0, module(ModuleKind.LIFESTEAL, 1), none) != null, "lifesteal needs an Age 4 weapon");
        helper.assertTrue(Modules.refusal(lance, 4, module(ModuleKind.LIFESTEAL, 1), none) == null, "lifesteal fits the Null Lance");
        helper.assertTrue(Modules.refusal(baton, 0, module(ModuleKind.FORTUNE, 1), none) != null, "tool module refused by a weapon");
        // armor
        helper.assertTrue(Modules.refusal(helmet, 0, module(ModuleKind.NIGHT_VISION, 1), none) == null, "Night Vision fits a helmet");
        helper.assertTrue(Modules.refusal(helmet, 0, module(ModuleKind.LOOTING, 1), none) != null, "weapon module refused by armor");
        helper.assertTrue(Modules.refusal(drill, 0, module(ModuleKind.NIGHT_VISION, 1), none) != null, "armor module refused by a drill");
        // Power Regulator: one item for all three
        ItemStack regulator = module(ModuleKind.POWER_REGULATOR, 2);
        helper.assertTrue(Modules.refusal(new ItemStack(GearItems.MAGMA_DRILL.get()), 0, regulator, none) == null
                && Modules.refusal(blade, 0, regulator, none) == null
                && Modules.refusal(new ItemStack(ExoItems.CHESTPLATE_MK3.get()), 0, regulator, none) == null, "Power Regulator II fits tools, weapons and armor");
        // once per item, forced modules do nothing
        Modules.setModule(nullDrill, 0, module(ModuleKind.LAMP_PLACER, 1));
        helper.assertTrue(Modules.refusal(nullDrill, 1, module(ModuleKind.LAMP_PLACER, 1), none) != null, "a kind goes in once");
        Modules.setModule(blade, 0, module(ModuleKind.LIFESTEAL, 1));
        helper.assertTrue(Modules.level(blade, ModuleKind.LIFESTEAL) == 0, "a forced lifesteal on an Age 3 weapon stays inactive");
        Modules.setModule(drill, 0, module(ModuleKind.NIGHT_VISION, 1));
        helper.assertTrue(Modules.level(drill, ModuleKind.NIGHT_VISION) == 0, "a forced armor module in a drill does nothing");
        helper.succeed();
    }

    /** Sharpened Edge raises the damage of a paid hit, Thermal Edge sets the target on fire, Looting counts for drops. */
    @GameTest(template = "empty")
    public static void edgeThermalAndLooting(GameTestHelper helper) {
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        Husk plain = still(helper, new Vec3(0.5, 1.0, 0.5));
        Husk sharp = still(helper, new Vec3(2.5, 1.0, 2.5));
        ItemStack baton = new ItemStack(GearItems.SHOCK_BATON.get());
        ItemEnergy.fill(baton);
        player.setItemInHand(InteractionHand.MAIN_HAND, baton);
        plain.hurt(helper.getLevel().damageSources().playerAttack(player), 4.0F);
        Modules.setModule(player.getMainHandItem(), 0, module(ModuleKind.SHARPENED_EDGE, 1));
        Modules.setModule(player.getMainHandItem(), 1, module(ModuleKind.THERMAL_EDGE, 1));
        sharp.hurt(helper.getLevel().damageSources().playerAttack(player), 4.0F);
        float lostPlain = plain.getMaxHealth() - plain.getHealth();
        float lostSharp = sharp.getMaxHealth() - sharp.getHealth();
        helper.assertTrue(lostSharp > lostPlain + 0.1F, "Sharpened Edge hits harder: " + lostSharp + " vs " + lostPlain);
        helper.assertTrue(sharp.getRemainingFireTicks() > 0 && plain.getRemainingFireTicks() <= 0, "Thermal Edge sets the target on fire");
        var lookup = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var looting = lookup.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING);
        ItemStack lance = new ItemStack(GearItems.NULL_LANCE.get());
        Modules.setModule(lance, 0, module(ModuleKind.LOOTING, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, lance);
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(looting, player) == 0,
                "an empty weapon has no Looting");
        ItemEnergy.fill(player.getMainHandItem());
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(looting, player) == 3,
                "Looting III counts for mob drops while charged");
        helper.assertTrue(lance.getTagEnchantments().isEmpty(), "no real enchantment is written");
        helper.succeed();
    }

    /**
     * The Lamp Placer puts a Spark Lamp in a dark spot, pays FE from the drill, needs build rights and FE, and waits for
     * its cooldown. The lamp lights its spot to 14.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lampPlacerLightsTheDark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spot = helper.absolutePos(new BlockPos(1, 1, 1));
        for (Direction d : Direction.values()) level.setBlock(spot.relative(d), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(spot, Blocks.AIR.defaultBlockState(), 3);
        ServerPlayer player = GearGameTests.survivalPlayer(helper, spot);
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        Modules.setModule(drill, 0, module(ModuleKind.LAMP_PLACER, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        helper.startSequence().thenExecuteAfter(10, () -> {
            ItemStack held = player.getMainHandItem();
            helper.assertTrue(LampPlacer.isDark(level, spot), "a closed pocket is dark");
            BlockPos o = new BlockPos(0, 10, 0);
            helper.assertTrue(LampPlacer.spotFor(o, AreaShape.positions(o, Direction.NORTH, AreaMode.AREA_3, 9, false), 9)
                    .equals(o.below()), "3x3 on a wall: lamp on the bottom row");
            helper.assertTrue(LampPlacer.spotFor(o, AreaShape.positions(o, Direction.NORTH, AreaMode.AREA_5, 9, false), 9)
                    .equals(o.below()), "5x5 on a wall: lamp at the player's feet level, not below the floor");
            helper.assertTrue(LampPlacer.spotFor(o, java.util.List.of(), 9).equals(o), "1x1: lamp at the mined block");
            helper.assertTrue(!LampPlacer.tryPlace(player, level, held, spot), "no FE, no lamp");
            ItemEnergy.fill(held);
            player.getAbilities().mayBuild = false;
            helper.assertTrue(!LampPlacer.tryPlace(player, level, held, spot), "no lamp where the player may not build");
            player.getAbilities().mayBuild = true;
            int before = ItemEnergy.get(held);
            helper.assertTrue(LampPlacer.tryPlace(player, level, held, spot), "a lamp is placed");
            BlockState lamp = level.getBlockState(spot);
            helper.assertTrue(lamp.is(GearBlocks.SPARK_LAMP.get()) && lamp.getValue(SparkLampBlock.FACING) == Direction.UP, "on the floor");
            helper.assertTrue(before - ItemEnergy.get(held) == GearConfig.lampCost(), "paid in FE");
            level.setBlock(spot.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!LampPlacer.tryPlace(player, level, held, spot.above()), "cooldown between lamps");
            Modules.setEnabled(held, ModuleKind.LAMP_PLACER, false);
            helper.assertTrue(Modules.active(held, ModuleKind.LAMP_PLACER) == 0, "switched off in G");
            LampPlacer.forget(player.getUUID());
        }).thenWaitUntil(() -> helper.assertTrue(level.getBrightness(LightLayer.BLOCK, spot) == SparkLampBlock.LIGHT, "the lamp gives light 14"))
                .thenSucceed();
    }

    /**
     * Lamp Rod: places a Spark Lamp on the clicked face for FE, refuses without FE or build rights; the lamp breaks
     * instantly, drops nothing, falls off when its block goes, and a sneak-right-click takes it away.
     */
    @GameTest(template = "empty")
    public static void lampRodPlacesLamps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(1, 0, 1));
        BlockPos wall = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer player = GearGameTests.survivalPlayer(helper, floor.above());
        ItemStack rod = new ItemStack(GearItems.LAMP_ROD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        LampRodItem item = GearItems.LAMP_ROD.get();
        helper.assertTrue(!item.use(player, level, rod, floor, Direction.UP) && level.getBlockState(floor.above()).isAir(), "no FE, no lamp");
        ItemEnergy.fill(rod);
        player.getAbilities().mayBuild = false;
        helper.assertTrue(!item.use(player, level, rod, floor, Direction.UP) && level.getBlockState(floor.above()).isAir(),
                "no lamp where the player may not build");
        player.getAbilities().mayBuild = true;
        int before = ItemEnergy.get(rod);
        helper.assertTrue(item.use(player, level, rod, floor, Direction.UP), "a lamp is placed");
        BlockState lamp = level.getBlockState(floor.above());
        helper.assertTrue(lamp.is(GearBlocks.SPARK_LAMP.get()) && lamp.getValue(SparkLampBlock.FACING) == Direction.UP, "on the floor");
        helper.assertTrue(before - ItemEnergy.get(rod) == GearConfig.rodCost(), "paid in FE");
        helper.assertTrue(lamp.getLightEmission(level, floor.above()) == SparkLampBlock.LIGHT, "torch light");
        helper.assertTrue(lamp.getDestroySpeed(level, floor.above()) == 0.0F, "breaks instantly");
        helper.assertTrue(Block.getDrops(lamp, level, floor.above(), null).isEmpty(), "drops nothing");
        helper.assertTrue(lamp.getCollisionShape(level, floor.above()).isEmpty(), "no collision");

        player.getCooldowns().removeCooldown(item);
        helper.assertTrue(item.use(player, level, rod, wall, Direction.WEST), "a lamp goes on a wall");
        BlockPos wallLamp = wall.west();
        helper.assertTrue(level.getBlockState(wallLamp).is(GearBlocks.SPARK_LAMP.get())
                && level.getBlockState(wallLamp).getValue(SparkLampBlock.FACING) == Direction.WEST, "facing away from the wall");
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(level.getBlockState(wallLamp).isAir(), "falls off when its block goes");

        player.setShiftKeyDown(true);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor.above()), Direction.UP, floor.above(), false);
        item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(level.getBlockState(floor.above()).isAir(), "sneak-right-click removes the lamp");
        helper.succeed();
    }

    /** Armor Pierce: a paid hit with the module ignores part of the armor, so an armored mob loses more health. */
    @GameTest(template = "empty")
    public static void armorPierceIgnoresArmor(GameTestHelper helper) {
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        Husk plain = armored(helper, new BlockPos(0, 1, 0));
        Husk pierced = armored(helper, new BlockPos(2, 1, 2));
        ItemStack baton = new ItemStack(GearItems.SHOCK_BATON.get());
        ItemEnergy.fill(baton);
        player.setItemInHand(InteractionHand.MAIN_HAND, baton);
        plain.hurt(helper.getLevel().damageSources().playerAttack(player), 10.0F);
        Modules.setModule(player.getMainHandItem(), 0, module(ModuleKind.ARMOR_PIERCE, 1));
        var source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(Math.abs(WeaponModuleEvents.pierceShare(source) - GearConfig.pierceShare(1)) < 1.0E-4, "Pierce I share applies");
        pierced.hurt(source, 10.0F);
        float lostPlain = plain.getMaxHealth() - plain.getHealth();
        float lostPierced = pierced.getMaxHealth() - pierced.getHealth();
        helper.assertTrue(lostPierced > lostPlain + 0.1F, "pierce should deal more through armor: " + lostPierced + " vs " + lostPlain);
        // Unpaid hits (an empty weapon) get no pierce.
        ItemEnergy.set(player.getMainHandItem(), 0);
        helper.assertTrue(WeaponModuleEvents.pierceShare(source) == 0.0F, "an empty weapon does not pierce");
        helper.succeed();
    }

    private static Husk armored(GameTestHelper helper, BlockPos rel) {
        Husk husk = helper.spawn(EntityType.HUSK, rel);
        // Worn armor only counts after the mob's next tick; set the attribute directly for an immediate hit.
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20.0);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        husk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        husk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        husk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        return husk;
    }

    private static Husk still(GameTestHelper helper, Vec3 rel) {
        Husk husk = helper.spawn(EntityType.HUSK, rel);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setDeltaMovement(Vec3.ZERO);
        return husk;
    }

    /** Ricochet II: one rivet hits the first monster and bounces to two more, never a fourth. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ricochetHitsTwoMoreTargets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        // Everything stays inside the 3x3x3 test area. From A the nearest is B, from B it is C; D is farther every time.
        Husk a = still(helper, new Vec3(1.2, 1.0, 0.5));
        Husk b = still(helper, new Vec3(2.7, 1.0, 0.5));
        Husk c = still(helper, new Vec3(2.7, 1.0, 2.5));
        Husk d = still(helper, new Vec3(0.5, 1.0, 2.6));
        RivetEntity rivet = new RivetEntity(level, player, GearConfig.rivetDamage(), 0.0F, GearConfig.ricochetBounces(2));
        Vec3 start = helper.absoluteVec(new Vec3(0.02, 1.9, 0.5));
        rivet.setPos(start.x, start.y, start.z);
        rivet.shoot(1.0, 0.0, 0.0, GearConfig.rivetSpeed(), 0.0F);
        var first = rivet.ricochetTarget(level, a);
        helper.assertTrue(first == b, "from A the nearest monster in sight is B");
        level.addFreshEntity(rivet);
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(a) && hurt(b) && hurt(c), "the rivet should hit three monsters: " + hurt(a) + " " + hurt(b) + " " + hurt(c));
            helper.assertTrue(rivet.isRemoved(), "the rivet is gone after its last bounce");
            helper.assertTrue(!hurt(d), "two bounces only: the fourth monster is untouched");
            helper.assertTrue(rivet.bouncesDone() == 2, "two bounces, got " + rivet.bouncesDone());
        });
    }

    private static boolean hurt(Mob mob) {
        return mob.getHealth() < mob.getMaxHealth();
    }

    /** Firing the Rivet Gun spawns a Rivet entity (no arrow, no pickup) that deals the configured damage and costs its FE. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void rivetGunFiresRivetEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        Vec3 feet = helper.absoluteVec(new Vec3(0.2, 0.0, 1.5));
        player.moveTo(feet.x, feet.y, feet.z, -90.0F, 0.0F);
        var creeper = helper.spawn(EntityType.CREEPER, new Vec3(2.5, 0.0, 1.5));
        creeper.setNoAi(true);
        creeper.setNoGravity(true);
        ItemStack gun = new ItemStack(GearItems.RIVET_GUN.get());
        ItemEnergy.fill(gun);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        int before = ItemEnergy.get(player.getMainHandItem());
        player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
        List<RivetEntity> rivets = level.getEntitiesOfClass(RivetEntity.class, new AABB(feet, feet).inflate(8));
        helper.assertTrue(rivets.size() == 1, "one rivet entity should fly, found " + rivets.size());
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, new AABB(feet, feet).inflate(8)).isEmpty(),
                "no vanilla arrows any more");
        int cost = ((RivetGunItem) gun.getItem()).cost(player.getMainHandItem());
        helper.assertTrue(before - ItemEnergy.get(player.getMainHandItem()) == cost, "one shot costs " + cost + " FE");
        float expected = creeper.getMaxHealth() - GearConfig.rivetDamage();
        helper.succeedWhen(() -> {
            helper.assertTrue(creeper.getHealth() < creeper.getMaxHealth(), "the rivet should hit the creeper");
            helper.assertTrue(Math.abs(creeper.getHealth() - expected) < 0.01F, "rivet damage " + GearConfig.rivetDamage()
                    + ", creeper at " + creeper.getHealth());
            helper.assertTrue(rivets.get(0).isRemoved(), "the rivet is gone after the hit");
        });
    }

    /**
     * Lifesteal (Age 4): heals a share of the damage, pays FE per point, never more than the cap per second, then a
     * cooldown with no healing; no healing at full health or with an empty weapon.
     */
    @GameTest(template = "empty")
    public static void lifestealCapCooldownAndCost(GameTestHelper helper) {
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        Husk target = still(helper, new Vec3(1.5, 1.0, 1.5));
        ItemStack lance = new ItemStack(GearItems.NULL_LANCE.get());
        ItemEnergy.fill(lance);
        float share = GearConfig.lifestealShare();
        float cap = GearConfig.lifestealMaxPerSecond();
        int costPer = GearConfig.lifestealCost();
        helper.assertTrue(Lifesteal.onHit(player, lance, target, 10.0F) == 0.0F, "no module, no healing");
        Modules.setModule(lance, 0, module(ModuleKind.LIFESTEAL, 1));
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(Lifesteal.onHit(player, lance, target, 10.0F) == 0.0F, "no healing at full health");

        player.setHealth(4.0F);
        int before = ItemEnergy.get(lance);
        float small = Lifesteal.onHit(player, lance, target, 10.0F);
        helper.assertTrue(Math.abs(small - Math.min(cap, 10.0F * share)) < 1.0E-3, "heals the share of the damage, got " + small);
        helper.assertTrue(before - ItemEnergy.get(lance) == (int) Math.ceil(small * costPer), "paid per point healed");
        float big = Lifesteal.onHit(player, lance, target, 1000.0F);
        helper.assertTrue(Math.abs(small + big - cap) < 1.0E-3, "a second's healing is capped at " + cap + ", got " + (small + big));
        helper.assertTrue(player.getCooldowns().isOnCooldown(Lifesteal.cooldownItem()), "reaching the cap starts the cooldown");
        helper.assertTrue(Lifesteal.onHit(player, lance, target, 1000.0F) == 0.0F, "no healing during the cooldown");

        player.getCooldowns().removeCooldown(Lifesteal.cooldownItem());
        Lifesteal.forget(player.getUUID());
        ItemEnergy.set(lance, 0);
        helper.assertTrue(Lifesteal.onHit(player, lance, target, 1000.0F) == 0.0F, "an empty weapon heals nothing");
        ItemEnergy.set(lance, costPer / 2);
        float partial = Lifesteal.onHit(player, lance, target, 1000.0F);
        helper.assertTrue(partial > 0.0F && partial <= 0.5F + 1.0E-3, "healing is limited by the FE in the weapon, got " + partial);
        Modules.setEnabled(lance, ModuleKind.LIFESTEAL, false);
        helper.assertTrue(Modules.active(lance, ModuleKind.LIFESTEAL) == 0, "switched off in G");
        Lifesteal.forget(player.getUUID());
        helper.succeed();
    }

    /**
     * The Lifesteal budget: hits just under the cap every second (or every tick) heal at most the cap in any rolling
     * second, across window edges too, and at most cap per refill time over a long fight.
     */
    @GameTest(template = "empty")
    public static void lifestealBudgetLimitsLongRunHealing(GameTestHelper helper) {
        float cap = 3.0F;
        int refill = 100;
        for (int gap : new int[] {1, 7, 19, 20, 21}) {
            Lifesteal.Budget budget = new Lifesteal.Budget();
            float[] perTick = new float[4000];
            float total = 0.0F;
            for (int t = 0; t < perTick.length; t += gap) {
                float amount = Math.min(cap - 0.1F, budget.room(1000 + t, cap, refill));
                if (amount < 0.01F) continue;
                budget.spend(1000 + t, amount);
                perTick[t] = amount;
                total += amount;
            }
            float window = 0.0F;
            for (int t = 0; t < perTick.length; t++) {
                window += perTick[t] - (t >= 20 ? perTick[t - 20] : 0.0F);
                helper.assertTrue(window <= cap + 1.0E-3, "gap " + gap + ": " + window + " healed in one second at tick " + t);
            }
            float allowed = cap + cap * perTick.length / refill;
            helper.assertTrue(total <= allowed + 1.0E-2, "gap " + gap + ": healed " + total + " over 200 s, allowed " + allowed);
        }
        helper.succeed();
    }

    /** G toggles an installed module of the item in hand; a forged toggle for a missing module changes nothing. */
    @GameTest(template = "empty")
    public static void gScreenTogglesModules(GameTestHelper helper) {
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        ItemStack gun = new ItemStack(GearItems.RIVET_GUN.get());
        Modules.setModule(gun, 0, module(ModuleKind.RICOCHET, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        int cost = ((RivetGunItem) gun.getItem()).cost(player.getMainHandItem());
        helper.assertTrue(cost == ((RivetGunItem) gun.getItem()).cost() + GearConfig.ricochetCost(1), "ricochet adds to the shot cost");
        GearActions.apply(player, GearActions.MODULE_TOGGLE, ModuleKind.RICOCHET.ordinal());
        helper.assertTrue(!Modules.enabled(player.getMainHandItem(), ModuleKind.RICOCHET), "toggled off");
        helper.assertTrue(((RivetGunItem) gun.getItem()).cost(player.getMainHandItem()) == ((RivetGunItem) gun.getItem()).cost(), "off costs nothing extra");
        GearActions.apply(player, GearActions.MODULE_TOGGLE, ModuleKind.LIFESTEAL.ordinal());
        GearActions.apply(player, GearActions.MODULE_TOGGLE, 99);
        helper.assertTrue(Modules.enabled(player.getMainHandItem(), ModuleKind.LIFESTEAL), "a missing module is not toggled");
        helper.succeed();
    }
}
