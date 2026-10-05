package com.arno.robotica.drones.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.List;

/** One courier route: take items out of {@code source} (through {@code sourceFace}) and put them into {@code target}, in one dimension. */
public record CourierRoute(String dim, BlockPos source, Direction sourceFace, BlockPos target, Direction targetFace) {
    public static final int MAX_ROUTES = 4;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Dim", dim);
        t.putLong("Source", source.asLong());
        t.putInt("SourceFace", sourceFace.get3DDataValue());
        t.putLong("Target", target.asLong());
        t.putInt("TargetFace", targetFace.get3DDataValue());
        return t;
    }

    public static CourierRoute load(CompoundTag t) {
        return new CourierRoute(t.getString("Dim"), BlockPos.of(t.getLong("Source")), Direction.from3DDataValue(t.getInt("SourceFace")),
                BlockPos.of(t.getLong("Target")), Direction.from3DDataValue(t.getInt("TargetFace")));
    }

    public static List<CourierRoute> loadList(CompoundTag tag) {
        List<CourierRoute> list = new ArrayList<>();
        for (Tag entry : tag.getList("Routes", Tag.TAG_COMPOUND)) {
            if (list.size() < MAX_ROUTES && entry instanceof CompoundTag c) list.add(load(c));
        }
        return list;
    }

    public static void saveList(CompoundTag tag, List<CourierRoute> routes) {
        ListTag list = new ListTag();
        for (CourierRoute r : routes) list.add(r.save());
        tag.put("Routes", list);
    }

    /**
     * Handles one sneak-click of the linking tools on an inventory. {@code holder} stores the pending source, {@code routes} is
     * changed in place. Returns the feedback message.
     */
    public static Component click(CompoundTag holder, List<CourierRoute> routes, ServerLevel level, BlockPos pos, Direction face) {
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face) == null) {
            return Component.translatable("message.robotica.courier.not_inventory");
        }
        String dim = level.dimension().location().toString();
        if (holder.contains("Pending") && holder.getCompound("Pending").getString("Dim").equals(dim)) {
            CompoundTag p = holder.getCompound("Pending");
            BlockPos source = BlockPos.of(p.getLong("Pos"));
            Direction sourceFace = Direction.from3DDataValue(p.getInt("Face"));
            if (source.equals(pos)) return Component.translatable("message.robotica.courier.same");
            if (routes.size() >= MAX_ROUTES) {
                holder.remove("Pending");
                return Component.translatable("message.robotica.courier.full");
            }
            routes.add(new CourierRoute(dim, source, sourceFace, pos.immutable(), face));
            holder.remove("Pending");
            return Component.translatable("message.robotica.courier.linked", routes.size());
        }
        CompoundTag p = new CompoundTag();
        p.putString("Dim", dim);
        p.putLong("Pos", pos.asLong());
        p.putInt("Face", face.get3DDataValue());
        holder.put("Pending", p);
        return Component.translatable("message.robotica.courier.source", pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }
}
