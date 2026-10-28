package com.thepearlking.stevespace.client.cloud;

/**
 * The wind aloft over a world, the same for every cloud and trail in it: a direction that veers
 * with height and a speed that picks up towards a jet stream high up. In real metres per second;
 * callers scale it to their own size.
 */
public final class Winds {

    private Winds() {
    }

    /** East and south wind (m/s) at {@code km} above the ground in the world with this seed. */
    public static double[] at(double km, long seed) {
        double turn = (seed % 1000) / 1000.0 * Math.PI * 2;
        double angle = turn + 1.6 * Math.sin(km / 15 + turn) + 0.7 * Math.sin(km / 5.7 + 2 * turn);
        double speed = 5 + 14 * Math.min(1, km / 9) + 22 * Math.exp(-Math.pow((km - 11) / 4, 2)) - 10 * Math.min(1, Math.max(0, (km - 20) / 15));
        return new double[]{Math.cos(angle) * speed, Math.sin(angle) * speed};
    }
}
