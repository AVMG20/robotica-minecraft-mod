package com.arno.robotica.drones.item;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.DroneBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * A drone in item form. Right-click a block face to deploy it; sneak-right-click the drone to get this item back with its
 * energy, inventory and settings (data components). Mk2 is the same entity with better numbers, reached by smithing.
 */
public class DroneItem extends Item implements EnergyItem {
    private final DronesRegistry.Kind kind;
    private final int tier;

    public DroneItem(Properties props, DronesRegistry.Kind kind, int tier) {
        super(props.stacksTo(1));
        this.kind = kind;
        this.tier = tier;
    }

    public DronesRegistry.Kind kind() {
        return kind;
    }

    public int tier() {
        return tier;
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return kind == DronesRegistry.Kind.MINING ? DronesConfig.miningBuffer(tier) : DronesConfig.sentryBuffer(tier);
    }

    @Override
    public int getMaxReceive(ItemStack stack) {
        return 4000;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemEnergy.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemEnergy.BAR_COLOR;
    }

    private EntityType<? extends DroneBase> entityType() {
        return kind == DronesRegistry.Kind.MINING ? DronesRegistry.MINING_DRONE_ENTITY.get() : DronesRegistry.SENTRY_DRONE_ENTITY.get();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(face);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + (face == Direction.UP ? 0.45 : face == Direction.DOWN ? -0.3 : 0.15);
        double z = pos.getZ() + 0.5;
        EntityType<? extends DroneBase> type = entityType();
        AABB box = type.getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(box)) {
            y += 1.0;
            box = type.getDimensions().makeBoundingBox(x, y, z);
            if (!level.noCollision(box)) return InteractionResult.FAIL;
        }
        DroneBase drone = type.create(serverLevel);
        if (drone == null) return InteractionResult.FAIL;
        float yaw = player != null ? player.getYRot() : 0.0F;
        drone.moveTo(x, y, z, yaw, 0.0F);
        ItemStack stack = context.getItemInHand();
        drone.initFromStack(stack, player);
        drone.onDeployed(player, pos);
        serverLevel.addFreshEntity(drone);
        CoreSounds.play(drone, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.7F, 1.1F);
        if (player == null || !player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ItemEnergy.appendTooltip(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.robotica.drone." + kind.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.drone.pickup").withStyle(ChatFormatting.DARK_GRAY));
        CompoundTag state = stack.get(DronesRegistry.DRONE_STATE.get());
        if (state != null && state.contains("Storage")) {
            int items = 0;
            for (var tag : state.getCompound("Storage").getList("Items", 10)) {
                if (tag instanceof CompoundTag c) items += Math.max(1, c.getInt("count"));
            }
            if (items > 0) tooltip.add(Component.translatable("tooltip.robotica.drone.stored", items).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
