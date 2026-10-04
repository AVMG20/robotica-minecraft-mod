package com.arno.robotica.core.util;

import java.util.Locale;

/** Number formatting for tooltips and GUIs. */
public final class Fmt {
    private Fmt() {}

    /** 950 → "950 FE", 2304000 → "2.30M FE". */
    public static String energy(long fe) {
        return compact(fe) + " FE";
    }

    public static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fG", n / 1e9);
        if (n >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", n / 1e6);
        if (n >= 10_000L) return String.format(Locale.ROOT, "%.1fk", n / 1e3);
        return Long.toString(n);
    }

    /** Ticks to "1h 42m" / "3m 10s". */
    public static String duration(long ticks) {
        long s = ticks / 20;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
