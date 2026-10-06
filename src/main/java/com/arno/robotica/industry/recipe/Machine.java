package com.arno.robotica.industry.recipe;

import com.arno.robotica.industry.IndustryConfig;

import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The processing machines of the industry module. Each has its own recipe type {@code robotica:<recipeId>}
 * (see {@link ProcessingRecipe}), a fixed number of input and output slots, a base FE/t from the server config, a
 * default recipe time and the age of its Mk1. Every machine comes in Mk1-Mk4 (see {@link #TIERS}).
 */
public enum Machine {
    ALLOY_SMELTER("alloying", 3, 1, IndustryConfig::alloySmelterPower, 200, 1),
    CENTRIFUGE("centrifuging", 1, 4, IndustryConfig::centrifugePower, 300, 2),
    ASSEMBLER("assembling", 6, 1, IndustryConfig::assemblerPower, 400, 2);

    public static final int TIERS = 4;

    /** Recipe type and serializer path, e.g. {@code alloying}. */
    public final String recipeId;
    public final int inputs;
    public final int outputs;
    private final IntSupplier power;
    public final int defaultTime;
    /** Age of the Mk1; each higher Mk is one age later (capped at Age 4). */
    public final int baseAge;

    Machine(String recipeId, int inputs, int outputs, IntSupplier power, int defaultTime, int baseAge) {
        this.recipeId = recipeId;
        this.inputs = inputs;
        this.outputs = outputs;
        this.power = power;
        this.defaultTime = defaultTime;
        this.baseAge = baseAge;
    }

    /** Base name, e.g. {@code alloy_smelter}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Block and item registry name of a tier, e.g. {@code alloy_smelter_mk2}. */
    public String id(int tier) {
        return id() + "_mk" + tier;
    }

    /** Base FE/t from the server config (before tier, cards and the global energy multiplier). */
    public int basePower() {
        return power.getAsInt();
    }

    public int slots() {
        return inputs + outputs;
    }

    public int age(int tier) {
        return Math.min(4, baseAge + tier - 1);
    }
}
