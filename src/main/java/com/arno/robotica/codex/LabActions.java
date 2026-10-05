package com.arno.robotica.codex;

import com.arno.robotica.Robotica;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;
import java.util.Map;

/** Server-side test helpers shared by the Creative Lab page and the /robotica command. Callers check permission. */
public final class LabActions {
    private LabActions() {}

    public static final List<String> ACTIONS = List.of("charge", "charge_target", "day", "clear_weather", "heal",
            "kill_hostiles", "spawn", "gamemode", "kit", "give");

    /** Item ids per age. Missing ids (module not built yet) are skipped. Value = count. */
    public static final List<Map<String, Integer>> KITS = List.of(
            Map.ofEntries(Map.entry("copper_gear", 16), Map.entry("clockwork_mechanism", 4), Map.entry("wooden_chassis", 2),
                    Map.entry("mainspring", 2), Map.entry("stumpy", 1), Map.entry("sprout", 1), Map.entry("supply_crate", 2),
                    Map.entry("winding_crank", 1), Map.entry("tinkers_hammer", 1), Map.entry("felling_axe", 1),
                    Map.entry("gearblade", 1), Map.entry("architect_table", 1), Map.entry("minecraft:bone_meal", 32), Map.entry("minecraft:copper_ingot", 64), Map.entry("minecraft:oak_sapling", 16)),
            Map.ofEntries(Map.entry("iron_plate", 32), Map.entry("copper_coil", 8), Map.entry("iron_casing", 8),
                    Map.entry("basic_circuit", 8), Map.entry("electric_motor", 4), Map.entry("copper_cell", 4),
                    Map.entry("combustion_generator", 2), Map.entry("solar_panel_mk1", 4), Map.entry("accumulator_1", 1),
                    Map.entry("copper_conduit", 32), Map.entry("charger", 1), Map.entry("metal_press", 1),
                    Map.entry("excavator", 1), Map.entry("farm_kit_mk2", 2), Map.entry("bore_drill", 1), Map.entry("chainsaw", 1),
                    Map.entry("shock_baton", 1), Map.entry("recall_remote", 1), Map.entry("warp_pad", 2),
                    Map.entry("mining_drone", 1), Map.entry("sentry_drone", 1), Map.entry("courier_drone", 1), Map.entry("courier_remote", 1), Map.entry("exo_helmet_mk1", 1), Map.entry("exo_chestplate_mk1", 1), Map.entry("exo_leggings_mk1", 1), Map.entry("exo_boots_mk1", 1), Map.entry("servo_stride_module_1", 1), Map.entry("magnet_module", 1),
                    Map.entry("upgrade_speed", 4), Map.entry("upgrade_efficiency", 4), Map.entry("upgrade_growth", 4), Map.entry("upgrade_void", 1)),
            Map.ofEntries(Map.entry("reinforced_casing", 8), Map.entry("advanced_circuit", 8), Map.entry("servo_actuator", 4),
                    Map.entry("redstone_cell", 2), Map.entry("solar_panel_mk2", 2), Map.entry("accumulator_2", 1), Map.entry("gold_conduit", 32), Map.entry("tool_upgrade_kit_2", 1), Map.entry("servo_core", 2), Map.entry("servo_drill", 1), Map.entry("rivet_gun", 1),
                    Map.entry("farm_kit_mk3", 2), Map.entry("essence_vial", 4),
                    Map.entry("replicator_controller", 1), Map.entry("replicator_frame", 24), Map.entry("replicator_glass", 4),
                    Map.entry("upgrade_speed", 8), Map.entry("upgrade_range", 4), Map.entry("upgrade_fortune", 3), Map.entry("upgrade_silk", 1)),
            Map.ofEntries(Map.entry("blazing_casing", 8), Map.entry("quantum_circuit", 4), Map.entry("plasma_actuator", 4),
                    Map.entry("magma_core", 2), Map.entry("rift_upgrade", 1), Map.entry("rift_remote", 1), Map.entry("accumulator_3", 1), Map.entry("tool_upgrade_kit_3", 1), Map.entry("magma_drill", 1), Map.entry("arc_blade", 1),
                    Map.entry("upgrade_speed", 8)),
            Map.ofEntries(Map.entry("null_casing", 8), Map.entry("null_circuit", 4), Map.entry("ender_cell", 2),
                    Map.entry("antigrav_core", 2), Map.entry("tool_upgrade_kit_4", 1), Map.entry("flight_module", 1), Map.entry("null_drill", 1), Map.entry("null_lance", 1), Map.entry("farm_kit_mk4", 2),
                    Map.entry("gate_controller", 2), Map.entry("linking_card", 1)));

    public static void run(ServerPlayer player, String action, String arg, int count) {
        ServerLevel level = player.serverLevel();
        switch (action) {
            case "give" -> give(player, arg, count);
            case "kit" -> giveKit(player, count);
            case "charge" -> {
                int n = chargeInventory(player);
                say(player, "Charged " + n + " energy items.");
            }
            case "charge_target" -> chargeTarget(player);
            case "day" -> level.setDayTime(1000);
            case "clear_weather" -> level.setWeatherParameters(6000, 0, false, false);
            case "heal" -> {
                player.setHealth(player.getMaxHealth());
                player.getFoodData().eat(20, 20.0F);
                player.clearFire();
            }
            case "kill_hostiles" -> {
                List<Entity> hostiles = level.getEntities(player, new AABB(player.blockPosition()).inflate(48),
                        e -> e instanceof Enemy && e.isAlive());
                hostiles.forEach(e -> e.kill());
                say(player, "Removed " + hostiles.size() + " hostile mobs.");
            }
            case "spawn" -> spawn(player, arg);
            case "gamemode" -> player.setGameMode(player.isCreative() ? GameType.SURVIVAL : GameType.CREATIVE);
            default -> say(player, "Unknown lab action: " + action);
        }
    }

    /** Stacks a single give or kit may drop on the floor once the inventory is full. */
    public static final int MAX_DROPPED_STACKS = 4;

    public static void give(ServerPlayer player, String id, int count) {
        int[] budget = {MAX_DROPPED_STACKS};
        int refused = give(player, id, count, budget);
        if (refused > 0) say(player, "Inventory full, " + refused + " items were not given.");
    }

    /** Gives items; when the inventory is full at most {@code dropBudget[0]} stacks are dropped. Returns the items not given. */
    private static int give(ServerPlayer player, String id, int count, int[] dropBudget) {
        ResourceLocation key = ResourceLocation.tryParse(id.contains(":") ? id : Robotica.MODID + ":" + id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            say(player, "No such item: " + id);
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        int remaining = Math.max(1, Math.min(count, 64 * 36));
        int refused = 0;
        while (remaining > 0) {
            int n = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, n);
            if (!player.getInventory().add(stack)) {
                if (dropBudget[0] > 0) {
                    dropBudget[0]--;
                    player.drop(stack, false);
                } else {
                    refused += stack.getCount() + (remaining - n);
                    break;
                }
            }
            remaining -= n;
        }
        return refused;
    }

    public static void giveKit(ServerPlayer player, int age) {
        if (age < 0 || age >= KITS.size()) {
            say(player, "Kits exist for ages 0 to " + (KITS.size() - 1) + ".");
            return;
        }
        int given = 0;
        int refused = 0;
        int[] budget = {MAX_DROPPED_STACKS};
        for (Map.Entry<String, Integer> e : KITS.get(age).entrySet()) {
            ResourceLocation key = ResourceLocation.tryParse(e.getKey().contains(":") ? e.getKey() : Robotica.MODID + ":" + e.getKey());
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                refused += give(player, key.toString(), e.getValue(), budget);
                given++;
            }
        }
        chargeInventory(player);
        say(player, "Age " + age + " kit: " + given + " item types, energy items charged.");
        if (refused > 0) say(player, "Inventory full, " + refused + " items were not given. Make room and run it again.");
    }

    /** Fills every FE item in the player's inventory. Returns how many were charged. */
    public static int chargeInventory(ServerPlayer player) {
        int charged = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (energy == null) continue;
            if (stack.getItem() instanceof com.arno.robotica.core.energy.EnergyItem) {
                com.arno.robotica.core.energy.ItemEnergy.fill(stack);
            } else {
                for (int guard = 0; guard < 64 && energy.receiveEnergy(Integer.MAX_VALUE, false) > 0; guard++) {
                    // fill items from other mods through their own capability
                }
            }
            charged++;
        }
        return charged;
    }

    private static void chargeTarget(ServerPlayer player) {
        HitResult hit = player.pick(8.0, 1.0F, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            say(player, "Look at a block with an energy buffer.");
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        IEnergyStorage energy = player.level().getCapability(Capabilities.EnergyStorage.BLOCK, pos, bhr.getDirection());
        if (energy == null) energy = player.level().getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energy == null) {
            say(player, "That block has no energy buffer.");
            return;
        }
        long total = 0;
        for (int guard = 0; guard < 4096; guard++) {
            int r = energy.receiveEnergy(Integer.MAX_VALUE, false);
            if (r <= 0) break;
            total += r;
        }
        say(player, "Inserted " + total + " FE.");
    }

    private static void spawn(ServerPlayer player, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id.isEmpty() ? "minecraft:zombie" : id);
        if (key == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            say(player, "No such entity: " + id);
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
        if (!type.canSummon()) {
            say(player, "Can not spawn " + key + " (not summonable)");
            return;
        }
        BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);
        Entity e = type.spawn(player.serverLevel(), pos, MobSpawnType.COMMAND);
        if (e == null) say(player, "Could not spawn " + key);
    }

    private static void say(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[Robotica Lab] " + text).withStyle(ChatFormatting.AQUA));
    }
}
