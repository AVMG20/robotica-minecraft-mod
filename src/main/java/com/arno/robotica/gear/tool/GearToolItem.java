package com.arno.robotica.gear.tool;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.HasDetails;
import com.arno.robotica.core.module.ModuleHolder;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.ModuleTarget;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.gear.GearConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hammer, axes and drills. Durability tools (Age 0) use vanilla damage and take normal enchantments. FE tools (see
 * {@link GearEnergyToolItem}) drain energy per block, never break and take modules instead of enchantments
 * (Overclock, Fortune / Silk Touch, Auto-Pickup...). A depleted FE tool mines like a wooden tool and has no area mode.
 */
public class GearToolItem extends Item implements HasDetails, ModuleHolder {
    /** Mining speed of a wooden tool, used by depleted FE tools. */
    public static final float EMPTY_SPEED = 2.0F;

    public final ToolSpec spec;

    public GearToolItem(Properties props, ToolSpec spec) {
        super(props.stacksTo(1).component(DataComponents.TOOL, spec.toolComponent()));
        this.spec = spec;
    }

    /** False for a depleted FE tool. */
    public boolean hasPower(ItemStack stack) {
        return !spec.isEnergy() || ItemEnergy.get(stack) > 0;
    }

    /** True if the block is in one of the tool's effective tags (ignores tier). */
    public boolean canMine(BlockState state) {
        for (TagKey<Block> tag : spec.tags) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    /** Tier speed on effective blocks, times the Overclock module. */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!hasPower(stack)) return canMine(state) ? EMPTY_SPEED : 1.0F;
        float speed = super.getDestroySpeed(stack, state);
        int overclock = Modules.powered(stack, ModuleKind.OVERCLOCK);
        return overclock > 0 && speed > 1.0F ? speed * (float) (1.0 + GearConfig.overclockSpeed(overclock)) : speed;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (!hasPower(stack)) return canMine(state) && !state.is(BlockTags.INCORRECT_FOR_WOODEN_TOOL);
        return super.isCorrectToolForDrops(stack, state);
    }

    /** FE this block costs with this tool stack. Leaves are free; Overclock adds to it, the Power Regulator saves. */
    public int cost(ItemStack stack, BlockState state) {
        if (!spec.isEnergy() || state.is(BlockTags.LEAVES)) return 0;
        int base = spec.costPerBlock.getAsInt();
        double overclock = GearConfig.overclockCost(Modules.active(stack, ModuleKind.OVERCLOCK));
        return Modules.regulated(stack, (int) Math.round(base * (1.0 + overclock)));
    }

    /** Whether the tool can pay for breaking another block (extras of an area break only). */
    public boolean canAfford(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return false;
        if (spec.isEnergy()) return ItemEnergy.get(stack) >= cost(stack, state);
        return stack.getMaxDamage() - stack.getDamageValue() > 1;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (level.isClientSide || state.getDestroySpeed(level, pos) == 0.0F) return true;
        if (spec.isEnergy()) {
            ItemEnergy.drain(stack, cost(stack, state));
        } else {
            stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!spec.isEnergy()) stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    /** The stored mode, or the tool's default mode when unset or no longer allowed. */
    public AreaMode mode(ItemStack stack) {
        AreaMode m = stack.get(com.arno.robotica.gear.GearComponents.MODE.get());
        return m != null && spec.hasMode(m) ? m : spec.defaultMode;
    }

    /** Mode that applies right now: sneaking or an empty tool always means single blocks. Server enforces this. */
    public AreaMode activeMode(ItemStack stack, Player player) {
        if (player.isShiftKeyDown() || !hasPower(stack)) return AreaMode.SINGLE;
        return mode(stack);
    }

    /** The tool has the setting and it is switched on. */
    public boolean toggleActive(ItemStack stack, ToggleKind kind) {
        return spec.toggles.contains(kind) && ToolSettings.has(stack, kind);
    }

    // ---- modules (FE tools only) ----

    @Nullable
    @Override
    public ModuleTarget moduleTarget(ItemStack stack) {
        if (!spec.isEnergy()) return null;
        return spec.isAxe() ? ModuleTarget.CHAINSAW : ModuleTarget.DRILL;
    }

    @Override
    public int moduleTier(ItemStack stack) {
        return spec.age;
    }

    /** True when a Fortune or Silk Touch module works in the tool (the B key cycles them). */
    public static boolean hasDropModules(ItemStack stack) {
        return Modules.level(stack, ModuleKind.FORTUNE) > 0 || Modules.level(stack, ModuleKind.SILK_TOUCH) > 0;
    }

    // ---- enchanting: Age 0 tools take the normal mining enchantments, FE tools none (they take modules) ----

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return !spec.isEnergy();
    }

    @Override
    public int getEnchantmentValue() {
        return spec.isEnergy() ? 0 : 15;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return !spec.isEnergy() && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return !spec.isEnergy() && super.isBookEnchantable(stack, book);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return !spec.isEnergy() && super.isPrimaryItemFor(stack, enchantment);
    }

    /**
     * FE tools: only the switched-on Fortune or Silk Touch module counts, as that enchantment, and only while the tool
     * has FE (like Overclock); nothing else does.
     */
    @Override
    public int getEnchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        if (spec.isEnergy()) {
            if (enchantment.is(Enchantments.FORTUNE)) return Modules.powered(stack, ModuleKind.FORTUNE);
            if (enchantment.is(Enchantments.SILK_TOUCH)) return Math.min(1, Modules.powered(stack, ModuleKind.SILK_TOUCH));
            return 0;
        }
        return super.getEnchantmentLevel(stack, enchantment);
    }

    @Override
    public ItemEnchantments getAllEnchantments(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup) {
        ItemEnchantments base = super.getAllEnchantments(stack, lookup);
        if (!spec.isEnergy()) return base;
        int fortune = Modules.powered(stack, ModuleKind.FORTUNE);
        int silk = Modules.powered(stack, ModuleKind.SILK_TOUCH);
        ItemEnchantments.Mutable all = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        if (fortune > 0) all.set(lookup.getOrThrow(Enchantments.FORTUNE), fortune);
        if (silk > 0) all.set(lookup.getOrThrow(Enchantments.SILK_TOUCH), 1);
        return all.toImmutable();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // A drill smithed from a worn hammer inherits its damage value; FE tools have no durability, so drop it.
        if (!level.isClientSide && spec.isEnergy() && stack.has(DataComponents.DAMAGE)) stack.remove(DataComponents.DAMAGE);
        // Enchantments smithed over from an Age 0 tool do nothing on FE tools: clear them.
        if (!level.isClientSide && spec.isEnergy() && stack.has(DataComponents.ENCHANTMENTS)) stack.remove(DataComponents.ENCHANTMENTS);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", spec.age, Component.translatable("age.robotica." + spec.age))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (spec.hasAreaModes()) tooltip.add(modeStrip(mode(stack)));
        if (spec.isEnergy()) {
            MutableComponent modules = Modules.describe(stack);
            if (modules != null) tooltip.add(Component.translatable("tooltip.robotica.gear.modules", modules).withStyle(ChatFormatting.GRAY));
            ItemEnergy.appendTooltip(stack, tooltip);
        }
    }

    /** "Mode: 1x1 [3x3] 5x5 Vein" with the current mode highlighted. Shared by the tooltip and the HUD. */
    public Component modeStrip(AreaMode current) {
        MutableComponent line = Component.translatable("gear.robotica.mode_label").withStyle(ChatFormatting.GRAY);
        for (AreaMode m : spec.modes) {
            line.append(" ");
            if (m == current) {
                line.append(Component.literal("[").append(m.displayName()).append("]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            } else {
                line.append(m.displayName().copy().withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return line;
    }

    @Override
    public void appendDetails(ItemStack stack, TooltipContext ctx, List<Component> lines) {
        lines.add(HasDetails.line("tooltip.robotica.gear.what." + BuiltInRegistries.ITEM.getKey(this).getPath()));
        if (spec.isEnergy()) {
            lines.add(HasDetails.line("tooltip.robotica.gear.fe_per_block", spec.costPerBlock.getAsInt()));
        }
        if (spec.modes.size() > 1) {
            lines.add(Component.translatable("tooltip.robotica.gear.key_mode", HasDetails.key("key.robotica.gear.cycle_mode")).withStyle(ChatFormatting.GRAY));
        }
        if (hasDropModules(stack)) {
            lines.add(Component.translatable("tooltip.robotica.gear.key_enchant", HasDetails.key("key.robotica.gear.swap_enchant")).withStyle(ChatFormatting.GRAY));
        }
        if (!spec.toggles.isEmpty() || Modules.describe(stack) != null) {
            MutableComponent on = Component.empty();
            boolean first = true;
            for (ToggleKind kind : ToggleKind.values()) {
                if (!spec.toggles.contains(kind)) continue;
                if (!first) on.append(", ");
                first = false;
                on.append(kind.displayName().copy().withStyle(ToolSettings.has(stack, kind) ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable("tooltip.robotica.gear.key_settings", HasDetails.key("key.robotica.gear.open_toggles"), on)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (spec.isEnergy() && Modules.describe(stack) == null) {
            lines.add(HasDetails.line("tooltip.robotica.gear.no_modules", Modules.slots(stack)));
        }
    }
}
