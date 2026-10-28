package com.thepearlking.stevespace.universe;

/**
 * Maps the real sun to Minecraft's day clock, so vanilla light levels, mob spawning, beds and
 * clocks behave as if the vanilla sun were where the real sun is.
 *
 * <p>Vanilla brightness depends on cos(2π·timeOfDay), where timeOfDay 0 means the sun is
 * overhead. Setting cos(2π·timeOfDay) = sin(real elevation) gives the same brightness curve:
 * full daylight above ~15°, full night below ~-15°.
 */
public final class VanillaTime {

    public static final int TICKS_PER_DAY = 24000;

    private VanillaTime() {
    }

    /**
     * Position within the vanilla day (0..1, 0.25 = noon, 0.75 = midnight) for a sun at
     * {@code elevation} radians that is currently rising or setting.
     */
    public static double dayFraction(double elevation, boolean rising) {
        double timeOfDay = Math.acos(Math.max(-1, Math.min(1, Math.sin(elevation)))) / (2 * Math.PI);
        if (rising) {
            timeOfDay = 1 - timeOfDay;
        }
        // Invert vanilla's DimensionType.timeOfDay easing by bisection (it is monotonic).
        double low = 0;
        double high = 1;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (easedTimeOfDay(mid) < timeOfDay) {
                low = mid;
            } else {
                high = mid;
            }
        }
        double fraction = (low + high) / 2 + 0.25;
        return fraction >= 1 ? fraction - 1 : fraction;
    }

    /** Vanilla's easing from the raw day phase to the sun angle. */
    private static double easedTimeOfDay(double phase) {
        double eased = 0.5 - Math.cos(phase * Math.PI) / 2;
        return (phase * 2 + eased) / 3;
    }

    /**
     * Next vanilla dayTime value: keeps the day counter of {@code previous} and moves to
     * {@code fraction}, counting a new day when the clock passes midnight-to-morning wrap.
     */
    public static long advance(long previous, double fraction) {
        long day = Math.floorDiv(previous, TICKS_PER_DAY);
        long within = Math.floorMod(previous, TICKS_PER_DAY);
        long target = Math.round(fraction * TICKS_PER_DAY) % TICKS_PER_DAY;
        if (target < within - TICKS_PER_DAY / 2) {
            day++;
        } else if (target > within + TICKS_PER_DAY / 2 && day > 0) {
            // Tiny backwards jitter across the wrap: stay on the same day.
            day--;
        }
        return day * TICKS_PER_DAY + target;
    }
}
