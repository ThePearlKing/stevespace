package com.thepearlking.stevespace.world.planet;

/**
 * Impact craters scattered over a sphere: a bowl, a raised rim, ejecta fading outward, a central
 * peak in the big ones. Craters sit in a 3D cell grid around the planet (at most one per cell),
 * so they are round everywhere, poles included, and need no storage.
 */
public final class CraterField {

    /** One crater size class: cell size, radius range and how many cells hold a crater. */
    public record Scale(double cell, double minRadius, double maxRadius, double chance, long salt) {
    }

    /** Result of a lookup: height change, and how bright the fresh ejecta around young craters is (0..1). */
    public static final class Sample {
        public double height;
        public double fresh;
    }

    private final double radius;
    private final Scale[] scales;
    private final long seed;
    /** 1 = deep, sharp craters (airless Moon); lower = worn down and partly filled (Mars). */
    private final double sharpness;

    public CraterField(double radius, long seed, double sharpness, Scale... scales) {
        this.radius = radius;
        this.seed = seed;
        this.sharpness = sharpness;
        this.scales = scales;
    }

    /**
     * Adds up the craters at the surface point (px, py, pz) (a unit-sphere direction scaled by the
     * planet radius). {@code amount} thins them out (0 none, 1 all).
     */
    public Sample sample(double px, double py, double pz, double amount, Sample out) {
        out.height = 0;
        out.fresh = 0;
        for (Scale s : scales) {
            add(s, px, py, pz, amount, out);
        }
        return out;
    }

    private void add(Scale s, double px, double py, double pz, double amount, Sample out) {
        int cx = (int) Math.floor(px / s.cell());
        int cy = (int) Math.floor(py / s.cell());
        int cz = (int) Math.floor(pz / s.cell());
        for (int i = cx - 1; i <= cx + 1; i++) {
            for (int j = cy - 1; j <= cy + 1; j++) {
                for (int k = cz - 1; k <= cz + 1; k++) {
                    long h = hash(i, j, k, s.salt() ^ seed);
                    if (unit(h) > s.chance() * amount) {
                        continue;
                    }
                    double ox = (i + unit(h * 31 + 1)) * s.cell();
                    double oy = (j + unit(h * 31 + 2)) * s.cell();
                    double oz = (k + unit(h * 31 + 3)) * s.cell();
                    double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
                    if (len < 1e-6 || Math.abs(len - radius) > s.cell()) {
                        continue; // this cell's crater would be deep inside or far above the planet
                    }
                    double sx = ox / len * radius;
                    double sy = oy / len * radius;
                    double sz = oz / len * radius;
                    // Small craters are common, big ones rare.
                    double size = Math.pow(unit(h * 31 + 4), 2.2);
                    double r = s.minRadius() + (s.maxRadius() - s.minRadius()) * size;
                    double dx = px - sx;
                    double dy = py - sy;
                    double dz = pz - sz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
                    if (d > 2.2) {
                        continue;
                    }
                    double age = unit(h * 31 + 5); // 0 = fresh and sharp, 1 = old and worn
                    double depth = r * (0.26 - 0.12 * age) * sharpness;
                    double rim = r * (0.06 - 0.03 * age) * sharpness;
                    out.height += profile(d, depth, rim, r);
                    if (age < 0.25 && d < 2.0) {
                        out.fresh = Math.max(out.fresh, (1 - age / 0.25) * (1 - Math.max(0, d - 1)) * 0.7);
                    }
                }
            }
        }
    }

    /** Crater cross-section: a bowl, a raised rim, ejecta fading outward, a peak in big ones. */
    private static double profile(double d, double depth, double rim, double radius) {
        if (d < 1) {
            double bowl = depth * (d * d - 1) + rim;
            if (radius > 40 && d < 0.22) {
                double t = 1 - d / 0.22;
                bowl += depth * 0.55 * t * t;
            }
            return bowl;
        }
        double t = (d - 1) / 1.2;
        return rim * Math.exp(-t * t * 5);
    }

    private static long hash(int x, int y, int z, long salt) {
        long h = salt;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 23) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 29) * 0xD6E8FEB86659FD93L;
        h ^= z * 0x2545F4914F6CDD1DL;
        h ^= h >>> 31;
        return h * 0x9E3779B97F4A7C15L;
    }

    /** A hash turned into [0, 1). */
    static double unit(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
