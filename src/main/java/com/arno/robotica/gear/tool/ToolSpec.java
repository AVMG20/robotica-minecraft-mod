package com.arno.robotica.gear.tool;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/** Static description of a mining tool: harvest tier, effective block tags, modes, toggles and costs. */
public final class ToolSpec {
    public final Tier tier;
    public final float speed;
    public final List<TagKey<Block>> tags;
    public final List<AreaMode> modes;
    /** 0 = durability based (vanilla damage), otherwise FE capacity. */
    public final int energyCapacity;
    public final IntSupplier costPerBlock;
    public final int maxLogs;
    public final Set<ToggleKind> toggles;
    /** Fortune level used by the silk/fortune swap, 0 = no swap. */
    public final int fortuneLevel;
    /** The hammer mines slower in area modes. */
    public final boolean slowArea;
    public final int age;

    private ToolSpec(Builder b) {
        this.tier = b.tier;
        this.speed = b.speed;
        this.tags = List.copyOf(b.tags);
        this.modes = List.copyOf(b.modes);
        this.energyCapacity = b.energyCapacity;
        this.costPerBlock = b.cost;
        this.maxLogs = b.maxLogs;
        this.toggles = b.toggles.isEmpty() ? EnumSet.noneOf(ToggleKind.class) : EnumSet.copyOf(b.toggles);
        this.fortuneLevel = b.fortuneLevel;
        this.slowArea = b.slowArea;
        this.age = b.age;
    }

    public boolean isEnergy() {
        return energyCapacity > 0;
    }

    public boolean hasMode(AreaMode mode) {
        return modes.contains(mode);
    }

    public boolean isAxe() {
        return tags.contains(BlockTags.MINEABLE_WITH_AXE);
    }

    /** Vanilla Tool component: tier speed on the tagged blocks, tier harvest level, no vanilla durability use for FE tools. */
    public Tool toolComponent() {
        List<Tool.Rule> rules = new ArrayList<>();
        rules.add(Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops()));
        for (TagKey<Block> tag : tags) {
            rules.add(Tool.Rule.minesAndDrops(tag, speed));
        }
        return new Tool(rules, 1.0F, isEnergy() ? 0 : 1);
    }

    public static Builder builder(Tier tier, float speed) {
        return new Builder(tier, speed);
    }

    public static final class Builder {
        private final Tier tier;
        private final float speed;
        private final List<TagKey<Block>> tags = new ArrayList<>();
        private final List<AreaMode> modes = new ArrayList<>(List.of(AreaMode.SINGLE));
        private int energyCapacity;
        private IntSupplier cost = () -> 0;
        private int maxLogs = 64;
        private final Set<ToggleKind> toggles = EnumSet.noneOf(ToggleKind.class);
        private int fortuneLevel;
        private boolean slowArea;
        private int age;

        private Builder(Tier tier, float speed) {
            this.tier = tier;
            this.speed = speed;
        }

        @SafeVarargs
        public final Builder tags(TagKey<Block>... t) {
            tags.addAll(List.of(t));
            return this;
        }

        public Builder modes(AreaMode... m) {
            modes.clear();
            modes.addAll(List.of(m));
            return this;
        }

        public Builder energy(int capacity, IntSupplier costPerBlock) {
            this.energyCapacity = capacity;
            this.cost = costPerBlock;
            return this;
        }

        public Builder maxLogs(int n) {
            this.maxLogs = n;
            return this;
        }

        public Builder toggles(ToggleKind... t) {
            toggles.addAll(List.of(t));
            return this;
        }

        public Builder fortune(int level) {
            this.fortuneLevel = level;
            return this;
        }

        public Builder slowArea() {
            this.slowArea = true;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public ToolSpec build() {
            if (tags.isEmpty()) throw new IllegalStateException("tool needs at least one effective tag");
            return new ToolSpec(this);
        }
    }
}
