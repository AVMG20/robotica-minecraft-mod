package com.arno.robotica.drones.item;

import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.CourierRoute;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Links Courier Drone routes. Right-click a deployed Courier Drone to bind the remote to it, then sneak-right-click a source
 * inventory and a target inventory. The remote works from up to 128 blocks away as long as the drone's chunk is loaded.
 */
public class CourierRemoteItem extends Item {
    private static final double RANGE = 128.0;

    public CourierRemoteItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CourierDrone drone)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!drone.canInteract(player)) {
            player.displayClientMessage(Component.translatable("message.robotica.drone.not_yours", drone.currentOwnerName()), true);
            return InteractionResult.CONSUME;
        }
        CompoundTag tag = stack.getOrDefault(DronesRegistry.COURIER_LINK.get(), new CompoundTag()).copy();
        tag.putUUID("Drone", drone.getUUID());
        tag.remove("Pending");
        stack.set(DronesRegistry.COURIER_LINK.get(), tag);
        player.displayClientMessage(Component.translatable("message.robotica.courier.bound"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!sp.isShiftKeyDown()) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DronesRegistry.COURIER_LINK.get(), new CompoundTag()).copy();
        if (!tag.hasUUID("Drone")) {
            sp.displayClientMessage(Component.translatable("message.robotica.courier.no_drone"), true);
            return InteractionResult.CONSUME;
        }
        UUID id = tag.getUUID("Drone");
        Entity e = level.getEntity(id);
        if (!(e instanceof CourierDrone drone) || drone.distanceToSqr(sp) > RANGE * RANGE) {
            sp.displayClientMessage(Component.translatable("message.robotica.courier.far"), true);
            return InteractionResult.CONSUME;
        }
        if (!drone.canInteract(sp)) {
            sp.displayClientMessage(Component.translatable("message.robotica.drone.not_yours", drone.currentOwnerName()), true);
            return InteractionResult.CONSUME;
        }
        List<CourierRoute> routes = new ArrayList<>(drone.routes());
        Component msg = CourierRoute.click(tag, routes, level, context.getClickedPos(), context.getClickedFace());
        drone.setRoutes(routes);
        stack.set(DronesRegistry.COURIER_LINK.get(), tag);
        sp.displayClientMessage(msg, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.courier_remote").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.get(DronesRegistry.COURIER_LINK.get());
        if (tag != null && tag.hasUUID("Drone")) tooltip.add(Component.translatable("tooltip.robotica.courier_remote.bound").withStyle(ChatFormatting.DARK_AQUA));
        if (tag != null && tag.contains("Pending")) tooltip.add(Component.translatable("tooltip.robotica.courier_remote.pending").withStyle(ChatFormatting.GOLD));
    }
}
