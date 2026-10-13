package com.arno.robotica.core;

import com.arno.robotica.Robotica;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Version of every Robotica payload channel: the mod version plus a hash of our block state layout. Chunks travel with
 * numeric block state ids that each side numbers from its own blocks, so a server and client whose blocks have
 * different states (0.8.1 gave the Tinker's Bench a facing) show every later block shifted onto another one. NeoForge
 * turns away a connection whose channel versions differ, so such a pair never joins.
 */
public final class NetworkVersion {
    private NetworkVersion() {}

    private static String version;

    /** Read when the payloads register, after the block registry is frozen. */
    public static String get() {
        if (version == null) {
            String mod = ModList.get().getModContainerById(Robotica.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("dev");
            version = mod + "-" + Long.toHexString(hash(layout()));
        }
        return version;
    }

    /** One line per Robotica block in registry order: its id and each property with its values, e.g. {@code robotica:x facing=north,south}. */
    public static List<String> layout() {
        List<String> lines = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Robotica.MODID)) continue;
            StringBuilder line = new StringBuilder(id.toString());
            for (Property<?> property : block.getStateDefinition().getProperties()) line.append(' ').append(describe(property));
            lines.add(line.toString());
        }
        return lines;
    }

    private static <T extends Comparable<T>> String describe(Property<T> property) {
        StringBuilder s = new StringBuilder(property.getName()).append('=');
        boolean first = true;
        for (T value : property.getPossibleValues()) {
            if (!first) s.append(',');
            s.append(property.getName(value));
            first = false;
        }
        return s.toString();
    }

    public static long hash(List<String> layout) {
        CRC32 crc = new CRC32();
        for (String line : layout) {
            crc.update(line.getBytes(StandardCharsets.UTF_8));
            crc.update('\n');
        }
        return crc.getValue();
    }
}
