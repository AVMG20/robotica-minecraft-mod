package com.arno.robotica.warp.gate;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Which Gate Controller is linked to which, across dimensions. Stored on the overworld so a link survives while one of the
 * two gates is unloaded or in another dimension. Controllers keep a copy in their block entity; this is the source of truth.
 */
public class GateLinks extends SavedData {
    public static final String NAME = "robotica_gate_links";
    public static final SavedData.Factory<GateLinks> FACTORY = new SavedData.Factory<>(GateLinks::new, GateLinks::load, null);

    private final Map<GlobalPos, GlobalPos> links = new HashMap<>();

    public GateLinks() {}

    public static GateLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static GateLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        GateLinks data = new GateLinks();
        for (Tag entry : tag.getList("links", Tag.TAG_COMPOUND)) {
            CompoundTag pair = (CompoundTag) entry;
            GlobalPos a = GlobalPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, pair.get("a")).result().orElse(null);
            GlobalPos b = GlobalPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, pair.get("b")).result().orElse(null);
            if (a != null && b != null) {
                data.links.put(a, b);
                data.links.put(b, a);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, GlobalPos> e : links.entrySet()) {
            // every pair is stored once: the smaller side first
            if (compare(e.getKey(), e.getValue()) > 0) continue;
            CompoundTag pair = new CompoundTag();
            pair.put("a", GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, e.getKey()).getOrThrow());
            pair.put("b", GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, e.getValue()).getOrThrow());
            list.add(pair);
        }
        tag.put("links", list);
        return tag;
    }

    private static int compare(GlobalPos a, GlobalPos b) {
        int c = a.dimension().location().compareTo(b.dimension().location());
        return c != 0 ? c : Long.compare(a.pos().asLong(), b.pos().asLong());
    }

    @Nullable
    public GlobalPos partner(GlobalPos gate) {
        return links.get(gate);
    }

    /** Links both ways. Both gates lose their old partner first. */
    public void link(GlobalPos a, GlobalPos b) {
        unlink(a);
        unlink(b);
        links.put(a, b);
        links.put(b, a);
        setDirty();
    }

    /** Removes the link of a gate (and the partner's side). Returns the former partner. */
    @Nullable
    public GlobalPos unlink(GlobalPos gate) {
        GlobalPos other = links.remove(gate);
        if (other != null) {
            if (gate.equals(links.get(other))) links.remove(other);
            setDirty();
        }
        return other;
    }

    public int size() {
        return links.size() / 2;
    }
}
