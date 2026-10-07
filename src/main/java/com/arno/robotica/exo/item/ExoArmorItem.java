package com.arno.robotica.exo.item;

import com.arno.robotica.core.item.HasDetails;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.module.ModuleHolder;
import com.arno.robotica.core.module.ModuleItem;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One piece of the Exo-Frame. An FE item with no durability: it never breaks and keeps its base protection when empty,
 * only the modules stop. Its mark sets its module slots (see {@link Modules}) and a battery
 * {@code markCapacityMultiplier}^(mk-1) times the Mk1 battery; Capacitor Plating adds to it. Chestplates from Mk2 on
 * have a core socket. Takes modules instead of enchantments.
 */
public class ExoArmorItem extends ArmorItem implements EnergyItem, ModuleHolder {
    /** Module slots the J screen shows per piece. */
    public static final int MAX_SLOTS = Modules.MAX_ARMOR_SLOTS;

    public final int mk;

    public ExoArmorItem(Properties props, Holder<ArmorMaterial> material, Type type, int mk) {
        super(material, type, mk >= 4 ? props.stacksTo(1).rarity(Rarity.EPIC).fireResistant() : props.stacksTo(1).rarity(rarity(mk)));
        this.mk = mk;
    }

    private static Rarity rarity(int mk) {
        return switch (mk) {
            case 1 -> Rarity.COMMON;
            case 2 -> Rarity.UNCOMMON;
            default -> Rarity.RARE;
        };
    }

    @Override
    public ModuleTarget moduleTarget(ItemStack stack) {
        return ModuleTarget.armor(getEquipmentSlot());
    }

    @Override
    public int moduleTier(ItemStack stack) {
        return mk;
    }

    /** Chestplates from Mk2 on hold one boss core. */
    public boolean hasCoreSocket() {
        return mk >= 2 && getEquipmentSlot() == EquipmentSlot.CHEST;
    }

    /** Battery without Capacitor Plating. */
    public int baseCapacity() {
        long cap = ExoConfig.baseCapacity(ExoSuit.index(getEquipmentSlot()));
        for (int i = 1; i < mk; i++) cap *= ExoConfig.markMultiplier();
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        int plating = Modules.level(stack, ModuleKind.CAPACITOR_PLATING);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(baseCapacity() * (1.0 + ExoConfig.capacitorBonus(plating))));
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

    // ---- no enchanting: Exo armor takes modules ----

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
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
        int slots = Modules.slots(stack);
        tooltip.add(Component.translatable("exo.robotica.tooltip.slots", mk, slots).withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < slots; i++) {
            ItemStack module = Modules.module(stack, i);
            if (!(module.getItem() instanceof ModuleItem)) continue;
            boolean on = Modules.enabled(stack, i);
            Component state = Component.translatable(on ? "exo.robotica.on" : "exo.robotica.off");
            tooltip.add(Component.literal(" ").append(module.getHoverName()).append(": ").append(state)
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        if (hasCoreSocket()) {
            ItemStack core = ExoData.core(stack);
            tooltip.add(core.isEmpty()
                    ? Component.translatable("exo.robotica.tooltip.core_empty").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("exo.robotica.tooltip.core", core.getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("exo.robotica.tooltip.keys", HasDetails.key("key.robotica.exo.open_modules"),
                HasDetails.key("key.robotica.exo.toggle_flight"), HasDetails.key("key.robotica.exo.dash"),
                HasDetails.key("key.robotica.exo.sonar"), HasDetails.key("key.robotica.exo.overclock")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("exo.robotica.tooltip.set").withStyle(ChatFormatting.DARK_GRAY));
    }
}
