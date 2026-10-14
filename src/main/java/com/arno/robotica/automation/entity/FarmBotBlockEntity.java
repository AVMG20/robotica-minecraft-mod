package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.FarmBotBlock;
import com.arno.robotica.automation.block.StumpyBlock;
import com.arno.robotica.automation.farmbot.FarmBot;
import com.arno.robotica.automation.farmbot.FarmBotContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Data of a Stumpy or Sprout block from before the robots walked. Holds the saved tag until the first server tick, then
 * {@link #convert} spawns the walking robot with it and removes the block.
 */
public class FarmBotBlockEntity extends BlockEntity {
    /** Keys of the old area worker data that the robot takes over. */
    private static final List<String> KEYS = List.of("owner", "energy", "battery", "upgrades", "buffer", "pending");

    private CompoundTag saved = new CompoundTag();
    private boolean converted;

    public FarmBotBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
    }

    private static BlockEntityType<?> typeFor(BlockState state) {
        return state.getBlock() instanceof StumpyBlock ? AutomationContent.STUMPY_BE.get() : AutomationContent.SPROUT_BE.get();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        saved = new CompoundTag();
        for (String key : KEYS) {
            if (tag.contains(key)) saved.put(key, tag.get(key).copy());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (String key : saved.getAllKeys()) tag.put(key, saved.get(key).copy());
    }

    /** Spawns the walking robot in the block's place, facing the same way, and removes the block. */
    public void convert(ServerLevel sl) {
        if (converted || isRemoved()) return;
        BlockState state = getBlockState();
        EntityType<? extends FarmBot> type = state.getBlock() instanceof StumpyBlock
                ? FarmBotContent.STUMPY_ENTITY.get() : FarmBotContent.SPROUT_ENTITY.get();
        FarmBot bot = type.create(sl);
        if (bot == null) return;
        converted = true;
        float yaw = state.hasProperty(FarmBotBlock.FACING) ? state.getValue(FarmBotBlock.FACING).toYRot() : Direction.SOUTH.toYRot();
        BlockPos pos = worldPosition;
        bot.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        bot.setYBodyRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setHome(pos);
        bot.loadFromBlock(saved, state.hasProperty(FarmBotBlock.TIER) ? state.getValue(FarmBotBlock.TIER) : 1, sl.registryAccess());
        sl.removeBlock(pos, false);
        sl.addFreshEntity(bot);
    }

    /** Broken before it turned into a robot: the robot item of its Mk drops with its battery, cards and buffer (the block has no loot). */
    public void dropContents() {
        if (converted || level == null || level.isClientSide) return;
        converted = true;
        HolderLookup.Provider registries = level.registryAccess();
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (String key : List.of("battery", "upgrades", "buffer")) {
            if (!saved.contains(key)) continue;
            ItemStackHandler items = new ItemStackHandler();
            items.deserializeNBT(registries, saved.getCompound(key));
            for (int i = 0; i < items.getSlots(); i++) Containers.dropItemStack(level, x, y, z, items.getStackInSlot(i));
        }
        for (Tag t : saved.getList("pending", Tag.TAG_COMPOUND)) ItemStack.parse(registries, t).ifPresent(s -> Containers.dropItemStack(level, x, y, z, s));
        int tier = getBlockState().hasProperty(FarmBotBlock.TIER) ? getBlockState().getValue(FarmBotBlock.TIER) : 1;
        var items = getBlockState().getBlock() instanceof StumpyBlock ? FarmBotContent.STUMPY_ITEMS : FarmBotContent.SPROUT_ITEMS;
        Containers.dropItemStack(level, x, y, z, new ItemStack(FarmBotContent.item(items, tier)));
    }
}
