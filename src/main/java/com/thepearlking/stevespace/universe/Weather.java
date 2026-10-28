package com.thepearlking.stevespace.universe;

/**
 * Weather that server and client agree on, worked out from the universe clock alone: Mars' dust
 * storms. A storm blows up every so often, lasts a while and dies down; while it does, the air is
 * thick with dust and an easterly wind pushes at anything standing or flying low.
 */
public final class Weather {

    /** Seconds between the starts of two storms, and how long one lasts. */
    private static final double STORM_PERIOD = 2400;
    private static final double STORM_LENGTH = 600;

    private Weather() {
    }

    /** Strength of the dust storm on Mars at time {@code t} (0 calm .. 1 at its height). */
    public static double marsStorm(double t) {
        double phase = ((t % STORM_PERIOD) + STORM_PERIOD) % STORM_PERIOD;
        if (phase > STORM_LENGTH) {
            return 0;
        }
        double x = phase / STORM_LENGTH;
        // Builds over the first fifth, rages, fades over the last fifth.
        double up = Math.min(1, x / 0.2);
        double down = Math.min(1, (1 - x) / 0.2);
        double s = Math.min(up, down);
        return s * s * (3 - 2 * s);
    }

    /** The storm's wind, blocks per second toward the east (+x). */
    public static double marsWind(double t) {
        return marsStorm(t) * 18;
    }
}
