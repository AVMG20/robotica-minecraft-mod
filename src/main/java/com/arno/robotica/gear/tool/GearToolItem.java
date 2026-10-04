package com.arno.robotica.gear.tool;

import com.arno.robotica.core.energy.ItemEnergy;
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
public class GearToolItem extends Item {
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

    /** FE this block costs. Leaves are free so a tree felling does not drain extra. */
    public int cost(BlockState state) {
        if (!spec.isEnergy() || state.is(BlockTags.LEAVES)) return 0;
        return spec.costPerBlock.getAsInt();
    }

    /** Whether the tool can pay for breaking another block (extras of an area break only). */
    public boolean canAfford(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return false;
        if (spec.isEnergy()) return ItemEnergy.get(stack) >= cost(state);
        return stack.getMaxDamage() - stack.getDamageValue() > 1;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (level.isClientSide || state.getDestroySpeed(level, pos) == 0.0F) return true;
        if (spec.isEnergy()) {
            ItemEnergy.drain(stack, cost(state));
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

    /** The stored mode, or the first mode of the tool when unset or no longer allowed. */
    public AreaMode mode(ItemStack stack) {
        AreaMode m = stack.get(com.arno.robotica.gear.GearComponents.MODE.get());
        return m != null && spec.hasMode(m) ? m : spec.modes.get(0);
    }

    /** Mode that applies right now: sneaking or an empty tool always means single blocks. Server enforces this. */
    public AreaMode activeMode(ItemStack stack, Player player) {
        if (player.isShiftKeyDown() || !hasPower(stack)) return AreaMode.SINGLE;
        return mode(stack);
    }

    public boolean toggleActive(ItemStack stack, ToggleKind kind) {
        return spec.toggles.contains(kind) && ToolSettings.has(stack, kind);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack tool = context.getItemInHand();
        if (player == null || !toggleActive(tool, ToggleKind.LIGHT_PLACER)) return InteractionResult.PASS;
        ItemStack torch = findTorch(player);
        if (torch.isEmpty()) return InteractionResult.PASS;
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        return torch.useOn(new UseOnContext(context.getLevel(), player, context.getHand(), torch, hit));
    }

    private static ItemStack findTorch(Player player) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.offhand) {
            if (s.is(Items.TORCH)) return s;
        }
        for (ItemStack s : inv.items) {
            if (s.is(Items.TORCH)) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server && spec.fortuneLevel > 0 && server.getGameTime() % 20 == 0) {
            RegistryAccess access = server.registryAccess();
            ToolSettings.syncEnchantments(stack, this, access);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.robotica.age", spec.age, Component.translatable("age.robotica." + spec.age))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (spec.modes.size() > 1 || spec.modes.get(0) != AreaMode.SINGLE) {
            tooltip.add(Component.translatable("tooltip.robotica.gear.mode", mode(stack).displayName()).withStyle(ChatFormatting.GRAY));
        }
        for (ToggleKind kind : ToggleKind.values()) {
            if (toggleActive(stack, kind) && kind != ToggleKind.KEEP_FLOOR) {
                tooltip.add(Component.literal("+ ").append(kind.displayName()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (spec.fortuneLevel > 0) {
            int em = ToolSettings.enchantMode(stack);
            if (em != ToolSettings.ENCHANT_NONE) {
                tooltip.add(Component.translatable(em == ToolSettings.ENCHANT_SILK ? "gear.robotica.enchant.silk" : "gear.robotica.enchant.fortune")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (spec.isEnergy()) ItemEnergy.appendTooltip(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.robotica.gear.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
