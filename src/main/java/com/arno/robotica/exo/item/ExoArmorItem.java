package com.arno.robotica.exo.item;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoModuleKind;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One piece of the Exo-Frame. An FE item with no durability: it never breaks and keeps its base protection when empty,
 * only the modules stop. Mk1 has one module slot, Mk2 has two and a four times bigger battery.
 */
public class ExoArmorItem extends ArmorItem implements EnergyItem {
    /** Battery of a Mk1 piece in FE: helmet, chestplate, leggings, boots. Mk2 is four times as much. */
    private static final int[] MK1_CAPACITY = {200_000, 1_000_000, 400_000, 200_000};

    public final int mk;

    public ExoArmorItem(Properties props, Holder<ArmorMaterial> material, Type type, int mk) {
        super(material, type, props.stacksTo(1).rarity(mk >= 2 ? Rarity.UNCOMMON : Rarity.COMMON));
        this.mk = mk;
    }

    public int moduleSlots() {
        return mk >= 2 ? 2 : 1;
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return MK1_CAPACITY[ExoModuleKind.slotIndex(getEquipmentSlot())] * (mk >= 2 ? 4 : 1);
    }

    @Override
    public int getMaxExtract(ItemStack stack) {
        return 0;
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

    /** Sneak + right-click opens the module screen for this piece; a normal right-click equips it like any armor. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) ExoMenu.openForHand(serverPlayer, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ItemEnergy.appendTooltip(stack, tooltip);
        int slots = moduleSlots();
        tooltip.add(Component.translatable("exo.robotica.tooltip.slots", slots).withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < slots; i++) {
            ExoModuleKind kind = ExoData.kind(stack, i);
            if (kind == null) continue;
            Component state = Component.translatable(ExoData.isEnabled(stack, i) ? "exo.robotica.on" : "exo.robotica.off");
            tooltip.add(Component.literal(" ").append(Component.translatable(kind.nameKey())).append(": ").append(state)
                    .withStyle(ExoData.isEnabled(stack, i) ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("exo.robotica.tooltip.keys").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("exo.robotica.tooltip.set").withStyle(ChatFormatting.DARK_GRAY));
    }
}
