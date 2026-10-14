package com.arno.robotica.exo.item;

import com.arno.robotica.core.item.HasDetails;
import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.module.ModuleHolder;
import com.arno.robotica.core.module.ModuleItem;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.core.util.Fmt;
import com.arno.robotica.exo.ExoConfig;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoJet;
import com.arno.robotica.exo.ExoSuit;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One piece of the Exo-Frame. An FE item with no durability: it never breaks and keeps its base protection when empty,
 * only the modules stop. Its mark sets its module slots (see {@link Modules}) and a battery
 * {@code markCapacityMultiplier}^(mk-1) times the Mk1 battery; Capacitor Plating adds to it. Chestplates from Mk2 on
 * have a core socket; every chestplate has a cell slot. Takes modules instead of enchantments. A worn chestplate with
 * a charged Glider module glides like an Elytra.
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

    /** Chargers fill a chestplate's own battery first, then the cell in its cell slot. */
    @Override
    public IEnergyStorage energyStorage(ItemStack stack) {
        IEnergyStorage own = EnergyItem.super.energyStorage(stack);
        return getEquipmentSlot() == EquipmentSlot.CHEST ? new WithCell(stack, own) : own;
    }

    private record WithCell(ItemStack chest, IEnergyStorage own) implements IEnergyStorage {
        /** The cell's storage (on a copy of the cell), or null without a cell that takes FE. */
        @Nullable
        private IEnergyStorage cell(ItemStack cell) {
            return cell.getItem() instanceof EnergyItem e && e.getMaxReceive(cell) > 0 ? e.energyStorage(cell) : null;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int got = own.receiveEnergy(toReceive, simulate);
            if (got >= toReceive) return got;
            ItemStack cell = ExoData.cell(chest);
            IEnergyStorage storage = cell(cell);
            if (storage == null) return got;
            int more = storage.receiveEnergy(toReceive - got, simulate);
            if (!simulate && more > 0) ExoData.setCell(chest, cell);
            return got + more;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return own.extractEnergy(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            ItemStack cell = ExoData.cell(chest);
            long cellStored = cell(cell) == null ? 0 : ItemEnergy.get(cell);
            return (int) Math.min(Integer.MAX_VALUE, own.getEnergyStored() + cellStored);
        }

        @Override
        public int getMaxEnergyStored() {
            ItemStack cell = ExoData.cell(chest);
            long cellCap = cell(cell) == null ? 0 : ItemEnergy.capacity(cell);
            return (int) Math.min(Integer.MAX_VALUE, own.getMaxEnergyStored() + cellCap);
        }

        @Override
        public boolean canExtract() {
            return own.canExtract();
        }

        @Override
        public boolean canReceive() {
            return own.canReceive() || cell(ExoData.cell(chest)) != null;
        }
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

    // ---- Glider module: Elytra flight (the Exo ticker charges the time in the air; a Jet Assist goes first) ----

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return getEquipmentSlot() == EquipmentSlot.CHEST && ExoSuit.pieceLevel(entity, EquipmentSlot.CHEST, ModuleKind.GLIDER) > 0
                && ExoJet.gliderMayOpen(entity);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level().isClientSide && (flightTicks + 1) % 10 == 0) entity.gameEvent(GameEvent.ELYTRA_GLIDE);
        return true;
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
        ItemStack cell = ExoData.cell(stack);
        if (!cell.isEmpty()) {
            tooltip.add(Component.translatable("exo.robotica.tooltip.cell", cell.getHoverName(),
                    Fmt.energy(ItemEnergy.get(cell))).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("exo.robotica.tooltip.keys", HasDetails.key("key.robotica.exo.open_modules"),
                HasDetails.key("key.robotica.exo.toggle_flight"), HasDetails.key("key.robotica.exo.dash"),
                HasDetails.key("key.robotica.exo.sonar"), HasDetails.key("key.robotica.exo.overclock")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("exo.robotica.tooltip.set").withStyle(ChatFormatting.DARK_GRAY));
    }
}
