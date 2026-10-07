package com.arno.robotica.core.item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Translation arguments (config numbers) for the Shift details lang key {@code tooltip.robotica.<item>.details}.
 * Modules register them in init; a details line without arguments is shown as it is.
 */
public final class DetailArgs {
    private DetailArgs() {}

    private static final Map<String, Supplier<Object[]>> ARGS = new ConcurrentHashMap<>();

    /** {@code item} is the item's registry path, e.g. "survey_rig". */
    public static void register(String item, Supplier<Object[]> args) {
        ARGS.put(item, args);
    }

    public static Object[] get(String item) {
        Supplier<Object[]> args = ARGS.get(item);
        return args == null ? new Object[0] : args.get();
    }
}
