package com.arno.robotica.gear.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.gear.GearItems;
import com.arno.robotica.gear.bench.TinkersBenchMenu;
import com.arno.robotica.gear.entity.RivetEntity;
import com.arno.robotica.gear.module.GearModuleKind;
import com.arno.robotica.gear.module.GearModules;
import com.arno.robotica.gear.tool.GearActions;
import com.arno.robotica.gear.tool.ToggleKind;
import com.arno.robotica.gear.tool.ToolSettings;
import com.arno.robotica.gear.tool.TorchPlacer;
import com.arno.robotica.gear.weapon.Lifesteal;
import com.arno.robotica.gear.weapon.RivetGunItem;
import com.arno.robotica.gear.weapon.WeaponModuleEvents;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Tool and weapon modules: bench rules, card migration, torch placer, armor pierce, ricochet, lifesteal, the rivet. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class GearModuleGameTests {

    private static ItemStack module(GearModuleKind kind, int level) {
        return new ItemStack(GearItems.module(kind, level).get());
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
     * Tinker's Bench: power tools and FE weapons go in, Age 0 tools do not. Cards go in their own slots (used up, given
     * back on removal); modules use the Age's module slots; closing returns the item with everything installed.
     */
    @GameTest(template = "empty")
    public static void benchInstallsCardsAndModules(GameTestHelper helper) {
        ServerPlayer player = GearGameTests.survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        player.getInventory().clearContent();
        TinkersBenchMenu menu = bench(helper, player);
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(GearItems.TINKERS_HAMMER.get())), "the hammer takes no modules");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(GearItems.GEARBLADE.get())), "the Gearblade takes no modules");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(GearItems.SHOCK_BATON.get())), "FE weapons go in");
        ItemStack pickup = CoreItems.cards(UpgradeKind.PICKUP, 3);
        helper.assertTrue(!menu.getSlot(1).isActive() && !menu.getSlot(1).mayPlace(pickup), "no tool: the card slots are closed");

        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.fill(drill);
        menu.getSlot(0).set(drill);
        helper.assertTrue(menu.getSlot(1).mayPlace(pickup) && !menu.getSlot(2).mayPlace(pickup), "the pickup card goes in its own slot");
        helper.assertTrue(!moduleSlot(menu, 0).mayPlace(pickup), "cards do not go in module slots");
        menu.getSlot(1).set(pickup.split(1));
        helper.assertTrue(pickup.getCount() == 2 && ToolSettings.installed(menu.tool(), ToggleKind.AUTO_PICKUP), "one card is used and installed");

        // Shift-click from the player's inventory (first inventory slot of the menu = inventory slot 9).
        player.getInventory().setItem(9, CoreItems.cards(UpgradeKind.VOID, 2));
        menu.quickMoveStack(player, TinkersBenchMenu.MACHINE_SLOTS);
        helper.assertTrue(ToolSettings.installed(menu.tool(), ToggleKind.VOID_FILTER), "shift-click installs the void card");
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 1, "only one void card is used");

        // The Bore Drill (Age 1) has one module slot: the Torch Placer goes in, the second slot is locked.
        ItemStack torch = module(GearModuleKind.TORCH_PLACER, 1);
        helper.assertTrue(moduleSlot(menu, 0).mayPlace(torch), "torch placer fits a drill");
        moduleSlot(menu, 0).set(torch.copy());
        helper.assertTrue(GearModules.level(menu.tool(), GearModuleKind.TORCH_PLACER) == 1, "torch placer installed");
        helper.assertTrue(GearModules.refusal(menu.tool(), 1, torch) != null && !moduleSlot(menu, 1).mayPlace(torch), "slot 2 is locked at Age 1");

        ItemStack back = menu.getSlot(1).remove(1);
        helper.assertTrue(back.is(CoreItems.card(UpgradeKind.PICKUP).get()) && !ToolSettings.installed(menu.tool(), ToggleKind.AUTO_PICKUP),
                "removing the card gives it back");

        menu.removed(player);
        helper.assertTrue(menu.tool().isEmpty(), "the bench keeps nothing");
        ItemStack returned = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(GearItems.BORE_DRILL.get())) returned = player.getInventory().getItem(i);
        }
        helper.assertTrue(!returned.isEmpty() && ToolSettings.installed(returned, ToggleKind.VOID_FILTER)
                && GearModules.level(returned, GearModuleKind.TORCH_PLACER) == 1 && ItemEnergy.get(returned) == ItemEnergy.capacity(returned),
                "modules, cards and energy survive the bench");
        helper.succeed();
    }

    /** Every refusal has a reason: wrong item type, Age too low, slot locked, already installed, cards on weapons. */
    @GameTest(template = "empty")
    public static void benchRefusesWithReasons(GameTestHelper helper) {
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemStack nullDrill = new ItemStack(GearItems.NULL_DRILL.get());
        ItemStack baton = new ItemStack(GearItems.SHOCK_BATON.get());
        ItemStack gun = new ItemStack(GearItems.RIVET_GUN.get());
        ItemStack blade = new ItemStack(GearItems.ARC_BLADE.get());
        ItemStack lance = new ItemStack(GearItems.NULL_LANCE.get());
        helper.assertTrue(GearModules.slots(drill) == 1 && GearModules.slots(gun) == 2 && GearModules.slots(blade) == 3 && GearModules.slots(lance) == 4,
                "module slots follow the Age: 1 / 2 / 3 / 4");
        helper.assertTrue(GearModules.slots(new ItemStack(GearItems.TINKERS_HAMMER.get())) == 0, "Age 0 tools have none");
        helper.assertTrue(GearModules.refusal(drill, 0, module(GearModuleKind.ARMOR_PIERCE, 1)) != null, "weapon module refused by a drill");
        helper.assertTrue(GearModules.refusal(new ItemStack(GearItems.CHAINSAW.get()), 0, module(GearModuleKind.TORCH_PLACER, 1)) != null,
                "the torch placer is for drills only");
        helper.assertTrue(GearModules.refusal(baton, 0, module(GearModuleKind.ARMOR_PIERCE, 1)) == null, "Pierce I fits the baton");
        helper.assertTrue(GearModules.refusal(baton, 0, module(GearModuleKind.ARMOR_PIERCE, 2)) != null, "Pierce II needs Age 2");
        helper.assertTrue(GearModules.refusal(gun, 0, module(GearModuleKind.CHAIN_LIGHTNING, 1)) != null, "chain lightning is Arc Blade only");
        helper.assertTrue(GearModules.refusal(blade, 0, module(GearModuleKind.CHAIN_LIGHTNING, 3)) == null, "chain lightning III fits the Arc Blade");
        helper.assertTrue(GearModules.refusal(gun, 1, module(GearModuleKind.RICOCHET, 2)) == null, "ricochet fits the Rivet Gun");
        helper.assertTrue(GearModules.refusal(blade, 0, module(GearModuleKind.RICOCHET, 1)) != null, "ricochet is Rivet Gun only");
        helper.assertTrue(GearModules.refusal(blade, 0, module(GearModuleKind.LIFESTEAL, 1)) != null, "lifesteal needs an Age 4 weapon");
        helper.assertTrue(GearModules.refusal(lance, 3, module(GearModuleKind.LIFESTEAL, 1)) == null, "lifesteal fits the Null Lance");
        helper.assertTrue(GearModules.refusal(drill, 0, new ItemStack(Items.DIRT)) != null, "not a module");
        GearModules.setModule(nullDrill, 0, module(GearModuleKind.TORCH_PLACER, 1));
        helper.assertTrue(GearModules.refusal(nullDrill, 1, module(GearModuleKind.TORCH_PLACER, 1)) != null, "a kind goes in once");
        helper.assertTrue(GearModules.cardRefusal(lance, ToggleKind.AUTO_PICKUP, CoreItems.cards(UpgradeKind.PICKUP, 1)) != null, "weapons take no cards");
        // A module forced into a weapon that cannot take it does nothing.
        GearModules.setModule(blade, 0, module(GearModuleKind.LIFESTEAL, 1));
        helper.assertTrue(GearModules.level(blade, GearModuleKind.LIFESTEAL) == 0, "a forced lifesteal on an Age 3 weapon stays inactive");
        helper.succeed();
    }

    /** Tools from 0.3 kept their cards as bits; they keep working and move into the new layout. */
    @GameTest(template = "empty")
    public static void legacyCardsMigrate(GameTestHelper helper) {
        ItemStack drill = new ItemStack(GearItems.SERVO_DRILL.get());
        drill.set(GearComponents.MODULES.get(), ToggleKind.AUTO_PICKUP.bit | ToggleKind.VOID_FILTER.bit);
        helper.assertTrue(ToolSettings.installed(drill, ToggleKind.AUTO_PICKUP) && ToolSettings.installed(drill, ToggleKind.VOID_FILTER),
                "old cards work before the migration");
        helper.assertTrue(GearModules.migrate(drill), "the migration runs once");
        helper.assertTrue(!drill.has(GearComponents.MODULES.get()), "the old component is gone");
        helper.assertTrue(GearModules.get(drill, GearModules.CARD_PICKUP).is(CoreItems.card(UpgradeKind.PICKUP).get())
                && GearModules.get(drill, GearModules.CARD_VOID).is(CoreItems.card(UpgradeKind.VOID).get()), "both cards moved to their slots");
        helper.assertTrue(!GearModules.migrate(drill), "nothing left to migrate");
        ItemStack ticked = withLegacy();
        GearItems.SERVO_DRILL.get().inventoryTick(ticked, helper.getLevel(), helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), 0, false);
        helper.assertTrue(!ticked.has(GearComponents.MODULES.get()) && ToolSettings.installed(ticked, ToggleKind.VOID_FILTER),
                "a tool in an inventory migrates by itself");
        helper.succeed();
    }

    private static ItemStack withLegacy() {
        ItemStack s = new ItemStack(GearItems.SERVO_DRILL.get());
        s.set(GearComponents.MODULES.get(), ToggleKind.VOID_FILTER.bit);
        return s;
    }

    /** The Torch Placer puts a torch from the inventory on the floor, pays FE, and waits for its cooldown. */
    @GameTest(template = "empty")
    public static void torchPlacerPlacesFromInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(1, 0, 1));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
        ServerPlayer player = GearGameTests.survivalPlayer(helper, floor.above());
        player.getInventory().clearContent();
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemEnergy.fill(drill);
        GearModules.setModule(drill, 0, module(GearModuleKind.TORCH_PLACER, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        helper.assertTrue(!TorchPlacer.tryPlace(player, level, player.getMainHandItem(), floor.above()), "no torches, no torch");
        player.getInventory().add(new ItemStack(Items.TORCH, 4));
        player.getAbilities().mayBuild = false;
        helper.assertTrue(!TorchPlacer.tryPlace(player, level, player.getMainHandItem(), floor.above()), "no torch where the player may not build");
        player.getAbilities().mayBuild = true;
        int before = ItemEnergy.get(player.getMainHandItem());
        helper.assertTrue(TorchPlacer.tryPlace(player, level, player.getMainHandItem(), floor.above()), "a torch is placed");
        helper.assertTrue(level.getBlockState(floor.above()).is(Blocks.TORCH), "on the floor");
        helper.assertTrue(player.getInventory().countItem(Items.TORCH) == 3, "taken from the inventory");
        helper.assertTrue(before - ItemEnergy.get(player.getMainHandItem()) == GearConfig.torchCost(), "paid in FE");
        BlockPos other = floor.offset(1, 1, 0);
        level.setBlock(other.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!TorchPlacer.tryPlace(player, level, player.getMainHandItem(), other), "cooldown between torches");
        GearModules.setEnabled(player.getMainHandItem(), GearModuleKind.TORCH_PLACER, false);
        helper.assertTrue(GearModules.active(player.getMainHandItem(), GearModuleKind.TORCH_PLACER) == 0, "switched off in G");
        TorchPlacer.forget(player.getUUID());
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
        GearModules.setModule(player.getMainHandItem(), 0, module(GearModuleKind.ARMOR_PIERCE, 1));
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
        GearModules.setModule(lance, 0, module(GearModuleKind.LIFESTEAL, 1));
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
        GearModules.setEnabled(lance, GearModuleKind.LIFESTEAL, false);
        helper.assertTrue(GearModules.active(lance, GearModuleKind.LIFESTEAL) == 0, "switched off in G");
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
        GearModules.setModule(gun, 0, module(GearModuleKind.RICOCHET, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        int cost = ((RivetGunItem) gun.getItem()).cost(player.getMainHandItem());
        helper.assertTrue(cost == ((RivetGunItem) gun.getItem()).cost() + GearConfig.ricochetCost(1), "ricochet adds to the shot cost");
        GearActions.apply(player, GearActions.MODULE_TOGGLE, GearModuleKind.RICOCHET.ordinal());
        helper.assertTrue(!GearModules.enabled(player.getMainHandItem(), GearModuleKind.RICOCHET), "toggled off");
        helper.assertTrue(((RivetGunItem) gun.getItem()).cost(player.getMainHandItem()) == ((RivetGunItem) gun.getItem()).cost(), "off costs nothing extra");
        GearActions.apply(player, GearActions.MODULE_TOGGLE, GearModuleKind.LIFESTEAL.ordinal());
        GearActions.apply(player, GearActions.MODULE_TOGGLE, 99);
        helper.assertTrue(GearModules.enabled(player.getMainHandItem(), GearModuleKind.LIFESTEAL), "a missing module is not toggled");
        helper.succeed();
    }
}
