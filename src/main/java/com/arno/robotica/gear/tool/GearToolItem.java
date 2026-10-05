package com.arno.robotica.gear.tool;

import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.HasDetails;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Hammer, axes and drills. Durability tools (Age 0) use vanilla damage, FE tools (see {@link GearEnergyToolItem}) drain
 * energy per block and never break. A depleted FE tool mines like a wooden tool and has no area mode.
 */
public class GearToolItem extends Item implements HasDetails {
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

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!hasPower(stack)) return canMine(state) ? EMPTY_SPEED : 1.0F;
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (!hasPower(stack)) return canMine(state) && !state.is(BlockTags.INCORRECT_FOR_WOODEN_TOOL);
        return super.isCorrectToolForDrops(stack, state);
    }

    /** FE this block costs with this tool stack. Leaves are free; Unbreaking lowers the cost (III: 40%). */
    public int cost(ItemStack stack, BlockState state) {
        if (!spec.isEnergy() || state.is(BlockTags.LEAVES)) return 0;
        int base = spec.costPerBlock.getAsInt();
        int unbreaking = unbreakingLevel(stack);
        if (unbreaking <= 0 || base <= 0) return base;
        return Math.max(1, (int) Math.ceil(base / (1.0 + 0.5 * unbreaking)));
    }

    /** Unbreaking level from the stack's enchantments, without a registry lookup (works on both sides). */
    public static int unbreakingLevel(ItemStack stack) {
        for (var entry : stack.getTagEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.UNBREAKING)) return entry.getIntValue();
        }
        return 0;
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

    public boolean toggleActive(ItemStack stack, ToggleKind kind) {
        return spec.toggles.contains(kind) && ToolSettings.has(stack, kind);
    }

    // ---- enchanting: Robotica tools take the normal mining enchantments (tag minecraft:enchantable/mining) ----

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return switch (spec.age) {
            case 0 -> 15;
            case 1 -> 14;
            case 2 -> 10;
            default -> 15;
        };
    }

    /** FE tools never wear out, so Mending would do nothing: it is not offered. Unbreaking lowers the FE per block. */
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (spec.isEnergy() && enchantment.is(Enchantments.MENDING)) return false;
        return super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server && spec.fortuneLevel > 0 && server.getGameTime() % 20 == 0) {
            RegistryAccess access = server.registryAccess();
            ToolSettings.syncEnchantments(stack, this, access);
        }
        // A drill smithed from a worn hammer inherits its damage value; FE tools have no durability, so drop it.
        if (!level.isClientSide && spec.isEnergy() && stack.has(DataComponents.DAMAGE)) stack.remove(DataComponents.DAMAGE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", spec.age, Component.translatable("age.robotica." + spec.age))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (spec.hasAreaModes()) tooltip.add(modeStrip(mode(stack)));
        if (spec.fortuneLevel > 0) {
            int em = ToolSettings.enchantMode(stack);
            if (em != ToolSettings.ENCHANT_NONE) {
                tooltip.add(Component.translatable(em == ToolSettings.ENCHANT_SILK ? "gear.robotica.enchant.silk" : "gear.robotica.enchant.fortune")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (spec.isEnergy()) ItemEnergy.appendTooltip(stack, tooltip);
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
            lines.add(HasDetails.line("tooltip.robotica.gear.sneak_single"));
        }
        if (spec.fortuneLevel > 0) {
            lines.add(Component.translatable("tooltip.robotica.gear.key_enchant", HasDetails.key("key.robotica.gear.swap_enchant")).withStyle(ChatFormatting.GRAY));
        }
        if (!spec.toggles.isEmpty()) {
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
        if (spec.slowArea) lines.add(HasDetails.line("tooltip.robotica.gear.slow_area"));
        lines.add(HasDetails.line("tooltip.robotica.gear.enchantable"));
    }
}
