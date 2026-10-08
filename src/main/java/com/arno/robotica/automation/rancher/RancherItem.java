package com.arno.robotica.automation.rancher;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** The Rancher in item form: right-click a block to place it there (its home). Energy and settings travel in components. */
public class RancherItem extends Item implements EnergyItem {
    private final int tier;

    public RancherItem(Properties props, int tier) {
        super(props.stacksTo(1));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return Rancher.energyCapacityFor(tier);
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(face);
        ItemStack stack = context.getItemInHand();
        if (player != null && (!player.mayUseItemAt(pos, face, stack) || !level.mayInteract(player, pos))) return InteractionResult.FAIL;
        var type = RancherContent.RANCHER_ENTITY.get();
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        AABB box = type.getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(box)) return InteractionResult.FAIL;
        Rancher rancher = type.create(serverLevel);
        if (rancher == null) return InteractionResult.FAIL;
        rancher.moveTo(x, y, z, player != null ? player.getYRot() + 180.0F : 0.0F, 0.0F);
        rancher.initFromStack(stack, player);
        rancher.setHome(pos);
        serverLevel.addFreshEntity(rancher);
        CoreSounds.play(rancher, CoreSounds.ROBOT_BEEP, SoundSource.NEUTRAL, 0.7F, 1.0F);
        if (player == null || !player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ItemEnergy.appendTooltip(stack, tooltip);
        int side = tier >= 2 ? 13 : 9;
        tooltip.add(Component.translatable("tooltip.robotica.rancher", side, side).withStyle(ChatFormatting.GRAY));
    }
}
