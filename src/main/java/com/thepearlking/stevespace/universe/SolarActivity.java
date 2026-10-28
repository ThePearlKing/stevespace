package com.thepearlking.stevespace.universe;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sun's weather, worked out from the time alone (so server and client agree without sending
 * anything): an activity cycle, sunspot groups that rotate with the Sun and grow and fade, flares
 * from those groups, and the big ones throwing out a cloud of plasma (a CME) that crosses the
 * solar system at hundreds of kilometres a second and hits whatever is in its way.
 */
public final class SolarActivity {

    /** Length of one activity cycle, universe seconds (the real one is 11 years; here about a month). */
    public static final double CYCLE = 30 * 86400;
    /** One flare "slot" per this many seconds: each slot may hold a flare (about one an hour at maximum). */
    private static final double SLOT = 1800;
    /** How long a sunspot group lives, in seconds. */
    private static final double SPOT_LIFE = 12 * 86400;
    /** New groups appear about this often at solar maximum, in seconds. */
    private static final double SPOT_EVERY = 1.5 * 86400;

    private SolarActivity() {
    }

    /** One sunspot group: where on the Sun (latitude, Carrington longitude, radians) and how big now (0..1). */
    public record Spot(double latitude, double longitude, double size, long id) {
    }

    /**
     * A flare: when it peaks, how strong (log scale: 1 = C, 2 = M, 3 = X), from which group, and
     * for the big ones the CME's speed (m/s) and direction (unit, ecliptic), else speed 0.
     */
    public record Flare(double peak, double strength, Spot from, double cmeSpeed, DVec3 cmeDirection) {

        /** Brightness of the flash at time t (0..1): a sharp rise, a slower fall. */
        public double flash(double t) {
            double dt = t - peak;
            if (dt < -300 || dt > 3600) {
                return 0;
            }
            return dt < 0 ? Math.exp(dt / 90) : Math.exp(-dt / 900);
        }

        public String className() {
            return strength >= 3 ? "X" : strength >= 2 ? "M" : "C";
        }
    }

    /** 0 at solar minimum, 1 at maximum. */
    public static double cycle(double t) {
        double phase = t / CYCLE * 2 * Math.PI;
        double s = 0.5 - 0.5 * Math.cos(phase);
        return s * s * (3 - 2 * s);
    }

    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    private static double rand(long seed, int k) {
        return (mix(seed * 31 + k) >>> 11) * 0x1.0p-53;
    }

    /** The groups on the Sun at time t (with their longitudes as they are then: the Sun turns). */
    public static List<Spot> spots(double t) {
        List<Spot> spots = new ArrayList<>();
        long first = (long) Math.floor((t - SPOT_LIFE) / SPOT_EVERY);
        long last = (long) Math.floor(t / SPOT_EVERY);
        double rotation = 2 * Math.PI / (25.4 * 86400); // the Sun turns in about 25 days
        for (long i = first; i <= last; i++) {
            double born = i * SPOT_EVERY + rand(i, 0) * SPOT_EVERY;
            double age = (t - born) / SPOT_LIFE;
            if (age < 0 || age > 1) {
                continue;
            }
            double activity = cycle(born);
            if (rand(i, 1) > 0.15 + 0.85 * activity) {
                continue; // fewer groups near minimum
            }
            // Spots appear in two belts either side of the equator, closer in late in the cycle.
            double belt = Math.toRadians(8 + 22 * (1 - activity));
            double latitude = (rand(i, 2) < 0.5 ? -1 : 1) * (belt + (rand(i, 3) - 0.5) * Math.toRadians(10));
            double longitude = rand(i, 4) * 2 * Math.PI + (t - born) * rotation;
            double size = Math.sin(Math.PI * Math.min(1, age * 1.4)) * (0.3 + 0.7 * rand(i, 5)) * (0.4 + 0.6 * activity);
            spots.add(new Spot(latitude, longitude % (2 * Math.PI), size, i));
        }
        return spots;
    }

    /** Flares that peak within {@code window} seconds before t (newest last). */
    public static List<Flare> flares(double t, double window) {
        List<Flare> found = new ArrayList<>();
        long first = (long) Math.floor((t - window) / SLOT);
        long last = (long) Math.floor(t / SLOT);
        for (long k = first; k <= last; k++) {
            Flare f = flareInSlot(k);
            if (f != null && f.peak() <= t && f.peak() > t - window) {
                found.add(f);
            }
        }
        return found;
    }

    /** The flare in one slot, or null. Stronger ones are rarer; the big ones launch a CME. */
    static Flare flareInSlot(long k) {
        double peak = k * SLOT + rand(k, 10) * SLOT;
        double activity = cycle(peak);
        if (rand(k, 11) > 0.08 + 0.55 * activity) {
            return null;
        }
        List<Spot> spots = spots(peak);
        if (spots.isEmpty()) {
            return null;
        }
        Spot from = spots.get((int) (rand(k, 12) * spots.size()));
        // Power law: mostly C, some M, a few X.
        double strength = 1 + 2.4 * Math.pow(rand(k, 13), 2.2) * (0.5 + from.size());
        double speed = 0;
        DVec3 direction = new DVec3(1, 0, 0);
        if (strength >= 2.2 || rand(k, 14) < 0.1) {
            speed = 350_000 + 1_500_000 * Math.pow(rand(k, 15), 2) * (strength - 1) / 2;
            // Roughly where the group faces, give or take: the cloud spreads over ~60 degrees.
            double lon = from.longitude() + (rand(k, 16) - 0.5) * 0.6;
            double lat = from.latitude() * 0.5;
            direction = new DVec3(Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat));
        }
        return new Flare(peak, strength, from, speed, direction);
    }

    /** How hard a storm is hitting a point (ecliptic position from the Sun) at time t: 0 calm .. ~3 severe. */
    public static double storm(DVec3 position, double t) {
        double r = position.length();
        if (r < 1) {
            return 0;
        }
        DVec3 dir = position.scale(1 / r);
        double total = 0;
        // CMEs take hours to days to arrive: look back far enough for the slowest to have got here.
        double lookBack = r / 300_000 + 2 * 86400;
        for (Flare f : flares(t, lookBack)) {
            if (f.cmeSpeed() <= 0) {
                continue;
            }
            double cos = dir.dot(f.cmeDirection());
            if (cos < 0.5) {
                continue; // outside the cloud's ~60 degree cone
            }
            double front = (t - f.peak()) * f.cmeSpeed();
            double thickness = 0.15 * r + 2e9;
            double into = front - r;
            if (into < 0 || into > thickness * 3) {
                continue;
            }
            double shape = into < thickness ? into / thickness : Math.exp(-(into - thickness) / thickness);
            double spread = (cos - 0.5) / 0.5;
            // Weaker with distance (the cloud spreads out), stronger for fast, strong eruptions.
            double au = r / SolarSystem.AU;
            total += shape * spread * (f.strength() - 1) * (f.cmeSpeed() / 600_000) / Math.max(0.3, au);
        }
        return Math.min(3, total);
    }
}
