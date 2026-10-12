package com.arno.robotica.architect.pocket;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.matter.MatterTable;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.core.util.Fmt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pocket Architect Mk1 / Mk2: a hand-held Architect Table without the plot grid. It stores matter (rustic, and refined on
 * the Mk2), eats junk the player picks up, hands out building blocks for matter and places the picked block on
 * right-click. Mk1 makes the styles the table has without a casing; Mk2 makes every style. Sneak-right-click: GUI
 * ({@link PocketMenu}); sneak-right-click an Architect Table: move the stored matter into it.
 */
public class PocketArchitectItem extends Item {
    /** Most matter of each grade a pocket holds. */
    public static final int CAP = 1_000_000_000;
    public static final int BLOCKS = BuildStyle.values().length * Role.values().length;
    /** Junk the table's clear terrain voids; also the starting junk list of a pocket. */
    public static final TagKey<Item> VOIDABLE = TagKey.create(Registries.ITEM, Robotica.id("voidable"));

    /** {@link #take} results below 1. */
    public static final int TAKE_FULL = 0, TAKE_NO_MATTER = -1, TAKE_LOCKED = -2;

    private final boolean mk2;

    public PocketArchitectItem(Properties props, boolean mk2) {
        super(props.stacksTo(1));
        this.mk2 = mk2;
    }

    public boolean mk2() {
        return mk2;
    }

    // ---------------------------------------------------------------- blocks

    /** Index of a building block in the GUI grid: one row per style, one column per role. */
    public static int blockIndex(BuildStyle style, Role role) {
        return style.ordinal() * Role.values().length + role.ordinal();
    }

    public static boolean validIndex(int index) {
        return index >= 0 && index < BLOCKS;
    }

    public static BuildStyle styleAt(int index) {
        return BuildStyle.values()[index / Role.values().length];
    }

    public static Role roleAt(int index) {
        return Role.values()[index % Role.values().length];
    }

    public static BlockItem blockAt(int index) {
        return ArchitectRegistry.styleItem(styleAt(index), roleAt(index)).get();
    }

    /** Mk1 makes the styles the table has without a casing, Mk2 every style. */
    public boolean makes(BuildStyle style) {
        return mk2 || style.casingLevel == 0;
    }

    // ---------------------------------------------------------------- stored data

    public static Matter matter(ItemStack pocket) {
        return pocket.getOrDefault(ArchitectRegistry.MATTER_COMPONENT.get(), Matter.ZERO);
    }

    public static void setMatter(ItemStack pocket, Matter m) {
        if (m.isZero()) pocket.remove(ArchitectRegistry.MATTER_COMPONENT.get());
        else pocket.set(ArchitectRegistry.MATTER_COMPONENT.get(), m);
    }

    public static PocketSettings settings(ItemStack pocket) {
        return pocket.getOrDefault(ArchitectRegistry.POCKET_SETTINGS.get(), PocketSettings.DEFAULT);
    }

    public static void setSettings(ItemStack pocket, PocketSettings settings) {
        if (settings.equals(PocketSettings.DEFAULT)) pocket.remove(ArchitectRegistry.POCKET_SETTINGS.get());
        else pocket.set(ArchitectRegistry.POCKET_SETTINGS.get(), settings);
    }

    /** The junk list: what the player set, or the {@link #VOIDABLE} tag until they edit it. */
    public static List<Item> junkList(PocketSettings settings) {
        if (settings.junk().isPresent()) return settings.junk().get();
        List<Item> list = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(VOIDABLE)) {
            if (list.size() < PocketSettings.JUNK_SLOTS) list.add(holder.value());
        }
        return list;
    }

    public static boolean isJunk(PocketSettings settings, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return settings.junk().isPresent() ? settings.junk().get().contains(stack.getItem()) : stack.is(VOIDABLE);
    }

    /** True for items this pocket turns into matter: anything with a matter value, the Mk1 only rustic-only items. */
    public boolean accepts(ItemStack stack) {
        Matter v = MatterTable.valueOf(stack);
        return !v.isZero() && (mk2 || v.refined() == 0);
    }

    // ---------------------------------------------------------------- matter in

    /** Turns as much of {@code input} into matter as fits (shrinks it). Returns the number of items used. */
    public int absorb(ItemStack pocket, ItemStack input) {
        if (input.isEmpty() || !accepts(input)) return 0;
        Matter v = MatterTable.valueOf(input);
        Matter m = matter(pocket);
        int fit = input.getCount();
        if (v.rustic() > 0) fit = Math.min(fit, (CAP - m.rustic()) / v.rustic());
        if (v.refined() > 0) fit = Math.min(fit, (CAP - m.refined()) / v.refined());
        if (fit <= 0) return 0;
        input.shrink(fit);
        setMatter(pocket, new Matter(m.rustic() + v.rustic() * fit, m.refined() + v.refined() * fit));
        return fit;
    }

    /**
     * Feeds a stack the player is picking up to the pockets in their inventory (main slots, hotbar, off hand) that eat
     * it as junk. Shrinks the stack; true when nothing is left of it.
     */
    public static boolean eatPickup(Player player, ItemStack picked) {
        if (picked.isEmpty() || picked.getItem() instanceof PocketArchitectItem) return false;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size() && !picked.isEmpty(); i++) eatInto(inv.items.get(i), picked);
        if (!picked.isEmpty()) eatInto(inv.offhand.get(0), picked);
        return picked.isEmpty();
    }

    private static void eatInto(ItemStack stack, ItemStack picked) {
        if (!(stack.getItem() instanceof PocketArchitectItem pocket)) return;
        PocketSettings settings = settings(stack);
        if (settings.eatJunk() && isJunk(settings, picked)) pocket.absorb(stack, picked);
    }

    /** Junk on the ground goes straight into a carried pocket instead of the inventory; stacks already carried stay. */
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity entity = event.getItemEntity();
        Player player = event.getPlayer();
        if (player.level().isClientSide || entity.hasPickUpDelay() || event.canPickup() == TriState.FALSE) return;
        ItemStack stack = entity.getItem();
        int count = stack.getCount();
        if (!eatPickup(player, stack)) {
            // partly eaten (the pockets are full): show the new count; the rest is picked up as usual
            if (stack.getCount() != count) entity.setItem(stack.copy());
            return;
        }
        player.take(entity, count);
        entity.discard();
        event.setCanPickup(TriState.FALSE);
    }

    // ---------------------------------------------------------------- matter out

    /** Matter needed for {@code count} blocks at {@code index}. */
    public static Matter cost(int index, int count) {
        return styleAt(index).cost.times(count);
    }

    /** How many blocks at {@code index} the stored matter pays for, at most {@code max}. */
    public static int affordable(ItemStack pocket, int index, int max) {
        Matter cost = styleAt(index).cost;
        Matter m = matter(pocket);
        int n = max;
        if (cost.rustic() > 0) n = Math.min(n, m.rustic() / cost.rustic());
        if (cost.refined() > 0) n = Math.min(n, m.refined() / cost.refined());
        return Math.max(0, n);
    }

    private static void pay(ItemStack pocket, int index, int count) {
        Matter m = matter(pocket);
        Matter c = cost(index, count);
        setMatter(pocket, new Matter(m.rustic() - c.rustic(), m.refined() - c.refined()));
    }

    /**
     * Puts up to {@code count} blocks at {@code index} into the player's inventory and pays for the ones that fit.
     * Returns how many were given, or {@link #TAKE_FULL}, {@link #TAKE_NO_MATTER}, {@link #TAKE_LOCKED}.
     */
    public int take(Player player, ItemStack pocket, int index, int count) {
        if (!validIndex(index) || !makes(styleAt(index))) return TAKE_LOCKED;
        int n = affordable(pocket, index, Math.min(count, 64));
        if (n <= 0) return TAKE_NO_MATTER;
        ItemStack give = new ItemStack(blockAt(index), n);
        player.getInventory().add(give);
        int given = n - give.getCount();
        if (given > 0) pay(pocket, index, given);
        return given;
    }

    /** Message for a {@link #take} or placement that gave nothing. */
    public static Component refusal(int result, int index, ItemStack pocket) {
        if (result == TAKE_LOCKED) return Component.translatable("message.robotica.pocket_architect_locked");
        if (result == TAKE_FULL) return Component.translatable("message.robotica.pocket_architect_full");
        Matter cost = styleAt(index).cost;
        boolean refined = cost.refined() > 0 && matter(pocket).refined() < cost.refined();
        return Component.translatable(refined ? "message.robotica.pocket_architect_no_refined" : "message.robotica.pocket_architect_no_rustic");
    }

    /** Moves as much stored matter as the table can hold into it. Returns what moved. */
    public static Matter moveToTable(ItemStack pocket, ArchitectTableBlockEntity table) {
        Matter m = matter(pocket);
        Matter t = table.matter();
        int cap = ArchitectConfig.matterCap();
        int rustic = Math.max(0, Math.min(m.rustic(), cap - t.rustic()));
        int refined = Math.max(0, Math.min(m.refined(), cap - t.refined()));
        if (rustic == 0 && refined == 0) return Matter.ZERO;
        table.setMatter(new Matter(t.rustic() + rustic, t.refined() + refined));
        setMatter(pocket, new Matter(m.rustic() - rustic, m.refined() - refined));
        return new Matter(rustic, refined);
    }

    // ---------------------------------------------------------------- use

    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PocketArchitectItem)) return;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PocketMenu(id, inv, hand), stack.getHoverName()),
                buf -> buf.writeEnum(hand));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // with a block picked, right-click in the air does nothing, so a missed block never opens the GUI
        if (!player.isSecondaryUseActive() && validIndex(settings(stack).selected())) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer) open(serverPlayer, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack pocket = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            if (level.getBlockEntity(context.getClickedPos()) instanceof ArchitectTableBlockEntity table) {
                if (!level.isClientSide) fillTable(player, pocket, table);
            } else if (player instanceof ServerPlayer serverPlayer) {
                open(serverPlayer, context.getHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int index = settings(pocket).selected();
        if (!validIndex(index)) return InteractionResult.PASS;
        return place(context, index);
    }

    private static void fillTable(Player player, ItemStack pocket, ArchitectTableBlockEntity table) {
        if (!table.canUse(player)) {
            player.displayClientMessage(Component.translatable("message.robotica.architect_not_owner", table.ownerName()), true);
            return;
        }
        Matter moved = moveToTable(pocket, table);
        if (moved.isZero()) {
            player.displayClientMessage(Component.translatable(matter(pocket).isZero()
                    ? "message.robotica.pocket_architect_empty" : "message.robotica.pocket_architect_table_full"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.robotica.pocket_architect_moved", moved.rustic(), moved.refined()), true);
        CoreSounds.play(table.getLevel(), table.getBlockPos(), CoreSounds.MATTER_ABSORB, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    /** Places the picked block like a block item would and pays for it. Runs on both sides; the client predicts it. */
    private InteractionResult place(UseOnContext context, int index) {
        Player player = context.getPlayer();
        ItemStack pocket = context.getItemInHand();
        int refused = !makes(styleAt(index)) ? TAKE_LOCKED : affordable(pocket, index, 1) < 1 ? TAKE_NO_MATTER : 1;
        if (refused < 1) {
            if (player != null && !context.getLevel().isClientSide) player.displayClientMessage(refusal(refused, index, pocket), true);
            return InteractionResult.FAIL;
        }
        BlockItem block = blockAt(index);
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        InteractionResult result = block.place(new BlockPlaceContext(context.getLevel(), player, context.getHand(), new ItemStack(block), hit));
        if (result.consumesAction() && !context.getLevel().isClientSide) pay(pocket, index, 1);
        return result;
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(mk2 ? "tooltip.robotica.pocket_architect_mk2" : "tooltip.robotica.pocket_architect").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.robotica.pocket_architect_use").withStyle(ChatFormatting.DARK_GRAY));
        Matter m = matter(stack);
        tooltip.add(Component.translatable("tooltip.robotica.pocket_architect_matter", Fmt.compact(m.rustic()),
                Component.translatable(Matter.Grade.RUSTIC.langKey())).withStyle(ChatFormatting.DARK_AQUA));
        if (mk2 || m.refined() > 0) {
            tooltip.add(Component.translatable("tooltip.robotica.pocket_architect_matter", Fmt.compact(m.refined()),
                    Component.translatable(Matter.Grade.REFINED.langKey())).withStyle(ChatFormatting.DARK_AQUA));
        }
        int selected = settings(stack).selected();
        if (validIndex(selected)) {
            tooltip.add(Component.translatable("tooltip.robotica.pocket_architect_places", blockAt(selected).getDescription()).withStyle(ChatFormatting.GOLD));
        }
    }

    @Nullable
    public static PocketArchitectItem of(ItemStack stack) {
        return stack.getItem() instanceof PocketArchitectItem item ? item : null;
    }
}
