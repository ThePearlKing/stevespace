package com.thepearlking.stevespace.client.gui;

import java.util.Locale;

/** Number formats shared by the flight HUD and the map. */
public final class FlightText {

    private FlightText() {
    }

    public static String distance(double blocks) {
        if (Math.abs(blocks) >= 100_000) {
            return String.format(Locale.ROOT, "%,.0f km", blocks / 1000);
        }
        return String.format(Locale.ROOT, "%,.0f m", blocks);
    }

    public static String speed(double value) {
        return String.format(Locale.ROOT, "%,.0f m/s", value);
    }

    public static String signedSpeed(double value) {
        return String.format(Locale.ROOT, "%+,.0f m/s", value);
    }

    /** Seconds as m:ss, h:mm:ss or days. */
    public static String duration(double seconds) {
        if (!Double.isFinite(seconds)) {
            return "—";
        }
        long s = Math.max(0, Math.round(seconds));
        if (s >= 86_400 * 2) {
            return String.format(Locale.ROOT, "%.1f d", s / 86_400.0);
        }
        if (s >= 3600) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
