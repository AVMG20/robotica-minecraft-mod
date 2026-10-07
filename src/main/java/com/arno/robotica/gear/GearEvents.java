package com.arno.robotica.gear;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.tool.AreaBreaker;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.BreakQueue;
import com.arno.robotica.gear.tool.GearSounds;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToggleKind;
import com.arno.robotica.gear.tool.TorchPlacer;
import com.arno.robotica.gear.weapon.Lifesteal;
import com.arno.robotica.gear.weapon.EnergyWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server side hooks of the gear module: area breaking, drop handling, break speed, empty weapon damage, cleanup. */
@EventBusSubscriber(modid = Robotica.MODID)
public final class GearEvents {
    private GearEvents() {}

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getFace() != null
                && player.getMainHandItem().getItem() instanceof GearToolItem) {
            BreakQueue.rememberFace(player, event.getPos(), event.getFace());
        }
    }

    /**
     * Area, vein and tree breaking. Runs after protection mods (which cancel at normal priority); the extra blocks
     * each get their own BreakEvent through destroyBlock, so claims can still veto them one by one.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (BreakQueue.isBreaking(player.getUUID())) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GearToolItem tool)) return;
        BlockPos origin = event.getPos();
        TorchPlacer.schedule(player, level, stack, origin);
        Direction face = BreakQueue.faceFor(player, origin);
        List<BlockPos> targets = AreaBreaker.collect(level, player, stack, tool, origin, face);
        AreaMode mode = tool.activeMode(stack, player);
        if (targets.isEmpty() && !(mode == AreaMode.TREE && tool.spec.replants)) return;
        BreakQueue.start(player, level, tool, stack, origin, targets);
        if (!targets.isEmpty()) GearSounds.breakStarted(player, level, tool, mode, origin, targets.size() + 1);
    }

    /** Redirects drops of Robotica tools: Void Filter module, auto-smelt, Auto-Pickup module (in that order). */
    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        ItemStack tool = event.getTool();
        if (!(tool.getItem() instanceof GearToolItem item)) return;
        boolean voidIt = Modules.active(tool, ModuleKind.VOID_FILTER) > 0;
        boolean smelt = item.toggleActive(tool, ToggleKind.AUTO_SMELT);
        boolean pickup = Modules.active(tool, ModuleKind.AUTO_PICKUP) > 0;
        if (!voidIt && !smelt && !pickup) return;
        ServerLevel level = event.getLevel();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity entity = it.next();
            ItemStack drop = entity.getItem();
            if (voidIt && drop.is(GearItems.VOIDABLE)) {
                it.remove();
                continue;
            }
            if (smelt) {
                drop = smelted(level, drop);
                entity.setItem(drop);
            }
            if (pickup) {
                player.getInventory().add(drop);
                if (drop.isEmpty()) {
                    it.remove();
                } else {
                    entity.setItem(drop);
                }
            }
        }
    }

    /** Smelting recipe result for the whole stack, or the stack itself when nothing smelts. */
    public static ItemStack smelted(ServerLevel level, ItemStack stack) {
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> {
                    ItemStack result = holder.value().assemble(input, level.registryAccess());
                    if (result.isEmpty()) return stack;
                    result.setCount(result.getCount() * stack.getCount());
                    return result;
                })
                .orElse(stack);
    }

    /** Early tools mine slower in their box modes (see ToolSpec.areaSpeed). */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack.getItem() instanceof GearToolItem tool && tool.spec.areaSpeed < 1.0F
                && tool.activeMode(stack, event.getEntity()).isBox()) {
            event.setNewSpeed(event.getNewSpeed() * tool.spec.areaSpeed);
        }
    }

    /** Energy weapons deal 1 damage (base hand damage) when they can not pay for a use. */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (item instanceof EnergyWeaponItem weapon && !weapon.hasCharge(stack)) {
            event.replaceModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 0.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        BreakQueue.tick(event.getServer());
        TorchPlacer.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BreakQueue.clear(event.getEntity().getUUID());
        GearSounds.forget(event.getEntity().getUUID());
        TorchPlacer.forget(event.getEntity().getUUID());
        Lifesteal.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BreakQueue.clearAll();
        GearSounds.clearAll();
        TorchPlacer.clearAll();
        Lifesteal.clearAll();
    }
}
